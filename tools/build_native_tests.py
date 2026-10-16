"""Build the optional on-device instrumentation checks with the app's signing key."""
import zipfile
from toolchain import ROOT, BUILD, Toolchain, Signing, run


def main():
    tools = Toolchain(xposed=False)
    signing = Signing(tools)
    output = BUILD / 'native-tests'
    classes = output / 'classes'
    dex = output / 'dex'
    classes.mkdir(parents=True, exist_ok=True)
    dex.mkdir(exist_ok=True)
    run([tools.java_tool('javac'), '-J-Duser.language=en', '-J-Duser.country=US',
         '-encoding', 'UTF-8', '-source', '8', '-target', '8', '-Xlint:-options',
         '-classpath', tools.android_jar, '-d', classes, ROOT / 'tests/android/NativeChecks.java'])
    jar = output / 'checks.jar'
    with zipfile.ZipFile(jar, 'w') as archive:
        for path in sorted(classes.rglob('*.class')):
            archive.write(path, path.relative_to(classes).as_posix())
    run([tools.android_tool('d8'), '--min-api', '26', '--lib', tools.android_jar, '--output', dex, jar])
    manifest = output / 'AndroidManifest.xml'
    manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.muyue.joyoseleash.qa"><uses-sdk android:minSdkVersion="26" android:targetSdkVersion="35"/><application android:label="JoyoseLeash QA" android:allowBackup="false"/><instrumentation android:name="com.muyue.joyoseleash.qa.NativeChecks" android:targetPackage="com.muyue.joyoseleash"/></manifest>', encoding='utf-8')
    unsigned = output / 'unsigned.apk'
    run([tools.android_tool('aapt2'), 'link', '-I', tools.android_jar, '--manifest', manifest, '-o', unsigned])
    with zipfile.ZipFile(unsigned, 'a', zipfile.ZIP_DEFLATED) as archive:
        archive.write(dex / 'classes.dex', 'classes.dex')
    run([tools.android_tool('zipalign'), '-f', '4', unsigned, output / 'aligned.apk'])
    signing.sign(tools, output / 'aligned.apk', output / 'checks.apk')
    print(output / 'checks.apk')


if __name__ == '__main__':
    main()
