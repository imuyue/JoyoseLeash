"""Build the native manager, LSPosed hook, root backend and KernelSU module."""
import argparse
import hashlib
import json
import os
import shutil
import sys
import zipfile
from toolchain import ROOT, BUILD, DIST, Toolchain, Signing, run, version


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--release', action='store_true', help='Require explicit release signing credentials')
    args = parser.parse_args()
    tools = Toolchain()
    signing = Signing(tools, release=args.release)
    src = ROOT / 'src'
    release_version = version()

    # A failed compiler run must not reuse stale classes or dex files.
    for name in ['classes', 'apk-dex', 'backend-dex', 'assets']:
        clean = (BUILD / name).resolve()
        if not clean.is_relative_to(BUILD.resolve()):
            raise RuntimeError('Invalid build directory')
        if clean.exists():
            shutil.rmtree(clean)
        clean.mkdir(parents=True)
    DIST.mkdir(exist_ok=True)
    argfile = BUILD / 'javac.args'
    argfile.write_text('\n'.join('"' + str(p).replace('\\', '/') + '"'
                                 for p in sorted((src / 'java').rglob('*.java'))), encoding='utf-8')
    run([tools.java_tool('javac'), '-J-Duser.language=en', '-J-Duser.country=US',
         '-encoding', 'UTF-8', '-source', '8', '-target', '8', '-Xlint:-options',
         '-classpath', str(tools.android_jar) + os.pathsep + str(tools.xposed_api),
         '-d', BUILD / 'classes', '@' + str(argfile)])

    for backend, name in [(False, 'hook'), (True, 'backend')]:
        jar = BUILD / (name + '-classes.jar')
        with zipfile.ZipFile(jar, 'w', zipfile.ZIP_DEFLATED) as archive:
            for path in sorted((BUILD / 'classes').rglob('*.class')):
                is_backend = path.name.startswith(('Backend', 'Protocol', 'Messages'))
                if (backend and is_backend) or (not backend and not path.name.startswith('Backend')):
                    archive.write(path, path.relative_to(BUILD / 'classes').as_posix())
        run([tools.android_tool('d8'), '--min-api', '26', '--lib', tools.android_jar,
             '--output', BUILD / ('backend-dex' if backend else 'apk-dex'), jar])

    with zipfile.ZipFile(DIST / 'backend.jar', 'w', zipfile.ZIP_DEFLATED) as archive:
        archive.write(BUILD / 'backend-dex/classes.dex', 'classes.dex')
    shutil.copytree(src / 'assets', BUILD / 'assets', dirs_exist_ok=True)
    shutil.copytree(ROOT / 'licenses', BUILD / 'assets/licenses', dirs_exist_ok=True)
    run([tools.android_tool('aapt2'), 'compile', '--dir', src / 'res', '-o', BUILD / 'resources.zip'])
    run([tools.android_tool('aapt2'), 'link', '-I', tools.android_jar, '--manifest', src / 'AndroidManifest.xml',
         '-A', BUILD / 'assets', '-o', BUILD / 'unsigned.apk', BUILD / 'resources.zip'])
    with zipfile.ZipFile(BUILD / 'unsigned.apk', 'a', zipfile.ZIP_DEFLATED) as archive:
        archive.write(BUILD / 'apk-dex/classes.dex', 'classes.dex')
    run([tools.android_tool('zipalign'), '-f', '4', BUILD / 'unsigned.apk', BUILD / 'aligned.apk'])
    apk = DIST / ('JoyoseLeash-' + release_version + '.apk')
    signing.sign(tools, BUILD / 'aligned.apk', apk)

    module = DIST / ('JoyoseLeash-KSU-' + release_version + '.zip')
    with zipfile.ZipFile(module, 'w', zipfile.ZIP_DEFLATED) as archive:
        for path in sorted((src / 'module').rglob('*')):
            if path.is_file():
                info = zipfile.ZipInfo(path.relative_to(src / 'module').as_posix())
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = (0o100755 if path.suffix == '.sh' else 0o100644) << 16
                archive.writestr(info, path.read_bytes().replace(b'\r\n', b'\n'))
        archive.write(ROOT / 'LICENSE', 'LICENSE')
        archive.write(ROOT / 'licenses/JoyCtl-MIT.txt', 'licenses/JoyCtl-MIT.txt')
        archive.write(apk, 'JoyoseLeash.apk')
        archive.write(DIST / 'backend.jar', 'backend.jar')
    hashes = {path.name: hashlib.sha256(path.read_bytes()).hexdigest()
              for path in [apk, module, DIST / 'backend.jar']}
    (DIST / 'SHA256.json').write_text(json.dumps(hashes, indent=2) + '\n', encoding='utf-8')
    for path in [apk, module, DIST / 'backend.jar']:
        print(path.name, path.stat().st_size, hashes[path.name])


if __name__ == '__main__':
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8')
    main()
