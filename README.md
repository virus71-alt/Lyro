<div align="center">

```
:::        :::   :::  :::::::::   ::::::::  
:+:        :+:   :+:  :+:    :+: :+:    :+: 
+:+         +:+ +:+   +:+    +:+ +:+    +:+ 
+#+          +#++:    +#++:++#:  +#+    +:+ 
+#+           +#+     +#+    +#+ +#+    +#+ 
#+#           #+#     #+#    #+# #+#    #+# 
##########    ###     ###    ###  ########  
```

# L Y R O &nbsp;•

**Minimalist, Offline Music Player Built with Flutter & The Nothing UI Design System**

[![Flutter](https://img.shields.io/badge/Flutter-3.x-02569B?logo=flutter&logoColor=white)](https://flutter.dev)
[![Dart](https://img.shields.io/badge/Dart-3.x-0175C2?logo=dart&logoColor=white)](https://dart.dev)
[![Design System](https://img.shields.io/badge/UI%20System-Nothing%20OS%20%2F%20Vibe-D71921)](https://github.com/wangbh030722/vibe-nothing-ui-design)
[![License](https://img.shields.io/badge/License-MIT-white.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20iOS-black.svg)](#requirements)

[Key Features](#-key-features) • [Design System](#-nothing-ui-design-system) • [Architecture](#-architecture) • [Getting Started](#-getting-started) • [Audio Engine](#-audio-playback-engine)

</div>

---

## ✦ Overview

**Lyro** is a high-performance, cross-platform offline music player inspired by **Auxio** and crafted entirely around the **Nothing UI Kit**. Engineered for audiophiles and minimalists alike, Lyro strips away cloud clutter, telemetry, and bloated interfaces in favor of pure local audio playback, bit-faithful lossless FLAC reproduction, and an authentic monochrome Nothing OS aesthetic.

```
+-------------------------------------------------------------+
|  LYRO •                                         [Q]   [*]   |
|                                                             |
|  [ ALL SONGS ]   [ ALBUMS    ]   [ ARTISTS   ]   [ FOLDERS ] |
|  [ 188       ]   [ 41        ]   [ 68        ]   [ 19      ] |
|                                                             |
|  DISCOVERED ALBUMS  41                             VIEW ALL |
|  [Cover] Aam Nepali Buwaa     [Cover] Aandhii, Ityaadi      |
|                                                             |
|  SONGS  188                                        VIEW ALL |
|  [#] 7 Years               Lukas Graham       OPUS    03:57 |
|  [#] Aam Nepali Buwaa      Balen              OPUS    04:16 |
|                                                             |
|  +-------------------------------------------------------+  |
|  | [Art] 7 Years • Lukas Graham         [<] [ || ] [>]   |  |
|  | =====------------------------------------------------ |  |
|  +-------------------------------------------------------+  |
+-------------------------------------------------------------+
```

---

## ✦ Key Features

- 🎧 **Pure Offline Playback**: Direct access to local storage — zero trackers, zero cloud dependencies, no accounts.
- 🎼 **True FLAC & Lossless Support**: Native bitstream decoding for FLAC, WAV, AAC, M4A, OPUS, and MP3 audio files.
- 🎨 **Authentic Nothing UI**: Doto dot-matrix typography, variable font weights, signal-red accents (`#D71921`), and dynamic dot fields.
- ⚡ **Zero-Flicker Architecture**: Playback ticks decoupled from UI rebuilds via isolated `ValueListenable` streams.
- 🖼️ **Robust Artwork Extraction**: Android MediaStore and embedded ID3/Vorbis tag artwork caching with graceful dot-matrix fallback badges.
- 📱 **Adaptive Now Playing**: Dynamic responsive viewport scaling with frosted glass bottom sheets and scrubbable timeline.
- 🎛️ **Background Audio & Media Controls**: Full notification controls, lockscreen artwork, Android Auto & headphone wire/Bluetooth control handling via `audio_service`.
- 🌓 **Monochrome Dark & Light Themes**: Handcrafted dual-theme tokens adhering to official Nothing UI specifications.

---

## ✦ Nothing UI Design System

Lyro implements the complete **Vibe-Nothing-UI** specification translated to native Flutter widgets:

### 1. Typography Hierarchy
- **Doto Display** (`Doto-ROND-wght.ttf`): Variable dot-matrix font used for heroes, headers, and numerical indicators. Variable weight axis scaled up to `wght: 850` for bold readability.
- **Geist UI** (`Geist-Regular.ttf`, `Geist-SemiBold.ttf`): Functional interface typography, track titles, and artist credits.
- **Geist Mono** (`GeistMono-Regular.ttf`, `GeistMono-Medium.ttf`): Technical metadata badges (`FLAC`, `OPUS`, `MP3`), timestamps, and navigation labels.
- **Newsreader Italic** (`Newsreader-Italic.ttf`): Editorial accent styling.

### 2. Color Palette
| Token | Dark Theme | Light Theme | Usage |
|:---|:---:|:---:|:---|
| **Background** (`--bg`) | `#000000` | `#F2F2F2` | Primary screen canvas |
| **Surface** (`--surface`) | `#0C0C0C` | `#FFFFFF` | Card backgrounds |
| **Raised** (`--raised`) | `#171717` | `#EDEDED` | Artwork placeholders, search bars |
| **Line / Border** (`--line`) | `#262626` | `#E2E2E2` | Hairline dividers (1.0px) |
| **Primary Text** (`--primary`) | `#EDEDED` | `#1C1C1C` | High-contrast body copy |
| **Secondary Text** (`--secondary`) | `#8C8C8C` | `#585A5A` | Subtitles, artists, albums |
| **Signal Accent** (`--accent`) | `#D71921` | `#D71921` | Active track status, playback indicators |

### 3. Surface & Visual Physics
- **Hairline Borders**: `1.0px` solid borders on all elevated cards (`NothingColors.radiusSm` / `radiusMd`).
- **Dot Matrix Canvas**: Procedural background dot pattern (`NothingDotPainter`) rendering a sparse matrix across views.
- **Concentric Artwork Fallbacks**: In the absence of embedded covers, procedural vinyl grooves with bold dot-matrix initials render dynamically.

---

## ✦ Architecture

Lyro follows a scalable, testable Flutter architecture with strict separation of concerns:

```
lib/
├── core/
│   ├── constants/       # App identity, playback modes, sort keys
│   ├── permissions/     # Android 13+ Granular Media & legacy storage permissions
│   ├── theme/           # Tokens, typography, NothingTheme, dot painter
│   └── utils/           # Formatters (duration, audio codecs, sizes)
├── data/
│   ├── models/          # Song, Album, Artist, Folder data models
│   ├── repositories/    # MusicRepository (MediaStore/Query), PreferencesRepository (SharedPreferences)
│   └── services/        # PlaybackService (AudioHandler & just_audio engine)
├── features/
│   ├── library/         # All Songs, Albums, Artists, Folders, SongTile, MiniPlayer
│   ├── player/          # NowPlayingScreen, LyroProgressBar, TrackArtwork, QueueScreen
│   ├── search/          # Instant multi-field library search (Title, Artist, Album)
│   └── settings/        # Theme switcher, library rescan, audio engine diagnostics
└── shared/
    └── widgets/         # NothingCard, NothingButton, NothingStatusBadge, NothingSectionHeader
```

### High-Frequency Playback Optimization
Audio playback position events fire every ~100ms. To prevent widget tree thrashing:
- `PlayerProvider` updates dedicated `ValueNotifier<Duration>` instances (`positionNotifier` and `bufferedPositionNotifier`).
- `notifyListeners()` is strictly reserved for state transitions (`_currentSong`, `_isPlaying`, `_queue`).
- Components like `LyroProgressBar` and `SongTile` isolate updates via `ValueListenableBuilder` and granular `context.select`.

---

## ✦ Audio Playback Engine

| Format | Decoding Engine | Verification Status |
|:---:|:---:|:---:|
| **FLAC** | Native Platform Decoder / `just_audio` | Verified bit-faithful |
| **WAV** | Uncompressed PCM | Verified |
| **AAC / M4A** | Platform MediaCodec / CoreAudio | Verified |
| **OPUS** | OGG container / MediaCodec | Verified |
| **MP3** | MPEG-1 Audio Layer III | Verified |

---

## ✦ Project Identity & Specification

| Parameter | Specification |
|:---|:---|
| **Application Name** | `Lyro` |
| **Android Package Name** | `com.lyro.doyalabs` |
| **iOS Bundle Identifier** | `com.lyro.doyalabs` |
| **Min Android SDK** | Android 7.0 (API 24+) |
| **Target Android SDK** | Android 15+ (API 35+) |
| **Min iOS Version** | iOS 13.0+ |

---

## ✦ Getting Started

### Prerequisites
- [Flutter SDK](https://docs.flutter.dev/get-started/install) (`>= 3.24.0`)
- Android Studio / Xcode
- Java 17+

### Installation & Run

1. **Clone the repository**:
   ```bash
   git clone https://github.com/doyalabs/lyro.git
   cd lyro
   ```

2. **Install Flutter dependencies**:
   ```bash
   flutter pub get
   ```

3. **Verify analyzer & unit tests**:
   ```bash
   flutter test
   flutter analyze
   ```

4. **Run on connected device**:
   ```bash
   # Run on physical Android or iOS device
   flutter run
   ```

5. **Build APK**:
   ```bash
   flutter build apk --release
   ```

---

## ✦ Reusable Nothing UI Components

Lyro comes with a suite of custom Nothing UI widgets ready to be referenced or reused:

- **`NothingCard`**: Frosted glass & bordered monochrome container with optional tactile feedback.
- **`NothingButton`**: Primary (inverted display pill), Secondary (hairline border), and Accent (signal red) button variants.
- **`NothingStatusBadge`**: Pill-shaped status indicator with signal-red / monochrome dot badges.
- **`NothingSectionHeader`**: Clean typographic section divider supporting bold dot-matrix counters.
- **`TrackArtwork`**: Cached artwork renderer with automatic fallback vinyl grooves and Nothing typography.
- **`LyroProgressBar`**: Custom scrubbable timeline slider with isolated tick listening.

---

## ✦ License & Credits

- Licensed under the [MIT License](LICENSE).
- **Design Inspiration**: [Vibe-Nothing-UI-Design](https://github.com/wangbh030722/vibe-nothing-ui-design) by `@wangbh030722`.
- **Audio Architecture**: Built on top of [just_audio](https://pub.dev/packages/just_audio) & [audio_service](https://pub.dev/packages/audio_service) by Ryan Heise.
- **Font Assets**: 
  - `Doto` by Florian Kar (SIL Open Font License 1.1)
  - `Geist` & `Geist Mono` by Vercel (SIL Open Font License 1.1)
  - `Newsreader` by Production Type (SIL Open Font License 1.1)

---

<div align="center">
<sub>Designed and engineered by DOYA Labs • Built for Nothing OS aesthetic</sub>
</div>
