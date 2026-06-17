package com.umain.spectra

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.nio.ByteBuffer

actual fun rgbaToImageBitmap(bytes: ByteArray, width: Int, height: Int): ImageBitmap? {
    if (width <= 0 || height <= 0 || bytes.size < width * height * 4) return null
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    // ARGB_8888 is stored RGBA in native byte order, matching our frame bytes.
    bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(bytes))
    return bitmap.asImageBitmap()
}
