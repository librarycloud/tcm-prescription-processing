# Android APK releases

Place signed release APK files in this directory. The filename must end in
`.apk`, and `data/app-version.android.json` should point `apkUrl` to
`/app/releases/<filename>.apk` (or to an HTTPS object-storage URL).

The normal release flow is:

1. GitHub Actions builds and publishes a GitHub Release.
2. Configure `GITHUB_REPOSITORY` and optionally `GITHUB_TOKEN` on the backend.
3. In Web Admin, open **系统管理 → Android版本发布** and click **从 GitHub 同步最新版本**.
4. Android clients read the synchronized metadata and download the APK from the backend.

The backend downloads the latest Release APK and metadata, stores the APK as
`app-release.apk`, and updates `data/app-version.android.json`. The local
`npm run sync:android-release` script remains available as a maintenance
fallback, but normal publishing does not require shell access.
