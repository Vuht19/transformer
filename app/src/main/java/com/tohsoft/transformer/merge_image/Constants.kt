package com.tohsoft.transformer.merge_image

import androidx.annotation.IntDef
import com.tohsoft.transformer.merge_image.Constants.NONE
import com.tohsoft.transformer.merge_image.Constants.TRANSITION_FADE
import com.tohsoft.transformer.merge_image.Constants.TRANSITION_ROTATE
import com.tohsoft.transformer.merge_image.Constants.TRANSITION_SLIDE_LEFT
import com.tohsoft.transformer.merge_image.Constants.TRANSITION_SLIDE_RIGHT
import com.tohsoft.transformer.merge_image.Constants.TRANSITION_ZOOM_IN

object Constants {
    const val NONE = 0
    const val TRANSITION_ZOOM_IN = 1
    const val TRANSITION_ROTATE = 2
    const val TRANSITION_SLIDE_LEFT = 3
    const val TRANSITION_SLIDE_RIGHT = 4
    const val TRANSITION_FADE = 5
    const val KEY_TRANSITION_VIDEO = "KEY_TRANSITION_VIDEO"

    val VIDEO_TRANSITION = arrayOf(
        "None",
        "Zoom In",
        "Rotate",
        "Slide Left",
        "Slide Right",
        "Fade",
    )
}

@IntDef(
    NONE,
    TRANSITION_ZOOM_IN,
    TRANSITION_ROTATE,
    TRANSITION_SLIDE_LEFT,
    TRANSITION_SLIDE_RIGHT,
    TRANSITION_FADE
)
@Retention(AnnotationRetention.SOURCE)
annotation class TransitionVideo