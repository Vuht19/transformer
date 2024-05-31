package androidx.media3.demo.transformer.merge_image

import androidx.annotation.IntDef
import androidx.media3.demo.transformer.merge_image.Constants.TRANSITION_FADE
import androidx.media3.demo.transformer.merge_image.Constants.TRANSITION_ROTATE
import androidx.media3.demo.transformer.merge_image.Constants.TRANSITION_SLIDE_LEFT
import androidx.media3.demo.transformer.merge_image.Constants.TRANSITION_SLIDE_RIGHT
import androidx.media3.demo.transformer.merge_image.Constants.TRANSITION_ZOOM_IN

object Constants {
    const val TRANSITION_ZOOM_IN = 0
    const val TRANSITION_ROTATE = 1
    const val TRANSITION_SLIDE_LEFT = 2
    const val TRANSITION_SLIDE_RIGHT = 3
    const val TRANSITION_FADE = 4
    const val KEY_TRANSITION_VIDEO = "KEY_TRANSITION_VIDEO"

    val VIDEO_TRANSITION = arrayOf(
        "Zoom In",
        "Rotate",
        "Slide Left",
        "Slide Right",
        "fade",
    )
}

@IntDef(
    TRANSITION_ZOOM_IN,
    TRANSITION_ROTATE,
    TRANSITION_SLIDE_LEFT,
    TRANSITION_SLIDE_RIGHT,
    TRANSITION_FADE
)
@Retention(AnnotationRetention.SOURCE)
annotation class TransitionVideo