package com.umain.spectra

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Turn a raw RGBA frame (width × height, 4 bytes per pixel, premultiplied alpha)
 * into a platform [ImageBitmap] so the demo can draw the actual camera image
 * instead of a coarse colour grid. Implemented per platform — Android via
 * `android.graphics.Bitmap`, iOS via Skia — because there's no common way to
 * build a bitmap from loose pixels. Returns null if the bytes don't add up.
 */
expect fun rgbaToImageBitmap(bytes: ByteArray, width: Int, height: Int): ImageBitmap?
