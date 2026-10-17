#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Build a dependency-free, independent editor APK with the installed Android tools."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk', type=Path, required=True)
    parser.add_argument('--jdk', type=Path, required=True)
    parser.add_argument('--keystore', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--observer', action='store_true')
    args = parser.parse_args()
    package = 'org.x2m7.trime.' + ('editorobserver' if args.observer else 'editorfixture')
    source = Path(__file__).resolve().parent
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    bt = args.sdk / 'build-tools/36.0.0'
    jar = args.sdk / 'platforms/android-35/android.jar'
    if not jar.is_file():
        jar = args.sdk / 'platforms/android-37/android.jar'
    import os
    env = dict(os.environ, JAVA_HOME=str(args.jdk), PATH=str(args.jdk / 'bin') + os.pathsep + os.environ['PATH'])

    def run(*command):
        subprocess.run([str(v) for v in command], env=env, check=True)

    for name in ('gen', 'classes', 'dex'):
        (out / name).mkdir()
    run(bt / 'aapt2', 'compile', '--dir', source / 'res', '-o', out / 'res.zip')
    run(bt / 'aapt2', 'link', '-I', jar, '--manifest', source / ('ObserverManifest.xml' if args.observer else 'AndroidManifest.xml'), '--java', out / 'gen', '-o', out / 'unsigned.apk', out / 'res.zip')
    run(args.jdk / 'bin/javac', '--release', '8', '-classpath', jar, '-d', out / 'classes',
        source / ('src/Observer.java' if args.observer else 'src/EditorActivity.java'), *sorted((out / 'gen').rglob('*.java')))
    run(bt / 'd8', '--min-api', '21', '--lib', jar, '--output', out / 'dex', *sorted((out / 'classes').rglob('*.class')))
    with zipfile.ZipFile(out / 'unsigned.apk', 'a') as apk:
        apk.write(out / 'dex/classes.dex', 'classes.dex')
    run(bt / 'zipalign', '-P', '16', '4', out / 'unsigned.apk', out / 'editor-fixture.apk')
    run(bt / 'apksigner', 'sign', '--ks', args.keystore, '--ks-key-alias', 'androiddebugkey', '--ks-pass', 'pass:android', '--key-pass', 'pass:android', out / 'editor-fixture.apk')
    run(bt / 'apksigner', 'verify', out / 'editor-fixture.apk')
    identity = {'package': package, 'sha256': hashlib.sha256((out / 'editor-fixture.apk').read_bytes()).hexdigest(),
                'sources': {str(f.relative_to(source)): hashlib.sha256(f.read_bytes()).hexdigest() for f in sorted(source.rglob('*')) if f.is_file() and '__pycache__' not in f.parts}}
    (out / 'identity.json').write_text(json.dumps(identity, indent=2) + '\n')
    print(json.dumps(identity), flush=True)


if __name__ == '__main__':
    main()
