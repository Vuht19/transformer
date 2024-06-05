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
package com.tohsoft.transformer.merge_image

import android.Manifest.permission
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
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
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.effect.DebugTraceUtil
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
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
import com.tohsoft.transformer.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONException
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.Arrays
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
/** An [Activity] that exports and plays media using [Transformer].  */
class TransformerActivity : AppCompatActivity() {
    private var displayInputButton: Button? = null
    private var inputCardView: MaterialCardView? = null
    private var inputTextView: TextView? = null
    private var inputImageView: ImageView? = null
    private var inputPlayerView: PlayerView? = null
    private var outputPlayerView: PlayerView? = null
    private var outputVideoTextView: TextView? = null
    private var debugTextView: TextView? = null
    private var informationTextView: TextView? = null
    private var progressViewGroup: ViewGroup? = null
    private var progressIndicator: LinearProgressIndicator? = null
    private var cancelButton: Button? = null
    private var resumeButton: Button? = null
    private var exportStopwatch: Stopwatch? = null
    private var debugFrame: AspectRatioFrameLayout? = null

    private var debugTextViewHelper: DebugTextViewHelper? = null
    private var inputPlayer: ExoPlayer? = null
    private var outputPlayer: ExoPlayer? = null
    private var transformer: Transformer? = null
    private var outputFile: File? = null
    private var oldOutputFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.transformer_activity)

        inputCardView = findViewById<MaterialCardView?>(R.id.input_card_view)
        inputTextView = findViewById<TextView?>(R.id.input_text_view)
        inputImageView = findViewById<ImageView?>(R.id.input_image_view)
        inputPlayerView = findViewById<PlayerView?>(R.id.input_player_view)
        outputPlayerView = findViewById<PlayerView?>(R.id.output_player_view)
        outputVideoTextView = findViewById<TextView?>(R.id.output_video_text_view)
        debugTextView = findViewById<TextView?>(R.id.debug_text_view)
        informationTextView = findViewById<TextView?>(R.id.information_text_view)
        progressViewGroup = findViewById<ViewGroup?>(R.id.progress_view_group)
        progressIndicator =
            findViewById<LinearProgressIndicator?>(R.id.progress_indicator)
        cancelButton = findViewById<Button?>(R.id.cancel_button)
        cancelButton!!.setOnClickListener(View.OnClickListener { view: View ->
            this.cancelExport(
                view
            )
        })
        resumeButton = findViewById<Button?>(R.id.resume_button)
        resumeButton!!.setOnClickListener(View.OnClickListener { view: View? -> startExport() })
        debugFrame =
            findViewById<AspectRatioFrameLayout?>(R.id.debug_aspect_ratio_frame_layout)
        displayInputButton = findViewById<Button?>(R.id.display_input_button)
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

        Assertions.checkNotNull<PlayerView?>(inputPlayerView).onResume()
        Assertions.checkNotNull<PlayerView?>(outputPlayerView).onResume()
    }

    override fun onStop() {
        super.onStop()

        if (transformer != null) {
            transformer!!.cancel()
            transformer = null
        }

        // The stop watch is reset after cancelling the export, in case cancelling causes the stop watch
        // to be stopped in a transformer callback.
        Assertions.checkNotNull<Stopwatch?>(exportStopwatch).reset()

        Assertions.checkNotNull<PlayerView?>(inputPlayerView).onPause()
        Assertions.checkNotNull<PlayerView?>(outputPlayerView).onPause()
        releasePlayer()

        Assertions.checkNotNull(outputFile).delete()
        outputFile = null
        if (oldOutputFile != null) {
            oldOutputFile!!.delete()
            oldOutputFile = null
        }
    }

    private fun startExport() {
        Assertions.checkNotNull<LinearProgressIndicator?>(progressIndicator)
        Assertions.checkNotNull<TextView?>(informationTextView)
        Assertions.checkNotNull<Stopwatch?>(exportStopwatch)
        Assertions.checkNotNull<MaterialCardView?>(inputCardView)
        Assertions.checkNotNull<TextView?>(inputTextView)
        Assertions.checkNotNull<ImageView?>(inputImageView)
        Assertions.checkNotNull<PlayerView?>(inputPlayerView)
        Assertions.checkNotNull<PlayerView?>(outputPlayerView)
        Assertions.checkNotNull<TextView?>(outputVideoTextView)
        Assertions.checkNotNull<TextView?>(debugTextView)
        Assertions.checkNotNull<ViewGroup?>(progressViewGroup)
        Assertions.checkNotNull<AspectRatioFrameLayout?>(debugFrame)
        Assertions.checkNotNull<Button?>(displayInputButton)
        Assertions.checkNotNull<Button?>(cancelButton)
        Assertions.checkNotNull<Button?>(resumeButton)

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
            val fataBack = bundle.getBoolean(ConfigurationActivity.ENABLE_FALLBACK, false)
            transformerBuilder.setEncoderFactory(
                DefaultEncoderFactory.Builder(this.applicationContext)
                    .setEnableFallback(fataBack)
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
                        exportException: ExportException,
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

    private fun startComposition(bundle: Bundle?) {
        bundle?.let {
            lifecycleScope.launch(Dispatchers.IO) {
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
//                        .setHdrMode(bundle.getInt(ConfigurationActivity.HDR_MODE))
                    /*.experimentalSetForceAudioTrack(
                        bundle.getBoolean(ConfigurationActivity.FORCE_AUDIO_TRACK)
                    )*/
                    // Setup transformer
                    val outputFilePath = outputFile!!.absolutePath
                    val composition = compositionBuilder.build()
                    if (transformer == null) transformer = createTransformer(bundle, outputFilePath)
                    transformer?.let {
                        mainHandler.post {
                            if (oldOutputFile == null) {
                                it.start(composition, outputFilePath)
                            } else {
                                it.resume(
                                    composition,
                                    outputFilePath,
                                    oldOutputFile!!.absolutePath
                                )
                            }
                        }

                        //Progress
                        val progressHolder = ProgressHolder()
                        mainHandler.postDelayed(
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
                                        mainHandler.postDelayed( /* r= */this,  /* delayMillis= */
                                            500
                                        )
                                    }
                                }
                            }, 500
                        )
                    }
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

    private suspend fun createEditedMediaItemList(
        bundle: Bundle,
        _uriList: List<Uri>?,
        callback: (List<EditedMediaItem>) -> Unit,
    ) {
        if (_uriList == null) return
        val uriList = resizeImageList(this, _uriList)
        val transitionVideo = bundle.getInt(Constants.KEY_TRANSITION_VIDEO, -1)
        val effectHelper = EffectMapper(presentationOneTimeUs)
        if (transitionVideo == Constants.RANDOM) {
            effectHelper.genRandomEffects(uriList, contentResolver).let {
                callback(it)
            }
        } else {
            val type = when (transitionVideo) {
                Constants.TRANSITION_ZOOM_IN -> EffectType.ZOOM_IN
                Constants.TRANSITION_ROTATE -> EffectType.ROTATE
                Constants.TRANSITION_SLIDE_LEFT -> EffectType.SLIDE_LEFT
                Constants.TRANSITION_SLIDE_RIGHT -> EffectType.SLIDE_RIGHT
                Constants.TRANSITION_FADE -> EffectType.FADE
                else -> {
                    EffectType.NONE
                }
            }
            effectHelper.genEffects(type, uriList, contentResolver).let {
                callback(it)
            }
        }
    }

    private fun createEditedMediaItem(
        bundle: Bundle?,
        uri: Uri,
        effects: ImmutableList.Builder<Effect>? = null,
    ): EditedMediaItem {
        return EditedMediaItem.Builder(createMediaItem(bundle, uri)).apply {
            // For image inputs. Automatically ignored if input is audio/video.
            setDurationUs(presentationOneTimeUs).setFrameRate(30)
            if (bundle != null) {
                val audioProcessors = createAudioProcessorsFromBundle(bundle)
                this.setRemoveAudio(true)
                    .setRemoveVideo(bundle.getBoolean(ConfigurationActivity.SHOULD_REMOVE_VIDEO))
                    /*   .setFlattenForSlowMotion(
                           bundle.getBoolean(ConfigurationActivity.SHOULD_FLATTEN_FOR_SLOW_MOTION)
                       )*/
                    .setEffects(
                        Effects(
                            audioProcessors,
                            effects?.build() ?: listOf()
                        )
                    )
            }
        }.build()
    }


//    private fun createOverlayEffectFromBundle(
//        bundle: Bundle,
//        selectedEffects: BooleanArray,
//    ): OverlayEffect? {
//        val overlaysBuilder = ImmutableList.Builder<TextureOverlay>()
//        if (selectedEffects[ConfigurationActivity.OVERLAY_LOGO_AND_TIMER_INDEX]) {
//            val logoSettings =
//                OverlaySettings.Builder() // Place the logo in the bottom left corner of the screen with some padding from the
//                    // edges.
//                    .setOverlayFrameAnchor( /* x= */1f,  /* y= */1f)
//                    .setBackgroundFrameAnchor( /* x= */-0.95f,  /* y= */-0.95f)
//                    .build()
//            val logo: Drawable
//            try {
//                logo = packageManager.getApplicationIcon(packageName)
//            } catch (e: PackageManager.NameNotFoundException) {
//                throw IllegalStateException(e)
//            }
//            logo.setBounds( /* left= */
//                0,  /* top= */0, logo.intrinsicWidth, logo.intrinsicHeight
//            )
//            val logoOverlay: TextureOverlay =
//                DrawableOverlay.createStaticDrawableOverlay(logo, logoSettings)
//            val timerOverlay: TextureOverlay = TimerOverlay()
//            overlaysBuilder.add(logoOverlay, timerOverlay)
//        }
//        if (selectedEffects[ConfigurationActivity.BITMAP_OVERLAY_INDEX]) {
//            val overlaySettings =
//                OverlaySettings.Builder()
//                    .setAlphaScale(
//                        bundle.getFloat(
//                            ConfigurationActivity.BITMAP_OVERLAY_ALPHA,  /* defaultValue= */1f
//                        )
//                    )
//                    .build()
//            val bitmapOverlay =
//                BitmapOverlay.createStaticBitmapOverlay(
//                    applicationContext,
//                    Uri.parse(Assertions.checkNotNull(bundle.getString(ConfigurationActivity.BITMAP_OVERLAY_URI))),
//                    overlaySettings
//                )
//            overlaysBuilder.add(bitmapOverlay)
//        }
//        if (selectedEffects[ConfigurationActivity.TEXT_OVERLAY_INDEX]) {
//            val overlaySettings =
//                OverlaySettings.Builder()
//                    .setAlphaScale(
//                        bundle.getFloat(
//                            ConfigurationActivity.TEXT_OVERLAY_ALPHA,  /* defaultValue= */
//                            1f
//                        )
//                    )
//                    .build()
//            val overlayText =
//                SpannableString(
//                    Assertions.checkNotNull(bundle.getString(ConfigurationActivity.TEXT_OVERLAY_TEXT))
//                )
//            overlayText.setSpan(
//                ForegroundColorSpan(bundle.getInt(ConfigurationActivity.TEXT_OVERLAY_TEXT_COLOR)),  /* start= */
//                0,
//                overlayText.length,
//                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
//            )
//            val textOverlay = TextOverlay.createStaticTextOverlay(overlayText, overlaySettings)
//            overlaysBuilder.add(textOverlay)
//        }
//
//        val overlays = overlaysBuilder.build()
//        return if (overlays.isEmpty()) null else OverlayEffect(overlays)
//    }

    private fun onError(exportException: ExportException) {
        exportStopwatch!!.stop()
        informationTextView!!.setText(R.string.export_error)
        progressViewGroup!!.visibility = View.GONE
        debugFrame!!.removeAllViews()
        Toast.makeText(applicationContext, "Export error: $exportException", Toast.LENGTH_LONG)
            .show()
        Log.e(TAG, "Export error", exportException)
    }

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
//        displayInputButton!!.visibility = View.VISIBLE
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
            Assertions.checkNotNull<TextView?>(outputVideoTextView)
                .setText(R.string.output_video_no_sound)
        } else {
            if (inputPlayer != null && inputTextView != null) {
                inputPlayer!!.volume = 0f
                inputTextView!!.text = getString(R.string.input_video_no_sound)
            }
            Assertions.checkNotNull(outputPlayer).volume = 1f
            Assertions.checkNotNull<TextView?>(outputVideoTextView)
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
        private var surfaceView: SurfaceView? = null
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
                val debugFrame = Assertions.checkNotNull<AspectRatioFrameLayout?>(
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
                                surfaceHolder: SurfaceHolder, format: Int, width: Int, height: Int,
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
        private const val TAG = "TransformerActivity"

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