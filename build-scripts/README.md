# build-scripts

Everything needed to turn the source tree into installable artifacts.

| File | Purpose |
| --- | --- |
| `package-release.sh` | Linux/macOS: builds the APK, the `.deb` and the portable jar. |
| `package-release.ps1` | Windows: builds the `.exe`/`.msi` installers and the portable jar. |
| `bundle-release.sh` | Merges the per-platform outputs into the single release ZIP and writes `MANIFEST.txt`. |
| `icon/IconRenderer.java` | Renders the app icon (PNG, ICO, Android vector drawable) from one geometry description. |
| `dev-signing.jks` | The development signing key. See below. |

## Android signing

Android will not install an unsigned package - it fails with
*"App not installed as package appears to be invalid"*, with no hint that a
signature is what is missing. A release APK therefore has to be signed with
*something*, always.

### The development key (committed, not a secret)

`dev-signing.jks` is checked into the repository on purpose:

```
keystore : build-scripts/dev-signing.jks   (PKCS12)
alias    : mcgui-dev
password : mcguidev          (store and key)
subject  : CN=Minecraft GUI Designer Dev Signing
```

Its password is written here, in the build script, and in the git history, so
treat it as public. It exists so that a fresh `git clone && ./gradlew
:androidApp:assembleRelease` produces an APK you can actually install, and so
that successive local builds upgrade cleanly over each other instead of
colliding on a mismatched signature.

**Never publish an APK signed with this key.** Anyone can re-sign an update
with it.

### A real key

Drop a `keystore.properties` next to this file and the build uses it instead -
no code change required:

```properties
storeFile=build-scripts/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Both `*.jks` and `keystore.properties` are git-ignored (with a single
exception for `dev-signing.jks`), so a real key cannot be committed by
accident.

To generate one:

```bash
keytool -genkeypair -v \
  -keystore build-scripts/release.jks -storetype PKCS12 \
  -alias mcgui -keyalg RSA -keysize 4096 -validity 10950 \
  -dname "CN=Your Name, O=Your Org, C=US"
```

CI writes this file from four repository secrets - `ANDROID_KEYSTORE_BASE64`
(`base64 -w0 release.jks`), `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`
and `ANDROID_KEY_PASSWORD`. When they are absent the release workflow falls
back to the development key, so a release never fails for want of a signature.

### Checking an APK

```bash
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --verbose --print-certs app.apk
```

`Verifies` plus at least one `true` among the v2/v3 lines is what an
installable APK looks like. `minSdk` is 26, so the v1 (JAR) scheme is not
required - Android 7.0+ reads v2.
