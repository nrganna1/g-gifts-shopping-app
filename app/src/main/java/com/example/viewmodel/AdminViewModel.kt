package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GRepository
import com.example.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AdminUiState(
    val products: List<Product> = emptyList(),
    val categories: List<Category> = emptyList(),
    val banners: List<Banner> = emptyList(),
    val orders: List<Order> = emptyList(),
    val bookings: List<Booking> = emptyList(),
    val designs: List<CustomerDesign> = emptyList(),
    val settings: ShopSettings = ShopSettings(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class AdminViewModel(private val repository: GRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadAdminData()
    }

    private fun loadAdminData() {
        _uiState.update { it.copy(isLoading = true) }

        repository.observeAdminProducts().onEach { products ->
            _uiState.update { it.copy(products = products) }
        }.launchIn(viewModelScope)

        repository.observeCategories().onEach { categories ->
            _uiState.update { it.copy(categories = categories) }
        }.launchIn(viewModelScope)

        repository.observeAdminOrders().onEach { orders ->
            _uiState.update { it.copy(orders = orders) }
        }.launchIn(viewModelScope)

        repository.observeAdminBookings().onEach { bookings ->
            _uiState.update { it.copy(bookings = bookings) }
        }.launchIn(viewModelScope)

        repository.observeCustomerDesigns().onEach { designs ->
            _uiState.update { it.copy(designs = designs) }
        }.launchIn(viewModelScope)

        repository.observeSettings().onEach { settings ->
            if (settings != null) {
                _uiState.update { it.copy(settings = settings) }
            }
        }.launchIn(viewModelScope)

        _uiState.update { it.copy(isLoading = false) }
    }

    fun saveProduct(product: Product) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(error = null) }
                if (product.id.isEmpty()) {
                    repository.addProduct(product)
                } else {
                    repository.updateProduct(product)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.localizedMessage ?: "Failed to save product") }
            }
        }
    }

    fun deleteProduct(productId: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(error = null) }
                repository.deleteProduct(productId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.localizedMessage ?: "Failed to delete product") }
            }
        }
    }

    fun saveCategory(category: Category) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(error = null) }
                if (category.id.isEmpty()) {
                    repository.addCategory(category)
                } else {
                    repository.updateCategory(category)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.localizedMessage ?: "Failed to save category") }
            }
        }
    }

    fun deleteCategory(categoryId: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(error = null) }
                repository.deleteCategory(categoryId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.localizedMessage ?: "Failed to delete category") }
            }
        }
    }

    fun updateOrderStatus(orderId: String, status: String) {
        viewModelScope.launch {
            try {
                repository.updateOrderStatus(orderId, status)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.localizedMessage ?: "Failed to update order status") }
            }
        }
    }

    fun updateBookingStatus(bookingDocId: String, status: String) {
        viewModelScope.launch {
            try {
                repository.updateBookingStatus(bookingDocId, status)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.localizedMessage ?: "Failed to update booking status") }
            }
        }
    }

    fun updateDesignApproval(
        designDocId: String,
        approvalStatus: String,
        adminNote: String = "",
        printStatus: String? = null,
        reviewedBy: String = ""
    ) {
        viewModelScope.launch {
            try {
                repository.updateDesignApprovalStatus(
                    designDocId = designDocId,
                    approvalStatus = approvalStatus,
                    adminNote = adminNote,
                    printStatus = printStatus,
                    reviewedBy = reviewedBy
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.localizedMessage ?: "Failed to update approval status") }
            }
        }
    }

    fun updateDesignPrintStatus(
        designDocId: String,
        printStatus: String
    ) {
        viewModelScope.launch {
            try {
                repository.updateDesignPrintStatus(designDocId, printStatus)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.localizedMessage ?: "Failed to update print status") }
            }
        }
    }

    fun saveSettings(settings: ShopSettings) {
        viewModelScope.launch {
            try {
                repository.saveSettings(settings)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.localizedMessage ?: "Failed to save settings") }
            }
        }
    }

    suspend fun uploadProductImage(uri: android.net.Uri): Result<String> {
        return try {
            val url = repository.uploadProductImage(uri)
            Result.success(url)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
