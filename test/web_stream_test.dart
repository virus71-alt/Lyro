import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:lyro/data/models/song_model.dart';
import 'package:lyro/data/repositories/preferences_repository.dart';
import 'package:lyro/data/services/web_stream_server.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  HttpOverrides.global = null; // flutter_test fakes HttpClient; we need real sockets

  late Directory dir;
  late WebStreamServer server;
  late HttpClient client;
  final bytes = List.generate(1000, (i) => i % 256);

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    dir = await Directory.systemTemp.createTemp('lyro_stream');
    final file = File('${dir.path}/song.flac')..writeAsBytesSync(bytes);
    server = WebStreamServer(prefsRepo: await PreferencesRepository.init())
      ..songs = () => [
            Song(id: 7, title: 'Song', artist: 'Artist', album: 'Album', duration: 1000, path: file.path, format: 'FLAC'),
          ];
    await server.start();
    client = HttpClient();
  });

  tearDown(() async {
    client.close(force: true);
    await server.stop();
    await dir.delete(recursive: true);
  });

  Future<HttpClientResponse> get(String path, {Map<String, String> headers = const {}, Cookie? cookie}) async {
    final port = Uri.parse(server.address!).port;
    final req = await client.get('127.0.0.1', port, path);
    headers.forEach(req.headers.set);
    if (cookie != null) req.cookies.add(cookie);
    return req.close();
  }

  test('serves the page to anyone but the library only with the key', () async {
    expect(server.running, isTrue);
    final page = await get('/');
    expect(page.statusCode, 200);
    expect(await utf8.decodeStream(page), contains('LYRO'));

    final locked = await get('/api/library');
    expect(locked.statusCode, 401);
    await locked.drain<void>();

    final wrong = await get('/api/library?k=nope');
    expect(wrong.statusCode, 401);
    await wrong.drain<void>();

    final open = await get('/api/library?k=${server.key}');
    expect(open.statusCode, 200);
    final cookie = open.cookies.single;
    final json = jsonDecode(await utf8.decodeStream(open)) as Map<String, dynamic>;
    expect((json['songs'] as List).single['t'], 'Song');

    // The cookie set by the link is enough afterwards.
    final again = await get('/api/library', cookie: cookie);
    expect(again.statusCode, 200);
    await again.drain<void>();
  });

  test('streams the file untouched, with byte ranges for seeking', () async {
    final k = server.key;
    final whole = await get('/api/stream/7?k=$k');
    expect(whole.statusCode, 200);
    expect(whole.headers.contentType?.mimeType, 'audio/flac');
    expect(await whole.expand((c) => c).toList(), bytes);

    final part = await get('/api/stream/7?k=$k', headers: {'range': 'bytes=100-199'});
    expect(part.statusCode, 206);
    expect(part.headers.value('content-range'), 'bytes 100-199/1000');
    expect(await part.expand((c) => c).toList(), bytes.sublist(100, 200));

    final tail = await get('/api/stream/7?k=$k', headers: {'range': 'bytes=-10'});
    expect(tail.statusCode, 206);
    expect(await tail.expand((c) => c).toList(), bytes.sublist(990));

    final bad = await get('/api/stream/7?k=$k', headers: {'range': 'bytes=5000-'});
    expect(bad.statusCode, 416);
    await bad.drain<void>();

    final missing = await get('/api/stream/99?k=$k');
    expect(missing.statusCode, 404);
    await missing.drain<void>();
  });

  test('a new key locks out the old one', () async {
    final old = server.key;
    await server.newKey();
    expect(server.key, isNot(old));
    final res = await get('/api/library?k=$old');
    expect(res.statusCode, 401);
    await res.drain<void>();
  });
}
