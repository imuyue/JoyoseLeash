"""Run generic system-cloud regressions on Android using in-memory fixtures only.

Requires the configured ADB device and local Android/JDK toolchain. Does not
install the app, invoke live RPCs, change profiles or write any system database.
"""
import uuid
import zipfile
from toolchain import ROOT, BUILD, EVIDENCE, Toolchain, run
from device import adb


def main():
    tools = Toolchain(False)
    token = uuid.uuid4().hex
    out = BUILD / ('cloud-catalog-tests-' + token)
    classes, dex = out / 'classes', out / 'dex'
    classes.mkdir(parents=True)
    dex.mkdir()
    java = ROOT / 'src/java/com/muyue/joyoseleash'
    tests = ROOT / 'tests/java/com/muyue/joyoseleash/CloudCatalogChecks.java'
    run([tools.java_tool('javac'), '-encoding', 'UTF-8', '-source', '8', '-target', '8',
         '-Xlint:-options', '-cp', tools.android_jar, '-d', classes,
         java / 'Backend.java', java / 'Protocol.java', java / 'Messages.java', tests])
    jar = out / 'checks-classes.jar'
    with zipfile.ZipFile(jar, 'w', zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(classes.rglob('*.class')):
            archive.write(path, path.relative_to(classes).as_posix())
    run([tools.android_tool('d8'), '--min-api', '26', '--lib', tools.android_jar,
         '--output', dex, jar])
    executable = out / 'checks.jar'
    with zipfile.ZipFile(executable, 'w', zipfile.ZIP_DEFLATED) as archive:
        archive.write(dex / 'classes.dex', 'classes.dex')
    remote = '/data/local/tmp/joyose-cloud-checks-' + token + '.jar'
    try:
        adb('push', executable, remote)
        output = adb('shell', 'env', 'CLASSPATH=' + remote, 'app_process',
                     '/system/bin', 'com.muyue.joyoseleash.CloudCatalogChecks', timeout=120)
        print(output)
        EVIDENCE.mkdir(parents=True, exist_ok=True)
        (EVIDENCE / 'cloud-catalog-checks.txt').write_text(output, encoding='utf-8')
        if 'Cloud catalog checks passed:' not in output:
            raise RuntimeError('Missing test completion marker')
    finally:
        # Only remove the exact unique file this invocation created.
        adb('shell', 'rm', '-f', remote)


if __name__ == '__main__':
    main()
