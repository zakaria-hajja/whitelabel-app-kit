# Release signing

Each app signs its release build with its own key, configured in a **gitignored** file next to its
`app.properties`:

```properties
# apps/<name>/signing.properties   (never committed; see apps/signing.properties.example)
storeFile=keys/upload.jks          # relative to apps/<name>/, or absolute
storePassword=…
keyAlias=upload
keyPassword=…
```

- **No file:** the release build is unsigned. It still assembles, which is what CI does today.
- **Incomplete file:** a missing key, or a keystore that doesn't exist, fails the build with a
  message naming the key.
- **Debug builds** always use the debug key.
- **Gitignored:** `*.jks`, `*.keystore` and `apps/*/signing.properties`. Check a new setup with
  `git check-ignore apps/<name>/signing.properties`.

## Build and check

```sh
./gradlew :apps:<name>:assembleRelease     # APK
./gradlew :apps:<name>:bundleRelease       # AAB for the Play Store
$ANDROID_HOME/build-tools/<version>/apksigner verify --print-certs apps/<name>/build/outputs/apk/release/<name>-release.apk
```

Release builds are minified (R8) with resource shrinking. App-specific R8 rules go in
`apps/<name>/proguard-rules.pro`. If R8 reports missing classes, copy the `-dontwarn` lines from
`apps/<name>/build/outputs/mapping/release/missing_rules.txt` into that file.

## Keys

- **Play App Signing:** use it. The key in `signing.properties` is then the *upload* key, and
  Google holds the app signing key, so a lost upload key can be reset.
- **One key per app:** keep each app's upload keystore and passwords in the team's password
  manager, never in the repo, chat or tickets.
- **CI (Phase 5):** the release job will write `signing.properties` and the keystore from
  repository secrets at build time, and delete them afterwards.
