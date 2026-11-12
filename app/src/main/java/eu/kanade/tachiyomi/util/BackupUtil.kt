package eu.kanade.tachiyomi.util

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.data.backup.BackupCreator
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.util.system.readAtMost
import okio.buffer
import okio.gzip
import okio.source

object BackupUtil {
    private const val MAX_BACKUP_BYTES = 128 * 1024 * 1024

    /**
     * Decode a potentially-gzipped backup.
     */
    @SuppressLint("Recycle")
    fun decodeBackup(
        context: Context,
        uri: Uri,
    ): Backup {
        val backupCreator = BackupCreator(context)

        val backupString =
            context.contentResolver.openInputStream(uri)?.source()?.buffer()?.use { original ->
                val peeked = original.peek()
                peeked.require(2)
                val magic = peeked.readShort()
                val decoded =
                    if (magic.toInt() == 0x1f8b) { // gzip header
                        original.gzip().buffer()
                    } else {
                        original
                    }
                decoded.use { source ->
                    // Limit decompressed backup size to avoid gzip bombs and OOM.
                    source.inputStream().readAtMost(MAX_BACKUP_BYTES)
                }
            } ?: throw java.io.IOException("Unable to open backup")

        return backupCreator.parser.decodeFromByteArray(Backup.serializer(), backupString)
    }
}
