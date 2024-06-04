package com.tohsoft.transformer.merge_image

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Pair
import org.jetbrains.annotations.Contract
import java.util.LinkedList

fun getBitmap(contentResolver: ContentResolver, photoUri: Uri): Bitmap? {
    try {
        return when {
            Build.VERSION.SDK_INT < 28 -> MediaStore.Images.Media.getBitmap(
                contentResolver,
                photoUri
            )

            else -> {
                val source = ImageDecoder.createSource(contentResolver, photoUri)
                val bitmap = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.setTargetSampleSize(1) // shrinking by
                    decoder.isMutableRequired =
                        true // this resolve the hardware type of bitmap problem
                }
                bitmap
            }
        }
    } catch (_: Exception) {

    }
    return null
}

fun Collection<Uri>.mapToBitmap(contentResolver: ContentResolver, action: ((Bitmap) -> Unit)? = null): List<Bitmap> {
    val result = ArrayList<Bitmap>(size)
    forEach {
        val bitmap = getBitmap(contentResolver, it)
        if (bitmap != null) {
            result.add(bitmap)
            action?.invoke(bitmap)
        }
    }
    return result
}

fun Collection<Uri>.mapToBitmapDictionary(contentResolver: ContentResolver, action: ((Bitmap) -> Unit)? = null): Map<Uri, Bitmap> {
    val result = HashMap<Uri, Bitmap>(size)
    forEach {
        val bitmap = getBitmap(contentResolver, it)
        if (bitmap != null) {
            result[it] = bitmap
            action?.invoke(bitmap)
        }
    }
    return result
}

data class BitmapWrapper(val bitmap: Bitmap?)

fun Collection<Uri>.mapToBitmapAndResize(contentResolver: ContentResolver, width: Int, height: Int): List<Bitmap> {
    val result = ArrayList<Bitmap>(size)
    forEach {
        val bitmap = fitBitmap(getBitmap(contentResolver, it), width, height)
        if (bitmap != null) {
            result.add(bitmap)
        }
    }
    return result
}

fun Collection<Uri>.mapToBitmapAutoResized(
    contentResolver: ContentResolver,
): Map<Uri, Bitmap?> {
    var maxWidth = -1
    var maxHeight = -1
    val map = LinkedHashMap<Uri, Bitmap>()
    forEach {
        val bitmap = getBitmap(contentResolver, it)
        if (bitmap != null) {
            map[it] = bitmap
            if (bitmap.width > maxWidth) maxWidth = bitmap.width
            if (bitmap.height > maxHeight) maxHeight = bitmap.height
        }
    }
    val result = LinkedHashMap<Uri, Bitmap>()
    map.forEach { (uri, bitmap) ->
        fitBitmap(bitmap, maxWidth, maxHeight)?.let {
            result[uri] = it
        } ?: let {
            //TODO có thể remove nó đi
        }
    }
    if (maxWidth < 0 || maxHeight < 0) {
        // TODO: Lấy theo kích thước màn hình
    }
    return result
}

fun Collection<Uri>.mapToBitmapListAutoResize(
    contentResolver: ContentResolver,
): Collection<Bitmap> {
    var maxWidth = -1
    var maxHeight = -1
    val map = LinkedHashMap<Uri, Bitmap>()
    forEach {
        val bitmap = getBitmap(contentResolver, it)
        if (bitmap != null) {
            if (bitmap.width > maxWidth) maxWidth = bitmap.width
            if (bitmap.height > maxHeight) maxHeight = bitmap.height
            map[it] = bitmap
        }
    }
    map.forEach { (uri, bitmap) ->
        fitBitmap(bitmap, maxWidth, maxHeight)?.let {
            map[uri] = it
        } ?: let {
            //TODO có thể remove nó đi
        }
    }
    if (maxWidth < 0 || maxHeight < 0) {
        // TODO: Lấy theo kích thước màn hình
    }
    return map.values
}

fun Collection<Bitmap>.fit(width: Int, height: Int) {
    val list = LinkedList<Bitmap>()
    forEach { bitmap ->
        fitBitmap(bitmap, width, height)?.let {
            list.add(it)
        }
    }
}
fun Uri.mapToBitmap(
    contentResolver: ContentResolver,
    maxWidth: Int,
    maxHeight: Int
): Bitmap? {
    return getBitmap(contentResolver, this)?.let { fitBitmap(it, 1280, 720) }
}

@Contract(value = "null,_,_ -> null; !null,_,_ -> !null")
fun fitBitmap(src: Bitmap?, maxWidth: Int, maxHeight: Int): Bitmap? {
    if (src == null) return null
    val fitDown = fitDown(src.width, src.height, maxWidth, maxHeight)
    val width = fitDown.first
    val height = fitDown.second
    val config = src.config
    val bitmap = getResizedBitmap(src, width, height)
//    src.recycle()
    val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Bitmap.createBitmap(maxWidth, maxHeight, config, false)
    } else {
        Bitmap.createBitmap(maxWidth, maxHeight, config)
    }
    val canvas = Canvas(result)
    canvas.drawColor(Color.BLACK)
    canvas.drawBitmap(bitmap, (maxWidth - width) / 2f, (maxHeight - height) / 2f, null)
    bitmap.recycle()
    return result
}

fun getResizedBitmap(bm: Bitmap, newWidth: Int, newHeight: Int): Bitmap {
    val width = bm.width
    val height = bm.height
    val scaleWidth = (newWidth.toFloat()) / width
    val scaleHeight = (newHeight.toFloat()) / height
    // CREATE A MATRIX FOR THE MANIPULATION
    val matrix: Matrix = Matrix()
    // RESIZE THE BIT MAP
    matrix.postScale(scaleWidth, scaleHeight)

    // "RECREATE" THE NEW BITMAP
    val resizedBitmap = Bitmap.createBitmap(
        bm, 0, 0, width, height, matrix, false)
    return resizedBitmap
}

private fun fitDown(w: Int, h: Int, maxWidth: Int, maxHeight: Int): Pair<Int, Int> {
    var width = 0
    var height = 0
    if (w * maxHeight > h * maxWidth) {
        height = maxWidth * h / w
        width = maxWidth
    } else {
        width = maxHeight * w / h
        height = maxHeight
    }
    return Pair(width, height)
}