package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts

// Robust Android Photo Picker Intent Helper.
// Fixed root cause of empty folders:
// 1. Removed restrictive EXTRA_MIME_TYPES which caused MediaStore queries to return 0 rows.
// 2. Uses pure unconstrained image MIME filter to accept JPEG, PNG, WEBP, GIF, and all valid image types.
// 3. Provides clean ACTION_PICK for Gallery, ACTION_GET_CONTENT for Files/Providers, and PickVisualMedia for modern Photo Picker.
object PhotoPickerHelper {

    // Checks if modern Android System Photo Picker is available.
    fun isSystemPhotoPickerAvailable(context: Context): Boolean {
        return ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)
    }

    // Creates ACTION_PICK intent targeting native device gallery / photo albums directly.
    // Notice: pure type = "image/*" without restrictive EXTRA_MIME_TYPES so all photos appear.
    fun createGalleryPickIntent(allowMultiple: Boolean = false): Intent {
        return Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            type = "image/*"
            if (allowMultiple) {
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
        }
    }

    // Creates fallback ACTION_GET_CONTENT intent for any available image provider.
    fun createGetContentIntent(allowMultiple: Boolean = false): Intent {
        return Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            if (allowMultiple) {
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
        }
    }

    // Creates ACTION_OPEN_DOCUMENT intent with CATEGORY_OPENABLE for broad document storage.
    fun createOpenDocumentIntent(allowMultiple: Boolean = false): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            if (allowMultiple) {
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
        }
    }

    // Creates a chooser intent allowing selection from native Gallery as well as file providers.
    fun createPhotoChooserIntent(context: Context, title: String = "Select Photo", allowMultiple: Boolean = false): Intent {
        val galleryIntent = createGalleryPickIntent(allowMultiple)
        val getContentIntent = createGetContentIntent(allowMultiple)
        val chooser = Intent.createChooser(getContentIntent, title)
        chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(galleryIntent))
        return chooser
    }

    fun extractUris(data: Intent?): List<Uri> {
        if (data == null) return emptyList()
        val uris = mutableListOf<Uri>()
        data.data?.let { uris.add(it) }
        data.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) {
                uris.add(clip.getItemAt(i).uri)
            }
        }
        return uris.distinct()
    }

    // Extracts filename, size, and MIME type from a content URI for full debugging & display.
    fun getFileDetails(context: Context, uri: Uri): Triple<String, Long, String>? {
        return try {
            val mimeType = context.contentResolver.getType(uri) ?: "image/*"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (nameIndex != -1 && sizeIndex != -1 && cursor.moveToFirst()) {
                    val name = cursor.getString(nameIndex) ?: "photo.jpg"
                    val size = cursor.getLong(sizeIndex)
                    Triple(name, size, mimeType)
                } else {
                    Triple("photo.jpg", 0L, mimeType)
                }
            } ?: Triple("photo.jpg", 0L, mimeType)
        } catch (e: Exception) {
            val mimeType = try { context.contentResolver.getType(uri) ?: "image/*" } catch (_: Exception) { "image/*" }
            Triple("photo.jpg", 0L, mimeType)
        }
    }
}
