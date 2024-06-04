package com.tohsoft.transformer.merge_image

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import com.google.common.collect.ImmutableList
import kotlin.random.Random

val defaultSize = Size(1280, 720)

@OptIn(UnstableApi::class)
/**
 * Effect mapper
 *
 * @property size
 * @constructor
 *
 * @param duration: UNIT micro seconds
 */
class EffectMapper(duration: Long, var size: Size = defaultSize) {
    var duration = duration
        private set(value) {
            matrixTransformationFactory = MatrixTransformationFactory(value / 1000_000)
        }

    companion object {

    }

    fun genRandomEffects(list: Collection<Uri>, contentResolver: ContentResolver): List<EditedMediaItem> {
        val map = HashMap<Uri, Bitmap>()
        var currentUri: Uri? = null
        var nextUri: Uri? = null
        val random = Random(System.currentTimeMillis())
        val types = EffectType.entries
        val effects = ArrayList<EditedMediaItem>()
        for (uri in list) {
            if (nextUri == null) {
                nextUri = uri
                continue
            }
            currentUri = nextUri
            nextUri = uri
            if (!map.contains(uri)) {
                uri.mapToBitmap(contentResolver, size.width, size.height)?.let {
                    map[uri] = it
                }
            }
            val type = types[random.nextInt(6)]
            effects.add(genEffect(type, currentUri, nextUri, map))
        }
        return effects
    }

    fun genEffects(type: EffectType, list: Collection<Uri>, contentResolver: ContentResolver): List<EditedMediaItem> {
        val map = HashMap<Uri, Bitmap>()
        var currentUri: Uri? = null
        var nextUri: Uri? = null
        val random = Random(System.currentTimeMillis())
        val effects = ArrayList<EditedMediaItem>()
        val needBitmap = when (type) {
            EffectType.FADE, EffectType.SLIDE_LEFT, EffectType.SLIDE_RIGHT -> {
                true
            }

            else -> false
        }
        for (uri in list) {
            if (nextUri == null) {
                nextUri = uri
                continue
            }
            currentUri = nextUri
            nextUri = uri
            if (needBitmap && !map.contains(uri)) {
                uri.mapToBitmap(contentResolver, size.width, size.height)?.let {
                    map[uri] = it
                }
            }
            effects.add(genEffect(type, currentUri, nextUri, map))
        }
        return effects
    }

    private var matrixTransformationFactory = MatrixTransformationFactory(duration / 1000_000)

    fun genEffect(type: EffectType, currentUri: Uri?, nextUri: Uri?, map: Map<Uri, Bitmap>): EditedMediaItem {
        val effect = ImmutableList.Builder<Effect>()
        effect.add(Presentation.createForWidthAndHeight(
            size.width,
            size.height,
            Presentation.LAYOUT_SCALE_TO_FIT
        ))
        when (type) {
            EffectType.NONE -> {
            }

            EffectType.ROTATE -> {
                effect.add(matrixTransformationFactory.createRotateTransition())
            }

            EffectType.ZOOM_IN -> {
                effect.add(matrixTransformationFactory.createZoomInTransition())
            }

            EffectType.SLIDE_LEFT -> {
                effect.add(matrixTransformationFactory.createSlideLeftTransition())
                nextUri?.let { uri ->
                    map[uri]?.let { bitmap ->
                        effect.add(SlideFadeOverlay.toOverlayEffect(
                            bitmap,
                            duration.toFloat()
                        ))
                    }
                }
            }

            EffectType.SLIDE_RIGHT -> {
                effect.add(matrixTransformationFactory.createSlideRightTransition())
                nextUri?.let { uri ->
                    map[uri]?.let { bitmap ->
                        effect.add(SlideFadeOverlay.toOverlayEffect(
                            bitmap,
                            duration.toFloat()
                        ))
                    }
                }
            }
//            else->{}
            EffectType.FADE -> {
                nextUri?.let { uri ->
                    map[uri]?.let { bitmap ->
                        effect.add(
                            FadeOverlay.toOverlayEffect(
                                bitmap,
                                duration.toFloat()
                            )
                        )
                    }
                }
            }
        }
        return EditedMediaItem.Builder(
            MediaItem.Builder().setUri(currentUri)
                .build())
            .setEffects(Effects(ImmutableList.of(), effect.build())).setDurationUs(duration).setFrameRate(30).build()
    }
}

enum class EffectType {
    NONE, SLIDE_LEFT, SLIDE_RIGHT, ROTATE, ZOOM_IN, FADE
}