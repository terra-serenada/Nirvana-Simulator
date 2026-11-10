# Build APK Terra Chemica melalui GitHub

## Status
- Source Android: tersedia (versi aplikasi 0.7.0).
- Workflow GitHub Actions: disertakan pada `.github/workflows/android-apk.yml` (menggunakan action GitHub versi terbaru yang diperiksa pada 10 November 2026).
- APK belum dibangun di lingkungan penyiapan ini. Build akan dijalankan oleh GitHub Actions setelah source masuk ke repository.
- Workflow menghasilkan **debug APK** yang ditandatangani dengan debug key otomatis. APK ini cocok untuk uji coba/sideload, bukan signing produksi atau Google Play.

## Cara mendapatkan APK dari GitHub

1. Buat repository baru di GitHub.
2. Ekstrak ZIP proyek ini di komputer.
3. Buka folder `terra_chemica_android` hasil ekstraksi. Upload/push **isi folder tersebut** ke root repository (folder `.github` harus terlihat di root repository).
4. Buka tab **Actions** di repository dan jalankan workflow **Build Terra Chemica APK**, atau lakukan push ke branch `main`/`master`.
5. Buka workflow run yang selesai, lalu unduh artifact bernama `Terra-Chemica-v0.7.0-debug-APK`.
6. Ekstrak artifact ZIP dari GitHub Actions untuk mendapatkan `Terra-Chemica-v0.7.0-debug.apk`.

## Membuat GitHub Release
Setelah build branch berhasil, buat dan push tag:

```bash
git tag v0.7.0
git push origin v0.7.0
```

Workflow akan membangun APK dan melampirkannya ke GitHub Release dengan tag tersebut.

## Catatan build dan instalasi
- Build menggunakan JDK 17, Gradle 8.9, Android SDK Platform 35, dan Build Tools 35.0.0. Workflow menggunakan actions/checkout v7, setup-java v6, setup-gradle v6, setup-android v4, upload-artifact v7, dan softprops/action-gh-release v3.
- Minimum Android yang dideklarasikan proyek: API 26.
- APK debug memiliki sertifikat debug otomatis. Untuk distribusi resmi, siapkan signing key privat dan GitHub Secrets lalu buat build release bertanda tangan sendiri.
- Build masih perlu diverifikasi oleh GitHub Actions pada repository sebenarnya; workflow lokal belum dapat dijalankan di lingkungan ini karena Android SDK dan Gradle tidak tersedia.
