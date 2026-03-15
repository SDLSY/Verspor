package com.example.newstart.ui.relax

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

data class ImportedImageAsset(
    val file: File,
    val uriString: String,
    val mimeType: String
)

object ImageImportHelper {

    fun saveBitmapToCache(context: Context, bitmap: Bitmap, prefix: String): ImportedImageAsset {
        val target = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}.jpg")
        FileOutputStream(target).use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
        }
        return ImportedImageAsset(
            file = target,
            uriString = Uri.fromFile(target).toString(),
            mimeType = "image/jpeg"
        )
    }

    @Suppress("DEPRECATION")
    fun copyUriToCache(context: Context, uri: Uri, prefix: String): ImportedImageAsset {
        val mimeType = context.contentResolver.getType(uri)?.ifBlank { null } ?: "image/jpeg"
        val extension = when {
            mimeType.contains("png", ignoreCase = true) -> "png"
            else -> "jpg"
        }
        val target = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}.$extension")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: run {
            val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            return saveBitmapToCache(context, bitmap, prefix)
        }
        return ImportedImageAsset(
            file = target,
            uriString = Uri.fromFile(target).toString(),
            mimeType = mimeType
        )
    }
}
