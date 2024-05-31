/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.demo.transformer

import android.Manifest.permission
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.DebugViewProvider
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.ClippingConfiguration
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.Assertions
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.Log
import androidx.media3.common.util.Util
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.demo.transformer.merge_image.Constants
import androidx.media3.demo.transformer.merge_image.Constants.TRANSITION_FADE
import androidx.media3.demo.transformer.merge_image.Constants.TRANSITION_ROTATE
import androidx.media3.demo.transformer.merge_image.Constants.TRANSITION_SLIDE_LEFT
import androidx.media3.demo.transformer.merge_image.Constants.TRANSITION_SLIDE_RIGHT
import androidx.media3.demo.transformer.merge_image.Constants.TRANSITION_ZOOM_IN
import androidx.media3.demo.transformer.merge_image.FadeOverlay
import androidx.media3.demo.transformer.merge_image.MatrixTransformationFactory
import androidx.media3.demo.transformer.merge_image.SlideFadeOverlay
import androidx.media3.demo.transformer.merge_image.mapToBitmap
import androidx.media3.demo.transformer.merge_image.mapToBitmapList
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.Contrast
import androidx.media3.effect.DebugTraceUtil
import androidx.media3.effect.DrawableOverlay
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.SingleColorLut
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.exoplayer.util.DebugTextViewHelper
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.DefaultMuxer
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.InAppMuxer
import androidx.media3.transformer.JsonUtil
import androidx.media3.transformer.Muxer
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.common.base.Stopwatch
import com.google.common.base.Ticker
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.checkerframework.checker.nullness.qual.MonotonicNonNull
import org.checkerframework.checker.nullness.qual.RequiresNonNull
import org.json.JSONException
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.Arrays
import java.util.LinkedList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/** An [Activity] that exports and plays media using [Transformer].  */
class TransformerActivity : AppCompatActivity() {
    private var displayInputButton: @MonotonicNonNull Button? = null
    private var inputCardView: @MonotonicNonNull MaterialCardView? = null
    private var inputTextView: @MonotonicNonNull TextView? = null
    private var inputImageView: @MonotonicNonNull ImageView? = null
    private var inputPlayerView: @MonotonicNonNull PlayerView? = null
    private var outputPlayerView: @MonotonicNonNull PlayerView? = null
    private var outputVideoTextView: @MonotonicNonNull TextView? = null
    private var debugTextView: @MonotonicNonNull TextView? = null
    private var informationTextView: @MonotonicNonNull TextView? = null
    private var progressViewGroup: @MonotonicNonNull ViewGroup? = null
    private var progressIndicator: @MonotonicNonNull LinearProgressIndicator? = null
    private var cancelButton: @MonotonicNonNull Button? = null
    private var resumeButton: @MonotonicNonNull Button? = null
    private var exportStopwatch: @MonotonicNonNull Stopwatch? = null
    private var debugFrame: @MonotonicNonNull AspectRatioFrameLayout? = null

    private var debugTextViewHelper: DebugTextViewHelper? = null
    private var inputPlayer: ExoPlayer? = null
    private var outputPlayer: ExoPlayer? = null
    private var transformer: Transformer? = null
    private var outputFile: File? = null
    private var oldOutputFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.transformer_activity)

        inputCardView = findViewById<@MonotonicNonNull MaterialCardView?>(R.id.input_card_view)
        inputTextView = findViewById<@MonotonicNonNull TextView?>(R.id.input_text_view)
        inputImageView = findViewById<@MonotonicNonNull ImageView?>(R.id.input_image_view)
        inputPlayerView = findViewById<@MonotonicNonNull PlayerView?>(R.id.input_player_view)
        outputPlayerView = findViewById<@MonotonicNonNull PlayerView?>(R.id.output_player_view)
        outputVideoTextView = findViewById<@MonotonicNonNull TextView?>(R.id.output_video_text_view)
        debugTextView = findViewById<@MonotonicNonNull TextView?>(R.id.debug_text_view)
        informationTextView = findViewById<@MonotonicNonNull TextView?>(R.id.information_text_view)
        progressViewGroup = findViewById<@MonotonicNonNull ViewGroup?>(R.id.progress_view_group)
        progressIndicator =
            findViewById<@MonotonicNonNull LinearProgressIndicator?>(R.id.progress_indicator)
        cancelButton = findViewById<@MonotonicNonNull Button?>(R.id.cancel_button)
        cancelButton!!.setOnClickListener(View.OnClickListener { view: View ->
            this.cancelExport(
                view
            )
        })
        resumeButton = findViewById<@MonotonicNonNull Button?>(R.id.resume_button)
        resumeButton!!.setOnClickListener(View.OnClickListener { view: View? -> startExport() })
        debugFrame =
            findViewById<@MonotonicNonNull AspectRatioFrameLayout?>(R.id.debug_aspect_ratio_frame_layout)
        displayInputButton = findViewById<@MonotonicNonNull Button?>(R.id.display_input_button)
        displayInputButton!!.setOnClickListener(View.OnClickListener { view: View ->
            this.toggleInputVideoDisplay(
                view
            )
        })

        exportStopwatch =
            Stopwatch.createUnstarted(
                object : Ticker() {
                    override fun read(): Long {
                        return SystemClock.elapsedRealtimeNanos()
                    }
                })
    }

    override fun onStart() {
        super.onStart()

        startExport()

        Assertions.checkNotNull<@MonotonicNonNull PlayerView?>(inputPlayerView).onResume()
        Assertions.checkNotNull<@MonotonicNonNull PlayerView?>(outputPlayerView).onResume()
    }

    override fun onStop() {
        super.onStop()

        if (transformer != null) {
            transformer!!.cancel()
            transformer = null
        }

        // The stop watch is reset after cancelling the export, in case cancelling causes the stop watch
        // to be stopped in a transformer callback.
        Assertions.checkNotNull<@MonotonicNonNull Stopwatch?>(exportStopwatch).reset()

        Assertions.checkNotNull<@MonotonicNonNull PlayerView?>(inputPlayerView).onPause()
        Assertions.checkNotNull<@MonotonicNonNull PlayerView?>(outputPlayerView).onPause()
        releasePlayer()

        Assertions.checkNotNull(outputFile).delete()
        outputFile = null
        if (oldOutputFile != null) {
            oldOutputFile!!.delete()
            oldOutputFile = null
        }
    }

    private fun startExport() {
        Assertions.checkNotNull<@MonotonicNonNull LinearProgressIndicator?>(progressIndicator)
        Assertions.checkNotNull<@MonotonicNonNull TextView?>(informationTextView)
        Assertions.checkNotNull<@MonotonicNonNull Stopwatch?>(exportStopwatch)
        Assertions.checkNotNull<@MonotonicNonNull MaterialCardView?>(inputCardView)
        Assertions.checkNotNull<@MonotonicNonNull TextView?>(inputTextView)
        Assertions.checkNotNull<@MonotonicNonNull ImageView?>(inputImageView)
        Assertions.checkNotNull<@MonotonicNonNull PlayerView?>(inputPlayerView)
        Assertions.checkNotNull<@MonotonicNonNull PlayerView?>(outputPlayerView)
        Assertions.checkNotNull<@MonotonicNonNull TextView?>(outputVideoTextView)
        Assertions.checkNotNull<@MonotonicNonNull TextView?>(debugTextView)
        Assertions.checkNotNull<@MonotonicNonNull ViewGroup?>(progressViewGroup)
        Assertions.checkNotNull<@MonotonicNonNull AspectRatioFrameLayout?>(debugFrame)
        Assertions.checkNotNull<@MonotonicNonNull Button?>(displayInputButton)
        Assertions.checkNotNull<@MonotonicNonNull Button?>(cancelButton)
        Assertions.checkNotNull<@MonotonicNonNull Button?>(resumeButton)

        requestReadVideoPermission( /* activity= */this)

        val intent = intent
//        val inputUri = Assertions.checkNotNull(intent.data)
        try {
            outputFile =
                createExternalCacheFile("transformer-output.mp4")
        } catch (e: IOException) {
            throw IllegalStateException(e)
        }
        val bundle = intent.extras
        val mediaItemList = ConfigurationActivity.localFileUriList!!.map {
            createMediaItem(bundle, it)
        }

        startComposition(bundle)
        exportStopwatch!!.reset()
        exportStopwatch!!.start()

        displayInputButton!!.visibility = View.GONE
        inputCardView!!.visibility = View.GONE
        outputPlayerView!!.visibility = View.GONE
        outputVideoTextView!!.visibility = View.GONE
        debugTextView!!.visibility = View.GONE
        informationTextView!!.setText(R.string.export_started)
        progressViewGroup!!.visibility = View.VISIBLE
        cancelButton!!.visibility = View.VISIBLE
        resumeButton!!.visibility = View.GONE
        progressIndicator!!.progress = 0
        val mainHandler = Handler(mainLooper)

    }

    private fun createMediaItem(bundle: Bundle?, uri: Uri): MediaItem {
        val mediaItemBuilder = MediaItem.Builder().setUri(uri)
        if (bundle != null) {
            val trimStartMs =
                bundle.getLong(
                    ConfigurationActivity.TRIM_START_MS,  /* defaultValue= */
                    C.TIME_UNSET
                )
            val trimEndMs =
                bundle.getLong(ConfigurationActivity.TRIM_END_MS,  /* defaultValue= */C.TIME_UNSET)
            if (trimStartMs != C.TIME_UNSET && trimEndMs != C.TIME_UNSET) {
                mediaItemBuilder.setClippingConfiguration(
                    ClippingConfiguration.Builder()
                        .setStartPositionMs(trimStartMs)
                        .setEndPositionMs(trimEndMs)
                        .build()
                )
            }
        }
        return mediaItemBuilder.build()
    }

    @RequiresNonNull(
        "inputCardView",
        "inputTextView",
        "inputImageView",
        "inputPlayerView",
        "outputPlayerView",
        "outputVideoTextView",
        "displayInputButton",
        "debugTextView",
        "informationTextView",
        "exportStopwatch",
        "progressViewGroup",
        "debugFrame"
    )
    private fun createTransformer(bundle: Bundle?, filePath: String): Transformer {
        val transformerBuilder = Transformer.Builder( /* context= */
            this
        )
        if (bundle != null) {
            val audioMimeType = bundle.getString(ConfigurationActivity.AUDIO_MIME_TYPE)
            if (audioMimeType != null) {
                transformerBuilder.setAudioMimeType(audioMimeType)
            }
            val videoMimeType = bundle.getString(ConfigurationActivity.VIDEO_MIME_TYPE)
            if (videoMimeType != null) {
                transformerBuilder.setVideoMimeType(videoMimeType)
            }

            transformerBuilder.setEncoderFactory(
                DefaultEncoderFactory.Builder(this.applicationContext)
                    .setEnableFallback(bundle.getBoolean(ConfigurationActivity.ENABLE_FALLBACK))
                    .build()
            )

            var maxDelayBetweenSamplesMs = DefaultMuxer.Factory.DEFAULT_MAX_DELAY_BETWEEN_SAMPLES_MS
            if (!bundle.getBoolean(ConfigurationActivity.ABORT_SLOW_EXPORT)) {
                maxDelayBetweenSamplesMs = C.TIME_UNSET
            }

            var muxerFactory: Muxer.Factory = DefaultMuxer.Factory(maxDelayBetweenSamplesMs)
            if (bundle.getBoolean(ConfigurationActivity.PRODUCE_FRAGMENTED_MP4)) {
                muxerFactory =
                    InAppMuxer.Factory.Builder()
                        .setMaxDelayBetweenSamplesMs(maxDelayBetweenSamplesMs)
                        .setFragmentedMp4Enabled(true)
                        .build()
            }
            transformerBuilder.setMuxerFactory(muxerFactory)

            if (bundle.getBoolean(ConfigurationActivity.ENABLE_DEBUG_PREVIEW)) {
                transformerBuilder.setDebugViewProvider(DemoDebugViewProvider())
            }
        }

        return transformerBuilder
            .addListener(
                object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        this@TransformerActivity.onCompleted(filePath, exportResult)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        this@TransformerActivity.onError(exportException)
                    }
                })
            .build()
    }

    /** Creates a cache file, resetting it if it already exists.  */
    @Throws(IOException::class)
    private fun createExternalCacheFile(fileName: String): File {
        val file = File(cacheDir, fileName)
        check(!(file.exists() && !file.delete())) { "Could not delete the previous export output file" }
        check(file.createNewFile()) { "Could not create the export output file" }
        return file
    }

    private val durationSeconds = 3L
    private val presentationOneTimeUs = C.MICROS_PER_SECOND * durationSeconds

    private val mainHandler = Handler(Looper.getMainLooper())

    @RequiresNonNull(
        "inputCardView", "outputPlayerView", "exportStopwatch", "progressViewGroup"
    )
    private fun startComposition(bundle: Bundle?) {
        bundle?.let {
            createEditedMediaItemList(
                it,
                ConfigurationActivity.localFileUriList
            ) { editedMediaItemList ->
                //Audio Effect
                var backgroundAudioSequence: EditedMediaItemSequence? = null
                val audioUri = ConfigurationActivity.selectedAudioUri
                if (audioUri != null) {
                    val backgroundAudio = EditedMediaItem.Builder(
                        MediaItem.Builder()
                            .setUri(audioUri)
//                    .setClippingConfiguration(
//                        ClippingConfiguration.Builder()
//                            .setStartPositionMs(10_000)
//                            .setStartPositionMs(20_000)
//                            .build()
//                    )
                            .build()

                    ).build()
                    backgroundAudioSequence =
                        EditedMediaItemSequence(
                            ImmutableList.of(backgroundAudio),/* isLooping= */
                            true
                        )

                }

                val compositionBuilder = if (backgroundAudioSequence != null) {
                    Composition.Builder(
                        EditedMediaItemSequence(editedMediaItemList),
                        backgroundAudioSequence
                    )
                } else {
                    Composition.Builder(
                        EditedMediaItemSequence(editedMediaItemList),
                    )
                }
                compositionBuilder
                    .setHdrMode(bundle.getInt(ConfigurationActivity.HDR_MODE))
                    .experimentalSetForceAudioTrack(
                        bundle.getBoolean(ConfigurationActivity.FORCE_AUDIO_TRACK)
                    )

                // Setup transformer
                val outputFilePath = outputFile!!.absolutePath
                val composition = compositionBuilder.build()
                if (transformer == null) transformer = createTransformer(bundle, outputFilePath)
                transformer?.let {
                    if (oldOutputFile == null) {
                        it.start(composition, outputFilePath)
                    } else {
                        it.resume(
                            composition,
                            outputFilePath,
                            oldOutputFile!!.absolutePath
                        )
                    }

                    //Progress
                    val progressHolder = ProgressHolder()
                    mainHandler.post(
                        object : Runnable {
                            override fun run() {
                                if (transformer != null
                                    && it.getProgress(progressHolder) != Transformer.PROGRESS_STATE_NOT_STARTED
                                ) {
                                    progressIndicator!!.progress = progressHolder.progress
                                    informationTextView!!.text = getString(
                                        R.string.export_timer, exportStopwatch!!.elapsed(
                                            TimeUnit.SECONDS
                                        )
                                    )
                                    mainHandler.postDelayed( /* r= */this,  /* delayMillis= */500)
                                }
                            }
                        })
                }
            }
        }
    }

    private fun createAudioProcessorsFromBundle(bundle: Bundle): ImmutableList<AudioProcessor> {
        val selectedAudioEffects =
            bundle.getBooleanArray(ConfigurationActivity.AUDIO_EFFECTS_SELECTIONS)
                ?: return ImmutableList.of()

        val processors = ImmutableList.Builder<AudioProcessor>()

        if (selectedAudioEffects[ConfigurationActivity.HIGH_PITCHED_INDEX]
            || selectedAudioEffects[ConfigurationActivity.SAMPLE_RATE_INDEX]
        ) {
            val sonicAudioProcessor = SonicAudioProcessor()
            if (selectedAudioEffects[ConfigurationActivity.HIGH_PITCHED_INDEX]) {
                sonicAudioProcessor.setPitch(2f)
            }
            if (selectedAudioEffects[ConfigurationActivity.SAMPLE_RATE_INDEX]) {
                sonicAudioProcessor.setOutputSampleRateHz(48000)
            }
            processors.add(sonicAudioProcessor)
        }

        if (selectedAudioEffects[ConfigurationActivity.SKIP_SILENCE_INDEX]) {
            val silenceSkippingAudioProcessor =
                SilenceSkippingAudioProcessor()
            silenceSkippingAudioProcessor.setEnabled(true)
            processors.add(silenceSkippingAudioProcessor)
        }

        val mixToMono = selectedAudioEffects[ConfigurationActivity.CHANNEL_MIXING_INDEX]
        val scaleVolumeToHalf = selectedAudioEffects[ConfigurationActivity.VOLUME_SCALING_INDEX]
        if (mixToMono || scaleVolumeToHalf) {
            val mixingAudioProcessor = ChannelMixingAudioProcessor()
            for (inputChannelCount in 1..6) {
                var matrix: ChannelMixingMatrix
                if (mixToMono) {
                    val mixingCoefficients = FloatArray(inputChannelCount)
                    // Each channel is equally weighted in the mix to mono.
                    Arrays.fill(mixingCoefficients, 1f / inputChannelCount)
                    matrix =
                        ChannelMixingMatrix(
                            inputChannelCount,  /* outputChannelCount= */1, mixingCoefficients
                        )
                } else {
                    // Identity matrix.
                    matrix =
                        ChannelMixingMatrix.create(
                            inputChannelCount,  /* outputChannelCount= */inputChannelCount
                        )
                }

                // Apply the volume adjustment.
                mixingAudioProcessor.putChannelMixingMatrix(
                    if (scaleVolumeToHalf) matrix.scaleBy(0.5f) else matrix
                )
            }
            processors.add(mixingAudioProcessor)
        }


        return processors.build()
    }

    private val matrixTransformationFactory by lazy {
        MatrixTransformationFactory(durationSeconds)
    }

    private fun createVideoEffectsFromBundle(
        bundle: Bundle,
        bitmap: Bitmap? = null
    ): ImmutableList<Effect> {
        val selectedEffects =
            bundle.getBooleanArray(ConfigurationActivity.VIDEO_EFFECTS_SELECTIONS)
                ?: return ImmutableList.of()

        val effects = ImmutableList.Builder<Effect>()
        if (selectedEffects[ConfigurationActivity.DIZZY_CROP_INDEX]) {
            effects.add(MatrixTransformationFactory12.createDizzyCropEffect())
        }
        if (selectedEffects[ConfigurationActivity.EDGE_DETECTOR_INDEX]) {
            try {
                val clazz = Class.forName("androidx.media3.demo.transformer.MediaPipeShaderProgram")
                val constructor =
                    clazz.getConstructor(
                        Context::class.java,
                        Boolean::class.javaPrimitiveType,
                        String::class.java,
                        Boolean::class.javaPrimitiveType,
                        String::class.java,
                        String::class.java
                    )
                effects.add(
                    GlEffect { context: Context?, useHdr: Boolean ->
                        try {
                            return@GlEffect (constructor.newInstance(
                                context,
                                useHdr,  /* graphName= */
                                "edge_detector_mediapipe_graph.binarypb",  /* isSingleFrameGraph= */
                                true,  /* inputStreamName= */
                                "input_video",  /* outputStreamName= */
                                "output_video"
                            ) as GlShaderProgram)
                        } catch (e: Exception) {
                            runOnUiThread { showToast(R.string.no_media_pipe_error) }
                            throw RuntimeException("Failed to load MediaPipeShaderProgram", e)
                        }
                    }
                )
            } catch (e: Exception) {
                showToast(R.string.no_media_pipe_error)
            }
        }
        if (selectedEffects[ConfigurationActivity.COLOR_FILTERS_INDEX]) {
            when (bundle.getInt(ConfigurationActivity.COLOR_FILTER_SELECTION)) {
                ConfigurationActivity.COLOR_FILTER_GRAYSCALE -> effects.add(RgbFilter.createGrayscaleFilter())
                ConfigurationActivity.COLOR_FILTER_INVERTED -> effects.add(RgbFilter.createInvertedFilter())
                ConfigurationActivity.COLOR_FILTER_SEPIA -> {
                    // W3C Sepia RGBA matrix with sRGB as a target color space:
                    // https://www.w3.org/TR/filter-effects-1/#sepiaEquivalent
                    // The matrix is defined for the sRGB color space and the Transformer library
                    // uses a linear RGB color space internally. Meaning this is only for demonstration
                    // purposes and it does not display a correct sepia frame.
                    val sepiaMatrix = floatArrayOf(
                        0.393f,
                        0.349f,
                        0.272f,
                        0f,
                        0.769f,
                        0.686f,
                        0.534f,
                        0f,
                        0.189f,
                        0.168f,
                        0.131f,
                        0f,
                        0f,
                        0f,
                        0f,
                        1f
                    )
                    effects.add(RgbMatrix { presentationTimeUs: Long, useHdr: Boolean -> sepiaMatrix })
                }

                else -> throw IllegalStateException(
                    "Unexpected color filter "
                            + bundle.getInt(ConfigurationActivity.COLOR_FILTER_SELECTION)
                )
            }
        }
        if (selectedEffects[ConfigurationActivity.MAP_WHITE_TO_GREEN_LUT_INDEX]) {
            val length = 3
            val mapWhiteToGreenLut = Array(length) { Array(length) { IntArray(length) } }
            val scale = 255 / (length - 1)
            for (r in 0 until length) {
                for (g in 0 until length) {
                    for (b in 0 until length) {
                        mapWhiteToGreenLut[r][g][b] =
                            Color.rgb( /* red= */r * scale,  /* green= */
                                g * scale,  /* blue= */
                                b * scale
                            )
                    }
                }
            }
            mapWhiteToGreenLut[length - 1][length - 1][length - 1] = Color.GREEN
            effects.add(SingleColorLut.createFromCube(mapWhiteToGreenLut))
        }
        if (selectedEffects[ConfigurationActivity.RGB_ADJUSTMENTS_INDEX]) {
            effects.add(
                RgbAdjustment.Builder()
                    .setRedScale(bundle.getFloat(ConfigurationActivity.RGB_ADJUSTMENT_RED_SCALE))
                    .setGreenScale(bundle.getFloat(ConfigurationActivity.RGB_ADJUSTMENT_GREEN_SCALE))
                    .setBlueScale(bundle.getFloat(ConfigurationActivity.RGB_ADJUSTMENT_BLUE_SCALE))
                    .build()
            )
        }
        if (selectedEffects[ConfigurationActivity.HSL_ADJUSTMENT_INDEX]) {
            effects.add(
                HslAdjustment.Builder()
                    .adjustHue(bundle.getFloat(ConfigurationActivity.HSL_ADJUSTMENTS_HUE))
                    .adjustSaturation(bundle.getFloat(ConfigurationActivity.HSL_ADJUSTMENTS_SATURATION))
                    .adjustLightness(bundle.getFloat(ConfigurationActivity.HSL_ADJUSTMENTS_LIGHTNESS))
                    .build()
            )
        }
        if (selectedEffects[ConfigurationActivity.CONTRAST_INDEX]) {
            effects.add(Contrast(bundle.getFloat(ConfigurationActivity.CONTRAST_VALUE)))
        }
        if (selectedEffects[ConfigurationActivity.PERIODIC_VIGNETTE_INDEX]) {
            effects.add(
                GlEffect { context: Context?, useHdr: Boolean ->
                    PeriodicVignetteShaderProgram(
                        context!!,
                        useHdr,
                        bundle.getFloat(ConfigurationActivity.PERIODIC_VIGNETTE_CENTER_X),
                        bundle.getFloat(ConfigurationActivity.PERIODIC_VIGNETTE_CENTER_Y),  /* minInnerRadius= */
                        bundle.getFloat(
                            ConfigurationActivity.PERIODIC_VIGNETTE_INNER_RADIUS
                        ),  /* maxInnerRadius= */
                        bundle.getFloat(
                            ConfigurationActivity.PERIODIC_VIGNETTE_OUTER_RADIUS
                        ),
                        bundle.getFloat(ConfigurationActivity.PERIODIC_VIGNETTE_OUTER_RADIUS)
                    )
                }
            )
        }
        if (selectedEffects[ConfigurationActivity.SPIN_3D_INDEX]) {
            effects.add(MatrixTransformationFactory12.createSpin3dEffect())
        }
        if (selectedEffects[ConfigurationActivity.ZOOM_IN_INDEX]) {
            effects.add(MatrixTransformationFactory12.createZoomInTransition())
        }

        val overlayEffect = createOverlayEffectFromBundle(bundle, selectedEffects)
        if (overlayEffect != null) {
            effects.add(overlayEffect)
        }

        val scaleX = bundle.getFloat(ConfigurationActivity.SCALE_X,  /* defaultValue= */1f)
        val scaleY = bundle.getFloat(ConfigurationActivity.SCALE_Y,  /* defaultValue= */1f)
        val rotateDegrees =
            bundle.getFloat(ConfigurationActivity.ROTATE_DEGREES,  /* defaultValue= */0f)
        if (scaleX != 1f || scaleY != 1f || rotateDegrees != 0f) {
            effects.add(
                ScaleAndRotateTransformation.Builder()
                    .setScale(scaleX, scaleY)
                    .setRotationDegrees(rotateDegrees)
                    .build()
            )
        }

        val resolutionHeight =
            bundle.getInt(
                ConfigurationActivity.RESOLUTION_HEIGHT,  /* defaultValue= */
                C.LENGTH_UNSET
            )
        if (resolutionHeight != C.LENGTH_UNSET) {
            effects.add(Presentation.createForHeight(resolutionHeight))
        }

//        effects.add(MatrixTransformationFactory1.createZoomInTransition())
//        effects.add(MatrixTransformationFactory1.createZoomOutTransition())
//        effects.add(MatrixTransformationFactory.createRotateTransition())
//        effects.add(MatrixTransformationFactory1.createSlideFadeTransition())
//        bitmap?.let {
//            effects.add(OverlayEffect(ImmutableList.of(FadeOverlay(it))))
//        }

//        effects.add(
//            GlEffect { context: Context?, useHdr: Boolean ->
//                CrossroadShaderPrograms(
//                    context!!,
//                    useHdr
//                )
//            }
//        )

        return effects.build()
    }


    private fun createEditedMediaItemList(
        bundle: Bundle,
        uriList: List<Uri>?,
        callback: (List<EditedMediaItem>) -> Unit
    ) {
        if (uriList == null) return
        var editedMediaItems: List<EditedMediaItem>
        val transitionVideo = bundle.getInt(Constants.KEY_TRANSITION_VIDEO, -1)
        if (transitionVideo > -1) {
            when (transitionVideo) {
                TRANSITION_ZOOM_IN -> {
                    val effect = ImmutableList.Builder<Effect>()
                    effect.add(matrixTransformationFactory.createZoomInTransition())
                    editedMediaItems = uriList.map {
                        createEditedMediaItem(
                            bundle,
                            it,
                            effect
                        )
                    }
                    callback.invoke(editedMediaItems)
                }

                TRANSITION_ROTATE -> {
                    val effect = ImmutableList.Builder<Effect>()
                    effect.add(matrixTransformationFactory.createRotateTransition())
                    editedMediaItems = uriList.map {
                        createEditedMediaItem(
                            bundle,
                            it,
                            effect
                        )
                    }
                    callback.invoke(editedMediaItems)
                }

                TRANSITION_SLIDE_LEFT, TRANSITION_SLIDE_RIGHT -> {
                    lifecycleScope.launch(Dispatchers.IO + CoroutineExceptionHandler { coroutineContext, throwable ->
                        android.util.Log.d(TAG, "createVideoTransitionFromBundle: $throwable")
                    }) {
                        val editedMediaList: LinkedList<EditedMediaItem> = LinkedList()
                        val mapToBitmapMap = uriList.mapToBitmapList(contentResolver)

                        mapToBitmapMap.forEach { (uri, bitmap) ->
                            var effect: ImmutableList.Builder<Effect>? = null
                            if (bitmap != null) {
                                effect = ImmutableList.Builder<Effect>()
                                effect.add(
                                    if (transitionVideo == TRANSITION_SLIDE_LEFT) matrixTransformationFactory.createSlideLeftTransition()
                                    else matrixTransformationFactory.createSlideRightTransition()
                                )
                                effect.add(
                                    SlideFadeOverlay.toOverlayEffect(
                                        bitmap,
                                        presentationOneTimeUs.toFloat()
                                    )
                                )
                            }
                            editedMediaList.add(
                                createEditedMediaItem(
                                    bundle,
                                    uri,
                                    effect
                                )
                            )
                        }
                        editedMediaItems = editedMediaList.toList()
                        withContext(Dispatchers.Main) {
                            callback.invoke(editedMediaItems)
                        }
                    }
                }

                TRANSITION_FADE -> {
                    lifecycleScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->
                        android.util.Log.d(TAG, "createVideoTransitionFromBundle: $throwable")
                    }) {
                        val mapToBitmapMap = uriList.mapToBitmapList(contentResolver)
                        val editedMediaList: LinkedList<EditedMediaItem> = LinkedList()
                        mapToBitmapMap.forEach { (uri, bitmap) ->
                            var effect: ImmutableList.Builder<Effect>? = null
                            if (bitmap != null) {
                                effect = ImmutableList.Builder<Effect>()
                                effect?.add(matrixTransformationFactory.createSlideFadeTransition())
                                effect?.add(
                                    FadeOverlay.toOverlayEffect(
                                        bitmap,
                                        presentationOneTimeUs.toFloat()
                                    )
                                )
                            }
                            editedMediaList.add(
                                createEditedMediaItem(
                                    bundle,
                                    uri,
                                    effect
                                )
                            )
                        }
                        editedMediaItems = editedMediaList.toList()
                        withContext(Dispatchers.Main) {
                            callback.invoke(editedMediaItems)
                        }
                    }
                }
            }
        } else {
            editedMediaItems = uriList.map { createEditedMediaItem(bundle, it, null) }
            callback.invoke(editedMediaItems)
        }
    }

    private fun createEditedMediaItem(
        bundle: Bundle?,
        uri: Uri,
        effects: ImmutableList.Builder<Effect>? = null
    ): EditedMediaItem {
        return EditedMediaItem.Builder(createMediaItem(bundle, uri)).apply {
            // For image inputs. Automatically ignored if input is audio/video.
            setDurationUs(presentationOneTimeUs).setFrameRate(30)
            if (bundle != null) {
                val audioProcessors = createAudioProcessorsFromBundle(bundle)
                this.setRemoveAudio(bundle.getBoolean(ConfigurationActivity.SHOULD_REMOVE_AUDIO))
                    .setRemoveVideo(bundle.getBoolean(ConfigurationActivity.SHOULD_REMOVE_VIDEO))
                    .setFlattenForSlowMotion(
                        bundle.getBoolean(ConfigurationActivity.SHOULD_FLATTEN_FOR_SLOW_MOTION)
                    )
                    .setEffects(
                        Effects(
                            audioProcessors,
                            effects?.build() ?: listOf()
                        )
                    )
            }
        }.build()
    }


    private fun createOverlayEffectFromBundle(
        bundle: Bundle,
        selectedEffects: BooleanArray
    ): OverlayEffect? {
        val overlaysBuilder = ImmutableList.Builder<TextureOverlay>()
        if (selectedEffects[ConfigurationActivity.OVERLAY_LOGO_AND_TIMER_INDEX]) {
            val logoSettings =
                OverlaySettings.Builder() // Place the logo in the bottom left corner of the screen with some padding from the
                    // edges.
                    .setOverlayFrameAnchor( /* x= */1f,  /* y= */1f)
                    .setBackgroundFrameAnchor( /* x= */-0.95f,  /* y= */-0.95f)
                    .build()
            val logo: Drawable
            try {
                logo = packageManager.getApplicationIcon(packageName)
            } catch (e: PackageManager.NameNotFoundException) {
                throw IllegalStateException(e)
            }
            logo.setBounds( /* left= */
                0,  /* top= */0, logo.intrinsicWidth, logo.intrinsicHeight
            )
            val logoOverlay: TextureOverlay =
                DrawableOverlay.createStaticDrawableOverlay(logo, logoSettings)
            val timerOverlay: TextureOverlay = TimerOverlay()
            overlaysBuilder.add(logoOverlay, timerOverlay)
        }
        if (selectedEffects[ConfigurationActivity.BITMAP_OVERLAY_INDEX]) {
            val overlaySettings =
                OverlaySettings.Builder()
                    .setAlphaScale(
                        bundle.getFloat(
                            ConfigurationActivity.BITMAP_OVERLAY_ALPHA,  /* defaultValue= */1f
                        )
                    )
                    .build()
            val bitmapOverlay =
                BitmapOverlay.createStaticBitmapOverlay(
                    applicationContext,
                    Uri.parse(Assertions.checkNotNull(bundle.getString(ConfigurationActivity.BITMAP_OVERLAY_URI))),
                    overlaySettings
                )
            overlaysBuilder.add(bitmapOverlay)
        }
        if (selectedEffects[ConfigurationActivity.TEXT_OVERLAY_INDEX]) {
            val overlaySettings =
                OverlaySettings.Builder()
                    .setAlphaScale(
                        bundle.getFloat(
                            ConfigurationActivity.TEXT_OVERLAY_ALPHA,  /* defaultValue= */
                            1f
                        )
                    )
                    .build()
            val overlayText =
                SpannableString(
                    Assertions.checkNotNull(bundle.getString(ConfigurationActivity.TEXT_OVERLAY_TEXT))
                )
            overlayText.setSpan(
                ForegroundColorSpan(bundle.getInt(ConfigurationActivity.TEXT_OVERLAY_TEXT_COLOR)),  /* start= */
                0,
                overlayText.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            val textOverlay = TextOverlay.createStaticTextOverlay(overlayText, overlaySettings)
            overlaysBuilder.add(textOverlay)
        }

        val overlays = overlaysBuilder.build()
        return if (overlays.isEmpty()) null else OverlayEffect(overlays)
    }

    @RequiresNonNull(
        "informationTextView", "progressViewGroup", "debugFrame", "exportStopwatch"
    )
    private fun onError(exportException: ExportException) {
        exportStopwatch!!.stop()
        informationTextView!!.setText(R.string.export_error)
        progressViewGroup!!.visibility = View.GONE
        debugFrame!!.removeAllViews()
        Toast.makeText(applicationContext, "Export error: $exportException", Toast.LENGTH_LONG)
            .show()
        Log.e(TAG, "Export error", exportException)
    }

    @RequiresNonNull(
        "inputCardView",
        "inputTextView",
        "inputImageView",
        "inputPlayerView",
        "outputPlayerView",
        "outputVideoTextView",
        "debugTextView",
        "displayInputButton",
        "informationTextView",
        "progressViewGroup",
        "debugFrame",
        "exportStopwatch"
    )
    private fun onCompleted(filePath: String, exportResult: ExportResult) {
        exportStopwatch!!.stop()
        val elapsedTimeMs = exportStopwatch!!.elapsed(TimeUnit.MILLISECONDS)
        informationTextView!!.text =
            getString(R.string.export_completed, elapsedTimeMs / 1000f, filePath)
        progressViewGroup!!.visibility = View.GONE
        debugFrame!!.removeAllViews()
//        inputCardView!!.visibility = View.VISIBLE
        outputPlayerView!!.visibility = View.VISIBLE
        outputVideoTextView!!.visibility = View.VISIBLE
        debugTextView!!.visibility = View.VISIBLE
        displayInputButton!!.visibility = View.VISIBLE
        Log.d(TAG, DebugTraceUtil.generateTraceSummary())
        val file = File(getExternalFilesDir(null), "trace.tsv")
        try {
            BufferedWriter(FileWriter(file)).use { writer ->
                DebugTraceUtil.dumpTsv(writer)
                Log.d(TAG, file.absolutePath)
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
        outputFile?.toUri()?.let {
            playMediaItems(MediaItem.fromUri(it))
        }
        Log.d(TAG, "Output file path: file://$filePath")
        try {
            val resultJson =
                JsonUtil.exportResultAsJsonObject(exportResult)
                    .put("elapsedTimeMs", elapsedTimeMs)
                    .put("device", JsonUtil.getDeviceDetailsAsJsonObject())
            for (line in Util.split(resultJson.toString(2), "\n")) {
                Log.d(TAG, line)
            }
        } catch (e: JSONException) {
            Log.d(TAG, "Unable to convert exportResult to JSON", e)
        }
    }

    @RequiresNonNull(
        "inputCardView",
        "inputTextView",
        "inputImageView",
        "inputPlayerView",
        "outputPlayerView",
        "debugTextView"
    )
    private fun playMediaItems(outputMediaItem: MediaItem) {
        inputPlayerView!!.player = null
        outputPlayerView!!.player = null
        releasePlayer()

        val outputPlayer = ExoPlayer.Builder( /* context= */this).build()
        outputPlayerView!!.player = outputPlayer
        outputPlayerView!!.controllerAutoShow = true
        outputPlayer.setMediaItem(outputMediaItem)
        outputPlayer.prepare()
        this.outputPlayer = outputPlayer

//        // Only support showing jpg images.
//        if (uri.toString().endsWith("jpg")) {
//            inputPlayerView!!.visibility = View.GONE
//            inputImageView!!.visibility = View.VISIBLE
//            inputTextView!!.text = getString(R.string.input_image)
//
//            val bitmapLoader: BitmapLoader = DataSourceBitmapLoader(
//                applicationContext
//            )
//            val future = bitmapLoader.loadBitmap(uri)
//            try {
//                val bitmap = future.get()
//                inputImageView!!.setImageBitmap(bitmap)
//            } catch (e: ExecutionException) {
//                throw IllegalArgumentException("Failed to load bitmap.", e)
//            } catch (e: InterruptedException) {
//                throw IllegalArgumentException("Failed to load bitmap.", e)
//            }
//        } else {
//            inputPlayerView!!.visibility = View.VISIBLE
//            inputImageView!!.visibility = View.GONE
//            inputTextView!!.text = getString(R.string.input_video_no_sound)
//
//            val inputPlayer = ExoPlayer.Builder( /* context= */this).build()
//            inputPlayerView!!.player = inputPlayer
//            inputPlayerView!!.controllerAutoShow = false
//            inputPlayerView!!.setOnClickListener { view: View -> this.onClickingPlayerView(view) }
//            outputPlayerView!!.setOnClickListener { view: View -> this.onClickingPlayerView(view) }
//            inputPlayer.prepare()
//            this.inputPlayer = inputPlayer
//            inputPlayer.volume = 0f
//            inputPlayer.play()
//        }
        outputPlayer.play()

        debugTextViewHelper = DebugTextViewHelper(outputPlayer, debugTextView!!)
        debugTextViewHelper!!.start()
    }

    private fun onClickingPlayerView(view: View) {
        if (view === inputPlayerView) {
            if (inputPlayer != null && inputTextView != null) {
                inputPlayer!!.volume = 1f
                inputTextView!!.setText(R.string.input_video_playing_sound)
            }
            Assertions.checkNotNull(outputPlayer).volume = 0f
            Assertions.checkNotNull<@MonotonicNonNull TextView?>(outputVideoTextView)
                .setText(R.string.output_video_no_sound)
        } else {
            if (inputPlayer != null && inputTextView != null) {
                inputPlayer!!.volume = 0f
                inputTextView!!.text = getString(R.string.input_video_no_sound)
            }
            Assertions.checkNotNull(outputPlayer).volume = 1f
            Assertions.checkNotNull<@MonotonicNonNull TextView?>(outputVideoTextView)
                .setText(R.string.output_video_playing_sound)
        }
    }

    private fun releasePlayer() {
        if (debugTextViewHelper != null) {
            debugTextViewHelper!!.stop()
            debugTextViewHelper = null
        }
        if (inputPlayer != null) {
            inputPlayer!!.release()
            inputPlayer = null
        }
        if (outputPlayer != null) {
            outputPlayer!!.release()
            outputPlayer = null
        }
    }

    private fun showToast(@StringRes messageResource: Int) {
        Toast.makeText(applicationContext, getString(messageResource), Toast.LENGTH_LONG).show()
    }

    @RequiresNonNull(
        "inputCardView", "displayInputButton"
    )
    private fun toggleInputVideoDisplay(view: View) {
        if (inputCardView!!.visibility == View.GONE) {
//            inputCardView!!.visibility = View.VISIBLE
//            displayInputButton!!.text =
//                getString(R.string.hide_input_video)
        } else if (inputCardView!!.visibility == View.VISIBLE) {
            if (inputPlayer != null) {
                inputPlayer!!.pause()
            }
            inputCardView!!.visibility = View.GONE
            displayInputButton!!.text = getString(R.string.show_input_video)
        }
    }

    @RequiresNonNull("transformer", "exportStopwatch", "cancelButton", "resumeButton")
    private fun cancelExport(view: View) {
        transformer?.cancel()
        transformer = null
        exportStopwatch!!.stop()
        cancelButton!!.visibility = View.GONE
        resumeButton!!.visibility = View.VISIBLE
        if (oldOutputFile != null) {
            oldOutputFile!!.delete()
        }
        oldOutputFile = outputFile
    }

    private inner class DemoDebugViewProvider : DebugViewProvider {
        private var surfaceView: @MonotonicNonNull SurfaceView? = null
        private var width: Int
        private var height: Int

        init {
            width = C.LENGTH_UNSET
            height = C.LENGTH_UNSET
        }

        override fun getDebugPreviewSurfaceView(width: Int, height: Int): SurfaceView? {
            Assertions.checkState(
                surfaceView == null || (this.width == width && this.height == height),
                "Transformer should not change the output size mid-export."
            )
            if (surfaceView != null) {
                return surfaceView
            }

            this.width = width
            this.height = height

            // Update the UI on the main thread and wait for the output surface to be available.
            val surfaceCreatedCountDownLatch = CountDownLatch(1)
            runOnUiThread {
                surfaceView = SurfaceView( /* context= */this@TransformerActivity)
                val debugFrame = Assertions.checkNotNull<@MonotonicNonNull AspectRatioFrameLayout?>(
                    this@TransformerActivity.debugFrame
                )
                debugFrame.addView(surfaceView)
                debugFrame.setAspectRatio(width.toFloat() / height)
                surfaceView!!
                    .holder
                    .addCallback(
                        object : SurfaceHolder.Callback {
                            override fun surfaceCreated(surfaceHolder: SurfaceHolder) {
                                surfaceCreatedCountDownLatch.countDown()
                            }

                            override fun surfaceChanged(
                                surfaceHolder: SurfaceHolder, format: Int, width: Int, height: Int
                            ) {
                                // Do nothing.
                            }

                            override fun surfaceDestroyed(surfaceHolder: SurfaceHolder) {
                                // Do nothing.
                            }
                        })
            }
            try {
                surfaceCreatedCountDownLatch.await()
            } catch (e: InterruptedException) {
                Log.w(TAG, "Interrupted waiting for debug surface.")
                Thread.currentThread().interrupt()
                return null
            }
            return surfaceView
        }
    }

    companion object {
        private const val TAG = "TransformerActivity111"

        private fun requestReadVideoPermission(activity: AppCompatActivity) {
            val permission =
                if (Util.SDK_INT >= 33) permission.READ_MEDIA_VIDEO else permission.READ_EXTERNAL_STORAGE
            if (ActivityCompat.checkSelfPermission(activity, permission)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(permission),  /* requestCode= */
                    0
                )
            }
        }
    }


    private fun createVideoEditedMediaItem(uri: String, height: Int): EditedMediaItem {
        return EditedMediaItem.Builder(MediaItem.fromUri(Uri.parse(uri)))
            .setEffects(
                Effects( /* audioProcessors= */
                    ImmutableList.of(),
                    ImmutableList.of(Presentation.createForHeight(height)) as List<Effect>
                )
            )
            .setFrameRate(30)
            .setDurationUs(3000000)
            .setRemoveAudio(true)
            .build()
    }

    private fun buildComposition(editedMediaItems: ImmutableList<EditedMediaItem>): Composition {
        return Composition.Builder(ImmutableList.of(EditedMediaItemSequence(editedMediaItems)))
            .setEffects(
                Effects(
                    /* audioProcessors = */ ImmutableList.of(),
                    /* videoEffects = */
                    ImmutableList.of<Effect>( // To ensure that software encoders can encode.
                        ScaleAndRotateTransformation.Builder().setScale(0.5f, 0.5f).build()
                    )
                )
            ).build()
    }
}
