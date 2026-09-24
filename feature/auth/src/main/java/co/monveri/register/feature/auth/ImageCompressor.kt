package co.monveri.register.feature.auth

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream

/**
 * Mirrors iOS's `ImageCompressor.swift`: bake EXIF orientation into the pixels, downscale so the
 * long side is at most [MAX_DIMENSION], re-encode as JPEG at [JPEG_QUALITY] regardless of the
 * source format. Runs on whatever dispatcher the caller is already on — callers should invoke
 * this from a background coroutine context, not the main thread.
 */
object ImageCompressor {

    private const val MAX_DIMENSION = 1500
    private const val JPEG_QUALITY = 70
    private const val BOUNDS_SAMPLE_TARGET = MAX_DIMENSION * 2

    /** Returns compressed JPEG bytes, or `null` if the image at [uri] couldn't be decoded. */
    fun compress(resolver: ContentResolver, uri: Uri): ByteArray? {
        val sampleSize = computeSampleSize(resolver, uri) ?: return null
        val decoded = decodeSampled(resolver, uri, sampleSize) ?: return null
        val oriented = applyExifRotation(resolver, uri, decoded)
        val resized = resizeToMax(oriented)

        val output = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)

        if (resized !== oriented) oriented.recycle()
        if (oriented !== decoded) decoded.recycle()
        resized.recycle()
        return output.toByteArray()
    }

    private fun computeSampleSize(resolver: ContentResolver, uri: Uri): Int? {
        // `decodeStream` always returns null when `inJustDecodeBounds = true` — that's how
        // bounds-only decoding works, not a failure signal. Only `openInputStream` returning
        // null means "couldn't read this Uri"; bounds validity is checked separately below.
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val stream = resolver.openInputStream(uri) ?: return null
        stream.use { BitmapFactory.decodeStream(it, null, options) }
        if (options.outWidth <= 0 || options.outHeight <= 0) return null

        var sampleSize = 1
        while (options.outWidth / (sampleSize * 2) >= BOUNDS_SAMPLE_TARGET &&
            options.outHeight / (sampleSize * 2) >= BOUNDS_SAMPLE_TARGET
        ) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun decodeSampled(resolver: ContentResolver, uri: Uri, sampleSize: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun applyExifRotation(resolver: ContentResolver, uri: Uri, bitmap: Bitmap): Bitmap {
        val degrees = resolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> ROTATE_90
                ExifInterface.ORIENTATION_ROTATE_180 -> ROTATE_180
                ExifInterface.ORIENTATION_ROTATE_270 -> ROTATE_270
                else -> null
            }
        } ?: null

        if (degrees == null) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun resizeToMax(bitmap: Bitmap): Bitmap {
        val longSide = maxOf(bitmap.width, bitmap.height)
        if (longSide <= MAX_DIMENSION) return bitmap

        val scale = MAX_DIMENSION.toFloat() / longSide
        val targetWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private const val ROTATE_90 = 90f
    private const val ROTATE_180 = 180f
    private const val ROTATE_270 = 270f
}
