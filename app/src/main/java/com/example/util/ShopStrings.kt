package com.example.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object ShopStrings {
    var isTelugu by mutableStateOf(false)

    fun toggleLanguage() {
        isTelugu = !isTelugu
    }

    // App & Header
    val appTitle: String
        get() = if (isTelugu) "జి గిఫ్ట్స్ షాపింగ్" else "G Gifts Shopping"

    val shopTagline: String
        get() = if (isTelugu) "టాయ్స్ • స్పోర్ట్స్ • డెకరేషన్ • గిఫ్ట్స్ • స్టేషనరీ" else "Toys • Sports • Decoration • Gifts • Stationery"

    val storePickupOnly: String
        get() = if (isTelugu) "స్టోర్ పికప్ మాత్రమే" else "STORE PICKUP ONLY"

    val storePickupNotice: String
        get() = if (isTelugu)
            "హోమ్ డెలివరీ లేదు • డెలివరీ ఛార్జీలు లేవు. ఆర్డర్ కన్ఫర్మ్ అయిన తర్వాత షాపుకు వచ్చి సరుకు తీసుకోవాలి."
        else
            "No home delivery • Zero delivery charges. Customer must collect products directly at the store."

    val storeAddress: String
        get() = if (isTelugu)
            "వీరారెడ్డి నగర్, బోడుప్పల్, తెలంగాణ (ఫోన్: 7036436305)"
        else
            "Veera Reddy Nagar, Boduppal, Telangana (Phone: 7036436305)"

    // Navigation & Buttons
    val tabShop: String
        get() = if (isTelugu) "షాప్" else "Shop"

    val tabCategories: String
        get() = if (isTelugu) "కేటగిరీలు" else "Categories"

    val tabPickup: String
        get() = if (isTelugu) "లొకేషన్ & QR" else "Pickup & QR"

    val tabAccount: String
        get() = if (isTelugu) "ఖాతా" else "Account"

    val bookNow: String
        get() = if (isTelugu) "ఇప్పుడే బుక్ చేయండి" else "Book Now"

    val whatsAppBook: String
        get() = if (isTelugu) "వాట్సాప్ బుకింగ్" else "WhatsApp Book"

    val searchPlaceholder: String
        get() = if (isTelugu) "టాయ్స్, స్పోర్ట్స్, గిఫ్ట్స్ వెతకండి..." else "Search toys, sports, gifts, stationery..."

    val customerPhone: String
        get() = if (isTelugu) "కస్టమర్ మొబైల్ నంబర్" else "Customer Mobile Number"

    val customerName: String
        get() = if (isTelugu) "కస్టమర్ పేరు" else "Customer Name"

    val selectQuantity: String
        get() = if (isTelugu) "పరిమాణం ఎంచుకోండి" else "Select Quantity"

    val totalAmount: String
        get() = if (isTelugu) "మొత్తం ధర" else "Total Amount"

    val unitPrice: String
        get() = if (isTelugu) "యూనిట్ ధర" else "Unit Price"

    val shopQRCode: String
        get() = if (isTelugu) "షాప్ క్యూఆర్ కోడ్" else "Shop QR Code"

    val scanToShop: String
        get() = if (isTelugu) "మొబైల్ కెమెరాతో స్కాన్ చేసి నేరుగా షాపింగ్ చేయండి" else "Scan with any phone camera to open online store directly"

    val downloadQR: String
        get() = if (isTelugu) "QR కోడ్ డౌన్‌లోడ్ చేయండి" else "Download QR Code"

    val printQR: String
        get() = if (isTelugu) "QR కోడ్ ప్రింట్ చేయండి" else "Print QR Code"

    val shareQR: String
        get() = if (isTelugu) "షేర్ చేయండి" else "Share QR Code"
}
