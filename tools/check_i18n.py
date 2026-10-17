#!/usr/bin/env python3
"""Validate standard Android locale resources without rewriting source or data."""
from collections import Counter
from pathlib import Path
import json
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src' / 'res'
KEY = re.compile(r'[a-z][a-z0-9_]*\Z')
FORMAT = re.compile(r'%(?:(\d+)\$)?[-#+ 0,(<]*\d*(?:\.\d+)?([a-zA-Z%])')


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def signature(text, where, formatted=True):
    if not formatted:
        return {}
    result = {}
    for match in FORMAT.finditer(text):
        position, conversion = match.groups()
        if conversion in ('%', 'n'):
            continue
        require(position is not None, f'{where}: format argument must be positional: {match.group()}')
        kind = conversion.lower()
        previous = result.get(int(position))
        require(previous in (None, kind), f'{where}: argument {position} has inconsistent types')
        result[int(position)] = kind
    return result


def resources(directory):
    result = {}
    for path in sorted(directory.glob('*.xml')):
        root = ET.parse(path).getroot()
        require(root.tag == 'resources', f'{path}: expected resources root')
        for element in root:
            if element.tag not in ('string', 'plurals'):
                continue
            name = element.attrib['name']
            require(KEY.fullmatch(name), f'{path}: invalid semantic resource name: {name}')
            require(name not in result, f'{path}: duplicate resource: {name}')
            formatted = element.attrib.get('formatted') != 'false'
            values = ({'string': ''.join(element.itertext())} if element.tag == 'string' else
                      {item.attrib['quantity']: ''.join(item.itertext()) for item in element})
            if element.tag == 'plurals':
                require('other' in values, f'{path}:{name}: missing other plural form')
                require(len(values) == len(element), f'{path}:{name}: duplicate plural quantity')
                require(set(values) <= {'zero', 'one', 'two', 'few', 'many', 'other'}, f'{path}:{name}: invalid quantity')
            for quantity, text in values.items():
                require(text == text.strip() or (text.startswith('\"') and text.endswith('\"')), f'{path}:{name}/{quantity}: quote intentional edge whitespace using Android resource syntax')
            signatures = [signature(text, f'{path}:{name}/{quantity}', formatted)
                          for quantity, text in values.items()]
            require(all(item == signatures[0] for item in signatures), f'{path}:{name}: plural argument mismatch')
            result[name] = (element.tag, signatures[0], formatted)
    return result


def main():
    default = resources(RES / 'values')
    chinese = resources(RES / 'values-zh')
    require(set(default) == set(chinese), f'English/Chinese resource key mismatch: {sorted(set(default) ^ set(chinese))}')
    for name, value in default.items():
        require(value == chinese[name], f'English/Chinese type, format argument, or formatted flag mismatch: {name}')
    # A partial extra locale is valid: Android falls back to the complete default resources.
    overlays = {}
    for directory in sorted(RES.glob('values-*')):
        if directory.name == 'values-zh':
            continue
        translated = resources(directory)
        require(set(translated) <= set(default), f'{directory}: undefined default keys: {sorted(set(translated) - set(default))}')
        for name, value in translated.items():
            require(value == default[name], f'{directory}:{name}: incompatible resource signature')
        overlays[directory.name] = len(translated)
    required = set()
    for path in (ROOT / 'src' / 'java').rglob('*.java'):
        source = path.read_text(encoding='utf-8-sig')
        required.update(re.findall(r'\b(?:s|q|failure|localFailure)\(\s*"([a-z][a-z0-9_]*)"\s*[,)]', source))
        required.update(re.findall(r'Messages\.(?:text|failure|failureAs)\(\s*"([a-z][a-z0-9_]*)"', source))
    require(required <= set(default), f'Java references missing resources: {sorted(required - set(default))}')
    aliases = json.loads((ROOT / 'src' / 'assets' / 'field-label-resources.json').read_text(encoding='utf-8'))
    original = json.loads((ROOT / 'src' / 'assets' / 'joyctl-field-names.json').read_text(encoding='utf-8'))
    require(set(aliases) == set(original), 'Field-label resource map changed the canonical alias paths')
    require(all(isinstance(value, str) and value in default and default[value][0] == 'string'
                for value in aliases.values()), 'Field-label resource map must contain existing string IDs only')
    configuration = ET.parse(RES / 'xml' / 'locales_config.xml').getroot()
    languages = [item.attrib['{http://schemas.android.com/apk/res/android}name'] for item in configuration]
    require(configuration.tag == 'locale-config', 'Expected standard Android locale-config root')
    require(len(languages) == len(set(languages)), 'Duplicate declared app languages')
    require({'en', 'zh'} <= set(languages), 'App language settings must declare English and Chinese')
    require(all(re.fullmatch(r'[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*', value) for value in languages), 'Invalid language tag in locale-config')
    counts = Counter(value[0] for value in default.values())
    print(f'Locale resource checks passed: {counts["string"]} strings, {counts["plurals"]} plurals per EN/ZH locale; '
          f'{len(required)} explicit Java references; {len(aliases)} field paths; extra overlays={overlays}')


if __name__ == '__main__':
    main()
