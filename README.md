# Gemini Voice Studio (Android 15 · Kotlin & Jetpack Compose)

Aplikasi Android native Material 3 Text-to-Speech berbasis Gemini API (`gemini-2.5-flash`) yang dioptimalkan untuk perangkat ARM64 (seperti Exynos 850) dan Android 15 AOSP murni.

## Cara Compile & Build APK di GitHub

1. Ekstrak seluruh isi arsip ini ke direktori root repositori GitHub Anda (pastikan folder `.github/workflows/android-build.yml`, `app/`, dan `gradle/` berada di root repo).
2. Push ke branch `main` atau `master`:
   ```bash
   git init
   git add .
   git commit -m "Initial Gemini Voice Studio Android project"
   git branch -M main
   git remote add origin https://github.com/<username>/<repo>.git
   git push -u origin main
   ```
3. Buka tab **Actions** di repositori GitHub Anda. Workflow **Build Android APK** akan berjalan otomatis (atau jalankan manual lewat **Run workflow**).
4. Setelah selesai, unduh APK dari bagian **Artifacts**:
   - `GeminiVoiceStudio-debug-apk` (siap langsung diinstal di Android 15)
   - `GeminiVoiceStudio-release-apk` (optimasi R8 & `arm64-v8a`)
