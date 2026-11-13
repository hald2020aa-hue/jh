/*
 * Modified for the independent J2K development fork (2026-11-12).
 * See FORK_CHANGELOG.md; original licenses and attribution are preserved.
 */
package eu.kanade.tachiyomi.ui.reader.viewer

import android.content.Context
import android.graphics.ImageDecoder as AndroidImageDecoder
import android.graphics.PointF
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowInsets
import android.widget.FrameLayout
import eu.kanade.tachiyomi.ui.reader.translation.ReaderBubbleOverlayView
import eu.kanade.tachiyomi.ui.reader.translation.TranslatedTextBlock
import androidx.annotation.AttrRes
import androidx.annotation.CallSuper
import androidx.annotation.StyleRes
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.isVisible
import coil.dispose
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
import com.github.chrisbanes.photoview.PhotoView
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerConfig
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonSubsamplingImageView
import eu.kanade.tachiyomi.util.system.GLUtil
import eu.kanade.tachiyomi.util.system.readAtMost
import eu.kanade.tachiyomi.util.system.animatorDurationScale
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * A wrapper view for showing page image.
 *
 * Animated image will be drawn by [PhotoView] while [SubsamplingScaleImageView] will take non-animated image.
 *
 * @param isWebtoon if true, [WebtoonSubsamplingImageView] will be used instead of [SubsamplingScaleImageView]
 * and [AppCompatImageView] will be used instead of [PhotoView]
 */
open class ReaderPageImageView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        @AttrRes defStyleAttrs: Int = 0,
        @StyleRes defStyleRes: Int = 0,
        private val isWebtoon: Boolean = false,
    ) : FrameLayout(context, attrs, defStyleAttrs, defStyleRes) {
        protected var pageView: View? = null

        /** Visible holder identity. Used to prevent placing an old chapter's OCR on a new page. */
        var translationChapterId: Long? = null
            private set
        var translationPageIndex: Int = -1
            private set
        var translationMergedSpread: Boolean = false
            private set
        private var translationOverlay: ReaderBubbleOverlayView? = null

        fun setTranslationIdentity(chapterId: Long?, pageIndex: Int, mergedSpread: Boolean = false) {
            translationMergedSpread = mergedSpread
            translationChapterId = chapterId
            translationPageIndex = pageIndex
            translationOverlay?.update(null, false, 0.83f, 1f)
        }

        fun showTranslation(
            blocks: List<TranslatedTextBlock>?,
            visible: Boolean,
            opacity: Float,
            fontScale: Float,
        ) {
            val overlay = translationOverlay ?: ReaderBubbleOverlayView(context) { pageView }.also {
                translationOverlay = it
                addView(it, MATCH_PARENT, MATCH_PARENT)
            }
            overlay.bringToFront()
            overlay.update(blocks, visible, opacity, fontScale)
        }


        private var config: Config? = null

        private var avifLoadJob: Job? = null

        var onImageLoaded: (() -> Unit)? = null
        var onImageLoadError: (() -> Unit)? = null
        var onScaleChanged: ((newScale: Float) -> Unit)? = null
        var onViewClicked: (() -> Unit)? = null

        open fun onNeedsLandscapeZoom() { }

        @CallSuper
        open fun onImageLoaded() {
            onImageLoaded?.invoke()
            translationOverlay?.invalidate()
        }

        @CallSuper
        open fun onImageLoadError() {
            onImageLoadError?.invoke()
        }

        @CallSuper
        open fun onScaleChanged(newScale: Float) {
            onScaleChanged?.invoke(newScale)
            translationOverlay?.invalidate()
        }

        @CallSuper
        open fun onViewClicked() {
            onViewClicked?.invoke()
        }

        fun setImage(
            drawable: Drawable,
            config: Config,
        ) {
            this.config = config
            if (drawable is Animatable) {
                prepareAnimatedImageView()
                setAnimatedImage(drawable, config)
            } else {
                prepareNonAnimatedImageView()
                setNonAnimatedImage(drawable, config)
            }
        }

        fun setImage(
            inputStream: InputStream,
            isAnimated: Boolean,
            config: Config,
        ) {
            avifLoadJob?.cancel()
            // The bundled native decoder can abort the entire process on some AVIF
            // files. Use the Android framework decoder on Android 12+ instead.
            val buffered = if (inputStream.markSupported()) inputStream else BufferedInputStream(inputStream)
            if (!isAnimated && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isAvifContainer(buffered)) {
                loadAvifWithAndroid(buffered, config)
            } else if (isAnimated) {
                prepareAnimatedImageView()
                setAnimatedImage(buffered, config)
            } else {
                prepareNonAnimatedImageView()
                setNonAnimatedImage(buffered, config)
            }
        }

        private fun loadAvifWithAndroid(stream: InputStream, config: Config) {
            avifLoadJob = CoroutineScope(Dispatchers.Main.immediate).launch {
                try {
                    val bitmap = withContext(Dispatchers.IO) {
                        val temporary = File.createTempFile("reader-avif-", ".avif", context.cacheDir)
                        try {
                            // A temporary file avoids duplicating a large AVIF in the heap.
                            FileOutputStream(temporary).use { output ->
                                val chunk = ByteArray(8 * 1024)
                                var size = 0L
                                while (true) {
                                    val n = stream.read(chunk)
                                    if (n < 0) break
                                    size += n
                                    if (size > 64L * 1024 * 1024) {
                                        throw IOException("AVIF image exceeds the safe input size")
                                    }
                                    output.write(chunk, 0, n)
                                }
                            }
                            val source = AndroidImageDecoder.createSource(temporary)
                            AndroidImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                                val size = info.size
                                val pixels = size.width.toLong() * size.height
                                if (pixels > 8_000_000L) {
                                    val scale = sqrt(8_000_000.0 / pixels)
                                    decoder.setTargetSize(
                                        (size.width * scale).toInt().coerceAtLeast(1),
                                        (size.height * scale).toInt().coerceAtLeast(1),
                                    )
                                }
                                decoder.allocator = AndroidImageDecoder.ALLOCATOR_SOFTWARE
                            }
                        } finally {
                            temporary.delete()
                        }
                    }
                    setImage(BitmapDrawable(context.resources, bitmap), config)
                } catch (_: CancellationException) {
                    // The reader moved to a different page.
                } catch (e: Exception) {
                    Timber.e(e, "Unable to decode AVIF using the Android framework")
                    onImageLoadError()
                }
            }
        }

        fun recycle() {
            translationOverlay?.update(null, false, 0.83f, 1f)
            avifLoadJob?.cancel()
            avifLoadJob = null
            pageView?.let {
                when (it) {
                    is SubsamplingScaleImageView -> it.recycle()
                    is AppCompatImageView -> it.dispose()
                }
                it.isVisible = false
            }
        }

        private fun prepareNonAnimatedImageView() {
            if (pageView is SubsamplingScaleImageView) return
            removeView(pageView)

            pageView =
                if (isWebtoon) {
                    WebtoonSubsamplingImageView(context)
                } else {
                    SubsamplingScaleImageView(context)
                }.apply {
                    setMaxTileSize(GLUtil.maxTextureSize)
                    setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_CENTER)
                    setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE)
                    setMinimumTileDpi(180)
                    setOnStateChangedListener(
                        object : SubsamplingScaleImageView.OnStateChangedListener {
                            override fun onScaleChanged(
                                newScale: Float,
                                origin: Int,
                            ) {
                                this@ReaderPageImageView.onScaleChanged(newScale)
                                translationOverlay?.invalidate()
                            }

                            override fun onCenterChanged(
                                newCenter: PointF?,
                                origin: Int,
                            ) {
                                translationOverlay?.invalidate()
                            }
                        },
                    )
                    setOnClickListener { this@ReaderPageImageView.onViewClicked() }
                }
            addView(pageView, MATCH_PARENT, MATCH_PARENT)
            translationOverlay?.bringToFront()
            translationOverlay?.invalidate()
        }

        protected fun SubsamplingScaleImageView.setupZoom(config: Config?) {
            // 5x zoom
            maxScale = scale * MAX_ZOOM_SCALE
            setDoubleTapZoomScale(scale * 2)

            config ?: return

            var centerV = 0f
            when (config.zoomStartPosition) {
                PagerConfig.ZoomType.Left -> {
                    setScaleAndCenter(scale, PointF(0f, 0f))
                }
                PagerConfig.ZoomType.Right -> {
                    setScaleAndCenter(scale, PointF(sWidth.toFloat(), 0f))
                    centerV = sWidth.toFloat()
                }
                PagerConfig.ZoomType.Center -> {
                    setScaleAndCenter(scale, center.also { it?.y = 0f })
                    centerV = center?.x ?: 0f
                }
            }
            val insetInfo = config.insetInfo ?: return
            val topInsets = insetInfo.topCutoutInset
            val bottomInsets = insetInfo.bottomCutoutInset
            if (insetInfo.cutoutBehavior == PagerConfig.CUTOUT_START_EXTENDED &&
                topInsets + bottomInsets > 0 &&
                insetInfo.isFullscreen
            ) {
                setScaleAndCenter(
                    scale,
                    PointF(centerV, height / (2f * scale)),
                )
            }
        }

        /**
         * Applies (or clears) the extend-past-cutout scale/pan behaviour for the given
         * [insetInfo]. Safe to call again on an already-loaded image, e.g. when fullscreen/
         * multi-window state changes, since it always resets extra space rather than leaving a
         * stale value from a previous call.
         */
        private fun SubsamplingScaleImageView.applyCutoutInsets(insetInfo: InsetInfo?) {
            insetInfo ?: return
            val topInsets = insetInfo.topCutoutInset
            val bottomInsets = insetInfo.bottomCutoutInset
            setExtendPastCutout(
                insetInfo.cutoutBehavior == PagerConfig.CUTOUT_START_EXTENDED &&
                    topInsets + bottomInsets > 0 &&
                    insetInfo.isFullscreen,
            )
            if (insetInfo.cutoutBehavior != PagerConfig.CUTOUT_IGNORE &&
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
                insetInfo.isFullscreen
            ) {
                val insets: WindowInsets? = insetInfo.insets
                setExtraSpace(
                    0f,
                    insets
                        ?.displayCutout
                        ?.boundingRectTop
                        ?.height()
                        ?.toFloat() ?: 0f,
                    0f,
                    insets
                        ?.displayCutout
                        ?.boundingRectBottom
                        ?.height()
                        ?.toFloat() ?: 0f,
                )
            } else {
                setExtraSpace(0f, 0f, 0f, 0f)
            }
        }

        /**
         * Re-applies cutout insets to an already-loaded image (e.g. after entering/exiting
         * multi-window mode) without reloading the image or resetting to the initial zoom-start
         * position.
         */
        fun refreshCutoutInsets(config: Config) {
            (pageView as? SubsamplingScaleImageView)?.apply {
                applyCutoutInsets(config.insetInfo)
                center?.let { setScaleAndCenter(scale, it) }
            }
        }

        private fun setNonAnimatedImage(
            image: Any,
            config: Config,
        ) = (pageView as? SubsamplingScaleImageView)?.apply {
            setDoubleTapZoomDuration(config.zoomDuration.getSystemScaledDuration())
            setMinimumScaleType(config.minimumScaleType)
            setMinimumDpi(1) // Just so that very small image will be fit for initial load
            setCropBorders(config.cropBorders)
            applyCutoutInsets(config.insetInfo)
            setOnImageEventListener(
                object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                    override fun onReady() {
                        // 5x zoom
                        setupZoom(config)
                        this@ReaderPageImageView.onNeedsLandscapeZoom()
                        this@ReaderPageImageView.onImageLoaded()
                    }

                    override fun onImageLoadError(e: Exception) {
                        this@ReaderPageImageView.onImageLoadError()
                    }
                },
            )

            when (image) {
                is Drawable -> {
                    val bitmap = (image as BitmapDrawable).bitmap
                    setImage(ImageSource.bitmap(bitmap))
                }
                is InputStream -> setImage(ImageSource.inputStream(image))
                else -> throw IllegalArgumentException("Not implemented for class ${image::class.simpleName}")
            }
            isVisible = true
        }

        private fun prepareAnimatedImageView() {
            if (pageView is AppCompatImageView) return
            removeView(pageView)

            pageView =
                if (isWebtoon) {
                    AppCompatImageView(context)
                } else {
                    PhotoView(context)
                }.apply {
                    adjustViewBounds = true

                    if (this is PhotoView) {
                        setScaleLevels(1F, 2F, MAX_ZOOM_SCALE)
                        // Force 2 scale levels on double tap
                        setOnDoubleTapListener(
                            object : GestureDetector.SimpleOnGestureListener() {
                                override fun onDoubleTap(e: MotionEvent): Boolean {
                                    if (scale > 1F) {
                                        setScale(1F, e.x, e.y, true)
                                    } else {
                                        setScale(2F, e.x, e.y, true)
                                    }
                                    return true
                                }

                                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                                    this@ReaderPageImageView.onViewClicked()
                                    return super.onSingleTapConfirmed(e)
                                }
                            },
                        )
                        setOnScaleChangeListener { _, _, _ ->
                            this@ReaderPageImageView.onScaleChanged(scale)
                        }
                        // The imageMatrix changes when the user drags without changing scale.
                        // Follow PhotoView panning so Arabic never floats over the wrong balloon.
                        setOnViewDragListener { _, _ -> translationOverlay?.invalidate() }
                    }
                }
            addView(pageView, MATCH_PARENT, MATCH_PARENT)
            translationOverlay?.bringToFront()
            translationOverlay?.invalidate()
        }

        private fun setAnimatedImage(
            image: Any,
            config: Config,
        ) = (pageView as? AppCompatImageView)?.apply {
            if (this is PhotoView) {
                setZoomTransitionDuration(config.zoomDuration.getSystemScaledDuration())
            }

            val data =
                try {
                    when (image) {
                        is Drawable -> image
                        is InputStream -> ByteBuffer.wrap(image.readAtMost(MAX_ANIMATED_IMAGE_BYTES))
                        else -> throw IllegalArgumentException("Not implemented for class ${image::class.simpleName}")
                    }
                } catch (e: IOException) {
                    Timber.w(e, "Animated image exceeds safe decode size or could not be read")
                    this@ReaderPageImageView.onImageLoadError()
                    return@apply
                }
            val request =
                ImageRequest
                    .Builder(context)
                    .data(data)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .target(
                        onSuccess = { result ->
                            setImageDrawable(result)
                            (result as? Animatable)?.start()
                            isVisible = true
                            this@ReaderPageImageView.onImageLoaded()
                        },
                        onError = {
                            this@ReaderPageImageView.onImageLoadError()
                        },
                    ).crossfade(false)
                    .build()
            context.imageLoader.enqueue(request)
        }

        private fun Int.getSystemScaledDuration(): Int = (this * context.animatorDurationScale).toInt().coerceAtLeast(1)

        /**
         * All of the config except [zoomDuration] will only be used for non-animated image.
         */
        data class Config(
            val zoomDuration: Int,
            val minimumScaleType: Int = SCALE_TYPE_CENTER_INSIDE,
            val cropBorders: Boolean = false,
            val zoomStartPosition: PagerConfig.ZoomType = PagerConfig.ZoomType.Center,
            val landscapeZoom: Boolean = false,
            val insetInfo: InsetInfo? = null,
            val hingeGapSize: Int = 0,
        )

        data class InsetInfo(
            val cutoutBehavior: Int,
            val topCutoutInset: Float,
            val bottomCutoutInset: Float,
            val isFullscreen: Boolean,
            val insets: WindowInsets?,
        )
    }

private const val MAX_ZOOM_SCALE = 5F
private const val MAX_ANIMATED_IMAGE_BYTES = 64 * 1024 * 1024
