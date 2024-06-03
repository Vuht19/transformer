package androidx.media3.demo.transformer.merge_image

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

        val displayTimeOfImage = if (presentationTimeUs > presentationOneTimeUs) {
            val coefficient = (presentationTimeUs / presentationOneTimeUs).toInt()
            (presentationTimeUs - (presentationOneTimeUs * coefficient)) / presentationOneTimeUs
        } else {
            presentationTimeUs / presentationOneTimeUs
        }
        val TIME_SHOW = 0.4f
        var alpha: Float
        val translate: Float = 1f.coerceAtMost(displayTimeOfImage)
        if (translate > TIME_SHOW) {
            alpha = (translate - TIME_SHOW) / (1 - TIME_SHOW)
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