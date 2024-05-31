package androidx.media3.demo.transformer.merge_image

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Pair

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

fun Collection<Uri>.mapToBitmapList(
    contentResolver: ContentResolver,
): Map<Uri, Bitmap?> {
    val bitmapList = this.map { getBitmap(contentResolver, it) }
    var maxWidth = -1
    var maxHeight = -1
    bitmapList.forEach { bitmap ->
        if (bitmap != null) {
            if (bitmap.width > maxWidth) maxWidth = bitmap.width
            if (bitmap.height > maxHeight) maxHeight = bitmap.height
        }
    }
    if (maxWidth < 0 || maxHeight < 0) {
        // TODO: Lấy theo kích thước màn hình
    }
    val map = mutableMapOf<Uri, Bitmap?>()
    bitmapList.forEachIndexed { index, bitmap ->
        map[this.elementAt(index)] = fitBitmap(bitmap, maxWidth, maxHeight)
    }
    return map
}

fun Uri.mapToBitmap(
    contentResolver: ContentResolver,
    maxWidth: Int,
    maxHeight: Int
): Bitmap? {
    return getBitmap(contentResolver, this)?.let { fitBitmap(it, maxWidth, maxHeight) }
}

fun fitBitmap(src: Bitmap?, maxWidth: Int, maxHeight: Int): Bitmap? {
    if (src == null) return null
    val fitDown = fitDown(src.width, src.height, maxWidth, maxHeight)
    val width = fitDown.first
    val height = fitDown.second
    val bitmap = Bitmap.createScaledBitmap(src, width, height, false)
    val result = Bitmap.createBitmap(maxWidth, maxHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    canvas.drawARGB(0, 0, 0, 0)
    val paint = Paint()
    paint.color = Color.BLACK
    canvas.drawBitmap(bitmap, (maxWidth - width) / 2f, (maxHeight - height) / 2f, paint)
    return result
}

private fun fitDown(vWidth: Int, vHeight: Int, maxWidth: Int, maxHeight: Int): Pair<Int, Int> {
    if (vWidth == 0 || vHeight == 0) {
        return Pair(maxWidth, maxHeight)
    } else if (maxWidth > vWidth || maxHeight > vHeight) {
        return Pair(vWidth, vHeight)
    } else if (maxWidth * vHeight < maxHeight * vWidth) {
        val height = maxWidth * vHeight / vWidth
        return Pair(maxWidth, height)
    } else {
        val width = maxHeight * vWidth / vHeight
        return Pair(width, maxHeight)
    }
}