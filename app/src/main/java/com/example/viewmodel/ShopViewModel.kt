package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GRepository
import com.example.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ShopUiState(
    val products: List<Product> = emptyList(),
    val categories: List<Category> = emptyList(),
    val banners: List<Banner> = emptyList(),
    val settings: ShopSettings = ShopSettings(),
    val cart: List<OrderItem> = emptyList(),
    val myOrders: List<Order> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: String = "All",
    val isLoading: Boolean = false,
    val bookingInProgress: Boolean = false,
    val lastBooking: Booking? = null,
    val error: String? = null
)

class ShopViewModel(private val repository: GRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ShopUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        _uiState.update { it.copy(isLoading = it.products.isEmpty(), error = null) }

        repository.observeProducts()
            .onEach { products ->
                Log.d("ShopViewModel", "Real-time products update: ${products.size} products received")
                _uiState.update { it.copy(products = products, isLoading = false, error = null) }
            }
            .catch { e ->
                Log.e("ShopViewModel", "Error in observeProducts flow", e)
                _uiState.update { it.copy(error = e.localizedMessage ?: "Failed to load products", isLoading = false) }
            }
            .launchIn(viewModelScope)

        repository.observeCategories()
            .onEach { categories ->
                _uiState.update { it.copy(categories = categories) }
            }
            .catch { e -> Log.e("ShopViewModel", "Error observing categories", e) }
            .launchIn(viewModelScope)

        repository.observeBanners()
            .onEach { banners ->
                _uiState.update { it.copy(banners = banners) }
            }
            .catch { e -> Log.e("ShopViewModel", "Error observing banners", e) }
            .launchIn(viewModelScope)

        repository.observeSettings()
            .onEach { settings ->
                if (settings != null) {
                    _uiState.update { it.copy(settings = settings) }
                }
            }
            .catch { e -> Log.e("ShopViewModel", "Error observing settings", e) }
            .launchIn(viewModelScope)

        repository.observeUserOrders()
            .onEach { orders ->
                _uiState.update { it.copy(myOrders = orders) }
            }
            .catch { e -> Log.e("ShopViewModel", "Error observing orders", e) }
            .launchIn(viewModelScope)
    }

    fun refreshProducts() = loadData()

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setSelectedCategory(category: String) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun bookProduct(
        product: Product,
        quantity: Int,
        customerPhone: String,
        customerName: String = "",
        onSuccess: (Booking) -> Unit,
        onError: (String) -> Unit
    ) {
        val cleanPhone = customerPhone.trim().replace("+91", "").replace(" ", "").replace("-", "")
        if (cleanPhone.length < 10) {
            onError("Please enter a valid 10-digit mobile number.")
            return
        }

        val totalAmount = product.sellingPrice * quantity
        val randomDigits = (100000..999999).random().toString()
        val bookingId = "GG-$randomDigits"

        val booking = Booking(
            bookingId = bookingId,
            customerPhone = cleanPhone,
            customerName = customerName.ifBlank { "Customer ($cleanPhone)" },
            productId = product.id,
            productName = product.name,
            productImage = product.images.firstOrNull() ?: "",
            quantity = quantity,
            unitPrice = product.sellingPrice,
            totalAmount = totalAmount,
            status = "Pending",
            deliveryType = "Store Pickup Only",
            storeAddress = "G Gifts Toys Sports & Decoration Store, Veera Reddy Nagar, Boduppal"
        )

        viewModelScope.launch {
            _uiState.update { it.copy(bookingInProgress = true, error = null) }
            try {
                val assignedBookingId = repository.createBooking(booking)
                val finalBooking = booking.copy(bookingId = assignedBookingId)
                _uiState.update { it.copy(bookingInProgress = false, lastBooking = finalBooking) }
                onSuccess(finalBooking)
            } catch (e: Exception) {
                _uiState.update { it.copy(bookingInProgress = false, error = e.localizedMessage) }
                onError(e.localizedMessage ?: "Failed to save booking. Please try again.")
            }
        }
    }

    fun bookCustomGift(
        booking: Booking,
        onSuccess: (Booking) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(bookingInProgress = true, error = null) }
            try {
                val assignedBookingId = repository.createBooking(booking)
                val finalBooking = booking.copy(bookingId = assignedBookingId)
                _uiState.update { it.copy(bookingInProgress = false, lastBooking = finalBooking) }
                onSuccess(finalBooking)
            } catch (e: Exception) {
                _uiState.update { it.copy(bookingInProgress = false, error = e.localizedMessage) }
                onError(e.localizedMessage ?: "Failed to save customized booking.")
            }
        }
    }

    fun submitCustomerDesign(
        design: CustomerDesign,
        imageUri: android.net.Uri?,
        onSuccess: (CustomerDesign) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(bookingInProgress = true, error = null) }
            try {
                var photoDownloadUrl = design.originalPhotoUrl
                if (imageUri != null) {
                    try {
                        photoDownloadUrl = repository.uploadCustomDesignImage(imageUri)
                    } catch (e: Exception) {
                        Log.w("ShopViewModel", "Photo upload warning: ${e.message}, fallback to uri")
                        photoDownloadUrl = imageUri.toString()
                    }
                }

                val designWithPhoto = design.copy(
                    originalPhotoUrl = photoDownloadUrl,
                    previewPhotoUrl = photoDownloadUrl
                )
                val (assignedDesignId, assignedBookingId) = repository.saveCustomerDesign(designWithPhoto)
                val finalDesign = designWithPhoto.copy(
                    designId = assignedDesignId,
                    bookingId = assignedBookingId
                )
                _uiState.update { it.copy(bookingInProgress = false) }
                onSuccess(finalDesign)
            } catch (e: Exception) {
                _uiState.update { it.copy(bookingInProgress = false, error = e.localizedMessage) }
                onError(e.localizedMessage ?: "Failed to submit design for approval.")
            }
        }
    }

    suspend fun getDesignStatus(designId: String): CustomerDesign? {
        return repository.getCustomerDesignById(designId)
    }

    fun observeDesignsForCustomer(phone: String): Flow<List<CustomerDesign>> {
        return repository.observeCustomerDesignsByPhone(phone)
    }

    fun addToCart(product: Product, quantity: Int = 1, customization: CustomizationDetails? = null) {
        val existingItem = _uiState.value.cart.find { it.productId == product.id && it.customizationDetails == customization }
        if (existingItem != null) {
            val updatedCart = _uiState.value.cart.map {
                if (it.productId == product.id && it.customizationDetails == customization) {
                    it.copy(quantity = it.quantity + quantity)
                } else it
            }
            _uiState.update { it.copy(cart = updatedCart) }
        } else {
            val newItem = OrderItem(
                productId = product.id,
                name = product.name,
                quantity = quantity,
                price = product.sellingPrice,
                customizationDetails = customization
            )
            _uiState.update { it.copy(cart = it.cart + newItem) }
        }
    }

    fun removeFromCart(item: OrderItem) {
        _uiState.update { it.copy(cart = it.cart - item) }
    }

    fun updateCartQuantity(item: OrderItem, delta: Int) {
        val updatedCart = _uiState.value.cart.map {
            if (it == item) {
                val newQuantity = (it.quantity + delta).coerceAtLeast(1)
                it.copy(quantity = newQuantity)
            } else it
        }
        _uiState.update { it.copy(cart = updatedCart) }
    }

    fun clearCart() {
        _uiState.update { it.copy(cart = emptyList()) }
    }

    fun placeOrder(
        customerName: String,
        customerPhone: String,
        deliveryAddress: String,
        onSuccess: (order: Order, savedToFirestore: Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        val trimmedName = customerName.trim()
        val cleanPhone = customerPhone.trim().replace("+91", "").replace(" ", "").replace("-", "")
        val trimmedAddress = deliveryAddress.trim()
        val cart = _uiState.value.cart

        if (cart.isEmpty()) {
            onError("Your cart is empty.")
            return
        }
        if (trimmedName.isEmpty()) {
            onError("Please enter your full name.")
            return
        }
        if (cleanPhone.length < 10) {
            onError("Please enter a valid 10-digit mobile number.")
            return
        }
        if (trimmedAddress.isEmpty()) {
            onError("Please enter your address.")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(bookingInProgress = true, error = null) }
            val subtotal = cart.sumOf { it.price * it.quantity }
            val deliveryCharges = 0.0 // Store Pickup Only
            val total = subtotal + deliveryCharges
            val fallbackRef = "GG-ORD-${(100000..999999).random()}"

            val order = Order(
                id = fallbackRef,
                customerName = trimmedName.take(120),
                customerPhone = cleanPhone.take(15),
                items = cart,
                subtotal = subtotal,
                deliveryCharges = deliveryCharges,
                totalAmount = total,
                deliveryType = "Store Pickup Only",
                deliveryAddress = trimmedAddress.take(300),
                paymentStatus = "pending",
                orderStatus = "pending_whatsapp"
            )

            var finalOrder = order
            var savedToFirestore = false
            try {
                val docId = repository.createOrder(order)
                finalOrder = order.copy(id = docId)
                savedToFirestore = true
            } catch (e: Exception) {
                Log.w("ShopViewModel", "Firestore order write failed; proceeding with WhatsApp checkout: ${e.message}")
            }

            _uiState.update { it.copy(cart = emptyList(), bookingInProgress = false) }
            onSuccess(finalOrder, savedToFirestore)
        }
    }
}

