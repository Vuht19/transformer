package androidx.media3.demo.transformer.merge_image

import android.graphics.Matrix
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.MatrixTransformation

@OptIn(UnstableApi::class)
internal class MatrixTransformationFactory(private val durationSeconds: Long = 3) {
    private val presentationOneTimeUs
        get() = C.MICROS_PER_SECOND * durationSeconds.toFloat()

    /**
     * Transition ZoomIn
     * */
    fun createZoomInTransition(): MatrixTransformation {
        return MatrixTransformation { presentationTimeUs: Long ->
            calculateZoomInTransitionMatrix(
                presentationTimeUs
            )
        }
    }

    private fun calculateZoomInTransitionMatrix(presentationTimeUs: Long): Matrix {
        val transformationMatrix = Matrix()
        val displayTimeOfImage = if (presentationTimeUs > presentationOneTimeUs) {
            presentationTimeUs % presentationOneTimeUs
        } else {
            presentationTimeUs.toFloat()
        }
        var scale = displayTimeOfImage / (C.MICROS_PER_SECOND * durationSeconds)
        if (scale in 0.0..1.0) {
            scale = (scale * 1.75).toFloat()
            if (scale <= 1) transformationMatrix.postScale(scale, scale)
        }
        return transformationMatrix
    }

    /**
     * Transition Rotate
     * */
    fun createRotateTransition(): MatrixTransformation {
        return MatrixTransformation { presentationTimeUs: Long ->
            createRotateTransitionMatrix(
                presentationTimeUs
            )
        }
    }

    private fun createRotateTransitionMatrix(presentationTimeUs: Long): Matrix {
        val transformationMatrix = Matrix()
        val displayTimeOfImage = if (presentationTimeUs > presentationOneTimeUs) {
            presentationTimeUs % presentationOneTimeUs
        } else {
            presentationTimeUs.toFloat()
        }
        var percentagePreviewed =
            1f.coerceAtMost((displayTimeOfImage / presentationOneTimeUs)) * 2f
        if (percentagePreviewed > 1) {
            percentagePreviewed = 1f
        }
        transformationMatrix.postScale(percentagePreviewed, percentagePreviewed)
        val degrees = percentagePreviewed * 360
        transformationMatrix.postRotate(-degrees)
        return transformationMatrix
    }

    /**
     * Transition SideLeft
     * */
    fun createSlideLeftTransition(): MatrixTransformation {
        return MatrixTransformation { presentationTimeUs: Long ->
            slideLeftTransitionMatrix(
                presentationTimeUs
            )
        }
    }

    private fun slideLeftTransitionMatrix(presentationTimeUs: Long): Matrix {
        val transformationMatrix = Matrix()
        val percentageDisplayTimeOfImage = if (presentationTimeUs > presentationOneTimeUs) {
            (presentationTimeUs % presentationOneTimeUs) / presentationOneTimeUs
        } else {
            presentationTimeUs / presentationOneTimeUs
        }
        val translate: Float = 1f.coerceAtMost(percentageDisplayTimeOfImage)
        if (translate > 0.85) {
            transformationMatrix.postTranslate(-translate, 0f)
            transformationMatrix.preTranslate(-translate, 0f)
        }
        return transformationMatrix
    }

    /**
     * Transition SlideRight
     * */
    fun createSlideRightTransition(): MatrixTransformation {
        return MatrixTransformation { presentationTimeUs: Long ->
            slideRightTransitionMatrix(
                presentationTimeUs
            )
        }
    }

    private fun slideRightTransitionMatrix(presentationTimeUs: Long): Matrix {
        val transformationMatrix = Matrix()
        val percentageDisplayTimeOfImage = if (presentationTimeUs > presentationOneTimeUs) {
            (presentationTimeUs % presentationOneTimeUs) / presentationOneTimeUs
        } else {
            presentationTimeUs / presentationOneTimeUs
        }
        val translate: Float = 1f.coerceAtMost(percentageDisplayTimeOfImage)
        if (translate > 0.85) {
            transformationMatrix.postTranslate(translate, 0f)
            transformationMatrix.preTranslate(translate, 0f)
        }
        return transformationMatrix
    }

    /**
     * Transition FadeIn
     * */
    fun createSlideFadeTransition(): MatrixTransformation {
        return MatrixTransformation { presentationTimeUs: Long ->
            slideFadeTransitionMatrix(
                presentationTimeUs
            )
        }
    }

    private fun slideFadeTransitionMatrix(presentationTimeUs: Long): Matrix {
        val transformationMatrix = Matrix()
        val percentageDisplayTimeOfImage = if (presentationTimeUs > presentationOneTimeUs) {
            (presentationTimeUs % presentationOneTimeUs) / presentationOneTimeUs
        } else {
            presentationTimeUs / presentationOneTimeUs
        }
        var alpha: Float
        val translate: Float = 1f.coerceAtMost(percentageDisplayTimeOfImage)
        if (translate > 0.9 && presentationTimeUs > 0) {
            alpha = (1 - percentageDisplayTimeOfImage)
            if (alpha <= 0) {
                alpha = 0f
            } else if (alpha > 1f) {
                alpha = 1f
            }
            transformationMatrix.postScale(alpha, alpha)
        }
        return transformationMatrix
    }
}