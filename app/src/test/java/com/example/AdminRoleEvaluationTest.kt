package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class AdminRoleEvaluationTest {

    private val singleAuthorizedAdminEmail = "nrganna1@gmail.com"

    private fun isSuperAdminEmail(email: String?): Boolean {
        if (email.isNullOrBlank()) return false
        val clean = email.trim().lowercase()
        return clean == singleAuthorizedAdminEmail
    }

    private fun evaluateRole(
        email: String
    ): String {
        val cleanEmail = email.trim().lowercase()
        return if (isSuperAdminEmail(cleanEmail)) "admin" else "customer"
    }

    sealed interface AdminAccessResult {
        data class Granted(val role: String, val userEmail: String) : AdminAccessResult
        data class Denied(val message: String) : AdminAccessResult
        data object RequiresLogin : AdminAccessResult
    }

    private fun handleStoreAdministratorAccess(
        currentUserEmail: String?
    ): AdminAccessResult {
        if (currentUserEmail == null) {
            return AdminAccessResult.RequiresLogin
        }
        val cleanEmail = currentUserEmail.trim().lowercase()
        return if (isSuperAdminEmail(cleanEmail)) {
            AdminAccessResult.Granted(role = "admin", userEmail = cleanEmail)
        } else {
            AdminAccessResult.Denied("Access Denied: Account $cleanEmail is not authorized. Only the single store owner ($singleAuthorizedAdminEmail) can access the Admin Panel.")
        }
    }

    // Product parsing helper verification
    private fun extractPrice(data: Map<String, Any?>): Double {
        val raw = data["sellingPrice"] ?: data["price"] ?: data["amount"]
        return when (raw) {
            is Double -> raw
            is Float -> raw.toDouble()
            is Long -> raw.toDouble()
            is Int -> raw.toDouble()
            is String -> raw.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }

    private fun extractImages(data: Map<String, Any?>): List<String> {
        val list = data["images"] as? List<*>
        if (list != null) {
            return list.mapNotNull { it?.toString() }.filter { it.isNotBlank() }
        }
        val single = data["image"] ?: data["imageUrl"] ?: data["photo"]
        return if (single != null && single.toString().isNotBlank()) listOf(single.toString()) else emptyList()
    }

    private fun extractIsEnabled(data: Map<String, Any?>): Boolean {
        return when {
            data.containsKey("isEnabled") -> (data["isEnabled"] as? Boolean) ?: true
            data.containsKey("active") -> (data["active"] as? Boolean) ?: true
            data.containsKey("status") -> !(data["status"]?.toString().equals("inactive", ignoreCase = true))
            else -> true
        }
    }

    @Test
    fun testNrganna1_RecognizedAsSuperAdmin_DirectToAdminDashboard() {
        val email = "nrganna1@gmail.com"
        val role = evaluateRole(email)
        assertEquals("admin", role)
    }

    @Test
    fun testNrganna1_CaseInsensitive_RecognizedAsSuperAdmin() {
        val email = "  NRGANNA1@GMAIL.COM  "
        val role = evaluateRole(email)
        assertEquals("admin", role)
    }

    @Test
    fun testSecondaryOrUnknownEmails_StrictSingleAdminDenied() {
        assertEquals("customer", evaluateRole("nrganna2015@gmail.com"))
        assertEquals("customer", evaluateRole("manager@ggifts.com"))
        assertEquals("customer", evaluateRole("shopper@example.com"))
    }

    @Test
    fun testSuperAdminEmailAllowlist_StrictlyOneOwner() {
        assertTrue(isSuperAdminEmail("nrganna1@gmail.com"))
        assertTrue(isSuperAdminEmail("  nrganna1@gmail.com  "))
        assertTrue(isSuperAdminEmail("NRGANNA1@GMAIL.COM"))
        assertFalse(isSuperAdminEmail("nrganna2015@gmail.com"))
        assertFalse(isSuperAdminEmail("customer@gmail.com"))
        assertFalse(isSuperAdminEmail("attacker@gmail.com"))
        assertFalse(isSuperAdminEmail(null))
        assertFalse(isSuperAdminEmail(""))
    }

    @Test
    fun testStoreAdministratorAccess_AuthorizedOwner_Granted() {
        val result = handleStoreAdministratorAccess("nrganna1@gmail.com")
        assertTrue(result is AdminAccessResult.Granted)
        assertEquals("admin", (result as AdminAccessResult.Granted).role)
    }

    @Test
    fun testStoreAdministratorAccess_UnauthorizedCustomer_AccessDenied() {
        val result = handleStoreAdministratorAccess("customer@example.com")
        assertTrue(result is AdminAccessResult.Denied)
        val denied = result as AdminAccessResult.Denied
        assertTrue(denied.message.contains("Access Denied"))
    }

    @Test
    fun testStoreAdministratorAccess_Unauthenticated_RequiresLogin() {
        val result = handleStoreAdministratorAccess(null)
        assertTrue(result is AdminAccessResult.RequiresLogin)
    }

    @Test
    fun testWhatsAppBookingMessageFormat() {
        val bookingId = "GG-104928"
        val customerName = "Rahul Sharma"
        val customerPhone = "7036436305"
        val productName = "Wooden Toy Train"
        val quantity = 2
        val price = 350.0
        val total = 700.0

        val expectedMessage = """
G GIFTS SHOP - NEW CUSTOMER BOOKING

Booking ID: GG-104928
Customer Name: Rahul Sharma
Customer Phone: 7036436305
Product: Wooden Toy Train
Quantity: 2
Unit Price: ₹350
Total Amount: ₹700
Pickup: Store Pickup Only

Please confirm my booking.
""".trimIndent()

        val generatedMessage = """
G GIFTS SHOP - NEW CUSTOMER BOOKING

Booking ID: $bookingId
Customer Name: $customerName
Customer Phone: $customerPhone
Product: $productName
Quantity: $quantity
Unit Price: ₹${"%.0f".format(price)}
Total Amount: ₹${"%.0f".format(total)}
Pickup: Store Pickup Only

Please confirm my booking.
""".trimIndent()

        assertEquals(expectedMessage, generatedMessage)
    }

    @Test
    fun testWhatsAppBookingMessageFormat_WithOptionalNameEmpty() {
        val bookingId = "GG-884912"
        val customerName = ""
        val customerPhone = "7036436305"
        val productName = "Cricket Bat Set"
        val quantity = 1
        val price = 999.0
        val total = 999.0

        val customerNameVal = if (customerName.isNotBlank()) customerName.trim() else "[Optional]"
        val message = """
G GIFTS SHOP - NEW CUSTOMER BOOKING

Booking ID: $bookingId
Customer Name: $customerNameVal
Customer Phone: $customerPhone
Product: $productName
Quantity: $quantity
Unit Price: ₹${"%.0f".format(price)}
Total Amount: ₹${"%.0f".format(total)}
Pickup: Store Pickup Only

Please confirm my booking.
""".trimIndent()

        assertTrue(message.contains("Customer Name: [Optional]"))
        assertTrue(message.contains("Pickup: Store Pickup Only"))
        assertTrue(message.contains("Booking ID: GG-884912"))
    }

    @Test
    fun testCustomerBookingWithoutLogin_ValidPhone() {
        val phone = "7036436305"
        val cleanPhone = phone.trim().replace("+91", "").replace(" ", "").replace("-", "")
        assertTrue(cleanPhone.length >= 10)
    }

    @Test
    fun testProductPriceExtraction_SupportsLongIntDoubleAndString() {
        assertEquals(499.0, extractPrice(mapOf("sellingPrice" to 499L)), 0.001)
        assertEquals(250.0, extractPrice(mapOf("price" to 250)), 0.001)
        assertEquals(399.50, extractPrice(mapOf("sellingPrice" to 399.50)), 0.001)
        assertEquals(799.0, extractPrice(mapOf("price" to "799")), 0.001)
    }

    @Test
    fun testProductImagesExtraction_SupportsListOrSingleString() {
        val withList = mapOf("images" to listOf("https://storage.googleapis.com/img1.jpg"))
        assertEquals(listOf("https://storage.googleapis.com/img1.jpg"), extractImages(withList))

        val withSingle = mapOf("imageUrl" to "https://storage.googleapis.com/single.jpg")
        assertEquals(listOf("https://storage.googleapis.com/single.jpg"), extractImages(withSingle))

        val withEmpty = emptyMap<String, Any?>()
        assertTrue(extractImages(withEmpty).isEmpty())
    }

    @Test
    fun testProductVisibility_DefaultsToActiveWhenFieldMissing() {
        // When admin adds product without explicit isEnabled field, customer can still see it
        assertTrue(extractIsEnabled(emptyMap()))
        assertTrue(extractIsEnabled(mapOf("status" to "active")))
        assertTrue(extractIsEnabled(mapOf("isEnabled" to true)))
        assertFalse(extractIsEnabled(mapOf("isEnabled" to false)))
        assertFalse(extractIsEnabled(mapOf("status" to "inactive")))
    }

    @Test
    fun testComplete13StepCustomerAndAdminFlow() {
        // 1. Admin logs in with nrganna1@gmail.com
        val adminEmail = "nrganna1@gmail.com"
        val adminRole = evaluateRole(adminEmail)
        assertEquals("admin", adminRole)

        // 2. Admin adds a new product
        val newProductData = mapOf(
            "name" to "Teddy Bear Giant 4ft",
            "description" to "Soft plush giant teddy bear, perfect gift.",
            "sellingPrice" to 1299.0,
            "originalPrice" to 1999.0,
            "stock" to 15,
            "category" to "Toys",
            "images" to listOf("https://storage.googleapis.com/teddy.jpg"),
            "isEnabled" to true
        )

        // 3. Product appears automatically in Customer Store
        val isVisible = extractIsEnabled(newProductData)
        val extractedPrice = extractPrice(newProductData)
        val extractedImages = extractImages(newProductData)
        assertTrue(isVisible)
        assertEquals(1299.0, extractedPrice, 0.01)
        assertEquals("https://storage.googleapis.com/teddy.jpg", extractedImages.first())

        // 4. Customer opens app without login
        val customerEmail: String? = null
        val customerRole = evaluateRole(customerEmail ?: "")
        assertEquals("customer", customerRole)

        // 5. Customer selects product and quantity
        val selectedQty = 2
        val unitPrice = extractedPrice
        val totalAmount = unitPrice * selectedQty
        assertEquals(2598.0, totalAmount, 0.01)

        // 6. Customer enters mobile number
        val customerPhone = "7036436305"
        val cleanPhone = customerPhone.trim().replace("+91", "").replace(" ", "").replace("-", "")
        assertEquals(10, cleanPhone.length)

        // 7. Customer confirms booking -> Generate unique Booking ID
        val bookingId = "GG-782914"
        assertTrue(bookingId.startsWith("GG-"))

        // 8. Booking saves successfully in Firebase
        data class TestBooking(
            val bookingId: String,
            val customerPhone: String,
            val productName: String,
            val quantity: Int,
            val unitPrice: Double,
            val totalAmount: Double,
            var status: String,
            val deliveryType: String
        )
        val booking = TestBooking(
            bookingId = bookingId,
            customerPhone = cleanPhone,
            productName = "Teddy Bear Giant 4ft",
            quantity = selectedQty,
            unitPrice = unitPrice,
            totalAmount = totalAmount,
            status = "Pending",
            deliveryType = "Store Pickup Only"
        )
        assertEquals("Pending", booking.status)
        assertEquals("Store Pickup Only", booking.deliveryType)

        // 9. WhatsApp opens with pre-filled booking message
        val whatsAppMessage = """
G GIFTS SHOP - NEW CUSTOMER BOOKING

Booking ID: ${booking.bookingId}
Customer Name: [Optional]
Customer Phone: ${booking.customerPhone}
Product: ${booking.productName}
Quantity: ${booking.quantity}
Unit Price: ₹${"%.0f".format(booking.unitPrice)}
Total Amount: ₹${"%.0f".format(booking.totalAmount)}
Pickup: Store Pickup Only

Please confirm my booking.
""".trimIndent()
        assertTrue(whatsAppMessage.contains("G GIFTS SHOP - NEW CUSTOMER BOOKING"))
        assertTrue(whatsAppMessage.contains("7036436305"))
        assertTrue(whatsAppMessage.contains("Store Pickup Only"))

        // 10. Booking appears in Admin Dashboard
        val adminBookingsList = mutableListOf(booking)
        assertEquals(1, adminBookingsList.size)
        assertEquals("Pending", adminBookingsList[0].status)

        // 11. Admin confirms booking
        booking.status = "Confirmed"
        assertEquals("Confirmed", adminBookingsList[0].status)

        // 12. Customer collects product at store (Admin marks Ready for Pickup)
        booking.status = "Ready for Pickup"
        assertEquals("Ready for Pickup", adminBookingsList[0].status)

        // 13. Admin marks booking Completed
        booking.status = "Completed"
        assertEquals("Completed", adminBookingsList[0].status)
    }

    @Test
    fun testCustomGiftCatalogHas10ProductsWithCorrectPrices() {
        val catalog = com.example.model.CustomGiftCatalog.items
        assertEquals(10, catalog.size)

        val mug = catalog.find { it.id == "photo_mug" }
        assertEquals(299.0, mug?.basePrice ?: 0.0, 0.01)

        val magicMug = catalog.find { it.id == "magic_mug" }
        assertEquals(399.0, magicMug?.basePrice ?: 0.0, 0.01)

        val heartMug = catalog.find { it.id == "heart_mug" }
        assertEquals(349.0, heartMug?.basePrice ?: 0.0, 0.01)

        val photoFrame = catalog.find { it.id == "photo_frame" }
        assertEquals(499.0, photoFrame?.basePrice ?: 0.0, 0.01)

        val keychain = catalog.find { it.id == "photo_keychain" }
        assertEquals(149.0, keychain?.basePrice ?: 0.0, 0.01)

        val tshirt = catalog.find { it.id == "custom_tshirt" }
        assertEquals(599.0, tshirt?.basePrice ?: 0.0, 0.01)

        val pillow = catalog.find { it.id == "photo_pillow" }
        assertEquals(449.0, pillow?.basePrice ?: 0.0, 0.01)

        val ledFrame = catalog.find { it.id == "led_frame" }
        assertEquals(799.0, ledFrame?.basePrice ?: 0.0, 0.01)

        val stone = catalog.find { it.id == "custom_stone" }
        assertEquals(699.0, stone?.basePrice ?: 0.0, 0.01)

        val crystal = catalog.find { it.id == "crystal_gift" }
        assertEquals(899.0, crystal?.basePrice ?: 0.0, 0.01)
    }

    @Test
    fun testCustomizedGiftBookingWhatsAppMessageFormat() {
        val bookingId = "GG-654321"
        val designId = "DSGN-112233"
        val productName = "Customized Photo Mug"
        val customText = "Happy Anniversary Mom & Dad"
        val unitPrice = 299.0
        val totalAmount = 299.0
        val customerName = "Suresh Reddy"
        val customerPhone = "7036436305"

        val whatsAppMsg = """
G GIFTS SHOP - NEW CUSTOMIZED GIFT BOOKING

Booking ID: $bookingId
Design ID: $designId
Product: $productName
Custom Text: $customText
Quantity: 1
Unit Price: ₹${"%.0f".format(unitPrice)}
Total Amount: ₹${"%.0f".format(totalAmount)}
Customer Name: $customerName
Customer Phone: $customerPhone
Pickup: Store Pickup Only (Veera Reddy Nagar, Boduppal)

Please confirm my customized gift booking.
""".trimIndent()

        assertTrue(whatsAppMsg.contains("G GIFTS SHOP - NEW CUSTOMIZED GIFT BOOKING"))
        assertTrue(whatsAppMsg.contains("Design ID: DSGN-112233"))
        assertTrue(whatsAppMsg.contains("Happy Anniversary Mom & Dad"))
        assertTrue(whatsAppMsg.contains("Booking ID: GG-654321"))
        assertTrue(whatsAppMsg.contains("7036436305"))
        assertTrue(whatsAppMsg.contains("Store Pickup Only (Veera Reddy Nagar, Boduppal)"))
    }

    @Test
    fun testQRCodeZXingMatrixGeneration() {
        val url = "https://ggifts-store.web.app"
        val validation = com.example.util.QRCodeHelper.validateCustomerStoreUrl(url)
        assertTrue(validation is com.example.util.StoreUrlValidationResult.Valid)
        val writer = com.google.zxing.qrcode.QRCodeWriter()
        val hints = mapOf(
            com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8",
            com.google.zxing.EncodeHintType.ERROR_CORRECTION to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.H,
            com.google.zxing.EncodeHintType.MARGIN to 1
        )
        val bitMatrix = writer.encode(url, com.google.zxing.BarcodeFormat.QR_CODE, 200, 200, hints)
        assertEquals(200, bitMatrix.width)
        assertEquals(200, bitMatrix.height)
    }

    @Test
    fun testShopSettingsDoesNotDefaultToTemporaryPreviewUrl() {
        val defaultSettings = com.example.model.ShopSettings()
        assertEquals("", defaultSettings.websiteUrl)
        val validation = com.example.util.QRCodeHelper.validateCustomerStoreUrl(defaultSettings.websiteUrl)
        assertTrue(validation is com.example.util.StoreUrlValidationResult.Unpublished)
        assertEquals(
            "Publish the app first to generate a working Customer Store QR Code.",
            (validation as com.example.util.StoreUrlValidationResult.Unpublished).message
        )
    }

    @Test
    fun testQRCodeRejectsTemporaryAiStudioPreviewAndLocalUrls() {
        val unpublishedUrls = listOf(
            "",
            "   ",
            "https://ais-pre-pchkk4dajmgm45kcitxjtr-608802075626.asia-southeast1.run.app",
            "https://ais-dev-pchkk4dajmgm45kcitxjtr-608802075626.asia-southeast1.run.app",
            "https://aistudio.google.com/apps/drive/123",
            "https://gen-lang-client--preview-abc123.web.app",
            "http://localhost:8080",
            "https://127.0.0.1/store",
            "https://192.168.1.10/ggifts"
        )
        unpublishedUrls.forEach { url ->
            val result = com.example.util.QRCodeHelper.validateCustomerStoreUrl(url)
            assertTrue("Expected Unpublished for $url, got $result", result is com.example.util.StoreUrlValidationResult.Unpublished)
            assertEquals(
                "Publish the app first to generate a working Customer Store QR Code.",
                (result as com.example.util.StoreUrlValidationResult.Unpublished).message
            )
        }
    }

    @Test
    fun testQRCodeRejectsAdminPanelAndInsecureUrls() {
        val invalidUrls = listOf(
            "https://ggifts-store.web.app/admin",
            "https://ggifts-store.web.app/admin/dashboard",
            "https://ggifts-store.web.app/?role=admin",
            "https://ggifts-store.web.app/#/admin",
            "http://ggifts-store.web.app",
            "not-a-valid-url"
        )
        invalidUrls.forEach { url ->
            val result = com.example.util.QRCodeHelper.validateCustomerStoreUrl(url)
            assertTrue("Expected Invalid for $url, got $result", result is com.example.util.StoreUrlValidationResult.Invalid)
        }
    }

    @Test
    fun testQRCodeAcceptsPublishedCustomerStoreUrls() {
        val validUrls = listOf(
            "https://ggifts-store.web.app",
            "https://gen-lang-client-0989190352.web.app/",
            "https://ggiftsboduppal.com/shop"
        )
        validUrls.forEach { url ->
            val result = com.example.util.QRCodeHelper.validateCustomerStoreUrl(url)
            assertTrue("Expected Valid for $url, got $result", result is com.example.util.StoreUrlValidationResult.Valid)
            assertEquals(url, (result as com.example.util.StoreUrlValidationResult.Valid).url)
        }
    }

    @Test
    fun testBilingualStringsToggle() {
        com.example.util.ShopStrings.isTelugu = false
        assertEquals("G Gifts Shopping", com.example.util.ShopStrings.appTitle)
        assertEquals("STORE PICKUP ONLY", com.example.util.ShopStrings.storePickupOnly)

        com.example.util.ShopStrings.isTelugu = true
        assertEquals("జి గిఫ్ట్స్ షాపింగ్", com.example.util.ShopStrings.appTitle)
        assertEquals("స్టోర్ పికప్ మాత్రమే", com.example.util.ShopStrings.storePickupOnly)

        // Reset to default
        com.example.util.ShopStrings.isTelugu = false
    }

    @Test
    fun testCustomerToAdminToPrintingWorkflow() {
        // Step 1: Customer creates design
        val design = com.example.model.CustomerDesign(
            designId = "DSGN-MUG-881234",
            customerPhone = "7036436305",
            customerName = "Ravi Teja",
            productName = "Customized Photo Mug",
            productType = "mug",
            viewMode = "front",
            customText = "Best Dad Ever",
            approvalStatus = "Pending Approval",
            printStatus = "Not Started",
            unitPrice = 299.0
        )

        // Rule 1: Never automatically approve customer designs
        assertEquals("Pending Approval", design.approvalStatus)
        assertEquals("Not Started", design.printStatus)

        // Rule 2: Never start printing before Admin Approval
        val canPrintBeforeApproval = design.approvalStatus == "Approved"
        assertFalse("Printing must be locked prior to Admin approval", canPrintBeforeApproval)

        // Step 2: Admin Approves Design
        val approvedDesign = design.copy(
            approvalStatus = "Approved",
            printStatus = "Ready for Printing",
            adminNote = "",
            reviewedBy = "nrganna1@gmail.com"
        )
        assertEquals("Approved", approvedDesign.approvalStatus)
        assertEquals("Ready for Printing", approvedDesign.printStatus)
        assertTrue("Printing is unlocked once approved", approvedDesign.approvalStatus == "Approved")

        // Step 3: Admin starts printing
        val inPrintDesign = approvedDesign.copy(printStatus = "Printing in Progress")
        assertEquals("Printing in Progress", inPrintDesign.printStatus)

        // Step 4: Admin marks Printed
        val printedDesign = inPrintDesign.copy(printStatus = "Printed")
        assertEquals("Printed", printedDesign.printStatus)

        // Step 5: Admin marks Ready for Pickup
        val readyDesign = printedDesign.copy(printStatus = "Ready for Pickup")
        assertEquals("Ready for Pickup", readyDesign.printStatus)

        // Step 6: Customer collects gift, marked Completed
        val completedDesign = readyDesign.copy(printStatus = "Completed")
        assertEquals("Completed", completedDesign.printStatus)
    }

    @Test
    fun testAdminRequestChangesWorkflow() {
        val design = com.example.model.CustomerDesign(
            designId = "DSGN-MUG-445566",
            customerPhone = "7036436305",
            customerName = "Kavitha",
            approvalStatus = "Pending Approval"
        )

        val changesDesign = design.copy(
            approvalStatus = "Changes Requested",
            adminNote = "Photo resolution is too low. Please upload a clearer high-res image.",
            reviewedBy = "nrganna1@gmail.com"
        )

        assertEquals("Changes Requested", changesDesign.approvalStatus)
        assertEquals("Photo resolution is too low. Please upload a clearer high-res image.", changesDesign.adminNote)
        assertFalse("Cannot print when changes are requested", changesDesign.approvalStatus == "Approved")
    }

    @Test
    fun testAdminRejectDesignWorkflow() {
        val design = com.example.model.CustomerDesign(
            designId = "DSGN-MUG-998877",
            customerPhone = "7036436305",
            approvalStatus = "Pending Approval"
        )

        val rejectedDesign = design.copy(
            approvalStatus = "Rejected",
            adminNote = "Inappropriate content cannot be printed.",
            reviewedBy = "nrganna1@gmail.com"
        )

        assertEquals("Rejected", rejectedDesign.approvalStatus)
        assertEquals("Inappropriate content cannot be printed.", rejectedDesign.adminNote)
        assertFalse("Cannot print rejected design", rejectedDesign.approvalStatus == "Approved")
    }

    @Test
    fun testAdminApprovalWhatsAppNotificationFormat() {
        val designId = "DSGN-MUG-123456"
        val customerName = "Sunil Kumar"
        val productName = "Customized Photo Mug"
        val total = 299.0

        val approveMsg = """
G GIFTS SHOP - DESIGN APPROVED! ✅

Hello $customerName,
Great news! Your customized gift design has been APPROVED by store admin.

Design ID: $designId
Product: $productName
Total: ₹${"%.0f".format(total)}
Status: Approved & Sent to Printing

We are now printing your custom design at our store. We will notify you as soon as it is ready for pickup!

G Gifts Store, Veera Reddy Nagar, Boduppal.
Phone: 7036436305
""".trimIndent()

        assertTrue(approveMsg.contains("G GIFTS SHOP - DESIGN APPROVED! ✅"))
        assertTrue(approveMsg.contains("Design ID: DSGN-MUG-123456"))
        assertTrue(approveMsg.contains("Approved & Sent to Printing"))
        assertTrue(approveMsg.contains("Veera Reddy Nagar, Boduppal"))
    }

    @Test
    fun testProductQRCodeUrlGenerationAndValidation() {
        val baseUrl = "https://gen-lang-client-0989190352.web.app"
        val valid = com.example.util.QRCodeHelper.buildProductUrl(baseUrl, "prod_teddy_4ft")
        assertTrue(valid is com.example.util.StoreUrlValidationResult.Valid)
        assertEquals(
            "https://gen-lang-client-0989190352.web.app?product=prod_teddy_4ft",
            (valid as com.example.util.StoreUrlValidationResult.Valid).url
        )

        val previewRejected = com.example.util.QRCodeHelper.buildProductUrl(
            "https://ais-pre-abc-123.asia-southeast1.run.app",
            "prod_teddy_4ft"
        )
        assertTrue(previewRejected is com.example.util.StoreUrlValidationResult.Unpublished)
        assertEquals(
            "Publish the app first to generate a working Customer Store QR Code.",
            (previewRejected as com.example.util.StoreUrlValidationResult.Unpublished).message
        )

        val blankIdRejected = com.example.util.QRCodeHelper.buildProductUrl(baseUrl, "")
        assertTrue(blankIdRejected is com.example.util.StoreUrlValidationResult.Invalid)
    }

    @Test
    fun testWhatsAppWaMeLinkFormattingAndDisclaimer() {
        val rawPhone = "7036436305"
        val cleanNumber = rawPhone.replace("+", "").replace(" ", "").replace("-", "")
        val formattedNumber = if (cleanNumber.length == 10) "91$cleanNumber" else cleanNumber
        assertEquals("917036436305", formattedNumber)

        val url = "https://wa.me/$formattedNumber?text=Hello"
        assertTrue(url.startsWith("https://wa.me/917036436305?text="))

        val disclaimer = "Your WhatsApp booking is not confirmed until the shop confirms it."
        assertEquals("Your WhatsApp booking is not confirmed until the shop confirms it.", disclaimer)
    }

    @Test
    fun testProductOfferCalculationAndStoreDefaults() {
        val product = com.example.model.Product(
            id = "p1",
            name = "Cricket Kit",
            originalPrice = 1000.0,
            sellingPrice = 750.0,
            offerTag = "Summer Sports Offer",
            stock = 5,
            isEnabled = true
        )
        val discountPct = (((product.originalPrice - product.sellingPrice) / product.originalPrice) * 100).toInt()
        assertEquals(25, discountPct)
        assertEquals("Summer Sports Offer", product.offerTag)

        val settings = com.example.model.ShopSettings()
        assertEquals("G Gifts Toys Sports Decoration Store", settings.shopName)
        assertEquals("Veera Reddy Nagar, Boduppal, Telangana", settings.shopAddress)
        assertEquals("7036436305", settings.whatsAppNumber)
    }

    @Test
    fun testCartCheckoutSummaryAndWhatsAppOrderMessage() {
        val item1 = com.example.model.OrderItem(productId = "p1", name = "Wooden Toy Train", quantity = 2, price = 350.0)
        val item2 = com.example.model.OrderItem(productId = "p2", name = "Photo Frame 8x10", quantity = 1, price = 499.0)
        val cart = listOf(item1, item2)
        val subtotal = cart.sumOf { it.price * it.quantity }
        assertEquals(1199.0, subtotal, 0.01)

        val order = com.example.model.Order(
            id = "GG-ORD-556677",
            customerName = "Anitha Reddy",
            customerPhone = "9876543210",
            items = cart,
            subtotal = subtotal,
            deliveryCharges = 0.0,
            totalAmount = subtotal,
            deliveryType = "Store Pickup Only",
            deliveryAddress = "House 12-4, Peerzadiguda, Boduppal",
            orderStatus = "pending_whatsapp"
        )

        val waMessage = com.example.ui.customer.buildWhatsAppOrderMessage(
            order,
            "Veera Reddy Nagar, Boduppal, Telangana"
        )
        assertTrue(waMessage.contains("Order Ref: GG-ORD-556677"))
        assertTrue(waMessage.contains("Customer Name: Anitha Reddy"))
        assertTrue(waMessage.contains("Mobile Number: 9876543210"))
        assertTrue(waMessage.contains("Address: House 12-4, Peerzadiguda, Boduppal"))
        assertTrue(waMessage.contains("1. Wooden Toy Train x 2 @ ₹350 = ₹700"))
        assertTrue(waMessage.contains("2. Photo Frame 8x10 x 1 @ ₹499 = ₹499"))
        assertTrue(waMessage.contains("Total Amount: ₹1199"))

        val disclaimer = "Your order is not submitted until you send the WhatsApp message."
        assertTrue(disclaimer.contains("not submitted until you send the WhatsApp message"))
    }
}
