"""Run format regression checks on the host; this never connects to a phone."""
from toolchain import ROOT, BUILD, Toolchain, run


def main():
    tools = Toolchain(xposed=False)
    output = BUILD / 'rule-checks'
    output.mkdir(parents=True, exist_ok=True)
    run([tools.java_tool('javac'), '-encoding', 'UTF-8', '-d', output,
         ROOT / 'src/java/com/muyue/joyoseleash/Messages.java',
         ROOT / 'src/java/com/muyue/joyoseleash/StringRules.java',
         ROOT / 'tests/java/com/muyue/joyoseleash/StringRulesChecks.java'])
    print(run([tools.java_tool('java'), '-cp', output, 'com.muyue.joyoseleash.StringRulesChecks']).strip())


if __name__ == '__main__':
    main()
