package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.URI
import java.net.URLEncoder

sealed class StoreUrlValidationResult {
    data class Valid(val url: String) : StoreUrlValidationResult()
    data class Unpublished(val message: String = QRCodeHelper.UNPUBLISHED_QR_MESSAGE) : StoreUrlValidationResult()
    data class Invalid(val message: String) : StoreUrlValidationResult()
}

object QRCodeHelper {

    const val UNPUBLISHED_QR_MESSAGE =
        "Publish the app first to generate a working Customer Store QR Code."

    private val previewHostPrefixes = listOf("ais-dev-", "ais-pre-")
    private val previewHostSuffixes = listOf(
        ".aistudio.google.com",
        "aistudio.google.com",
        ".makersuite.google.com",
        "makersuite.google.com",
        ".usercontent.goog",
        ".ai.studio",
        ".local"
    )
    private val localHosts = setOf("localhost", "127.0.0.1", "10.0.2.2", "0.0.0.0", "local.ggifts")
    private val privateIpRegex = Regex("^(10\\.|192\\.168\\.|172\\.(1[6-9]|2[0-9]|3[0-1])\\.).*")
    private val adminPathRegex = Regex("(^|/)(admin|admin-panel|adminpanel|wp-admin)(/|$)")
    private val adminQueryRegex = Regex("(^|&)(admin|isadmin|role|panel|view|mode)=admin(&|$)|(^|&)admin=true(&|$)")

    fun validateCustomerStoreUrl(rawUrl: String?): StoreUrlValidationResult {
        val trimmed = rawUrl?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            return StoreUrlValidationResult.Unpublished()
        }

        val uri = try {
            URI(trimmed)
        } catch (_: Exception) {
            return StoreUrlValidationResult.Invalid(
                "Invalid URL format. Enter a valid published HTTPS Customer Store URL."
            )
        }

        val scheme = uri.scheme?.lowercase().orEmpty()
        val host = uri.host?.lowercase().orEmpty()

        // Detect temporary AI Studio preview URLs, Firebase preview channels, and local/emulator hosts
        val isAiStudioPreviewHost = previewHostPrefixes.any { host.startsWith(it) || host.contains(".$it") } ||
            (host.endsWith(".run.app") && (host.contains("ais-dev") || host.contains("ais-pre"))) ||
            previewHostSuffixes.any { suffix ->
                if (suffix.startsWith(".")) host.endsWith(suffix) else host == suffix
            }
        val isFirebasePreviewChannel =
            (host.endsWith(".web.app") || host.endsWith(".firebaseapp.com")) &&
                host.substringBefore(".").contains("--")
        val isLocalHost = localHosts.contains(host) || privateIpRegex.matches(host)

        if (isAiStudioPreviewHost || isFirebasePreviewChannel || isLocalHost) {
            return StoreUrlValidationResult.Unpublished()
        }

        if (scheme != "https") {
            return StoreUrlValidationResult.Invalid(
                "Customer Store URL must start with https:// so any Android or iPhone camera can open it securely."
            )
        }

        val tld = host.substringAfterLast('.', "")
        if (host.isBlank() || !host.contains(".") || tld.length < 2) {
            return StoreUrlValidationResult.Invalid(
                "Enter a valid publicly accessible domain name (e.g. https://gen-lang-client-0989190352.web.app)."
            )
        }

        // Prevent QR code from pointing to Admin Panel routes
        val path = (uri.rawPath ?: "").lowercase()
        val query = (uri.rawQuery ?: "").lowercase()
        val fragment = (uri.rawFragment ?: "").lowercase()
        val targetsAdmin = adminPathRegex.containsMatchIn(path) ||
            adminQueryRegex.containsMatchIn(query) ||
            fragment == "admin" ||
            fragment.startsWith("/admin") ||
            fragment.contains("/admin/")

        if (targetsAdmin) {
            return StoreUrlValidationResult.Invalid(
                "Admin Panel URLs cannot be used for the Customer Store QR Code. Enter the public Customer Store URL only."
            )
        }

        return StoreUrlValidationResult.Valid(trimmed)
    }

    fun buildProductUrl(baseStoreUrl: String?, productId: String): StoreUrlValidationResult {
        val cleanId = productId.trim()
        if (cleanId.isEmpty()) {
            return StoreUrlValidationResult.Invalid("Save the product first before generating its QR code.")
        }
        return when (val baseResult = validateCustomerStoreUrl(baseStoreUrl)) {
            is StoreUrlValidationResult.Valid -> {
                val baseWithoutFragment = baseResult.url.substringBefore("#")
                val separator = if (baseWithoutFragment.contains("?")) "&" else "?"
                val encodedId = URLEncoder.encode(cleanId, "UTF-8")
                StoreUrlValidationResult.Valid("${baseWithoutFragment}${separator}product=$encodedId")
            }
            is StoreUrlValidationResult.Unpublished -> baseResult
            is StoreUrlValidationResult.Invalid -> baseResult
        }
    }

    fun generateCustomerStoreQRCodeBitmap(websiteUrl: String?, size: Int = 600): Bitmap? {
        return when (val result = validateCustomerStoreUrl(websiteUrl)) {
            is StoreUrlValidationResult.Valid -> generateQRCodeBitmap(result.url, size)
            else -> null
        }
    }

    fun generateProductQRCodeBitmap(baseStoreUrl: String?, productId: String, size: Int = 600): Bitmap? {
        return when (val result = buildProductUrl(baseStoreUrl, productId)) {
            is StoreUrlValidationResult.Valid -> generateQRCodeBitmap(result.url, size)
            else -> null
        }
    }

    fun openStoreUrlInBrowser(context: Context, websiteUrl: String?): Boolean {
        val result = validateCustomerStoreUrl(websiteUrl)
        if (result !is StoreUrlValidationResult.Valid) {
            val msg = when (result) {
                is StoreUrlValidationResult.Unpublished -> result.message
                is StoreUrlValidationResult.Invalid -> result.message
                else -> UNPUBLISHED_QR_MESSAGE
            }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            return false
        }
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(result.url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open browser: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun generateQRCodeBitmap(content: String, size: Int = 600): Bitmap {
        require(content.isNotBlank()) { "QR code content cannot be blank" }
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
            EncodeHintType.MARGIN to 1
        )
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    fun printQRCode(
        context: Context,
        bitmap: Bitmap,
        title: String,
        subtitle: String,
        url: String
    ): Boolean {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            Toast.makeText(context, "Print service is not available on this device.", Toast.LENGTH_SHORT).show()
            return false
        }

        return try {
            val safeJobName = "G_Gifts_QR_${title.replace(Regex("[^A-Za-z0-9]"), "_").take(24)}"
            val adapter = object : PrintDocumentAdapter() {
                private var currentAttributes: PrintAttributes? = null

                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback,
                    extras: Bundle?
                ) {
                    currentAttributes = newAttributes
                    if (cancellationSignal?.isCanceled == true) {
                        callback.onLayoutCancelled()
                        return
                    }
                    val info = PrintDocumentInfo.Builder("$safeJobName.pdf")
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(1)
                        .build()
                    callback.onLayoutFinished(info, true)
                }

                override fun onWrite(
                    pages: Array<out PageRange>,
                    destination: ParcelFileDescriptor,
                    cancellationSignal: CancellationSignal?,
                    callback: WriteResultCallback
                ) {
                    val attrs = currentAttributes ?: PrintAttributes.Builder().build()
                    val pdfDocument = PrintedPdfDocument(context, attrs)
                    try {
                        val page = pdfDocument.startPage(0)
                        if (cancellationSignal?.isCanceled == true) {
                            callback.onWriteCancelled()
                            pdfDocument.close()
                            return
                        }
                        drawQrPrintPage(page.canvas, bitmap, title, subtitle, url)
                        pdfDocument.finishPage(page)

                        FileOutputStream(destination.fileDescriptor).use { out ->
                            pdfDocument.writeTo(out)
                        }
                        callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        callback.onWriteFailed(e.localizedMessage)
                    } finally {
                        pdfDocument.close()
                    }
                }
            }
            printManager.print(safeJobName, adapter, PrintAttributes.Builder().build())
            true
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to start print dialog: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun drawQrPrintPage(
        canvas: Canvas,
        qrBitmap: Bitmap,
        title: String,
        subtitle: String,
        url: String
    ) {
        val pageWidth = canvas.width.toFloat()
        val pageHeight = canvas.height.toFloat()
        val centerX = pageWidth / 2f

        val bgPaint = Paint().apply { color = Color.WHITE }
        canvas.drawRect(0f, 0f, pageWidth, pageHeight, bgPaint)

        val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6750A4")
            textSize = 22f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 16f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 12f
            textAlign = Paint.Align.CENTER
        }
        val urlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#333333")
            textSize = 10f
            textAlign = Paint.Align.CENTER
        }

        var y = 64f
        canvas.drawText("G Gifts Toys Sports Decoration Store", centerX, y, headingPaint)
        y += 22f
        canvas.drawText("Veera Reddy Nagar, Boduppal, Telangana • WhatsApp: 7036436305", centerX, y, bodyPaint)
        y += 28f
        canvas.drawText(title, centerX, y, titlePaint)
        y += 18f
        canvas.drawText(subtitle, centerX, y, bodyPaint)
        y += 24f

        val qrSize = minOf(pageWidth * 0.6f, pageHeight * 0.45f).toInt().coerceAtLeast(180)
        val left = ((pageWidth - qrSize) / 2f).toInt()
        val top = y.toInt()
        val destRect = Rect(left, top, left + qrSize, top + qrSize)
        canvas.drawBitmap(qrBitmap, null, destRect, null)

        y = (top + qrSize + 26).toFloat()
        canvas.drawText("Scan to view & Book Now for Store Pickup (No Home Delivery)", centerX, y, bodyPaint)
        y += 18f
        canvas.drawText(url, centerX, y, urlPaint)
    }

    fun saveBitmapToGallery(context: Context, bitmap: Bitmap, title: String = "GGifts_Shop_QR"): Boolean {
        val filename = "${title}_${System.currentTimeMillis()}.png"
        var fos: OutputStream? = null
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/GGifts")
                }
                val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (imageUri != null) {
                    fos = resolver.openOutputStream(imageUri)
                }
            } else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + "/GGifts"
                val file = File(imagesDir)
                if (!file.exists()) {
                    file.mkdirs()
                }
                val image = File(file, filename)
                fos = FileOutputStream(image)
            }
            fos?.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                Toast.makeText(context, "QR Code saved to Gallery successfully!", Toast.LENGTH_SHORT).show()
                true
            } ?: false
        } catch (e: Exception) {
            Toast.makeText(context, "Could not save image: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun shareQRCode(context: Context, bitmap: Bitmap, title: String, message: String) {
        try {
            val cachePath = File(context.cacheDir, "images")
            cachePath.mkdirs()
            val file = File(cachePath, "ggifts_qr.png")
            val stream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()

            val contentUri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, message)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share G Gifts Shop QR").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            // Fallback: share text link directly
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "$message\n$title")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share G Gifts Store Link").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}
