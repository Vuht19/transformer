package androidx.media3.demo.transformer.merge_image

import android.graphics.Matrix
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.MatrixTransformation

@OptIn(UnstableApi::class)
internal class MatrixTransformationFactory(
    private val durationSeconds: Long = 3,
    private val percentTransitionTime: Float = 1f / 3
) {
    private val presentationOneTimeUs
        get() = C.MICROS_PER_SECOND * durationSeconds.toFloat()

    private val timeTransitionDuration = presentationOneTimeUs * percentTransitionTime
    private val ratioOfTimeToTransitionTime = 1 / percentTransitionTime

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
        if (displayTimeOfImage < timeTransitionDuration) {
            val scale = displayTimeOfImage / presentationOneTimeUs * ratioOfTimeToTransitionTime
            if (scale in 0.0..1.0) {
                Log.d("TAG111: ", "calculateZoomInTransitionMatrix: $displayTimeOfImage $scale")
                transformationMatrix.postScale(scale, scale)
            }
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
        if (displayTimeOfImage < timeTransitionDuration) {
            val percentagePreviewed =
                1f.coerceAtMost((displayTimeOfImage / presentationOneTimeUs)) * ratioOfTimeToTransitionTime
            transformationMatrix.postScale(percentagePreviewed, percentagePreviewed)
            val degrees = percentagePreviewed * 360
            transformationMatrix.postRotate(-degrees)
            Log.d("TAG111: ", "calculateZoomInTransitionMatrix: $percentagePreviewed")
        }
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
        val percentageDisplayTimeOfImage = (presentationTimeUs % presentationOneTimeUs) / presentationOneTimeUs
        val TIME_SHOW_STATIC = 1 - percentTransitionTime
        val translate: Float = 1f.coerceAtMost(percentageDisplayTimeOfImage)
        if (translate > TIME_SHOW_STATIC) {
            val pos = (translate - TIME_SHOW_STATIC) / (0.95f - TIME_SHOW_STATIC)
            transformationMatrix.postTranslate(-pos, 0f)
            transformationMatrix.preTranslate(-pos, 0f)
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
        val percentageDisplayTimeOfImage = (presentationTimeUs % presentationOneTimeUs) / presentationOneTimeUs
        val TIME_SHOW_STATIC = 1 - percentTransitionTime
        val translate: Float = 1f.coerceAtMost(percentageDisplayTimeOfImage)
        if (translate > TIME_SHOW_STATIC) {
            val pos = (translate - TIME_SHOW_STATIC) / (0.95f - TIME_SHOW_STATIC)
            transformationMatrix.postTranslate(pos, 0f)
            transformationMatrix.preTranslate(pos, 0f)
        }
        return transformationMatrix
    }
}