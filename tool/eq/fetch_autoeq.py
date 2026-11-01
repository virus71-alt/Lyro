"""Downloads AutoEQ parametric EQ profiles and writes Lyro's bundled preset file.

Profiles come from the AutoEq project (https://github.com/jaakkopasanen/AutoEq,
MIT licence): corrections computed from published measurements towards the
Harman in-ear target. Each entry below maps a model name shown in Lyro to an
AutoEQ result path (source / rig / name).

usage: python tool/eq/fetch_autoeq.py   (writes assets/eq/iem_profiles.json)
"""
import json
import re
import urllib.parse
import urllib.request

RAW = 'https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results/'

# (display name, brand, AutoEQ result dir, match note)
PROFILES = [
    ("Wan'er S.G 2 (Red Lion)", 'TANGZU', "Jaytiss/in-ear/TANGZU Wan'er 2", 'Same IEM; Red Lion is a colourway'),
    ("Wan'er S.G", 'TANGZU', "crinacle/711 in-ear/TANGZU Wan'er S.G", None),
    ('Chu 2', 'Moondrop', 'crinacle/711 in-ear/Moondrop Chu 2', None),
    ('Chu', 'Moondrop', 'Super Review/in-ear/Moondrop Chu', None),
    ('EW300 DSP', 'Simgot', 'Kazi/in-ear/Simgot Audio EW300 DSP', None),
    ('EW300', 'Simgot', 'Kazi/in-ear/Simgot Audio EW300', 'Closest for EW300 HBB'),
    ('PRX', 'KZ', 'Jaytiss/in-ear/KZ PRX', None),
    ('EDX Pro', 'KZ', 'crinacle/711 in-ear/KZ EDX Pro', 'Closest for EDX Pro 2'),
    ('Castor (switches off)', 'KZ', 'Super Review/in-ear/KZ Castor (off-off-off-off)', 'Closest for Castor Harman / Pro / Bass'),
    ('Vader (switches off)', 'KZ', 'Harpo/in-ear/KZ Vader Balanced (off-off-off-off)', 'Closest for Vader Pro'),
    ('Pure', 'Truthear', 'Super Review/in-ear/Truthear Pure', None),
    ('Zero:2', '7Hz', 'ToneDeafMonk/in-ear/7Hz Salnotes Zero 2', None),
    ('Cadenza', 'Kiwi Ears', 'Super Review/in-ear/Kiwi Ears Cadenza', 'Closest for Cadenza II'),
    ('Klean', 'KEFINE', 'Jaytiss/in-ear/KEFINE Klean', 'Closest for Klean SV'),
]

FILTER = re.compile(
    r'Filter\s+\d+:\s+ON\s+(PK|LSC|HSC|LS|HS)\s+Fc\s+([\d.]+)\s+Hz\s+Gain\s+(-?[\d.]+)\s+dB(?:\s+Q\s+([\d.]+))?')
PREAMP = re.compile(r'Preamp:\s+(-?[\d.]+)\s+dB')


def parse(text):
    preamp = float(PREAMP.search(text).group(1))
    filters = []
    for m in FILTER.finditer(text):
        kind = {'LSC': 'LS', 'HSC': 'HS'}.get(m.group(1), m.group(1))
        filters.append({
            'type': kind,
            'fc': float(m.group(2)),
            'gain': float(m.group(3)),
            'q': float(m.group(4) or 0.7),
        })
    return preamp, filters


def main():
    out = []
    for name, brand, path, note in PROFILES:
        leaf = path.split('/')[-1]
        url = RAW + urllib.parse.quote(f'{path}/{leaf} ParametricEQ.txt')
        text = urllib.request.urlopen(url, timeout=30).read().decode('utf-8')
        preamp, filters = parse(text)
        source = path.split('/')[0]
        out.append({
            'brand': brand,
            'name': name,
            'source': source,
            'note': note,
            'preamp': preamp,
            'filters': filters,
        })
        print(f'{brand} {name}: {len(filters)} filters, preamp {preamp} dB ({source})')
    with open('assets/eq/iem_profiles.json', 'w', encoding='utf-8') as f:
        json.dump({'attribution': 'AutoEq by Jaakko Pasanen (MIT), measurements by their credited sources',
                   'profiles': out}, f, indent=1, ensure_ascii=False)


if __name__ == '__main__':
    main()
