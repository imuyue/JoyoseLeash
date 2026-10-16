"""Export only project source and documentation, excluding local/device material."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import zipfile
from toolchain import ROOT, DIST, version

TOP_FILES = ['.gitattributes', '.gitignore', 'README.md', 'LICENSE',
             'CHANGELOG.md', 'THIRD_PARTY_NOTICES.md']
SOURCE_DIRS = ['src', 'tools', 'tests', 'docs', 'licenses']
TEXT_SUFFIXES = {'.py', '.java', '.md', '.xml', '.sh', '.prop', '.json', '.txt'}
ALLOWED_SUFFIXES = TEXT_SUFFIXES | {'.png', ''}


def source_files():
    paths = [ROOT / name for name in TOP_FILES]
    for directory in SOURCE_DIRS:
        for path in (ROOT / directory).rglob('*'):
            if '__pycache__' in path.parts:
                continue
            if path.is_symlink():
                raise RuntimeError('Source export cannot include symlinks: ' + str(path))
            if path.is_file() and path.suffix not in {'.pyc', '.pyo'}:
                if path.suffix not in ALLOWED_SUFFIXES:
                    raise RuntimeError('Unexpected source file type: ' + str(path.relative_to(ROOT)))
                paths.append(path)
    for path in sorted(paths):
        if not path.is_file():
            raise RuntimeError('Required source file missing: ' + str(path.relative_to(ROOT)))
        if path.suffix in TEXT_SUFFIXES or path.suffix == '':
            text = path.read_text(encoding='utf-8')
            patterns = [r'(?i)[a-z]:[\\/]Users[\\/]',
                        r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----',
                        r'\bgh[pousr]_[A-Za-z0-9]{30,}\b',
                        r'\bgithub_pat_[A-Za-z0-9_]{30,}\b']
            if any(re.search(pattern, text) for pattern in patterns):
                raise RuntimeError('Private data pattern found in: ' + str(path.relative_to(ROOT)))
        yield path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='Validate and list public files without exporting')
    args = parser.parse_args()
    paths = list(source_files())
    if args.check:
        for path in paths:
            print(path.relative_to(ROOT).as_posix())
        print('Public files:', len(paths))
        return
    DIST.mkdir(exist_ok=True)
    archive = DIST / ('JoyoseLeash-' + version() + '-source.zip')
    prefix = 'JoyoseLeash-' + version() + '/'
    with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as output:
        for path in paths:
            info = zipfile.ZipInfo(prefix + path.relative_to(ROOT).as_posix())
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = (0o100755 if path.suffix == '.sh' else 0o100644) << 16
            data = path.read_bytes()
            if path.suffix in TEXT_SUFFIXES or path.suffix == '':
                data = data.replace(b'\r\n', b'\n')
            output.writestr(info, data)
    hashes_file = DIST / 'SHA256.json'
    hashes = json.loads(hashes_file.read_text(encoding='utf-8')) if hashes_file.exists() else {}
    digest = hashlib.sha256(archive.read_bytes()).hexdigest()
    hashes[archive.name] = digest
    hashes_file.write_text(json.dumps(hashes, indent=2) + '\n', encoding='utf-8')
    print(archive)
    print('Files:', len(paths), 'SHA256:', digest)


if __name__ == '__main__':
    main()
