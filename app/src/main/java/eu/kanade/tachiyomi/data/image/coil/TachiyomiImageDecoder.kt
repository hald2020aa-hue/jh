/*
 * Modified for the independent J2K development fork (2026-11-12).
 * See FORK_CHANGELOG.md; original licenses and attribution are preserved.
 */
package eu.kanade.tachiyomi.data.image.coil

import android.os.Build
import androidx.core.graphics.drawable.toDrawable
import coil.ImageLoader
import coil.decode.DecodeResult
import coil.decode.Decoder
import coil.decode.ImageDecoderDecoder
import coil.decode.ImageSource
import coil.fetch.SourceResult
import coil.request.Options
import eu.kanade.tachiyomi.util.system.ImageUtil
import tachiyomi.decoder.ImageDecoder

/**
 * A [Decoder] that uses built-in [ImageDecoder] to decode images that is not supported by the system.
 */
class TachiyomiImageDecoder(
    private val resources: ImageSource,
    private val options: Options,
) : Decoder {
    override suspend fun decode(): DecodeResult {
        val decoder =
            resources.sourceOrNull()?.use {
                ImageDecoder.newInstance(it.inputStream())
            }

        check(decoder != null && decoder.width > 0 && decoder.height > 0) { "Failed to initialize decoder." }

        val bitmap = decoder.decode()
        decoder.recycle()

        check(bitmap != null) { "Failed to decode image." }

        return DecodeResult(
            drawable = bitmap.toDrawable(options.context.resources),
            isSampled = false,
        )
    }

    class Factory : Decoder.Factory {
        override fun create(
            result: SourceResult,
            options: Options,
            imageLoader: ImageLoader,
        ): Decoder? {
            val type = result.source.source().peek().inputStream().use { ImageUtil.findImageType(it) }
            if (type == ImageUtil.ImageType.AVIF) {
                // Android 12+ handles AVIF via the platform image decoder. Avoid
                // the third-party libheif path that may terminate the process.
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ImageDecoderDecoder.Factory().create(result, options, imageLoader)
                } else {
                    // Preserve native support on older Android releases.
                    TachiyomiImageDecoder(result.source, options)
                }
            }
            return if (type == ImageUtil.ImageType.JXL || type == ImageUtil.ImageType.HEIF) {
                TachiyomiImageDecoder(result.source, options)
            } else {
                null
            }
        }

        override fun equals(other: Any?) = other is Factory

        override fun hashCode() = javaClass.hashCode()
    }
}
