package androidx.media3.demo.transformer.merge_image

import android.graphics.Bitmap
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import com.google.common.collect.ImmutableList

@UnstableApi
class SlideFadeOverlay(private val bitmap: Bitmap, private val presentationOneTimeUs: Float) :
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
        val percentDisplayTimeOfImage = if (presentationTimeUs > presentationOneTimeUs) {
            (presentationTimeUs % presentationOneTimeUs) / presentationOneTimeUs
        } else {
            presentationTimeUs / presentationOneTimeUs
        }
        var alpha: Float
        val translate: Float = 1f.coerceAtMost(percentDisplayTimeOfImage)
        if (translate > 0.5 && presentationTimeUs > 0) {
            alpha = (1 - percentDisplayTimeOfImage)
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