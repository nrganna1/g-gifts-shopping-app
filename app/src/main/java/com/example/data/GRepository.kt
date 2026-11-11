package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.R
import com.example.model.*
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import com.google.firebase.firestore.toObject
import com.google.firebase.firestore.toObjects
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.util.UUID

enum class OperationType { CREATE, READ, UPDATE, DELETE, LIST }

fun handleFirestoreError(exception: Exception, operation: OperationType, path: String) {
    val errorDetails = JSONObject().apply {
        put("operation", operation.name)
        put("path", path)
        put("exception", exception.javaClass.simpleName)
        put("message", exception.localizedMessage)
    }
    Log.e("FirestoreError", errorDetails.toString())
}

class GRepository(
    private val context: Context,
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(
        context.applicationContext.getString(R.string.firestore_database_id)
    ),
    private val auth: FirebaseAuth = Firebase.auth
) {
    private val storage by lazy { FirebaseStorage.getInstance() }

    private fun requireUserId(): String {
        return auth.currentUser?.uid ?: throw IllegalStateException("User not signed in")
    }

    private fun requireAdmin() {
        val user = auth.currentUser
            ?: throw SecurityException("Firebase Authentication required for admin actions.")
        val email = user.email?.trim()?.lowercase()
        val isVerified = user.isEmailVerified || user.providerData.any { it.providerId == "google.com" }
        if (!isVerified || !isSuperAdminEmail(email)) {
            throw SecurityException("Access Denied: Only $singleAuthorizedAdminEmail has admin privileges.")
        }
    }

    // --- Products ---
    fun parseProductFromSnapshot(doc: DocumentSnapshot): Product? {
        try {
            val direct = doc.toObject<Product>()
            if (direct != null && direct.name.isNotBlank()) {
                val finalId = if (direct.id.isNotBlank()) direct.id else doc.id
                val imagesList = if (direct.images.isNotEmpty()) {
                    direct.images
                } else {
                    val singleImg = doc.getString("image") ?: doc.getString("imageUrl")
                    if (!singleImg.isNullOrBlank()) listOf(singleImg) else emptyList()
                }
                val effectivePrice = if (direct.sellingPrice > 0) {
                    direct.sellingPrice
                } else {
                    doc.getDouble("price") ?: doc.getLong("price")?.toDouble() ?: 0.0
                }
                val isEnabledVal = doc.getBoolean("isEnabled") ?: true
                return direct.copy(id = finalId, images = imagesList, sellingPrice = effectivePrice, isEnabled = isEnabledVal)
            }
        } catch (e: Exception) {
            Log.w("GRepository", "toObject<Product>() failed for doc ${doc.id}: ${e.message}")
        }

        return try {
            val name = doc.getString("name") ?: doc.getString("title") ?: "Product ${doc.id.takeLast(4)}"
            val description = doc.getString("description") ?: ""

            val sellingPrice = doc.getDouble("sellingPrice")
                ?: doc.getLong("sellingPrice")?.toDouble()
                ?: doc.getDouble("price")
                ?: doc.getLong("price")?.toDouble()
                ?: (doc.get("sellingPrice")?.toString()?.toDoubleOrNull())
                ?: (doc.get("price")?.toString()?.toDoubleOrNull())
                ?: 0.0

            val originalPrice = doc.getDouble("originalPrice")
                ?: doc.getLong("originalPrice")?.toDouble()
                ?: doc.getDouble("mrp")
                ?: doc.getLong("mrp")?.toDouble()
                ?: (doc.get("originalPrice")?.toString()?.toDoubleOrNull())
                ?: (doc.get("mrp")?.toString()?.toDoubleOrNull())
                ?: sellingPrice

            val stock = doc.getLong("stock")?.toInt()
                ?: (doc.get("stock")?.toString()?.toIntOrNull())
                ?: doc.getLong("quantity")?.toInt()
                ?: 10

            val category = doc.getString("category") ?: ""

            val imagesList: List<String> = try {
                @Suppress("UNCHECKED_CAST")
                (doc.get("images") as? List<*>)?.mapNotNull { it?.toString() }?.filter { it.isNotBlank() }
                    ?: listOfNotNull(doc.getString("image") ?: doc.getString("imageUrl") ?: doc.getString("photo"))
            } catch (_: Exception) {
                listOfNotNull(doc.getString("image") ?: doc.getString("imageUrl"))
            }

            val isEnabled = when {
                doc.contains("isEnabled") -> doc.getBoolean("isEnabled") ?: true
                doc.contains("active") -> doc.getBoolean("active") ?: true
                doc.contains("status") -> !doc.getString("status").equals("inactive", ignoreCase = true)
                else -> true
            }

            val isFeatured = doc.getBoolean("isFeatured") ?: false
            val isBestSeller = doc.getBoolean("isBestSeller") ?: false
            val offerTag = doc.getString("offerTag") ?: ""
            val customizable = doc.getBoolean("customizable") ?: false

            Product(
                id = doc.id,
                name = name,
                description = description,
                originalPrice = originalPrice,
                sellingPrice = sellingPrice,
                stock = stock,
                category = category,
                images = imagesList,
                isEnabled = isEnabled,
                isFeatured = isFeatured,
                isBestSeller = isBestSeller,
                offerTag = offerTag,
                customizable = customizable,
                createdAt = doc.getTimestamp("createdAt"),
                updatedAt = doc.getTimestamp("updatedAt")
            )
        } catch (e: Exception) {
            Log.e("GRepository", "Failed fallback extraction for product ${doc.id}", e)
            null
        }
    }

    fun observeProducts(): Flow<List<Product>> = db.collection("products")
        .snapshots()
        .map { snapshot ->
            val list = snapshot.documents.mapNotNull { parseProductFromSnapshot(it) }
                .filter { it.isEnabled }
            Log.d("GRepository", "observeProducts: Real-time listener received ${list.size} active products (out of ${snapshot.size()} docs)")
            list
        }
        .catch { e ->
            handleFirestoreError(e as Exception, OperationType.LIST, "products")
            Log.e("GRepository", "Error in observeProducts snapshot listener", e)
            emit(emptyList())
        }

    fun observeAdminProducts(): Flow<List<Product>> = db.collection("products")
        .snapshots()
        .map { snapshot ->
            val list = snapshot.documents.mapNotNull { parseProductFromSnapshot(it) }
            Log.d("GRepository", "observeAdminProducts: Real-time listener received ${list.size} total products")
            list
        }
        .catch { e ->
            handleFirestoreError(e as Exception, OperationType.LIST, "products")
            Log.e("GRepository", "Error in observeAdminProducts snapshot listener", e)
            emit(emptyList())
        }

    suspend fun addProduct(product: Product): String {
        requireAdmin()
        val docRef = db.collection("products").document()
        val productToSave = product.copy(
            id = docRef.id,
            isEnabled = true,
            customizable = false,
            createdAt = null,
            updatedAt = null
        )
        try {
            docRef.set(productToSave).await()
            Log.d("GRepository", "addProduct: successfully saved product ${productToSave.name} (id=${docRef.id})")
            return docRef.id
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "products")
            throw e
        }
    }

    suspend fun updateProduct(product: Product) {
        requireAdmin()
        require(product.id.isNotBlank()) { "Product ID must not be blank for update" }
        val docRef = db.collection("products").document(product.id)
        val productToSave = product.copy(
            customizable = false,
            updatedAt = null
        )
        try {
            docRef.set(productToSave, SetOptions.merge()).await()
            Log.d("GRepository", "updateProduct: successfully updated product ${product.id}")
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteProduct(productId: String) {
        requireAdmin()
        try {
            db.collection("products").document(productId).delete().await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "products/$productId")
            throw e
        }
    }

    suspend fun uploadProductImage(uri: Uri): String {
        requireAdmin()
        val extension = try {
            context.contentResolver.getType(uri)?.let { mime ->
                when (mime) {
                    "image/png" -> "png"
                    "image/webp" -> "webp"
                    "image/jpeg", "image/jpg" -> "jpg"
                    else -> "jpg"
                }
            } ?: "jpg"
        } catch (_: Exception) {
            "jpg"
        }
        val filename = "product_${UUID.randomUUID()}.$extension"
        val storageRef = storage.reference.child("products/$filename")
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Could not read image data from selected file.")
        stream.use {
            storageRef.putStream(it).await()
        }
        return storageRef.downloadUrl.await().toString()
    }

    suspend fun uploadCustomDesignImage(uri: Uri): String {
        val filename = "custom_${UUID.randomUUID()}.jpg"
        val storageRef = storage.reference.child("custom_designs/$filename")
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Could not read image data.")
        stream.use {
            storageRef.putStream(it).await()
        }
        return storageRef.downloadUrl.await().toString()
    }

    suspend fun uploadCustomDesignBytes(bytes: ByteArray, filenamePrefix: String = "preview"): String {
        val filename = "${filenamePrefix}_${UUID.randomUUID()}.jpg"
        val storageRef = storage.reference.child("custom_designs/$filename")
        storageRef.putBytes(bytes).await()
        return storageRef.downloadUrl.await().toString()
    }

    // --- Categories ---
    fun observeCategories(): Flow<List<Category>> = db.collection("categories")
        .orderBy("order")
        .snapshots()
        .map { it.toObjects<Category>() }

    suspend fun addCategory(category: Category) {
        requireAdmin()
        val docRef = db.collection("categories").document()
        docRef.set(category).await()
    }

    suspend fun updateCategory(category: Category) {
        requireAdmin()
        db.collection("categories").document(category.id).set(category).await()
    }

    suspend fun deleteCategory(categoryId: String) {
        requireAdmin()
        db.collection("categories").document(categoryId).delete().await()
    }

    // --- Banners ---
    fun observeBanners(): Flow<List<Banner>> = db.collection("banners")
        .whereEqualTo("isEnabled", true)
        .orderBy("order")
        .snapshots()
        .map { it.toObjects<Banner>() }

    // --- User Profile ---
    fun observeUserProfile(): Flow<UserProfile?> = flow {
        val uid = auth.currentUser?.uid ?: return@flow
        emitAll(db.collection("users").document(uid).snapshots().map { it.toObject<UserProfile>() })
    }

    suspend fun saveUserProfile(profile: UserProfile) {
        val uid = requireUserId()
        val safeRole = if (isSuperAdminEmail(profile.email)) "admin" else "customer"
        val sanitized = mapOf(
            "id" to uid,
            "displayName" to profile.displayName,
            "email" to profile.email.trim().lowercase(),
            "phoneNumber" to profile.phoneNumber,
            "address" to profile.address,
            "role" to safeRole,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("users").document(uid).set(sanitized, SetOptions.merge()).await()
    }

    // --- Bookings ---
    fun observeAdminBookings(): Flow<List<Booking>> = db.collection("bookings")
        .orderBy("createdAt", Query.Direction.DESCENDING)
        .snapshots()
        .map { it.toObjects<Booking>() }
        .catch { e ->
            Log.e("GRepository", "Error observing bookings", e)
            emit(emptyList())
        }

    suspend fun createBooking(booking: Booking): String {
        val docRef = db.collection("bookings").document()
        val randomDigits = (100000..999999).random().toString()
        val uniqueBookingId = if (booking.bookingId.isNotBlank()) booking.bookingId else "GG-$randomDigits"
        val bookingToSave = booking.copy(
            id = docRef.id,
            bookingId = uniqueBookingId,
            deliveryType = "Store Pickup Only",
            storeAddress = "G Gifts Toys Sports Decoration Store, Veera Reddy Nagar, Boduppal, Telangana",
            createdAt = Timestamp.now()
        )
        docRef.set(bookingToSave).await()
        Log.d("GRepository", "Created booking with ID: $uniqueBookingId (docId=${docRef.id})")
        return uniqueBookingId
    }

    suspend fun updateBookingStatus(bookingDocId: String, status: String) {
        db.collection("bookings").document(bookingDocId).update(
            mapOf(
                "status" to status,
                "updatedAt" to Timestamp.now()
            )
        ).await()
    }

    // --- Customer Designs & Approval Workflow ---
    fun observeCustomerDesigns(): Flow<List<CustomerDesign>> = db.collection("designs")
        .orderBy("createdAt", Query.Direction.DESCENDING)
        .snapshots()
        .map { it.toObjects<CustomerDesign>() }
        .catch { e ->
            Log.e("GRepository", "Error observing customer designs", e)
            emit(emptyList())
        }

    fun observeCustomerDesignsByPhone(phone: String): Flow<List<CustomerDesign>> {
        val clean = phone.trim().replace("+91", "").replace(" ", "").replace("-", "")
        return db.collection("designs")
            .whereEqualTo("customerPhone", clean)
            .snapshots()
            .map { it.toObjects<CustomerDesign>() }
            .catch { emit(emptyList()) }
    }

    suspend fun getCustomerDesignById(designId: String): CustomerDesign? {
        return try {
            val query = db.collection("designs")
                .whereEqualTo("designId", designId.trim())
                .limit(1)
                .get()
                .await()
            query.documents.firstOrNull()?.toObject<CustomerDesign>()
        } catch (e: Exception) {
            Log.e("GRepository", "Error fetching design by ID $designId", e)
            null
        }
    }

    suspend fun saveCustomerDesign(design: CustomerDesign): Pair<String, String> {
        val designDocRef = db.collection("designs").document()
        val randomDesign = (100000..999999).random().toString()
        val prefix = when (design.productType) {
            "mug" -> "DSGN-MUG"
            "magic_mug" -> "DSGN-MMUG"
            "heart_mug" -> "DSGN-HMUG"
            "photo_frame" -> "DSGN-FRM"
            "photo_keychain" -> "DSGN-KEY"
            "custom_tshirt" -> "DSGN-TSH"
            "photo_pillow" -> "DSGN-PLW"
            "led_frame" -> "DSGN-LED"
            "custom_stone" -> "DSGN-STN"
            "crystal_gift" -> "DSGN-CRY"
            else -> "DSGN"
        }
        val uniqueDesignId = if (design.designId.isNotBlank()) design.designId else "$prefix-$randomDesign"

        val randomBooking = (100000..999999).random().toString()
        val uniqueBookingId = if (design.bookingId.isNotBlank()) design.bookingId else "GG-$randomBooking"

        val designToSave = design.copy(
            id = designDocRef.id,
            designId = uniqueDesignId,
            bookingId = uniqueBookingId,
            approvalStatus = "Pending Approval",
            printStatus = "Not Started",
            deliveryType = "Store Pickup Only",
            storeAddress = "G Gifts Toys Sports & Decoration Store, Veera Reddy Nagar, Boduppal",
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )
        designDocRef.set(designToSave).await()

        // Also create matching booking in bookings collection
        val bookingDocRef = db.collection("bookings").document()
        val booking = Booking(
            id = bookingDocRef.id,
            bookingId = uniqueBookingId,
            customerPhone = design.customerPhone,
            customerName = design.customerName,
            customerMessage = design.customerMessage,
            productId = design.productId,
            productName = design.productName,
            productImage = design.previewPhotoUrl.ifBlank { design.originalPhotoUrl },
            quantity = design.quantity,
            unitPrice = design.unitPrice,
            totalAmount = design.totalAmount,
            status = "Pending",
            deliveryType = "Store Pickup Only",
            storeAddress = "G Gifts Toys Sports & Decoration Store, Veera Reddy Nagar, Boduppal",
            isCustomized = true,
            designId = uniqueDesignId,
            customText = design.customText,
            customImageUrl = design.originalPhotoUrl,
            customFont = design.fontStyle,
            customColor = design.textColor,
            customFontSize = design.fontSize,
            productMockup = design.productType,
            previewViewMode = design.viewMode,
            approvalStatus = "Pending Approval",
            printStatus = "Not Started",
            originalPhotoUrl = design.originalPhotoUrl,
            createdAt = Timestamp.now()
        )
        bookingDocRef.set(booking).await()

        Log.d("GRepository", "Saved CustomerDesign $uniqueDesignId and Booking $uniqueBookingId")
        return Pair(uniqueDesignId, uniqueBookingId)
    }

    suspend fun updateDesignApprovalStatus(
        designDocId: String,
        approvalStatus: String,
        adminNote: String = "",
        printStatus: String? = null,
        reviewedBy: String = ""
    ) {
        val now = Timestamp.now()
        val resolvedPrintStatus = printStatus ?: when (approvalStatus) {
            "Approved" -> "Ready for Printing"
            else -> "Not Started"
        }

        val updateMap = mutableMapOf<String, Any>(
            "approvalStatus" to approvalStatus,
            "adminNote" to adminNote,
            "printStatus" to resolvedPrintStatus,
            "reviewedAt" to now,
            "updatedAt" to now
        )
        if (reviewedBy.isNotBlank()) {
            updateMap["reviewedBy"] = reviewedBy
        }

        db.collection("designs").document(designDocId).update(updateMap).await()

        // Sync with linked booking
        try {
            val designDoc = db.collection("designs").document(designDocId).get().await()
            val designId = designDoc.getString("designId") ?: ""
            val bookingId = designDoc.getString("bookingId") ?: ""

            val bookingQuery = if (designId.isNotBlank()) {
                db.collection("bookings").whereEqualTo("designId", designId).get().await()
            } else if (bookingId.isNotBlank()) {
                db.collection("bookings").whereEqualTo("bookingId", bookingId).get().await()
            } else null

            bookingQuery?.documents?.forEach { doc ->
                val bookingStatus = when (approvalStatus) {
                    "Approved" -> "Confirmed"
                    "Rejected" -> "Cancelled"
                    else -> "Pending"
                }
                doc.reference.update(
                    mapOf(
                        "approvalStatus" to approvalStatus,
                        "printStatus" to resolvedPrintStatus,
                        "adminNote" to adminNote,
                        "status" to bookingStatus,
                        "updatedAt" to now
                    )
                ).await()
            }
        } catch (e: Exception) {
            Log.w("GRepository", "Could not sync approval to booking: ${e.message}")
        }
    }

    suspend fun updateDesignPrintStatus(designDocId: String, printStatus: String) {
        val now = Timestamp.now()
        db.collection("designs").document(designDocId).update(
            mapOf(
                "printStatus" to printStatus,
                "updatedAt" to now
            )
        ).await()

        // Sync to linked booking
        try {
            val designDoc = db.collection("designs").document(designDocId).get().await()
            val designId = designDoc.getString("designId") ?: ""
            if (designId.isNotBlank()) {
                val bookings = db.collection("bookings").whereEqualTo("designId", designId).get().await()
                bookings.documents.forEach { doc ->
                    val bookingStatus = when (printStatus) {
                        "Ready for Pickup" -> "Ready for Pickup"
                        "Completed" -> "Completed"
                        else -> doc.getString("status") ?: "Confirmed"
                    }
                    doc.reference.update(
                        mapOf(
                            "printStatus" to printStatus,
                            "status" to bookingStatus,
                            "updatedAt" to now
                        )
                    ).await()
                }
            }
        } catch (e: Exception) {
            Log.w("GRepository", "Could not sync print status to booking: ${e.message}")
        }
    }

    // --- Orders ---
    fun observeUserOrders(): Flow<List<Order>> = flow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            emit(emptyList<Order>())
        } else {
            emitAll(
                db.collection("orders")
                    .whereEqualTo("userId", uid)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .snapshots()
                    .map { it.toObjects<Order>() }
                    .catch { emit(emptyList()) }
            )
        }
    }

    fun observeAdminOrders(): Flow<List<Order>> = db.collection("orders")
        .orderBy("createdAt", Query.Direction.DESCENDING)
        .snapshots()
        .map { it.toObjects<Order>() }
        .catch { emit(emptyList()) }

    suspend fun createOrder(order: Order): String {
        val docRef = db.collection("orders").document()
        val uid = auth.currentUser?.uid ?: "anonymous_${System.currentTimeMillis()}"
        val orderToSave = order.copy(id = docRef.id, userId = uid, createdAt = Timestamp.now())
        docRef.set(orderToSave).await()
        return docRef.id
    }

    suspend fun updateOrderStatus(orderId: String, status: String) {
        requireAdmin()
        db.collection("orders").document(orderId).update(
            mapOf(
                "orderStatus" to status,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    // --- Shop Settings ---
    fun observeSettings(): Flow<ShopSettings?> = db.collection("settings").document("global")
        .snapshots()
        .map { it.toObject<ShopSettings>() }

    suspend fun saveSettings(settings: ShopSettings) {
        requireAdmin()
        db.collection("settings").document("global").set(settings).await()
    }

    // --- Admin Operations (Strict Single Store Owner Only) ---
    val singleAuthorizedAdminEmail: String
        get() = try {
            val configured = com.example.BuildConfig.ADMIN_EMAIL.trim().lowercase()
            if (configured.isBlank() || configured == "nrganna2015@gmail.com") {
                "nrganna1@gmail.com"
            } else {
                configured
            }
        } catch (_: Exception) {
            "nrganna1@gmail.com"
        }

    fun isSuperAdminEmail(email: String?): Boolean {
        if (email.isNullOrBlank()) return false
        val clean = email.trim().lowercase()
        if (clean == "nrganna2015@gmail.com") return false
        return clean == singleAuthorizedAdminEmail
    }

    suspend fun isAdmin(): Boolean {
        val user = auth.currentUser ?: return false
        val email = user.email?.trim()?.lowercase() ?: return false
        val verified = user.isEmailVerified || user.providerData.any { it.providerId == "google.com" }
        val authorized = verified && isSuperAdminEmail(email)
        Log.d("GRepository", "Single-Admin check for $email (verified=$verified) = $authorized")
        return authorized
    }

    suspend fun hasAdmins(): Boolean {
        return try {
            val doc = db.collection("admins").document(singleAuthorizedAdminEmail).get().await()
            val exists = doc.exists()
            Log.d("GRepository", "hasAdmins check for $singleAuthorizedAdminEmail = $exists")
            exists
        } catch (e: Exception) {
            Log.e("GRepository", "hasAdmins check error", e)
            false
        }
    }

    suspend fun ensureSuperAdmin(email: String, uid: String, displayName: String) {
        val cleanEmail = email.trim().lowercase()
        if (!isSuperAdminEmail(cleanEmail)) {
            throw SecurityException("Access Denied: Only $singleAuthorizedAdminEmail can be granted admin access.")
        }
        Log.d("GRepository", "Ensuring Super Admin for $cleanEmail (uid=$uid)")

        // 1. Create or update the admin document in /admins/{cleanEmail}
        db.collection("admins").document(cleanEmail).set(
            mapOf(
                "email" to cleanEmail,
                "role" to "super_admin",
                "isSuperAdmin" to true,
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()

        // 2. Also register admin_seed_check to ensure setup is recorded
        db.collection("admins").document("admin_seed_check").set(
            mapOf(
                "seeded" to true,
                "primaryAdmin" to cleanEmail,
                "seededAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()

        // 3. Upgrade/create /users/{uid} with admin role cleanly via merge
        val userDocRef = db.collection("users").document(uid)
        val userDoc = userDocRef.get().await()
        if (userDoc.exists()) {
            userDocRef.update(
                mapOf(
                    "role" to "admin",
                    "email" to cleanEmail,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Log.d("GRepository", "Upgraded existing user $uid to admin role")
        } else {
            userDocRef.set(
                mapOf(
                    "id" to uid,
                    "displayName" to displayName,
                    "email" to cleanEmail,
                    "role" to "admin",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
            Log.d("GRepository", "Created initial profile for super admin $uid")
        }
    }

    suspend fun addAdmin(email: String) {
        val cleanEmail = email.trim().lowercase()
        if (!isSuperAdminEmail(cleanEmail)) {
            throw SecurityException("Public or multi-admin registration is disabled. Only $singleAuthorizedAdminEmail is authorized.")
        }
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Admin must sign in first.")
        ensureSuperAdmin(cleanEmail, uid, auth.currentUser?.displayName ?: "Store Owner")
    }
}
