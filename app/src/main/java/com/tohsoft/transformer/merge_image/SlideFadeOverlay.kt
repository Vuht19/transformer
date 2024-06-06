package com.tohsoft.transformer.merge_image

import android.graphics.Bitmap
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import com.google.common.collect.ImmutableList

@UnstableApi
class SlideFadeOverlay(private val bitmap: Bitmap, private val presentationOneTimeUs: Float, percentTransitionTime: Float = 0.3f) :
    BitmapOverlay() {
    companion object {
        fun toOverlayEffect(bitmap: Bitmap, presentationOneTimeUs: Float): OverlayEffect {
            return OverlayEffect(ImmutableList.of(SlideFadeOverlay(bitmap, presentationOneTimeUs)))
        }
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        return bitmap
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val percentDisplayTimeOfImage = (presentationTimeUs % presentationOneTimeUs) / presentationOneTimeUs
        val TIME_SHOW_STATIC = 1f - percentDisplayTimeOfImage
        var alpha: Float
        val translate: Float = 1f.coerceAtMost(percentDisplayTimeOfImage)
        if (translate > TIME_SHOW_STATIC) {
            alpha = (translate - TIME_SHOW_STATIC) / (2f * (1f - TIME_SHOW_STATIC))
            /*  alpha = (1 - percentDisplayTimeOfImage)
              if (alpha <= 0) {
                  alpha = 0f
              } else if (alpha > 1f) {
                  alpha = 1f
              }*/
            return OverlaySettings.Builder()
                .setAlpha(alpha)
                .build()
        }
        return OverlaySettings.Builder()
            .setAlpha(0f)
            .build()

    }
}