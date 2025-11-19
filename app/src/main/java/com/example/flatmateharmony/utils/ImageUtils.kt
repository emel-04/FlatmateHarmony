package com.example.flatmateharmony.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Chuyển đổi Uri thành Base64 string để lưu trực tiếp vào Firestore
 * Ảnh sẽ được nén và resize để không vượt quá giới hạn của Firestore (1MB)
 */
fun uriToBase64(context: Context, uri: Uri, maxWidth: Int = 800, quality: Int = 75): String? {
    try {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        inputStream?.use { stream ->
            // Đọc ảnh gốc
            val originalBitmap = BitmapFactory.decodeStream(stream)
            
            // Xử lý xoay ảnh theo EXIF orientation
            val rotatedBitmap = rotateImageIfRequired(context, uri, originalBitmap)
            
            // Resize ảnh nếu cần
            val resizedBitmap = resizeBitmap(rotatedBitmap, maxWidth)
            
            // Chuyển sang Base64
            val outputStream = ByteArrayOutputStream()
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            val byteArray = outputStream.toByteArray()
            
            // Kiểm tra kích thước (Firestore giới hạn 1MB cho 1 field)
            if (byteArray.size > 1_000_000) {
                // Nếu vượt quá 1MB, thử nén thêm
                return uriToBase64(context, uri, maxWidth - 100, quality - 10)
            }
            
            return Base64.encodeToString(byteArray, Base64.DEFAULT)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

/**
 * Resize bitmap để giảm kích thước
 */
private fun resizeBitmap(bitmap: Bitmap, maxWidth: Int): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    
    if (width <= maxWidth) {
        return bitmap
    }
    
    val ratio = width.toFloat() / height.toFloat()
    val newHeight = (maxWidth / ratio).toInt()
    
    return Bitmap.createScaledBitmap(bitmap, maxWidth, newHeight, true)
}

/**
 * Xoay ảnh theo EXIF orientation nếu cần
 */
private fun rotateImageIfRequired(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
    try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return bitmap
        val exif = ExifInterface(inputStream)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
            else -> bitmap
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return bitmap
}

/**
 * Xoay bitmap theo góc
 */
private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
    val matrix = Matrix()
    matrix.postRotate(degrees)
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

/**
 * Chuyển Base64 string về Bitmap để hiển thị
 */
fun base64ToBitmap(base64String: String): Bitmap? {
    return try {
        val decodedBytes = Base64.decode(base64String, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

