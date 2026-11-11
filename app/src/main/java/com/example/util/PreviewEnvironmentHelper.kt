package com.example.util

import android.content.Context
import android.os.Build
import android.provider.MediaStore

/**
 * Diagnostic and environment helper to identify Google AI Studio development/preview
 * mode and verify Android MediaStore gallery access.
 */
object PreviewEnvironmentHelper {

    /**
     * Detects if the app is executing inside the Google AI Studio cloud streaming emulator
     * or standard Android virtual test environment.
     */
    fun isCloudPreviewEnvironment(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val hardware = Build.HARDWARE.lowercase()
        val product = Build.PRODUCT.lowercase()
        val brand = Build.BRAND.lowercase()
        val device = Build.DEVICE.lowercase()

        return fingerprint.startsWith("generic")
                || fingerprint.startsWith("unknown")
                || model.contains("google_sdk")
                || model.contains("emulator")
                || model.contains("sdk")
                || product.contains("sdk")
                || hardware.contains("goldfish")
                || hardware.contains("ranchu")
                || (brand.startsWith("generic") && device.startsWith("generic"))
    }

    /**
     * Checks whether the device's MediaStore has any indexed photos.
     * In fresh cloud preview emulators, this returns true (no photos exist on the virtual disk).
     */
    fun isDeviceGalleryEmpty(context: Context): Boolean {
        return try {
            val projection = arrayOf(MediaStore.Images.Media._ID)
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Images.Media._ID} LIMIT 1"
            )?.use { cursor ->
                !cursor.moveToFirst()
            } ?: true
        } catch (_: Exception) {
            false
        }
    }
}
