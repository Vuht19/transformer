package androidx.media3.demo.transformer

import android.content.Context
import android.util.Log
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil.GlException
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import java.io.IOException

class CrossroadShaderPrograms(context: Context, useHdr: Boolean) :
    BaseGlShaderProgram(useHdr, 1) {
    private val VERTEX_SHADER_PATH = "shaders/fade_vertex.glsl"
    private val FRAGMENT_SHADER_PATH = "shaders/fade_fragment.glsl"

    private var glProgram: GlProgram? = null

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        return Size(inputWidth, inputHeight)
    }

    init {
        try {
            glProgram = GlProgram(
                context,
                VERTEX_SHADER_PATH,
                FRAGMENT_SHADER_PATH
            )
//            glProgram?.setFloatUniform("u_Progress", 0.5f)
        } catch (e: IOException) {
            throw VideoFrameProcessingException(e)
        } catch (e: GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            Log.d("TAG111", "drawFrame: $inputTexId presentationTimeUs: $presentationTimeUs")
            glProgram!!.use()
            glProgram!!.setFloatUniform("u_Progress", 0.4f)
            glProgram!!.bindAttributesAndUniforms()
        } catch (e: GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }
}