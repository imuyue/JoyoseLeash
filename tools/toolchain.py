"""Shared build and device configuration; machine-specific values stay in .local/."""
from pathlib import Path
import json
import os
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

for stream in [sys.stdout, sys.stderr]:
    if hasattr(stream, 'reconfigure'):
        stream.reconfigure(encoding='utf-8', errors='replace')

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / 'build'
DIST = ROOT / 'dist'
EVIDENCE = ROOT / '.local/evidence'
CONFIG_PATH = ROOT / '.local/config.json'


def local_config():
    if not CONFIG_PATH.exists():
        return {}
    value = json.loads(CONFIG_PATH.read_text(encoding='utf-8'))
    if not isinstance(value, dict):
        raise RuntimeError('.local/config.json must contain an object')
    return value


CONFIG = local_config()


def setting(env, key, default=None):
    return os.environ.get(env) or CONFIG.get(key) or default


def as_path(value):
    path = Path(value).expanduser()
    return path if path.is_absolute() else ROOT / path


def version():
    return ET.parse(ROOT / 'src/AndroidManifest.xml').getroot().get(
        '{http://schemas.android.com/apk/res/android}versionName')


def run(args, env=None):
    result = subprocess.run([str(arg) for arg in args], cwd=ROOT,
                            capture_output=True, encoding='utf-8', errors='replace', env=env)
    if result.returncode or 'An exception has occurred in the compiler' in result.stderr:
        raise RuntimeError('Command failed: ' + str(args[0]) + '\n' + result.stdout + result.stderr)
    if result.stderr.strip():
        print(result.stderr.strip()[:2000])
    return result.stdout


def sdk_version(path):
    return tuple(int(n) for n in re.findall(r'\d+', path.parent.name))


class Toolchain:
    def __init__(self, xposed=True):
        java_home = setting('JAVA_HOME', 'java_home')
        if java_home:
            self.java = as_path(java_home) / 'bin'
        else:
            javac = shutil.which('javac')
            if not javac:
                raise RuntimeError('Set JAVA_HOME to a JDK directory (see docs/BUILD.md)')
            self.java = Path(javac).resolve().parent
        self.java_tool('javac')
        sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
        platform = setting('ANDROID_PLATFORM_JAR', 'android_jar')
        if platform:
            self.android_jar = as_path(platform)
        elif sdk:
            candidates = sorted((as_path(sdk) / 'platforms').glob('*/android.jar'), key=sdk_version)
            if not candidates:
                raise RuntimeError('No Android platform found under ANDROID_HOME/platforms')
            self.android_jar = candidates[-1]
        else:
            raise RuntimeError('Set ANDROID_HOME or ANDROID_PLATFORM_JAR (see docs/BUILD.md)')
        if not self.android_jar.is_file():
            raise RuntimeError('Android platform jar not found: ' + str(self.android_jar))
        build_tools = setting('ANDROID_BUILD_TOOLS', 'build_tools')
        if build_tools:
            self.build_tools = as_path(build_tools)
        elif sdk:
            candidates = sorted((as_path(sdk) / 'build-tools').glob('*/aapt2*'), key=sdk_version)
            if not candidates:
                raise RuntimeError('No Android build-tools found under ANDROID_HOME/build-tools')
            self.build_tools = candidates[-1].parent
        else:
            raise RuntimeError('Set ANDROID_BUILD_TOOLS (see docs/BUILD.md)')
        for name in ['aapt2', 'd8', 'zipalign', 'apksigner']:
            self.android_tool(name)
        self.xposed_api = None
        if xposed:
            api = setting('XPOSED_API_JAR', 'xposed_api_jar')
            if not api or not as_path(api).is_file():
                raise RuntimeError('Set XPOSED_API_JAR to the Xposed API 82 jar (see docs/BUILD.md)')
            self.xposed_api = as_path(api)

    def java_tool(self, name):
        path = self.java / (name + ('.exe' if os.name == 'nt' else ''))
        if not path.is_file():
            raise RuntimeError('JDK tool not found: ' + str(path))
        return path

    def android_tool(self, name):
        suffix = ('.bat' if name in ['d8', 'apksigner'] else '.exe') if os.name == 'nt' else ''
        path = self.build_tools / (name + suffix)
        if not path.is_file():
            raise RuntimeError('Android tool not found: ' + str(path))
        return path


class Signing:
    def __init__(self, tools, release=False):
        if release:
            for env in ['JOYOSELEASH_KEYSTORE', 'JOYOSELEASH_KEY_ALIAS', 'JOYOSELEASH_STORE_PASS']:
                if not os.environ.get(env):
                    raise RuntimeError('Release signing requires ' + env + ' (see docs/BUILD.md)')
        self.key = as_path(setting('JOYOSELEASH_KEYSTORE', 'keystore', '.local/signing/local-development.jks'))
        self.alias = setting('JOYOSELEASH_KEY_ALIAS', 'key_alias', 'cloudruleguard')
        self.env = dict(os.environ)
        self.env.setdefault('JOYOSELEASH_STORE_PASS', 'local-development')
        self.env.setdefault('JOYOSELEASH_KEY_PASS', self.env['JOYOSELEASH_STORE_PASS'])
        if not self.key.is_file():
            if release or os.environ.get('JOYOSELEASH_KEYSTORE'):
                raise RuntimeError('Signing keystore not found: ' + str(self.key))
            self.key.parent.mkdir(parents=True, exist_ok=True)
            run([tools.java_tool('keytool'), '-genkeypair', '-keystore', self.key,
                 '-storepass:env', 'JOYOSELEASH_STORE_PASS', '-keypass:env', 'JOYOSELEASH_KEY_PASS',
                 '-alias', self.alias, '-keyalg', 'RSA', '-keysize', '3072', '-validity', '3650',
                 '-dname', 'CN=JoyoseLeash Local Development'], env=self.env)

    def sign(self, tools, source, target):
        run([tools.android_tool('apksigner'), 'sign', '--ks', self.key, '--ks-key-alias', self.alias,
             '--ks-pass', 'env:JOYOSELEASH_STORE_PASS', '--key-pass', 'env:JOYOSELEASH_KEY_PASS',
             '--out', target, source], env=self.env)
        run([tools.android_tool('apksigner'), 'verify', '--verbose', target])
