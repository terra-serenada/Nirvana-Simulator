package org.terrachemica.app.data

import android.content.Context
import org.terrachemica.app.core.SolarRadiationEngine
import org.terrachemica.app.core.SolarRadiationReport
import org.terrachemica.app.core.SolarSpectrumData
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.LinkedHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/** Download, checksum, cache, and select the VPL/Claire solar spectral anchors. */
object SolarSpectrumRepository {
    data class RemoteSpectrum(val fileName: String, val lookbackGa: Double, val md5: String)

    sealed class LookupResult {
        data class Available(
            val report: SolarRadiationReport,
            val sourceDescription: String,
            val interpolationDescription: String
        ) : LookupResult()

        data class Unavailable(val reason: String) : LookupResult()
    }

    // Zenodo record 17981836, file MD5 values displayed in its record metadata.
    val remoteFiles = listOf(
        RemoteSpectrum("Sun4.4Ga.txt", 4.4, "410507a37b2375ece9e2e2235cabb093"),
        RemoteSpectrum("Sun3.8Ga.txt", 3.8, "475be7cab37e4c9e314dcb9deed98e06"),
        RemoteSpectrum("Sun2.7Ga.txt", 2.7, "0ec923765beeadb410735b2318488f62"),
        RemoteSpectrum("Sun2.4Ga.txt", 2.4, "817f7b2649ae73ef5cf8f213c9234cf5"),
        RemoteSpectrum("Sun1.8Ga.txt", 1.8, "f694106a6b6e0f2ac4f7a20ddf307392"),
        RemoteSpectrum("Sun0.6Ga.txt", 0.6, "4d436397be6385b00f6b5e223f133123"),
        RemoteSpectrum("SunModern.txt", 0.0, "e2209fd85970361b8ee007c88f21b058")
    )

    private val downloadExecutor = Executors.newSingleThreadExecutor()
    private val calculationExecutor = Executors.newSingleThreadExecutor()
    private val verifiedFiles = ConcurrentHashMap.newKeySet<String>()
    private val spectrumCache = object : LinkedHashMap<String, SolarSpectrumData>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SolarSpectrumData>?): Boolean = size > 3
    }

    @Volatile private var downloadRunning = false

    fun localFile(context: Context, fileName: String): File = File(File(context.filesDir, "solar_spectra"), fileName)

    /** Fast UI-safe count: checksum verification is cached after background validation. */
    fun cachedCount(context: Context): Int = remoteFiles.count { entry ->
        localFile(context, entry.fileName).isFile && entry.fileName in verifiedFiles
    }

    fun downloadMissing(context: Context, callback: (String) -> Unit) {
        synchronized(this) {
            if (downloadRunning) {
                safeCallback(callback, "Pengunduhan spektrum sedang berjalan.")
                return
            }
            downloadRunning = true
        }
        val appContext = context.applicationContext
        downloadExecutor.execute {
            val directory = File(appContext.filesDir, "solar_spectra").apply { mkdirs() }
            var downloaded = 0
            var failed = 0
            try {
                remoteFiles.forEachIndexed { index, entry ->
                    val destination = File(directory, entry.fileName)
                    if (destination.isFile && runCatching { md5(destination) == entry.md5 }.getOrDefault(false)) {
                        verifiedFiles.add(entry.fileName)
                        safeCallback(callback, "Spektrum Matahari ${index + 1}/${remoteFiles.size}: ${entry.fileName} tervalidasi")
                        return@forEachIndexed
                    }
                    verifiedFiles.remove(entry.fileName)
                    val temporary = File(directory, entry.fileName + ".part")
                    try {
                        val url = URL("https://zenodo.org/records/17981836/files/${entry.fileName}?download=1")
                        val connection = (url.openConnection() as HttpURLConnection).apply {
                            connectTimeout = 15_000
                            readTimeout = 45_000
                            instanceFollowRedirects = true
                            setRequestProperty("User-Agent", "TerraChemica-Android/0.4")
                        }
                        try {
                            connection.connect()
                            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
                            connection.inputStream.use { input ->
                                FileOutputStream(temporary).use { output -> input.copyTo(output) }
                            }
                        } finally {
                            connection.disconnect()
                        }
                        if (md5(temporary) != entry.md5) error("Checksum MD5 tidak cocok")
                        if (!temporary.renameTo(destination)) {
                            destination.delete()
                            if (!temporary.renameTo(destination)) error("Gagal menyimpan file lokal")
                        }
                        synchronized(spectrumCache) { spectrumCache.remove(entry.fileName) }
                        verifiedFiles.add(entry.fileName)
                        downloaded++
                        safeCallback(callback, "Mengunduh spektrum Matahari: ${index + 1}/${remoteFiles.size} · ${entry.fileName}")
                    } catch (e: Exception) {
                        failed++
                        temporary.delete()
                        safeCallback(callback, "Spektrum ${entry.fileName} belum tersedia: ${e.message ?: "koneksi gagal"}")
                    }
                }
            } finally {
                synchronized(this) { downloadRunning = false }
            }

            val benchmarkText = when (val lookup = calculateAtLookback(appContext, 3.8)) {
                is LookupResult.Available -> {
                    val benchmark = SolarRadiationEngine.validateArchaeanPdfBenchmark(lookup.report)
                    if (benchmark.valid) {
                        val maximumDifference = benchmark.bandDifferences.maxOf { abs(it.differenceWm2) }
                        "Benchmark PDF 3,8 Ga cocok (maks selisih ${String.format(java.util.Locale.US, "%.2e", maximumDifference)} W/m²)."
                    } else {
                        "Benchmark PDF 3,8 Ga berbeda; cek tabel band, batas integrasi, dan versi file."
                    }
                }
                is LookupResult.Unavailable -> "Benchmark 3,8 Ga belum dapat diperiksa: ${lookup.reason}"
            }
            safeCallback(
                callback,
                "Spektrum tervalidasi: ${cachedCount(appContext)}/${remoteFiles.size}. Baru diunduh: $downloaded; gagal: $failed. $benchmarkText"
            )
        }
    }

    /** Compute a spectrum at a paleodate using exact anchors or log-linear interpolation. */
    fun calculateAtLookback(
        context: Context,
        lookbackGa: Double,
        bondAlbedo: Double? = null
    ): LookupResult {
        if (!lookbackGa.isFinite() || lookbackGa < 0.0) {
            return LookupResult.Unavailable("Umur lookback tidak valid")
        }
        if (lookbackGa > SolarRadiationEngine.SOURCE_LOOKBACK_MAX_GA) {
            return LookupResult.Unavailable(
                "Belum ada jangkar spektrum untuk ${String.format(java.util.Locale.US, "%.3f", lookbackGa)} Ga lalu. Dataset tertua adalah 4,4 Ga; tidak dilakukan ekstrapolasi ke awal pembentukan Bumi."
            )
        }
        if (lookbackGa < SolarRadiationEngine.SOURCE_LOOKBACK_MIN_GA) {
            return LookupResult.Unavailable("Tanggal berada di luar rentang dataset spektrum")
        }

        val exact = remoteFiles.firstOrNull { abs(it.lookbackGa - lookbackGa) < 1e-10 }
        val spectrum: SolarSpectrumData
        val description: String
        val method: String
        if (exact != null) {
            val loaded = loadSpectrum(context, exact) ?: return LookupResult.Unavailable(
                "${exact.fileName} belum diunduh atau checksum tidak valid"
            )
            spectrum = loaded
            description = exact.fileName
            method = "Snapshot langsung dari Claire et al. (2012)"
        } else {
            val sorted = remoteFiles.sortedByDescending { it.lookbackGa }
            val bracketIndex = sorted.indices.firstOrNull { index ->
                index < sorted.lastIndex && lookbackGa < sorted[index].lookbackGa && lookbackGa > sorted[index + 1].lookbackGa
            } ?: return LookupResult.Unavailable("Tidak ditemukan dua jangkar yang mengapit tanggal ini")
            val olderAnchor = sorted[bracketIndex]
            val newerAnchor = sorted[bracketIndex + 1]
            val older = loadSpectrum(context, olderAnchor) ?: return LookupResult.Unavailable(
                "${olderAnchor.fileName} belum diunduh atau checksum tidak valid"
            )
            val newer = loadSpectrum(context, newerAnchor) ?: return LookupResult.Unavailable(
                "${newerAnchor.fileName} belum diunduh atau checksum tidak valid"
            )
            spectrum = try {
                SolarRadiationEngine.interpolateInTime(older, newer, lookbackGa)
            } catch (e: Exception) {
                return LookupResult.Unavailable("Interpolasi spektrum gagal: ${e.message}")
            }
            description = "${olderAnchor.fileName} + ${newerAnchor.fileName}"
            method = "Interpolasi log-flux terhadap waktu di antara dua jangkar; bukan snapshot pengamatan tersendiri"
        }

        return try {
            LookupResult.Available(
                report = SolarRadiationEngine.calculateAtTopOfAtmosphere(spectrum, bondAlbedo),
                sourceDescription = description,
                interpolationDescription = method
            )
        } catch (e: Exception) {
            LookupResult.Unavailable("Integrasi spektrum gagal: ${e.message}")
        }
    }

    fun calculateAtLookbackAsync(
        context: Context,
        lookbackGa: Double,
        callback: (LookupResult) -> Unit
    ) {
        val appContext = context.applicationContext
        calculationExecutor.execute { safeResultCallback(callback, calculateAtLookback(appContext, lookbackGa)) }
    }

    private fun loadSpectrum(context: Context, remote: RemoteSpectrum): SolarSpectrumData? {
        synchronized(spectrumCache) { spectrumCache[remote.fileName]?.let { return it } }
        val file = localFile(context, remote.fileName)
        if (!file.isFile) return null
        if (remote.fileName !in verifiedFiles) {
            if (runCatching { md5(file) == remote.md5 }.getOrDefault(false)) {
                verifiedFiles.add(remote.fileName)
            } else return null
        }
        val parsed = runCatching {
            SolarRadiationEngine.parseVplSpectrum(remote.fileName, remote.lookbackGa, file.readText())
        }.getOrNull() ?: return null
        synchronized(spectrumCache) { spectrumCache[remote.fileName] = parsed }
        return parsed
    }

    private fun md5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun safeCallback(callback: (String) -> Unit, message: String) {
        runCatching { callback(message) }
    }

    private fun safeResultCallback(callback: (LookupResult) -> Unit, result: LookupResult) {
        runCatching { callback(result) }
    }
}
