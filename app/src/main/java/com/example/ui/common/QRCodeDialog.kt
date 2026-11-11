package com.example.ui.common

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Product
import com.example.util.QRCodeHelper
import com.example.util.ShopStrings
import com.example.util.StoreUrlValidationResult

@Composable
fun StoreQRCodeDialog(
    websiteUrl: String,
    shopName: String = "G Gifts Toys Sports Decoration Store",
    onDismiss: () -> Unit
) {
    val validation = remember(websiteUrl) {
        QRCodeHelper.validateCustomerStoreUrl(websiteUrl)
    }
    QRCodePreviewDialog(
        titleText = if (ShopStrings.isTelugu) "కస్టమర్ స్టోర్ QR కోడ్" else "Customer Store QR Code",
        shopName = shopName,
        itemSubtitle = "Veera Reddy Nagar, Boduppal, Telangana (Phone: 7036436305)",
        scanHint = if (ShopStrings.isTelugu)
            "లాగిన్ లేదా యాప్ ఇన్‌స్టాల్ అవసరం లేకుండానే డైరెక్ట్‌గా కస్టమర్ స్టోర్ ఓపెన్ అవుతుంది."
        else
            "Opens the public Customer Store directly in mobile browser without requiring login or app installation.",
        validation = validation,
        fileNamePrefix = "GGifts_Store_QR",
        printSubtitle = "Customer Store QR Code (Store Pickup Only)",
        shareMessagePrefix = "Scan to shop at G Gifts Store Boduppal: ",
        onDismiss = onDismiss
    )
}

@Composable
fun ProductQRCodeDialog(
    product: Product,
    websiteUrl: String,
    shopName: String = "G Gifts Toys Sports Decoration Store",
    onDismiss: () -> Unit
) {
    val validation = remember(websiteUrl, product.id) {
        QRCodeHelper.buildProductUrl(websiteUrl, product.id)
    }
    QRCodePreviewDialog(
        titleText = "Product QR Code",
        shopName = product.name,
        itemSubtitle = "$shopName • Price: ₹${"%.0f".format(product.sellingPrice)}",
        scanHint = "Scanning opens ${product.name} details directly in any mobile browser so customers can Book Now for Store Pickup.",
        validation = validation,
        fileNamePrefix = "GGifts_${product.name.replace(Regex("[^A-Za-z0-9]"), "_").take(16)}_QR",
        printSubtitle = "${product.name} • ₹${"%.0f".format(product.sellingPrice)}",
        shareMessagePrefix = "Check out ${product.name} at G Gifts Store Boduppal: ",
        onDismiss = onDismiss
    )
}

@Composable
private fun QRCodePreviewDialog(
    titleText: String,
    shopName: String,
    itemSubtitle: String,
    scanHint: String,
    validation: StoreUrlValidationResult,
    fileNamePrefix: String,
    printSubtitle: String,
    shareMessagePrefix: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(validation) {
        qrBitmap = if (validation is StoreUrlValidationResult.Valid) {
            QRCodeHelper.generateQRCodeBitmap(validation.url, size = 650)
        } else {
            null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.QrCode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = titleText,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = shopName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = itemSubtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                when (validation) {
                    is StoreUrlValidationResult.Valid -> {
                        val validUrl = validation.url

                        // QR Code Container
                        Box(
                            modifier = Modifier
                                .size(220.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                .padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (qrBitmap != null) {
                                Image(
                                    bitmap = qrBitmap!!.asImageBitmap(),
                                    contentDescription = titleText,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("qr_code_image")
                                )
                            } else {
                                CircularProgressIndicator(modifier = Modifier.size(36.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = validUrl,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("qr_verified_url_text")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "📸 " + (if (ShopStrings.isTelugu) "మొబైల్ కెమెరాతో స్కాన్ చేయండి" else "Scan with Any Mobile Camera"),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = scanHint,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Download & Print buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    qrBitmap?.let { bmp ->
                                        QRCodeHelper.saveBitmapToGallery(context, bmp, fileNamePrefix)
                                    }
                                },
                                enabled = qrBitmap != null,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("qr_download_button"),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (ShopStrings.isTelugu) "డౌన్‌లోడ్" else "Download",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }

                            FilledTonalButton(
                                onClick = {
                                    qrBitmap?.let { bmp ->
                                        QRCodeHelper.printQRCode(
                                            context = context,
                                            bitmap = bmp,
                                            title = shopName,
                                            subtitle = printSubtitle,
                                            url = validUrl
                                        )
                                    }
                                },
                                enabled = qrBitmap != null,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("qr_print_button"),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (ShopStrings.isTelugu) "ప్రింట్" else "Print",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Share & Test in Browser
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    qrBitmap?.let { bmp ->
                                        QRCodeHelper.shareQRCode(
                                            context = context,
                                            bitmap = bmp,
                                            title = shopName,
                                            message = "$shareMessagePrefix$validUrl"
                                        )
                                    }
                                },
                                enabled = qrBitmap != null,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("qr_share_button"),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (ShopStrings.isTelugu) "షేర్" else "Share",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }

                            TextButton(
                                onClick = { QRCodeHelper.openStoreUrlInBrowser(context, validUrl) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("qr_test_browser_button")
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    if (ShopStrings.isTelugu) "ఓపెన్ లింక్" else "Open Link",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }

                    is StoreUrlValidationResult.Unpublished -> {
                        Surface(
                            color = Color(0xFFFFF3E0),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFFE65100).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.WarningAmber,
                                    contentDescription = "Store not published",
                                    tint = Color(0xFFE65100),
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = validation.message,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE65100),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.testTag("qr_unpublished_message")
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Temporary preview URLs only work inside AI Studio and fail when scanned on other phones. Publish the free Firebase Hosting Customer Store first and save the public HTTPS URL in Admin > Settings.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF5D4037),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    is StoreUrlValidationResult.Invalid -> {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = "Invalid Store URL",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = validation.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.testTag("qr_invalid_message")
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
