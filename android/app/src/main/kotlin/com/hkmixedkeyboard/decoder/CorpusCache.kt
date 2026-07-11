package com.hkmixedkeyboard.decoder

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Binary cache for parsed corpus rows.
 *
 * The corpus ships as ~3.6 MB of CSV (≈168k rows). Parsing it — line reading,
 * comma splitting, trimming, number parsing, object allocation — is the dominant
 * cost of a cold IME start, and Android frequently kills the IME process, so that
 * cost is otherwise paid again on every reopen. After the first parse we serialize
 * the already-typed rows here; later cold starts read them back with no string
 * splitting, which is several times faster.
 *
 * The cache is strictly best-effort: any read failure (missing file, version
 * mismatch, truncated/corrupt data, schema change) returns null and the caller
 * falls back to parsing the CSV, so a broken cache can never break the dictionary.
 * Pure JVM (operates on a [File] directory) so it is unit-testable without Android.
 */
object CorpusCache {

    // Bumped if the on-disk row format ever changes shape. Combined with the app
    // build number by callers so a corpus update invalidates stale caches.
    private const val MAGIC = 0x484B4331 // "HKC1"

    /**
     * Reads cached rows for [name] written under [version], or null if absent,
     * stale, or unreadable. The trailing magic marker is checked so a file left
     * half-written by a killed process is rejected rather than read partially.
     */
    fun <T> load(dir: File, name: String, version: Int, readRow: (DataInputStream) -> T): List<T>? {
        val f = File(dir, "$name.idx")
        if (!f.exists()) return null
        return try {
            DataInputStream(BufferedInputStream(FileInputStream(f))).use { d ->
                if (d.readInt() != MAGIC) return null
                if (d.readInt() != version) return null
                val n = d.readInt()
                if (n < 0) return null
                val out = ArrayList<T>(n)
                repeat(n) { out.add(readRow(d)) }
                if (d.readInt() != MAGIC) return null // complete-file sentinel
                out
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Writes [rows] for [name] tagged with [version]. Writes to a temp file then
     * renames, so readers never observe a partial file. Best-effort: any failure
     * is swallowed (the next start simply re-parses the CSV).
     */
    fun <T> store(dir: File, name: String, version: Int, rows: List<T>, writeRow: (DataOutputStream, T) -> Unit) {
        try {
            if (!dir.exists()) dir.mkdirs()
            val tmp = File(dir, "$name.idx.tmp")
            DataOutputStream(BufferedOutputStream(FileOutputStream(tmp))).use { o ->
                o.writeInt(MAGIC)
                o.writeInt(version)
                o.writeInt(rows.size)
                for (r in rows) writeRow(o, r)
                o.writeInt(MAGIC)
            }
            val dest = File(dir, "$name.idx")
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
        } catch (e: Exception) {
            // best-effort; caching is an optimization only
        }
    }
}
