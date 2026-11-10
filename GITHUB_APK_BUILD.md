# Build APK Terra Chemica v0.8 melalui GitHub

## Perubahan v0.8
- Tampilan utama menjadi penampang skematik atmosfer, samudra, kerak basaltik, mantel, dan inti.
- Instalasi/upgrade pertama dibuka pada jangkar Arkean 3,8 Ga dan dalam keadaan jeda.
- Ada tombol untuk kembali langsung ke 3,8 Ga.
- Awan, hujan, dan kilat ditampilkan dari state model cuaca kolom tunggal. Geometri awan bersifat skematik.
- Auto Journey diperlambat menjadi 150 detik per segmen dan jeda tonggak 12 detik.

## Memperbarui repositori GitHub yang sudah ada

1. Ekstrak arsip source ini. Masuk ke folder `Nirvana-Simulator-main`.
2. Salin/ganti isi proyek tersebut ke **root** repositori `terra-serenada/Nirvana-Simulator`. Jangan membuat folder `Nirvana-Simulator-main` bersarang di root.
3. Pastikan `.github/workflows/android-apk.yml` ikut terunggah. Di GitHub, path tersebut harus persis sama.
4. Commit perubahan ke branch `main`. Push ke `main` akan memicu workflow **Build Terra Chemica APK**.
5. Buka tab **Actions**, pilih run terbaru, dan tunggu sampai statusnya `success`.
6. Pada bagian Artifacts di run itu, unduh `Terra-Chemica-v0.8.0-debug-APK`. Ekstrak ZIP artifact untuk mendapatkan `Terra-Chemica-v0.8.0-debug.apk`.

## Release opsional

Setelah build branch berhasil, tag dan push versi release:

```bash
git tag v0.8.0
git push origin v0.8.0
```

Workflow akan membangun APK debug dan melampirkannya ke GitHub Release untuk tag tersebut.

## Catatan build dan instalasi

- Build memakai JDK 17, Gradle 8.9, Android SDK Platform 35, dan Build Tools 35.0.0.
- Minimum Android: API 26. Target/compile SDK: 35.
- APK debug ditandatangani dengan debug key otomatis. Cocok untuk uji coba/sideload, bukan signing produksi atau Google Play.
- Arsip ini adalah source code, bukan APK yang sudah dikompilasi. Build APK v0.8.0 belum diverifikasi; hasilnya baru tersedia setelah GitHub Actions berhasil.
- Renderer penampang menggunakan Android Canvas. Pemeriksaan core Kotlin dan dataset lulus, tetapi toolchain Android SDK/Gradle tidak tersedia di lingkungan penyusunan arsip ini untuk menghasilkan APK lokal.

## Batas ilmiah penting

Penampang geologi adalah ilustrasi penjelas. Atmosfer cuaca masih satu kolom representatif yang diproyeksikan ke adegan; ia bukan prakiraan spasial atau rekonstruksi cuaca global Arkean. Lapisan mantel/inti dan panah konveksi tidak dihitung oleh solver geodinamika.
