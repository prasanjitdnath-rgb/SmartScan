# Smart Scan (Android, Kotlin + Jetpack Compose)

No login/signup. Features: smart document scanner (auto edge-detect, crop, filters, gallery import, multi-page),
on-device OCR (ML Kit), PDF + JPEG output, copy/share text, share/print/rename/delete/save-as-JPEG, AdMob banner + interstitial.

## Build
1. Open this folder in Android Studio (Koala+), let Gradle sync, press Run, or Build > Build APK(s).
2. CLI (Gradle 8.7, JDK 17, Android SDK 34): `gradle assembleDebug` -> app/build/outputs/apk/debug/app-debug.apk
3. No local setup: push to GitHub; .github/workflows/build.yml builds the APK and uploads it as an artifact.

## Ads
Uses Google TEST ad IDs. Replace in AndroidManifest.xml (APPLICATION_ID) and AdManager.kt (BANNER_ID, INTERSTITIAL_ID) before publishing.
Needs a device with Google Play services (scanner and OCR models are delivered by Play services).
