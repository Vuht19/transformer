package com.tohsoft.transformer.merge_image

import android.graphics.Bitmap
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import com.google.common.collect.ImmutableList


@UnstableApi
class FadeOverlay(private val bitmap: Bitmap, private val presentationOneTimeUs: Float) :
    BitmapOverlay() {

    companion object {
        fun toOverlayEffect(bitmap: Bitmap, presentationOneTimeUs: Float): OverlayEffect {
            return OverlayEffect(ImmutableList.of(FadeOverlay(bitmap, presentationOneTimeUs)))
        }
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        return bitmap
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {

        val coefficient = (presentationTimeUs / presentationOneTimeUs).toInt()
        val displayTimeOfImage = (presentationTimeUs - (presentationOneTimeUs * coefficient)) / presentationOneTimeUs
        val TIME_SHOW_STATIC = 0.7f
        var alpha: Float
        val translate: Float = 1f.coerceAtMost(displayTimeOfImage)
        if (translate > TIME_SHOW_STATIC) {
            alpha = (translate - TIME_SHOW_STATIC) / (1 - TIME_SHOW_STATIC)
            if (alpha <= 0) {
                alpha = 0f
            } else if (alpha > 1f) {
                alpha = 1f
            }
            return OverlaySettings.Builder()
                .setAlphaScale(alpha)
                .build()

        }
        return OverlaySettings.Builder()
            .setAlphaScale(0f)
            .build()

    }
}