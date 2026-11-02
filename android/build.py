"""Build using caller-supplied Android SDK/JDK; no Gradle or downloaded app code."""
import os
from pathlib import Path
import secrets
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent
sdk_override = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
sdk_candidates = [Path.home() / 'Android' / 'Sdk', Path.home() / '.cache' / 'recarga-android-sdk']
SDK = Path(sdk_override).expanduser() if sdk_override else next(
    (path for path in sdk_candidates if path.is_dir()), sdk_candidates[0])
TOOLS = SDK / 'build-tools' / '36.0.0'
PLATFORM = SDK / 'platforms' / 'android-36' / 'android.jar'
BUILD = ROOT / 'build'
for name in ['generated', 'classes', 'dex']:
    (BUILD/name).mkdir(parents=True, exist_ok=True)

def run(*args, env=None):
    subprocess.run([str(a) for a in args], check=True, env=env, cwd=ROOT)

run(TOOLS/'aapt2', 'compile', '--dir', ROOT/'res', '-o', BUILD/'compiled.zip')
run(TOOLS/'aapt2', 'link', '-o', BUILD/'resources.apk', '-I', PLATFORM,
    '--manifest', ROOT/'AndroidManifest.xml', '--min-sdk-version', '28', '--target-sdk-version', '35',
    '--version-code', '11', '--version-name', '0.4.2-rc.1', '--java', BUILD/'generated',
    '-A', ROOT/'assets', BUILD/'compiled.zip')
sources = sorted((ROOT/'src').rglob('*.java')) + sorted((BUILD/'generated').rglob('*.java'))
run('javac', '-encoding', 'UTF-8', '--release', '8', '-classpath', PLATFORM,
    '-d', BUILD/'classes', *sources)
classes = sorted((BUILD/'classes').rglob('*.class'))
run(TOOLS/'d8', '--lib', PLATFORM, '--min-api', '28', '--output', BUILD/'dex', *classes)
with zipfile.ZipFile(BUILD/'resources.apk') as original, zipfile.ZipFile(BUILD/'unsigned.apk', 'w') as target:
    for item in original.infolist(): target.writestr(item, original.read(item.filename))
    target.write(BUILD/'dex'/'classes.dex', 'classes.dex')
run(TOOLS/'zipalign', '-f', '4', BUILD/'unsigned.apk', BUILD/'aligned.apk')
state = ROOT.parent / '.state'
state.mkdir(exist_ok=True, mode=0o700)
password_file = state/'android-signing-password'
if not password_file.exists():
    with password_file.open('x') as f: f.write(secrets.token_urlsafe(32))
    password_file.chmod(0o600)
env = dict(os.environ, MANDO_SIGNING_PASSWORD=password_file.read_text())
keystore = state/'android-signing.jks'
if not keystore.exists():
    run('keytool', '-genkeypair', '-keystore', keystore, '-storepass:env', 'MANDO_SIGNING_PASSWORD',
        '-keypass:env', 'MANDO_SIGNING_PASSWORD', '-alias', 'mando', '-keyalg', 'RSA', '-keysize', '3072',
        '-validity', '3650', '-dname', 'CN=Mando Personal', env=env)
    keystore.chmod(0o600)
run(TOOLS/'apksigner', 'sign', '--ks', keystore, '--ks-key-alias', 'mando',
    '--ks-pass', 'env:MANDO_SIGNING_PASSWORD', '--out', ROOT/'mando-personal.apk', BUILD/'aligned.apk', env=env)
run(TOOLS/'apksigner', 'verify', '--verbose', ROOT/'mando-personal.apk')
print('APK:', ROOT/'mando-personal.apk')
