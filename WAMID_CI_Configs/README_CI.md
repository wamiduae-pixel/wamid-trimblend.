# Online Build (CI/CD) for WAMID TrimBlend

This folder gives you *three* cloud build options:

- **GitHub Actions** → `.github/workflows/android-release.yml`
- **GitLab CI** → `.gitlab-ci.yml`
- **Buddy** → `buddy-pipeline.yml`

## Secrets to add

**All platforms** (for signed builds):
- `SIGNING_KEYSTORE_BASE64` — your `.jks` file base64-encoded
- `SIGNING_KEYSTORE_PASSWORD`
- `SIGNING_KEY_ALIAS`
- `SIGNING_KEY_PASSWORD`

To create & encode a keystore:
```bash
keytool -genkeypair -v -keystore wamid.keystore -alias wamid -keyalg RSA -keysize 2048 -validity 3650
base64 -w0 wamid.keystore > keystore.b64
```

### GitHub Actions
1. Push your Android project to GitHub.
2. Add the secrets in **Settings → Secrets and variables → Actions**.
3. Run the workflow from the **Actions** tab (or push a tag `v1.0.0`).  
   Output: download from the job **Artifacts** named `wamid-trimblend-release-apk`.

### GitLab CI
1. Push repo to GitLab, enable Pipelines.
2. Add variables in **Settings → CI/CD → Variables**.
3. Run the pipeline; APKs show in **Job Artifacts**.

### Buddy
1. Connect your repo to **buddy.works**.
2. Create pipeline from `buddy-pipeline.yml` actions.
3. Upload your keystore securely in Buddy and map variables.
4. Run pipeline; grab the APK from `/builds/` artifacts.

## Notes
- These configs assume `compileSdk=34`, `build-tools=34.0.0`, Java 17. Adjust if you change Gradle.
- If you don’t want to sign in CI, remove the sign step; you’ll still get an **unsigned** APK.
- OpenCV is pulled from Maven Central (`org.opencv:opencv-android:4.9.0`) — no NDK steps needed.

