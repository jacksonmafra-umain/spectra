package com.umain.spectra

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ColorAlphaType

actual fun rgbaToImageBitmap(bytes: ByteArray, width: Int, height: Int): ImageBitmap? {
    if (width <= 0 || height <= 0 || bytes.size < width * height * 4) return null
    // The iOS bridge produces premultiplied-last RGBA (CGImageAlphaInfo.premultipliedLast).
    val info = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.PREMUL)
    return Image.makeRaster(info, bytes, width * 4).toComposeImageBitmap()
}
