package com.mohdshayan.rainfold.data

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads and writes the export file through the system document picker's Uri. */
object MixFiles {
    private const val MAX_BYTES = 2 * 1024 * 1024

    suspend fun write(resolver: ContentResolver, uri: Uri, text: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null
        }.getOrDefault(false)
    }

    /**
     * Null when the file cannot be read. A file far too large to be a mix file reads as empty text, which
     * the codec then names "not a Rainfold mix file"; only the first [MAX_BYTES] + 1 bytes are ever loaded.
     */
    suspend fun read(resolver: ContentResolver, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            resolver.openInputStream(uri)?.use { input ->
                val buf = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(64 * 1024)
                while (buf.size() <= MAX_BYTES) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    buf.write(chunk, 0, n)
                }
                if (buf.size() > MAX_BYTES) "" else buf.toString(Charsets.UTF_8.name())
            }
        }.getOrNull()
    }
}
