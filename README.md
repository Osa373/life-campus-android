# Life Campus — حياتي وجامعتي (Android)

Android WebView wrapper for the existing Life Campus Cloudflare app at https://life-campus.osamah-arishi.workers.dev/.

## Download APK

Open [Releases](https://github.com/Osa373/life-campus-android/releases) to download an Android test APK when the automated build finishes.

## Build

GitHub Actions runs Gradle 8.9, JDK 17, Android SDK 35 and uploads `life-campus-debug.apk`. The first successful build publishes the test APK as release `v0.1.0`.

**Warning:** The test APK uses GitHub Actions' temporary debug signing key. Later APKs may not update it in place. Uninstall and reinstall test versions when necessary. Before using for production, configure a private, permanent release signing key and keep that keystore and its passwords out of this public repository.

This is a site wrapper, not a native rewrite. It requires internet, and native camera/file upload/notification flows are not guaranteed without further Android integration and testing.

Package ID: `sa.osa373.lifecampus`.