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
import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Assertions
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.slider.RangeSlider
import com.google.android.material.slider.Slider
import com.google.common.collect.ImmutableMap
import com.tohsoft.transformer.R
import com.tohsoft.transformer.merge_image.Constants.KEY_TRANSITION_VIDEO
import com.tohsoft.transformer.merge_image.adapter.PhotoAdapter
import kotlin.math.sqrt

/**
 * An [Activity] that sets the configuration to use for exporting and playing media, using
 * [TransformerActivity].
 */
@UnstableApi
class ConfigurationActivity : AppCompatActivity() {
    private var onPermissionsGranted: Runnable? = null
    private var videoLocalFilePickerLauncher: ActivityResultLauncher<Intent>? =
        null
    private var overlayLocalFilePickerLauncher: ActivityResultLauncher<Intent>? =
        null
    private var audioResultLauncher: ActivityResultLauncher<Intent>? =
        null
    private var selectPresetFileButton: Button? = null
    private var selectLocalFileButton: Button? = null
    private var removeAudioCheckbox: CheckBox? = null
    private var removeVideoCheckbox: CheckBox? = null
    private var flattenForSlowMotionCheckbox: CheckBox? = null
    private var forceAudioTrackCheckbox: CheckBox? = null
    private var audioMimeSpinner: Spinner? = null
    private var videoMimeSpinner: Spinner? = null
    private var resolutionHeightSpinner: Spinner? = null
    private var scaleSpinner: Spinner? = null
    private var rotateSpinner: Spinner? = null
    private var trimCheckBox: CheckBox? = null
    private var enableFallbackCheckBox: CheckBox? = null
    private var enableDebugPreviewCheckBox: CheckBox? = null
    private var abortSlowExportCheckBox: CheckBox? = null
    private var produceFragmentedMp4CheckBox: CheckBox? = null
    private var hdrModeSpinner: Spinner? = null
    private var selectAudioEffectsButton: Button? = null
    private var selectVideoEffectsButton: Button? = null
    private var audioEffectsSelections: BooleanArray? = null
    private var videoEffectsSelections: BooleanArray? = null
    private var videoTransitionSelections: Int = -1
    private var inputUriPosition = 0
    private var trimStartMs: Long = 0
    private var trimEndMs: Long = 0
    private var colorFilterSelection = 0
    private var rgbAdjustmentRedScale = 0f
    private var rgbAdjustmentGreenScale = 0f
    private var rgbAdjustmentBlueScale = 0f
    private var contrastValue = 0f
    private var hueAdjustment = 0f
    private var saturationAdjustment = 0f
    private var lightnessAdjustment = 0f
    private var periodicVignetteCenterX = 0f
    private var periodicVignetteCenterY = 0f
    private var periodicVignetteInnerRadius = 0f
    private var periodicVignetteOuterRadius = 0f
    private var bitmapOverlayUri: String? = null
    private var bitmapOverlayAlpha = 0f
    private var textOverlayText: String? = null
    private var textOverlayTextColor = 0
    private var textOverlayAlpha = 0f
    private val mPhotoAdapter by lazy { PhotoAdapter() }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.configuration_activity)
        findViewById<RecyclerView>(R.id.rv_items).adapter = mPhotoAdapter
        findViewById<View>(R.id.export_button).setOnClickListener { view: View ->
            this.startExport(
                view
            )
        }

        audioResultLauncher =
            registerForActivityResult<Intent, ActivityResult>(
                ActivityResultContracts.StartActivityForResult(),
                ActivityResultCallback<ActivityResult> { result: ActivityResult ->
                    val intent = result.data
                    selectedAudioUri = intent?.data
                })

        videoLocalFilePickerLauncher =
            registerForActivityResult<Intent, ActivityResult>(
                ActivityResultContracts.StartActivityForResult(),
                ActivityResultCallback<ActivityResult> { result: ActivityResult ->
                    this.videoLocalFilePickerLauncherResult(
                        result
                    )
                })

        overlayLocalFilePickerLauncher =
            registerForActivityResult<Intent, ActivityResult>(
                ActivityResultContracts.StartActivityForResult(),
                ActivityResultCallback<ActivityResult> { result: ActivityResult ->
                    this.overlayLocalFilePickerLauncherResult(
                        result
                    )
                })

        selectPresetFileButton =
            findViewById<Button?>(R.id.select_preset_file_button)
        selectPresetFileButton!!.setOnClickListener(View.OnClickListener { view: View ->
            this.selectPresetFile(
                view
            )
        })

        findViewById<View>(R.id.select_audio_file).setOnClickListener { view: View ->
            this.selectAudioFile(audioResultLauncher!!)
        }

        findViewById<Button>(R.id.clear_data_button).setOnClickListener {
            mPhotoAdapter.clearAll()
            localFileUriList = null
            selectedAudioUri = null
        }

        selectLocalFileButton =
            findViewById<Button?>(R.id.select_local_file_button)
        selectLocalFileButton!!.setOnClickListener {
            videoLocalFilePickerLauncher?.let {
                selectLocalFile(
                    it,  /* mimeTypes= */
                    arrayOf("image/png", "image/jpeg", "image/webp")
                )
            }
        }

        removeAudioCheckbox = findViewById<CheckBox?>(R.id.remove_audio_checkbox)
        removeAudioCheckbox!!.setOnClickListener(View.OnClickListener { view: View ->
            this.onRemoveAudio(
                view
            )
        })

        removeVideoCheckbox = findViewById<CheckBox?>(R.id.remove_video_checkbox)
        removeVideoCheckbox!!.setOnClickListener(View.OnClickListener { view: View ->
            this.onRemoveVideo(
                view
            )
        })

        flattenForSlowMotionCheckbox =
            findViewById<CheckBox?>(R.id.flatten_for_slow_motion_checkbox)

        forceAudioTrackCheckbox =
            findViewById<CheckBox?>(R.id.force_audio_track_checkbox)

        val audioMimeAdapter =
            ArrayAdapter<String>( /* context= */this, R.layout.spinner_item)
        audioMimeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        audioMimeSpinner = findViewById<Spinner?>(R.id.audio_mime_spinner)
        audioMimeSpinner!!.setAdapter(audioMimeAdapter)
        audioMimeAdapter.addAll(
            SAME_AS_INPUT_OPTION,
            MimeTypes.AUDIO_AAC,
            MimeTypes.AUDIO_AMR_NB,
            MimeTypes.AUDIO_AMR_WB
        )

        val videoMimeAdapter =
            ArrayAdapter<String>( /* context= */this, R.layout.spinner_item)
        videoMimeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        videoMimeSpinner = findViewById<Spinner?>(R.id.video_mime_spinner)
        videoMimeSpinner!!.setAdapter(videoMimeAdapter)
        videoMimeAdapter.addAll(
            SAME_AS_INPUT_OPTION, MimeTypes.VIDEO_H263, MimeTypes.VIDEO_H264, MimeTypes.VIDEO_MP4V
        )
        if (Build.VERSION.SDK_INT >= 24) {
            videoMimeAdapter.add(MimeTypes.VIDEO_H265)
        }

        val resolutionHeightAdapter =
            ArrayAdapter<String>( /* context= */this, R.layout.spinner_item)
        resolutionHeightAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        resolutionHeightSpinner =
            findViewById<Spinner?>(R.id.resolution_height_spinner)
        resolutionHeightSpinner!!.setAdapter(resolutionHeightAdapter)
        resolutionHeightAdapter.addAll(
            SAME_AS_INPUT_OPTION, "144", "240", "360", "480", "720", "1080", "1440", "2160"
        )

        val scaleAdapter =
            ArrayAdapter<String>( /* context= */this, R.layout.spinner_item)
        scaleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        scaleSpinner = findViewById<Spinner?>(R.id.scale_spinner)
        scaleSpinner!!.setAdapter(scaleAdapter)
        scaleAdapter.addAll(
            SAME_AS_INPUT_OPTION,
            "-1, -1",
            "-1, 1",
            "1, 1",
            ".5, 1",
            ".5, .5",
            "2, 2"
        )

        val rotateAdapter =
            ArrayAdapter<String>( /* context= */this, R.layout.spinner_item)
        rotateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        rotateSpinner = findViewById<Spinner?>(R.id.rotate_spinner)
        rotateSpinner!!.setAdapter(rotateAdapter)
        rotateAdapter.addAll(SAME_AS_INPUT_OPTION, "0", "10", "45", "60", "90", "180")

        trimCheckBox = findViewById<CheckBox?>(R.id.trim_checkbox)
        trimCheckBox!!.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { view: CompoundButton, isChecked: Boolean ->
            this.selectTrimBounds(
                view,
                isChecked
            )
        })
        trimStartMs = C.TIME_UNSET
        trimEndMs = C.TIME_UNSET

        enableFallbackCheckBox =
            findViewById<CheckBox?>(R.id.enable_fallback_checkbox)
        enableDebugPreviewCheckBox =
            findViewById<CheckBox?>(R.id.enable_debug_preview_checkbox)

        abortSlowExportCheckBox =
            findViewById<CheckBox?>(R.id.abort_slow_export_checkbox)
        produceFragmentedMp4CheckBox =
            findViewById<CheckBox?>(R.id.produce_fragmented_mp4_checkbox)

        val hdrModeAdapter =
            ArrayAdapter<String>( /* context= */this, R.layout.spinner_item)
        hdrModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        hdrModeSpinner = findViewById<Spinner?>(R.id.hdr_mode_spinner)
        hdrModeSpinner!!.setAdapter(hdrModeAdapter)
        hdrModeAdapter.addAll(HDR_MODE_DESCRIPTIONS.keys)

        audioEffectsSelections = BooleanArray(AUDIO_EFFECTS.size)
        selectAudioEffectsButton =
            findViewById<Button?>(R.id.select_audio_effects_button)
        selectAudioEffectsButton!!.setOnClickListener(View.OnClickListener { view: View ->
            this.selectAudioEffects(
                view
            )
        })

        videoEffectsSelections = BooleanArray(VIDEO_EFFECTS.size)
        selectVideoEffectsButton =
            findViewById<Button?>(R.id.select_video_effects_button)
        selectVideoEffectsButton!!.setOnClickListener(View.OnClickListener { view: View ->
//            this.selectVideoEffects(
//                view
//            )
            this.selectVideoTransition(view)
        })
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == FILE_PERMISSION_REQUEST_CODE && grantResults.size == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Assertions.checkNotNull<Runnable?>(onPermissionsGranted).run()
        } else {
            Toast.makeText(
                applicationContext, getString(R.string.permission_denied), Toast.LENGTH_LONG
            )
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        val intentUri = intent.data
        if (intentUri != null) {
            Assertions.checkNotNull<Button?>(
                selectPresetFileButton
            ).isEnabled = false
            Assertions.checkNotNull<Button?>(
                selectLocalFileButton
            ).isEnabled = false
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun startExport(view: View) {
        if (localFileUriList.isNullOrEmpty()) {
            Toast.makeText(this, "Please select image", Toast.LENGTH_SHORT).show()
            return
        }
        val transformerIntent = Intent( /* packageContext= */this, TransformerActivity::class.java)
        val bundle = Bundle()
//        bundle.putBoolean(SHOULD_REMOVE_AUDIO, removeAudioCheckbox!!.isChecked)
//        bundle.putBoolean(SHOULD_REMOVE_VIDEO, removeVideoCheckbox!!.isChecked)
//        bundle.putBoolean(SHOULD_FLATTEN_FOR_SLOW_MOTION, flattenForSlowMotionCheckbox!!.isChecked)
//        bundle.putBoolean(FORCE_AUDIO_TRACK, forceAudioTrackCheckbox!!.isChecked)
//        val selectedAudioMimeType = audioMimeSpinner!!.selectedItem.toString()
//        if (SAME_AS_INPUT_OPTION != selectedAudioMimeType) {
//            bundle.putString(AUDIO_MIME_TYPE, selectedAudioMimeType)
//        }
//        val selectedVideoMimeType = videoMimeSpinner!!.selectedItem.toString()
//        if (SAME_AS_INPUT_OPTION != selectedVideoMimeType) {
//            bundle.putString(VIDEO_MIME_TYPE, selectedVideoMimeType)
//        }
//        val selectedResolutionHeight = resolutionHeightSpinner!!.selectedItem.toString()
//        if (SAME_AS_INPUT_OPTION != selectedResolutionHeight) {
//            bundle.putInt(RESOLUTION_HEIGHT, selectedResolutionHeight.toInt())
//        }


//        val selectedScale = scaleSpinner!!.selectedItem.toString()
//        if (SAME_AS_INPUT_OPTION != selectedScale) {
//            val scaleXY =
//                Arrays.asList(*selectedScale.split(", ".toRegex()).dropLastWhile { it.isEmpty() }
//                    .toTypedArray())
//            Assertions.checkState(scaleXY.size == 2)
//            bundle.putFloat(SCALE_X, scaleXY[0].toFloat())
//            bundle.putFloat(SCALE_Y, scaleXY[1].toFloat())
//        }
//        val selectedRotate = rotateSpinner!!.selectedItem.toString()
//        if (SAME_AS_INPUT_OPTION != selectedRotate) {
//            bundle.putFloat(ROTATE_DEGREES, selectedRotate.toFloat())
//        }
//        if (trimCheckBox!!.isChecked) {
//            bundle.putLong(TRIM_START_MS, trimStartMs)
//            bundle.putLong(TRIM_END_MS, trimEndMs)
//        }
        bundle.putBoolean(ENABLE_FALLBACK, enableFallbackCheckBox!!.isChecked)
        bundle.putBoolean(ENABLE_DEBUG_PREVIEW, enableDebugPreviewCheckBox!!.isChecked)
//        bundle.putBoolean(ABORT_SLOW_EXPORT, abortSlowExportCheckBox!!.isChecked)
//        bundle.putBoolean(PRODUCE_FRAGMENTED_MP4, produceFragmentedMp4CheckBox!!.isChecked)
//        val selectedhdrMode = hdrModeSpinner!!.selectedItem.toString()
//        bundle.putInt(
//            HDR_MODE, Assertions.checkNotNull(
//                HDR_MODE_DESCRIPTIONS[selectedhdrMode]
//            )
//        )
        bundle.putBooleanArray(AUDIO_EFFECTS_SELECTIONS, audioEffectsSelections)
//        bundle.putBooleanArray(VIDEO_EFFECTS_SELECTIONS, videoEffectsSelections)
//        bundle.putInt(COLOR_FILTER_SELECTION, colorFilterSelection)
//        bundle.putFloat(CONTRAST_VALUE, contrastValue)
//        bundle.putFloat(RGB_ADJUSTMENT_RED_SCALE, rgbAdjustmentRedScale)
//        bundle.putFloat(RGB_ADJUSTMENT_GREEN_SCALE, rgbAdjustmentGreenScale)
//        bundle.putFloat(RGB_ADJUSTMENT_BLUE_SCALE, rgbAdjustmentBlueScale)
//        bundle.putFloat(HSL_ADJUSTMENTS_HUE, hueAdjustment)
//        bundle.putFloat(HSL_ADJUSTMENTS_SATURATION, saturationAdjustment)
//        bundle.putFloat(HSL_ADJUSTMENTS_LIGHTNESS, lightnessAdjustment)
//        bundle.putFloat(PERIODIC_VIGNETTE_CENTER_X, periodicVignetteCenterX)
//        bundle.putFloat(PERIODIC_VIGNETTE_CENTER_Y, periodicVignetteCenterY)
//        bundle.putFloat(PERIODIC_VIGNETTE_INNER_RADIUS, periodicVignetteInnerRadius)
//        bundle.putFloat(PERIODIC_VIGNETTE_OUTER_RADIUS, periodicVignetteOuterRadius)
//        bundle.putString(BITMAP_OVERLAY_URI, bitmapOverlayUri)
//        bundle.putFloat(BITMAP_OVERLAY_ALPHA, bitmapOverlayAlpha)
//        bundle.putString(TEXT_OVERLAY_TEXT, textOverlayText)
//        bundle.putInt(TEXT_OVERLAY_TEXT_COLOR, textOverlayTextColor)
//        bundle.putFloat(TEXT_OVERLAY_ALPHA, textOverlayAlpha)
        bundle.putInt(KEY_TRANSITION_VIDEO, videoTransitionSelections)
        transformerIntent.putExtras(bundle)
//        val intentUri = if (intent.data != null) {
//            intent.data
//        } else if (localFileUriList != null) {
//            null
//        } else {
//            Uri.parse(PRESET_FILE_URIS[inputUriPosition])
//        }
//        transformerIntent.setData(intentUri)
        startActivity(transformerIntent)
    }

    private fun selectPresetFile(view: View) {
        AlertDialog.Builder( /* context= */this)
            .setTitle(R.string.select_preset_file_title)
            .setSingleChoiceItems(
                PRESET_FILE_URI_DESCRIPTIONS, inputUriPosition
            ) { dialog: DialogInterface, which: Int ->
                this.selectPresetFileInDialog(
                    dialog,
                    which
                )
            }
            .setPositiveButton(android.R.string.ok,  /* listener= */null)
            .create()
            .show()
    }

    private fun selectPresetFileInDialog(dialog: DialogInterface, which: Int) {
        inputUriPosition = which
        localFileUriList = null
    }

    private fun selectLocalFile(
        localFilePickerLauncher: ActivityResultLauncher<Intent>, mimeTypes: Array<String>,
    ) {
        val permission =
            if (Build.VERSION.SDK_INT >= 33) permission.READ_MEDIA_VIDEO else permission.READ_EXTERNAL_STORAGE
        if (ActivityCompat.checkSelfPermission( /* context= */this, permission)
            != PackageManager.PERMISSION_GRANTED
        ) {
            onPermissionsGranted =
                Runnable { launchLocalFilePicker(localFilePickerLauncher, mimeTypes) }
            ActivityCompat.requestPermissions( /* activity= */
                this, arrayOf(permission), FILE_PERMISSION_REQUEST_CODE
            )
        } else {
            launchLocalFilePicker(localFilePickerLauncher, mimeTypes)
        }
    }

    private fun selectAudioFile(
        audioFilePickerLauncher: ActivityResultLauncher<Intent>,
    ) {
        val permission =
            if (Build.VERSION.SDK_INT >= 33) permission.READ_MEDIA_VIDEO else permission.READ_EXTERNAL_STORAGE
        if (ActivityCompat.checkSelfPermission( /* context= */this, permission)
            != PackageManager.PERMISSION_GRANTED
        ) {
            onPermissionsGranted =
                Runnable { launchAudioLocalFilePicker(audioFilePickerLauncher) }
            ActivityCompat.requestPermissions( /* activity= */
                this, arrayOf(permission), FILE_PERMISSION_REQUEST_CODE
            )
        } else {
            launchAudioLocalFilePicker(audioFilePickerLauncher)
        }
    }

    private fun launchLocalFilePicker(
        localFilePickerLauncher: ActivityResultLauncher<Intent>, mimeTypes: Array<String>,
    ) {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.setType("*/*")
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        localFilePickerLauncher.launch(intent)
    }

    private fun launchAudioLocalFilePicker(
        localFilePickerLauncher: ActivityResultLauncher<Intent>,
    ) {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.setType("*/*")
        intent.putExtra(
            Intent.EXTRA_MIME_TYPES, arrayOf(
                "audio/*",
                "audio/3gpp",
                "audio/mpeg", "audio/x-ms-wma", "audio/x-wav", "audio/x-flac", "audio/aac"
            )
        )
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
        localFilePickerLauncher.launch(intent)
    }

    private fun videoLocalFilePickerLauncherResult(result: ActivityResult) {
        val intent = result.data
        if (intent != null) {
            if (intent.data != null) {
                localFileUriList!!.add(intent.data!!)
            } else if (intent.clipData != null) {
                val cd = intent.clipData
                val unsorted = ArrayList<Uri>()

                if (cd != null) {
                    for (i in 0 until cd.itemCount) {
                        val item = cd.getItemAt(i)
                        unsorted.add(item.uri)
                    }
                }

                localFileUriList!!.addAll(unsorted)
            }
            localFileUriList?.let { mPhotoAdapter.setData(it) }
        } else {
            Toast.makeText(
                applicationContext,
                getString(R.string.local_file_picker_failed),
                Toast.LENGTH_SHORT
            )
                .show()
        }
    }

    private fun overlayLocalFilePickerLauncherResult(result: ActivityResult) {
        val data = result.data
        if (data != null) {
            bitmapOverlayUri = data.data.toString()
        } else {
            Toast.makeText(
                applicationContext,
                getString(R.string.local_file_picker_failed),
                Toast.LENGTH_SHORT
            )
                .show()
        }
    }

    private fun selectAudioEffects(view: View) {
        AlertDialog.Builder( /* context= */this)
            .setTitle(R.string.select_audio_effects)
            .setMultiChoiceItems(
                AUDIO_EFFECTS, audioEffectsSelections
            ) { dialog: DialogInterface, which: Int, isChecked: Boolean ->
                this.selectAudioEffect(
                    dialog,
                    which,
                    isChecked
                )
            }
            .setPositiveButton(android.R.string.ok,  /* listener= */null)
            .create()
            .show()
    }

    private fun selectVideoEffects(view: View) {
        AlertDialog.Builder( /* context= */this)
            .setTitle(R.string.select_video_effects)
            .setMultiChoiceItems(
                VIDEO_EFFECTS, videoEffectsSelections
            ) { dialog: DialogInterface, which: Int, isChecked: Boolean ->
                this.selectVideoEffect(
                    dialog,
                    which,
                    isChecked
                )
            }
            .setPositiveButton(android.R.string.ok,  /* listener= */null)
            .create()
            .show()
    }

    private fun selectVideoTransition(view: View) {
        AlertDialog.Builder( /* context= */this)
            .setTitle("Add Transition")
            .setSingleChoiceItems(
                Constants.VIDEO_TRANSITION, videoTransitionSelections
            ) { dialog, which ->
                videoTransitionSelections = which
            }
            .setPositiveButton(android.R.string.ok,  /* listener= */null)
            .create()
            .show()
    }

    private fun selectTrimBounds(view: View, isChecked: Boolean) {
        if (!isChecked) {
            return
        }
        val dialogView = layoutInflater.inflate(R.layout.trim_options,  /* root= */null)
        val trimRangeSlider =
            dialogView.findViewById<RangeSlider>(R.id.trim_bounds_range_slider)
        trimRangeSlider.setValues(0f, 1f) // seconds
        AlertDialog.Builder( /* context= */this)
            .setView(dialogView)
            .setPositiveButton(
                android.R.string.ok
            ) { _: DialogInterface?, i: Int ->
                val trimRange = trimRangeSlider.values
                trimStartMs = Math.round(1000 * trimRange[0]).toLong()
                trimEndMs = Math.round(1000 * trimRange[1]).toLong()
            }
            .create()
            .show()
    }

    private fun selectAudioEffect(dialog: DialogInterface, which: Int, isChecked: Boolean) {
        audioEffectsSelections!![which] = isChecked
    }

    private fun selectVideoEffect(dialog: DialogInterface, which: Int, isChecked: Boolean) {
        videoEffectsSelections!![which] = isChecked
        if (!isChecked) {
            return
        }

        when (which) {
            COLOR_FILTERS_INDEX -> controlColorFiltersSettings()
            RGB_ADJUSTMENTS_INDEX -> controlRgbAdjustmentsScale()
            CONTRAST_INDEX -> controlContrastSettings()
            HSL_ADJUSTMENT_INDEX -> controlHslAdjustmentSettings()
            PERIODIC_VIGNETTE_INDEX -> controlPeriodicVignetteSettings()
            BITMAP_OVERLAY_INDEX -> controlBitmapOverlaySettings()
            TEXT_OVERLAY_INDEX -> controlTextOverlaySettings()
        }
    }

    private fun controlColorFiltersSettings() {
        AlertDialog.Builder( /* context= */this)
            .setPositiveButton(android.R.string.ok) { dialogInterface: DialogInterface, i: Int -> dialogInterface.dismiss() }
            .setSingleChoiceItems(
                this.resources.getStringArray(R.array.color_filter_options),
                colorFilterSelection
            ) { dialogInterface: DialogInterface, i: Int ->
                i == COLOR_FILTER_GRAYSCALE || i == COLOR_FILTER_INVERTED || i == COLOR_FILTER_SEPIA
                colorFilterSelection = i
                dialogInterface.dismiss()
            }
            .create()
            .show()
    }

    private fun controlRgbAdjustmentsScale() {
        val dialogView =
            layoutInflater.inflate(R.layout.rgb_adjustment_options,  /* root= */null)
        val redScaleSlider =
            dialogView.findViewById<Slider>(R.id.rgb_adjustment_red_scale)
        val greenScaleSlider =
            dialogView.findViewById<Slider>(R.id.rgb_adjustment_green_scale)
        val blueScaleSlider =
            dialogView.findViewById<Slider>(R.id.rgb_adjustment_blue_scale)
        AlertDialog.Builder( /* context= */this)
            .setTitle(R.string.rgb_adjustment_options)
            .setView(dialogView)
            .setPositiveButton(
                android.R.string.ok
            ) { dialogInterface: DialogInterface?, i: Int ->
                rgbAdjustmentRedScale = redScaleSlider.value
                rgbAdjustmentGreenScale = greenScaleSlider.value
                rgbAdjustmentBlueScale = blueScaleSlider.value
            }
            .create()
            .show()
    }

    private fun controlContrastSettings() {
        val dialogView = layoutInflater.inflate(R.layout.contrast_options,  /* root= */null)
        val contrastSlider =
            dialogView.findViewById<Slider>(R.id.contrast_slider)
        AlertDialog.Builder( /* context= */this)
            .setView(dialogView)
            .setPositiveButton(
                android.R.string.ok
            ) { _: DialogInterface?, i: Int -> contrastValue = contrastSlider.value }
            .create()
            .show()
    }

    private fun controlHslAdjustmentSettings() {
        val dialogView =
            layoutInflater.inflate(R.layout.hsl_adjustment_options,  /* root= */null)
        val hueAdjustmentSlider =
            dialogView.findViewById<Slider>(R.id.hsl_adjustments_hue)
        val saturationAdjustmentSlider =
            dialogView.findViewById<Slider>(R.id.hsl_adjustments_saturation)
        val lightnessAdjustmentSlider =
            dialogView.findViewById<Slider>(R.id.hsl_adjustment_lightness)
        AlertDialog.Builder( /* context= */this)
            .setTitle(R.string.hsl_adjustment_options)
            .setView(dialogView)
            .setPositiveButton(
                android.R.string.ok
            ) { _: DialogInterface?, i: Int ->
                hueAdjustment = hueAdjustmentSlider.value
                saturationAdjustment = saturationAdjustmentSlider.value
                lightnessAdjustment = lightnessAdjustmentSlider.value
            }
            .create()
            .show()
    }

    private fun controlPeriodicVignetteSettings() {
        val dialogView =
            layoutInflater.inflate(R.layout.periodic_vignette_options,  /* root= */null)
        val centerXSlider =
            dialogView.findViewById<Slider>(R.id.periodic_vignette_center_x_slider)
        val centerYSlider =
            dialogView.findViewById<Slider>(R.id.periodic_vignette_center_y_slider)
        val radiusRangeSlider =
            dialogView.findViewById<RangeSlider>(R.id.periodic_vignette_radius_range_slider)
        radiusRangeSlider.setValues(0f, HALF_DIAGONAL)
        AlertDialog.Builder( /* context= */this)
            .setTitle(R.string.periodic_vignette_options)
            .setView(dialogView)
            .setPositiveButton(
                android.R.string.ok
            ) { dialogInterface: DialogInterface?, i: Int ->
                periodicVignetteCenterX = centerXSlider.value
                periodicVignetteCenterY = centerYSlider.value
                val radiusRange = radiusRangeSlider.values
                periodicVignetteInnerRadius = radiusRange[0]
                periodicVignetteOuterRadius = radiusRange[1]
            }
            .create()
            .show()
    }

    private fun controlBitmapOverlaySettings() {
        val dialogView =
            layoutInflater.inflate(R.layout.bitmap_overlay_options,  /* root= */null)
        val uriButton =
            dialogView.findViewById<Button>(R.id.bitmap_overlay_uri)
        uriButton.setOnClickListener(
            (View.OnClickListener { view: View? ->
                overlayLocalFilePickerLauncher?.let {
                    selectLocalFile(
                        it,  /* mimeTypes= */
                        arrayOf("image/*")
                    )
                }
            })
        )
        val alphaSlider =
            dialogView.findViewById<Slider>(R.id.bitmap_overlay_alpha_slider)
        AlertDialog.Builder( /* context= */this)
            .setTitle(R.string.bitmap_overlay_settings)
            .setView(dialogView)
            .setPositiveButton(
                android.R.string.ok
            ) { dialogInterface: DialogInterface?, i: Int ->
                bitmapOverlayAlpha = alphaSlider.value
            }
            .create()
            .show()
    }

    private fun controlTextOverlaySettings() {
        val dialogView = layoutInflater.inflate(R.layout.text_overlay_options,  /* root= */null)
        val textEditText =
            dialogView.findViewById<EditText>(R.id.text_overlay_text)

        val textColorAdapter =
            ArrayAdapter<String>( /* context= */this, R.layout.spinner_item)
        textColorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        val textColorSpinner =
            dialogView.findViewById<Spinner>(R.id.text_overlay_text_color)
        textColorSpinner.adapter = textColorAdapter
        textColorAdapter.addAll(OVERLAY_COLORS.keys)

        val alphaSlider =
            dialogView.findViewById<Slider>(R.id.text_overlay_alpha_slider)
        AlertDialog.Builder( /* context= */this)
            .setTitle(R.string.bitmap_overlay_settings)
            .setView(dialogView)
            .setPositiveButton(
                android.R.string.ok
            ) { dialogInterface: DialogInterface?, i: Int ->
                textOverlayText = textEditText.text.toString()
                val selectedTextColor = textColorSpinner.selectedItem.toString()
                textOverlayTextColor = OVERLAY_COLORS[selectedTextColor] ?: 0
                textOverlayAlpha = alphaSlider.value
            }
            .create()
            .show()
    }

    private fun onRemoveAudio(view: View) {
        if ((view as CheckBox).isChecked) {
            removeVideoCheckbox!!.isChecked = false
            enableTrackSpecificOptions( /* isAudioEnabled= */false,  /* isVideoEnabled= */true)
        } else {
            enableTrackSpecificOptions( /* isAudioEnabled= */true,  /* isVideoEnabled= */true)
        }
    }

    private fun onRemoveVideo(view: View) {
        if ((view as CheckBox).isChecked) {
            removeAudioCheckbox!!.isChecked = false
            enableTrackSpecificOptions( /* isAudioEnabled= */true,  /* isVideoEnabled= */false)
        } else {
            enableTrackSpecificOptions( /* isAudioEnabled= */true,  /* isVideoEnabled= */true)
        }
    }

    private fun enableTrackSpecificOptions(isAudioEnabled: Boolean, isVideoEnabled: Boolean) {
        forceAudioTrackCheckbox!!.isEnabled = isVideoEnabled
        audioMimeSpinner!!.isEnabled = isAudioEnabled
        videoMimeSpinner!!.isEnabled = isVideoEnabled
        resolutionHeightSpinner!!.isEnabled = isVideoEnabled
        scaleSpinner!!.isEnabled = isVideoEnabled
        rotateSpinner!!.isEnabled = isVideoEnabled
        enableDebugPreviewCheckBox!!.isEnabled = isVideoEnabled
        hdrModeSpinner!!.isEnabled = isVideoEnabled
        selectAudioEffectsButton!!.isEnabled = isAudioEnabled
        selectVideoEffectsButton!!.isEnabled = isVideoEnabled

        findViewById<View>(R.id.audio_mime_text_view).isEnabled =
            isAudioEnabled
        findViewById<View>(R.id.video_mime_text_view).isEnabled =
            isVideoEnabled
        findViewById<View>(R.id.resolution_height_text_view).isEnabled =
            isVideoEnabled
        findViewById<View>(R.id.scale).isEnabled = isVideoEnabled
        findViewById<View>(R.id.rotate).isEnabled =
            isVideoEnabled
        findViewById<View>(R.id.hdr_mode).isEnabled =
            isVideoEnabled
    }

    companion object {
        const val SHOULD_REMOVE_AUDIO: String = "should_remove_audio"
        const val SHOULD_REMOVE_VIDEO: String = "should_remove_video"
        const val SHOULD_FLATTEN_FOR_SLOW_MOTION: String = "should_flatten_for_slow_motion"
        const val FORCE_AUDIO_TRACK: String = "force_audio_track"
        const val AUDIO_MIME_TYPE: String = "audio_mime_type"
        const val VIDEO_MIME_TYPE: String = "video_mime_type"
        const val RESOLUTION_HEIGHT: String = "resolution_height"
        const val SCALE_X: String = "scale_x"
        const val SCALE_Y: String = "scale_y"
        const val ROTATE_DEGREES: String = "rotate_degrees"
        const val TRIM_START_MS: String = "trim_start_ms"
        const val TRIM_END_MS: String = "trim_end_ms"
        const val ENABLE_FALLBACK: String = "enable_fallback"
        const val ENABLE_DEBUG_PREVIEW: String = "enable_debug_preview"
        const val ABORT_SLOW_EXPORT: String = "abort_slow_export"
        const val PRODUCE_FRAGMENTED_MP4: String = "produce_fragmented_mp4"
        const val HDR_MODE: String = "hdr_mode"
        const val AUDIO_EFFECTS_SELECTIONS: String = "audio_effects_selections"
        const val VIDEO_EFFECTS_SELECTIONS: String = "video_effects_selections"
        const val PERIODIC_VIGNETTE_CENTER_X: String = "periodic_vignette_center_x"
        const val PERIODIC_VIGNETTE_CENTER_Y: String = "periodic_vignette_center_y"
        const val PERIODIC_VIGNETTE_INNER_RADIUS: String = "periodic_vignette_inner_radius"
        const val PERIODIC_VIGNETTE_OUTER_RADIUS: String = "periodic_vignette_outer_radius"
        const val COLOR_FILTER_SELECTION: String = "color_filter_selection"
        const val CONTRAST_VALUE: String = "contrast_value"
        const val RGB_ADJUSTMENT_RED_SCALE: String = "rgb_adjustment_red_scale"
        const val RGB_ADJUSTMENT_GREEN_SCALE: String = "rgb_adjustment_green_scale"
        const val RGB_ADJUSTMENT_BLUE_SCALE: String = "rgb_adjustment_blue_scale"
        const val HSL_ADJUSTMENTS_HUE: String = "hsl_adjustments_hue"
        const val HSL_ADJUSTMENTS_SATURATION: String = "hsl_adjustments_saturation"
        const val HSL_ADJUSTMENTS_LIGHTNESS: String = "hsl_adjustments_lightness"
        const val BITMAP_OVERLAY_URI: String = "bitmap_overlay_uri"
        const val BITMAP_OVERLAY_ALPHA: String = "bitmap_overlay_alpha"
        const val TEXT_OVERLAY_TEXT: String = "text_overlay_text"
        const val TEXT_OVERLAY_TEXT_COLOR: String = "text_overlay_text_color"
        const val TEXT_OVERLAY_ALPHA: String = "text_overlay_alpha"
        const val URI_LIST: String = "uri_list"

        // Video effect selections.
        const val DIZZY_CROP_INDEX: Int = 0
        const val EDGE_DETECTOR_INDEX: Int = 1
        const val COLOR_FILTERS_INDEX: Int = 2
        const val MAP_WHITE_TO_GREEN_LUT_INDEX: Int = 3
        const val RGB_ADJUSTMENTS_INDEX: Int = 4
        const val HSL_ADJUSTMENT_INDEX: Int = 5
        const val CONTRAST_INDEX: Int = 6
        const val PERIODIC_VIGNETTE_INDEX: Int = 7
        const val SPIN_3D_INDEX: Int = 8
        const val ZOOM_IN_INDEX: Int = 9
        const val OVERLAY_LOGO_AND_TIMER_INDEX: Int = 10
        const val BITMAP_OVERLAY_INDEX: Int = 11
        const val TEXT_OVERLAY_INDEX: Int = 12

        // Audio effect selections.
        const val HIGH_PITCHED_INDEX: Int = 0
        const val SAMPLE_RATE_INDEX: Int = 1
        const val SKIP_SILENCE_INDEX: Int = 2
        const val CHANNEL_MIXING_INDEX: Int = 3
        const val VOLUME_SCALING_INDEX: Int = 4

        // Color filter options.
        const val COLOR_FILTER_GRAYSCALE: Int = 0
        const val COLOR_FILTER_INVERTED: Int = 1
        const val COLOR_FILTER_SEPIA: Int = 2

        const val FILE_PERMISSION_REQUEST_CODE: Int = 1
        private val PRESET_FILE_URIS = arrayOf(
            "https://storage.googleapis.com/exoplayer-test-media-1/mp4/android-screens-10s.mp4",
            "https://storage.googleapis.com/exoplayer-test-media-0/android-block-1080-hevc.mp4",
            "https://html5demos.com/assets/dizzy.mp4",
            "https://html5demos.com/assets/dizzy.webm",
            "https://storage.googleapis.com/exoplayer-test-media-1/mp4/portrait_4k60.mp4",
            "https://storage.googleapis.com/exoplayer-test-media-1/mp4/8k24fps_4s.mp4",
            "https://storage.googleapis.com/exoplayer-test-media-1/mp4/1920w_1080h_4s.mp4",
            "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4",
            "https://storage.googleapis.com/exoplayer-test-media-1/mp4/portrait_avc_aac.mp4",
            "https://storage.googleapis.com/exoplayer-test-media-1/mp4/portrait_rotated_avc_aac.mp4",
            "https://storage.googleapis.com/exoplayer-test-media-1/jpg/london.jpg",
            "https://storage.googleapis.com/exoplayer-test-media-1/jpg/tokyo.jpg",
            "https://storage.googleapis.com/exoplayer-test-media-1/mp4/slow-motion/slowMotion_stopwatch_240fps_long.mp4",
            "https://storage.googleapis.com/exoplayer-test-media-1/gen/screens/dash-vod-single-segment/manifest-baseline.mpd",
            "https://storage.googleapis.com/exoplayer-test-media-1/mp4/samsung-s21-hdr-hdr10.mp4",
            "https://storage.googleapis.com/exoplayer-test-media-1/mp4/Pixel7Pro_HLG_1080P.mp4",
            "https://storage.googleapis.com/exoplayer-test-media-1/mp4/sample_video_track_only.mp4",
        )
        private val PRESET_FILE_URI_DESCRIPTIONS = arrayOf(
            // same order as PRESET_FILE_URIS
            "720p H264 video and AAC audio (B-frames)",
            "1080p H265 video and AAC audio (B-frames)",
            "360p H264 video and AAC audio",
            "360p VP8 video and Vorbis audio",
            "4K H264 video and AAC audio (portrait, no B-frames)",
            "8k H265 video and AAC audio",
            "Short 1080p H265 video and AAC audio",
            "Long 180p H264 video and AAC audio",
            "H264 video and AAC audio (portrait, H > W, 0°)",
            "H264 video and AAC audio (portrait, H < W, 90°)",
            "London JPG image (Plays for 5secs at 30fps)",
            "Tokyo JPG image (Portrait, Plays for 5secs at 30fps)",
            "SEF slow motion with 240 fps",
            "480p DASH (non-square pixels)",
            "HDR (HDR10) H265 limited range video (encoding may fail)",
            "HDR (HLG) H265 limited range video (encoding may fail)",
            "720p H264 video with no audio (B-frames)",
        )
        private val AUDIO_EFFECTS = arrayOf(
            "High pitched",
            "Sample rate of 48000Hz",
            "Skip silence",
            "Mix channels into mono",
            "Scale volume to 50%"
        )
        private val VIDEO_EFFECTS = arrayOf(
            "Dizzy crop",
            "Edge detector (Media Pipe)",
            "Color filters",
            "Map White to Green Color Lookup Table",
            "RGB Adjustments",
            "HSL Adjustments",
            "Contrast",
            "Periodic vignette",
            "3D spin",
            "Zoom in start",
            "Overlay logo & timer",
            "Custom Bitmap Overlay",
            "Custom Text Overlay",
        )
        private val HDR_MODE_DESCRIPTIONS: ImmutableMap<String, Int> =
            ImmutableMap.Builder<String, Int>()
                .put("Keep HDR", Composition.HDR_MODE_KEEP_HDR)
                .put(
                    "MediaCodec tone-map HDR to SDR",
                    Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC
                )
                .put(
                    "OpenGL tone-map HDR to SDR",
                    Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL
                )
                .put(
                    "Force Interpret HDR as SDR",
                    Composition.HDR_MODE_EXPERIMENTAL_FORCE_INTERPRET_HDR_AS_SDR
                )
                .build()
        private val OVERLAY_COLORS: ImmutableMap<String, Int> = ImmutableMap.Builder<String, Int>()
            .put("BLACK", Color.BLACK)
            .put("BLUE", Color.BLUE)
            .put("CYAN", Color.CYAN)
            .put("DKGRAY", Color.DKGRAY)
            .put("GRAY", Color.GRAY)
            .put("GREEN", Color.GREEN)
            .put("LTGRAY", Color.LTGRAY)
            .put("MAGENTA", Color.MAGENTA)
            .put("RED", Color.RED)
            .put("WHITE", Color.WHITE)
            .put("YELLOW", Color.YELLOW)
            .build()
        private const val SAME_AS_INPUT_OPTION = "same as input"
        private val HALF_DIAGONAL = 1f / sqrt(2.0).toFloat()

        var localFileUriList: MutableList<Uri>? = ArrayList()
            get() {
                if (field == null) {
                    field = ArrayList()
                }
                return field
            }
        var selectedAudioUri: Uri? = null
    }
}