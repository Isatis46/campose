#!/usr/bin/env bash
set -euo pipefail
repository_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
source "$repository_dir/scripts/cloud-env.sh"
[[ "$(uname -s)" == Linux && "$(uname -m)" == x86_64 ]] || { echo 'Use Android Studio for non-Linux/x86_64 hosts.' >&2; exit 1; }
mkdir -p "$CAMPOSE_TOOLCHAIN_DIR/downloads" "$ANDROID_HOME/cmdline-tools" "$ANDROID_USER_HOME" "$GRADLE_USER_HOME" "$XDG_RUNTIME_DIR"
chmod 700 "$XDG_RUNTIME_DIR"

if [[ ! -x "$JAVA_HOME/bin/javac" ]]; then
    curl -fsSL --retry 3 'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.14%2B7/OpenJDK17U-jdk_x64_linux_hotspot_17.0.14_7.tar.gz' -o "$CAMPOSE_TOOLCHAIN_DIR/downloads/jdk.tar.gz"
    printf '%s  %s\n' 'a3af83983fb94dd7d11b13ba2dba0fb6819dc2caaf87e6937afd22ad4680ae9a' "$CAMPOSE_TOOLCHAIN_DIR/downloads/jdk.tar.gz" | sha256sum --check
    mkdir -p "$JAVA_HOME"
    tar -xzf "$CAMPOSE_TOOLCHAIN_DIR/downloads/jdk.tar.gz" -C "$JAVA_HOME" --strip-components=1
fi
if [[ ! -x "$ANDROID_HOME/cmdline-tools/19.0/bin/sdkmanager" ]]; then
    curl -fsSL --retry 3 'https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip' -o "$CAMPOSE_TOOLCHAIN_DIR/downloads/cmdline-tools.zip"
    # Checksum from Google's HTTPS repository2-1.xml, cmdline-tools;19.0 Linux archive.
    printf '%s  %s\n' '5fdcc763663eefb86a5b8879697aa6088b041e70' "$CAMPOSE_TOOLCHAIN_DIR/downloads/cmdline-tools.zip" | sha1sum --check
    unzip -q "$CAMPOSE_TOOLCHAIN_DIR/downloads/cmdline-tools.zip" -d "$ANDROID_HOME/cmdline-tools"
    mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/19.0"
fi

# Retain normal certificate verification; trust the cloud platform's installed CA.
trust_source="$JAVA_HOME/lib/security/cacerts"
if [[ -f /etc/ssl/certs/java/cacerts ]]; then trust_source=/etc/ssl/certs/java/cacerts; fi
cp "$trust_source" "$CAMPOSE_TOOLCHAIN_DIR/java-cacerts"
if [[ -f /usr/local/share/ca-certificates/environment-proxy-ca.crt ]]; then
    "$JAVA_HOME/bin/keytool" -importcert -noprompt -alias campose-environment-proxy \
        -file /usr/local/share/ca-certificates/environment-proxy-ca.crt \
        -keystore "$CAMPOSE_TOOLCHAIN_DIR/java-cacerts" -storepass changeit
fi
python3 - <<'PY'
import os
from pathlib import Path
from urllib.parse import urlparse
root = Path(os.environ['CAMPOSE_TOOLCHAIN_DIR'])
properties = [f'org.gradle.java.home={root}/jdk', f'systemProp.javax.net.ssl.trustStore={root}/java-cacerts']
options = [f'-Djavax.net.ssl.trustStore={root}/java-cacerts']
proxy = urlparse(os.environ.get('HTTPS_PROXY', ''))
if proxy.hostname:
    for scheme in ('http', 'https'):
        properties += [f'systemProp.{scheme}.proxyHost={proxy.hostname}', f'systemProp.{scheme}.proxyPort={proxy.port or 80}']
        options += [f'-D{scheme}.proxyHost={proxy.hostname}', f'-D{scheme}.proxyPort={proxy.port or 80}']
(root / 'gradle-home' / 'gradle.properties').write_text('\n'.join(properties) + '\n')
(root / 'java-options').write_text(' '.join(options))
PY
source "$repository_dir/scripts/cloud-env.sh"
# Finite input avoids the broken-pipe exit status of `yes` with pipefail.
python3 -c 'print("y\n" * 100)' | sdkmanager --sdk_root="$ANDROID_HOME" 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools'
cd "$repository_dir"
./gradlew --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
