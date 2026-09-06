package com.iykyk.collage.data.storage

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Handles saving generated collages to Android device gallery via [MediaStore]
 * and launching Android Sharesheet using [Intent.ACTION_SEND] and [FileProvider].
 */
class MediaExporter(
    private val context: Context
) {

    companion object {
        private const val TAG = "MediaExporter"
        private const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".fileprovider"
    }

    /**
     * Saves the collage Bitmap to device gallery using Android MediaStore.
     * Compliant with Android 10+ Scoped Storage guidelines.
     */
    suspend fun saveCollageToGallery(bitmap: Bitmap): Uri? = withContext(Dispatchers.IO) {
        val filename = "IYKYK_Collage_${System.currentTimeMillis()}.png"
        val imageCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/IYKYKCollages")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val contentResolver = context.contentResolver
        var imageUri: Uri? = null

        try {
            imageUri = contentResolver.insert(imageCollection, contentValues)
            if (imageUri != null) {
                contentResolver.openOutputStream(imageUri)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    contentResolver.update(imageUri, contentValues, null, null)
                }

                Log.d(TAG, "Successfully saved collage to gallery: $imageUri")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save collage to gallery: ${e.message}", e)
            if (imageUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    contentResolver.delete(imageUri, null, null)
                } catch (_: Exception) {}
            }
            imageUri = null
        }

        return@withContext imageUri
    }

    /**
     * Prepares and launches standard Android Sharesheet (Intent.ACTION_SEND) for the collage.
     */
    suspend fun shareCollage(bitmap: Bitmap) = withContext(Dispatchers.IO) {
        try {
            val cacheFolder = File(context.cacheDir, "images").apply { mkdirs() }
            val shareFile = File(cacheFolder, "iykyk_collage_share.png")

            FileOutputStream(shareFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val authority = "${context.packageName}$FILE_PROVIDER_AUTHORITY_SUFFIX"
            val contentUri = FileProvider.getUriForFile(context, authority, shareFile)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "IYKYK Unique-Person Collage")
                putExtra(Intent.EXTRA_TEXT, "Check out the unique people detected in this video!")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooserIntent = Intent.createChooser(shareIntent, "Share Collage").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(chooserIntent)
            Log.d(TAG, "Launched Android Sharesheet with URI: $contentUri")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share collage: ${e.message}", e)
        }
    }
}
