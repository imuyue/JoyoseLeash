"""Exercise the installer helpers with a mocked package manager, entirely locally."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
from toolchain import ROOT


def main():
    if os.name == 'nt':
        git = shutil.which('git')
        shell = Path(git).parents[1] / 'bin/bash.exe' if git else None
        if shell is None or not shell.is_file():
            raise RuntimeError('Installer checks on Windows require Git for Windows Bash')
    else:
        shell = shutil.which('sh')
        if not shell:
            raise RuntimeError('Installer checks require a POSIX shell')
    output = ROOT / '.local/installer-checks'
    output.mkdir(parents=True, exist_ok=True)
    work = Path(tempfile.mkdtemp(prefix='run-', dir=output))
    env = dict(os.environ)
    env['MSYS_NO_PATHCONV'] = '1'
    for source in (ROOT / 'src/module').glob('*.sh'):
        result = subprocess.run([str(shell), '-n', source.as_posix()], env=env, capture_output=True,
                                encoding='utf-8', errors='replace')
        if result.returncode:
            raise RuntimeError(result.stdout + result.stderr)
    result = subprocess.run([str(shell), (ROOT / 'tests/shell/installer-checks.sh').as_posix(),
                             (ROOT / 'src/module/apk.sh').as_posix(), work.as_posix()], env=env,
                            capture_output=True, encoding='utf-8', errors='replace')
    if result.returncode:
        raise RuntimeError(result.stdout + result.stderr)
    print(result.stdout.strip())


if __name__ == '__main__':
    main()
