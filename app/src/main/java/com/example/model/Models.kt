package com.example.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class Product(
    @DocumentId val id: String = "",
    val name: String = "",
    val description: String = "",
    val originalPrice: Double = 0.0,
    val sellingPrice: Double = 0.0,
    val discount: Double = 0.0,
    val stock: Int = 0,
    val category: String = "",
    val images: List<String> = emptyList(),
    val sizes: List<String> = emptyList(),
    val colors: List<String> = emptyList(),
    val isFeatured: Boolean = false,
    val isBestSeller: Boolean = false,
    val isEnabled: Boolean = true,
    val offerTag: String = "",
    val customizable: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Category(
    @DocumentId val id: String = "",
    val name: String = "",
    val order: Int = 0,
    val image: String = ""
)

@IgnoreExtraProperties
data class Banner(
    @DocumentId val id: String = "",
    val imageUrl: String = "",
    val offerText: String = "",
    val isEnabled: Boolean = true,
    val order: Int = 0
)

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val displayName: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val address: String = "",
    val role: String = "customer",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class OrderItem(
    val productId: String = "",
    val name: String = "",
    val quantity: Int = 0,
    val price: Double = 0.0,
    val customizationDetails: CustomizationDetails? = null
)

@IgnoreExtraProperties
data class CustomizationDetails(
    val photoUrl: String? = null,
    val text: String? = null,
    val instructions: String? = null
)

@IgnoreExtraProperties
data class Booking(
    @DocumentId val id: String = "",
    val bookingId: String = "",
    val customerPhone: String = "",
    val customerName: String = "",
    val productId: String = "",
    val productName: String = "",
    val productImage: String = "",
    val quantity: Int = 1,
    val unitPrice: Double = 0.0,
    val totalAmount: Double = 0.0,
    val status: String = "Pending", // Pending, Confirmed, Ready for Pickup, Completed, Cancelled
    val deliveryType: String = "Store Pickup Only",
    val storeAddress: String = "G Gifts Toys Sports & Decoration Store, Veera Reddy Nagar, Boduppal",
    val isCustomized: Boolean = false,
    val designId: String = "",
    val customText: String = "",
    val customerMessage: String = "",
    val customImageUrl: String = "",
    val customFont: String = "",
    val customColor: String = "",
    val customFontSize: Int = 16,
    val productMockup: String = "",
    val previewViewMode: String = "front",
    val approvalStatus: String = "Pending Approval", // "Pending Approval", "Approved", "Changes Requested", "Rejected"
    val printStatus: String = "Not Started", // "Not Started", "Ready for Printing", "Printing in Progress", "Printed", "Ready for Pickup", "Completed"
    val adminNote: String = "",
    val reviewedBy: String = "",
    val reviewedAt: Timestamp? = null,
    val originalPhotoUrl: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CustomerDesign(
    @DocumentId val id: String = "",
    val designId: String = "", // e.g. "DSGN-MUG-123456"
    val customerPhone: String = "",
    val customerName: String = "",
    val customerMessage: String = "",
    val productId: String = "photo_mug",
    val productName: String = "Customized Photo Mug",
    val productType: String = "mug", // "mug", "magic_mug", "heart_mug", "photo_frame", "photo_keychain", "custom_tshirt", "photo_pillow", "led_frame", "custom_stone", "crystal_gift"
    val viewMode: String = "front", // "front" or "wrap"
    val originalPhotoUrl: String = "",
    val previewPhotoUrl: String = "",
    val photoScale: Float = 1.0f,
    val photoRotation: Float = 0f,
    val photoOffsetX: Float = 0f,
    val photoOffsetY: Float = 0f,
    val customText: String = "",
    val textPosition: String = "bottom", // "top", "center", "bottom"
    val fontStyle: String = "Modern Sans",
    val fontSize: Int = 18,
    val textColor: String = "Black",
    val unitPrice: Double = 299.0,
    val quantity: Int = 1,
    val totalAmount: Double = 299.0,
    val approvalStatus: String = "Pending Approval", // "Pending Approval", "Approved", "Changes Requested", "Rejected"
    val printStatus: String = "Not Started", // "Not Started", "Ready for Printing", "Printing in Progress", "Printed", "Ready for Pickup", "Completed"
    val adminNote: String = "",
    val reviewedBy: String = "",
    val reviewedAt: Timestamp? = null,
    val bookingId: String = "",
    val deliveryType: String = "Store Pickup Only",
    val storeAddress: String = "G Gifts Toys Sports & Decoration Store, Veera Reddy Nagar, Boduppal",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Order(
    @DocumentId val id: String = "",
    val userId: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val items: List<OrderItem> = emptyList(),
    val subtotal: Double = 0.0,
    val deliveryCharges: Double = 0.0,
    val totalAmount: Double = 0.0,
    val deliveryType: String = "pickup",
    val deliveryAddress: String = "",
    val paymentStatus: String = "pending",
    val orderStatus: String = "pending",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ShopSettings(
    val shopName: String = "G Gifts Toys Sports Decoration Store",
    val shopAddress: String = "Veera Reddy Nagar, Boduppal, Telangana",
    val contactNumber: String = "7036436305",
    val whatsAppNumber: String = "7036436305",
    val deliveryCharges: Double = 0.0,
    val minOrderAmount: Double = 0.0,
    val businessTimings: String = "10:00 AM - 10:00 PM",
    val pickupAvailable: Boolean = true,
    val websiteUrl: String = ""
)

data class CustomGiftItem(
    val id: String,
    val name: String,
    val nameTe: String,
    val basePrice: Double,
    val description: String,
    val descriptionTe: String,
    val mockupType: String,
    val defaultMockupUrl: String = ""
)

object CustomGiftCatalog {
    val items = listOf(
        CustomGiftItem(
            id = "photo_mug",
            name = "Customized Photo Mug",
            nameTe = "కస్టమైజ్డ్ ఫోటో మగ్",
            basePrice = 299.0,
            description = "High quality 325ml glossy ceramic mug with vibrant photo print. Dishwasher and microwave safe.",
            descriptionTe = "అద్భుతమైన ప్రింటింగ్ గల 325మి.లీ సిరామిక్ ఫోటో మగ్. మైక్రోవేవ్ సేఫ్.",
            mockupType = "mug"
        ),
        CustomGiftItem(
            id = "magic_mug",
            name = "Magic Mug",
            nameTe = "మ్యాజిక్ మగ్ (కలర్ చేంజింగ్)",
            basePrice = 399.0,
            description = "Heat-sensitive color changing mug. Black when cold, reveals your hidden photo when hot liquid is poured!",
            descriptionTe = "వేడి ద్రవం పోసినప్పుడు మీ ఫోటో కనిపించే మ్యాజిక్ కలర్ చేంజింగ్ మగ్!",
            mockupType = "magic_mug"
        ),
        CustomGiftItem(
            id = "heart_mug",
            name = "Heart Handle Mug",
            nameTe = "హార్ట్ హ్యాండిల్ మగ్",
            basePrice = 349.0,
            description = "Romantic couple gift mug with premium heart-shaped handle and full-wrap photo print.",
            descriptionTe = "ప్రేమికులకు ప్రత్యేకమైన హార్ట్ హ్యాండిల్ డిజైన్ ఫోటో మగ్.",
            mockupType = "heart_mug"
        ),
        CustomGiftItem(
            id = "photo_frame",
            name = "Photo Frame",
            nameTe = "కస్టమ్ ఫోటో ఫ్రేమ్",
            basePrice = 499.0,
            description = "Elegant 8x10 inch wooden finish photo frame with protective acrylic glass covering.",
            descriptionTe = "8x10 అంగుళాల ప్రీమియం చెక్క ఫినిషింగ్ ఫోటో ఫ్రేమ్.",
            mockupType = "photo_frame"
        ),
        CustomGiftItem(
            id = "photo_keychain",
            name = "Photo Keychain",
            nameTe = "ఫోటో కీచైన్",
            basePrice = 149.0,
            description = "Durable double-sided acrylic/metal photo keychain. Perfect for bike, car, and house keys.",
            descriptionTe = "రెండు వైపులా ఫోటో ప్రింట్ తో కూడిన మెటల్ / యాక్రిలిక్ కీచైన్.",
            mockupType = "photo_keychain"
        ),
        CustomGiftItem(
            id = "custom_tshirt",
            name = "Customized T-Shirt",
            nameTe = "కస్టమైజ్డ్ టీ-షర్ట్",
            basePrice = 599.0,
            description = "100% premium breathable cotton t-shirt with high definition direct-to-fabric photo print.",
            descriptionTe = "100% ప్యూర్ కాటన్ టీ-షర్ట్ మీద మీ ఇష్టమైన ఫోటో & టెక్స్ట్ ప్రింట్.",
            mockupType = "custom_tshirt"
        ),
        CustomGiftItem(
            id = "photo_pillow",
            name = "Photo Pillow",
            nameTe = "ఫోటో కుషన్ పిల్లో",
            basePrice = 449.0,
            description = "Ultra-soft 16x16 inch satin plush cushion with edge-to-edge photo printing. Washable cover.",
            descriptionTe = "16x16 అంగుళాల మృదువైన శాటిన్ పిల్లో పై అద్భుతమైన ఫోటో ప్రింట్.",
            mockupType = "photo_pillow"
        ),
        CustomGiftItem(
            id = "led_frame",
            name = "LED Photo Frame",
            nameTe = "LED లైట్ ఫోటో ఫ్రేమ్",
            basePrice = 799.0,
            description = "Illuminated acrylic photo frame with warm ambient LED backlighting and power adapter included.",
            descriptionTe = "రాత్రి వేళల్లో వెలిగే లైటింగ్ తో కూడిన ప్రత్యేకమైన LED ఫోటో ఫ్రేమ్.",
            mockupType = "led_frame"
        ),
        CustomGiftItem(
            id = "custom_stone",
            name = "Customized Stone",
            nameTe = "రాక్ స్టోన్ ఫోటో స్లేట్",
            basePrice = 699.0,
            description = "Natural rock slate stone plaque with raw chiseled edges. Permanent sublimation imprint.",
            descriptionTe = "సహజమైన రాతి పలక పై లైఫ్ లాంగ్ ఉండే ఫోటో ప్రింట్.",
            mockupType = "custom_stone"
        ),
        CustomGiftItem(
            id = "crystal_gift",
            name = "Crystal Gifts",
            nameTe = "క్రిస్టల్ గిఫ్ట్స్ (3D/2D)",
            basePrice = 899.0,
            description = "Luxury optical K9 crystal block with laser etched internal engraving and beveled edges.",
            descriptionTe = "అందమైన K9 ఆప్టికల్ క్రిస్టల్ లోపల లేజర్ ద్వారా ఫోటో ముద్రణ.",
            mockupType = "crystal_gift"
        )
    )
}

