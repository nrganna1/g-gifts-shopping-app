package com.example.ui.customer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.data.GRepository
import com.example.model.Booking
import com.example.model.Order
import com.example.model.OrderItem
import com.example.model.Product
import com.example.ui.common.StoreQRCodeDialog
import com.example.util.ShopStrings
import com.example.viewmodel.ShopViewModel
import com.example.viewmodel.ViewModelFactory
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerMainScreen(
    repository: GRepository,
    onSignOut: () -> Unit,
    onOpenAdminPanel: (() -> Unit)? = null
) {
    val shopViewModel: ShopViewModel = viewModel(factory = ViewModelFactory(repository))
    val uiState by shopViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var currentTab by remember { mutableStateOf("home") }
    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var bookingProduct by remember { mutableStateOf<Product?>(null) }
    var confirmedBooking by remember { mutableStateOf<Booking?>(null) }
    var preparedOrder by remember { mutableStateOf<Pair<Order, Boolean>?>(null) }
    var showQRDialog by remember { mutableStateOf(false) }

    val storeWhatsApp = uiState.settings.whatsAppNumber.ifBlank { "7036436305" }
    val cartCount = uiState.cart.sumOf { it.quantity }

    if (selectedProduct != null) {
        ProductDetailScreen(
            product = selectedProduct!!,
            viewModel = shopViewModel,
            onBack = { selectedProduct = null },
            onBookNow = { product ->
                bookingProduct = product
            },
            onAddToCart = { product, qty ->
                shopViewModel.addToCart(product, qty)
                scope.launch {
                    snackbarHostState.showSnackbar("Added ${product.name} (x$qty) to Cart")
                }
            }
        )
    } else {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = ShopStrings.appTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "📍 " + (if (ShopStrings.isTelugu) "స్టోర్ పికప్: బోడుప్పల్, హైదరాబాద్" else "Store Pickup: Boduppal, Hyderabad"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        // Language Toggle Button (English <-> Telugu)
                        TextButton(
                            onClick = { ShopStrings.toggleLanguage() }
                        ) {
                            Text(
                                text = if (ShopStrings.isTelugu) "English" else "తెలుగు",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Shopping Cart Shortcut with Badge
                        IconButton(
                            onClick = { currentTab = "cart" },
                            modifier = Modifier.testTag("topbar_cart_button")
                        ) {
                            BadgedBox(badge = {
                                if (cartCount > 0) {
                                    Badge { Text(cartCount.toString()) }
                                }
                            }) {
                                Icon(
                                    Icons.Default.ShoppingCart,
                                    contentDescription = "Shopping Cart",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Shop Permanent QR Code Dialog
                        IconButton(onClick = { showQRDialog = true }) {
                            Icon(
                                Icons.Default.QrCode,
                                contentDescription = "Shop QR Code",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Quick WhatsApp Store Chat
                        IconButton(
                            onClick = {
                                openWhatsAppChat(context, storeWhatsApp, "Hello G Gifts Store! I am browsing your shopping app.")
                            }
                        ) {
                            Icon(
                                Icons.Default.Chat,
                                contentDescription = "Chat on WhatsApp",
                                tint = Color(0xFF25D366)
                            )
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Storefront, contentDescription = "Shop") },
                        label = { Text(ShopStrings.tabShop) },
                        selected = currentTab == "home",
                        onClick = { currentTab = "home" }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Category, contentDescription = "Categories") },
                        label = { Text(ShopStrings.tabCategories) },
                        selected = currentTab == "categories",
                        onClick = { currentTab = "categories" }
                    )
                    NavigationBarItem(
                        icon = {
                            BadgedBox(badge = {
                                if (cartCount > 0) {
                                    Badge { Text(cartCount.toString()) }
                                }
                            }) {
                                Icon(Icons.Default.ShoppingCart, contentDescription = "Cart")
                            }
                        },
                        label = { Text("Cart") },
                        selected = currentTab == "cart",
                        onClick = { currentTab = "cart" },
                        modifier = Modifier.testTag("nav_cart_tab")
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.LocationOn, contentDescription = "Pickup") },
                        label = { Text(ShopStrings.tabPickup) },
                        selected = currentTab == "pickup",
                        onClick = { currentTab = "pickup" }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Person, contentDescription = "Account") },
                        label = { Text(ShopStrings.tabAccount) },
                        selected = currentTab == "account",
                        onClick = { currentTab = "account" }
                    )
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                when (currentTab) {
                    "home" -> HomeScreen(
                        viewModel = shopViewModel,
                        onProductClick = { selectedProduct = it },
                        onBookClick = { bookingProduct = it },
                        onAddToCart = { product ->
                            shopViewModel.addToCart(product, 1)
                            scope.launch {
                                snackbarHostState.showSnackbar("Added ${product.name} to Cart")
                            }
                        }
                    )
                    "categories" -> CategoriesScreen(
                        viewModel = shopViewModel,
                        onCategorySelect = { categoryName ->
                            shopViewModel.setSelectedCategory(categoryName)
                            currentTab = "home"
                        }
                    )
                    "cart" -> CartCheckoutScreen(
                        viewModel = shopViewModel,
                        storeWhatsApp = storeWhatsApp,
                        onBrowseShop = { currentTab = "home" },
                        onOrderPrepared = { order, savedToDb ->
                            preparedOrder = order to savedToDb
                            val msg = buildWhatsAppOrderMessage(order, uiState.settings.shopAddress)
                            openWhatsAppChat(context, storeWhatsApp, msg)
                        }
                    )
                    "pickup" -> StorePickupInfoScreen(
                        settings = uiState.settings,
                        context = context,
                        onOpenQR = { showQRDialog = true }
                    )
                    "account" -> ProfileScreen(onSignOut = onSignOut, onOpenAdminPanel = onOpenAdminPanel)
                }
            }
        }
    }

    // Cart Order WhatsApp Hand-off Dialog
    if (preparedOrder != null) {
        val (order, savedToDb) = preparedOrder!!
        val orderMessage = remember(order) { buildWhatsAppOrderMessage(order, uiState.settings.shopAddress) }
        AlertDialog(
            onDismissRequest = { preparedOrder = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = Color(0xFF25D366))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Send Order on WhatsApp")
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "Reference: ${order.id}",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Name: ${order.customerName}")
                    Text("Mobile: ${order.customerPhone}")
                    Text("Address: ${order.deliveryAddress}")
                    Spacer(modifier = Modifier.height(8.dp))
                    order.items.forEach { item ->
                        Text("• ${item.name} × ${item.quantity} = ₹${"%.0f".format(item.price * item.quantity)}")
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Total Amount: ₹${"%.0f".format(order.totalAmount)}",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32)
                    )
                    if (savedToDb) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "✓ Order summary drafted in store database.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF2E7D32)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = Color(0xFFFFF3E0),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("order_not_submitted_notice")
                    ) {
                        Text(
                            text = "Your order is not submitted until you send the WhatsApp message.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { openWhatsAppChat(context, storeWhatsApp, orderMessage) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                ) {
                    Text("Open WhatsApp")
                }
            },
            dismissButton = {
                TextButton(onClick = { preparedOrder = null }) {
                    Text("Close")
                }
            }
        )
    }

    if (showQRDialog) {
        StoreQRCodeDialog(
            websiteUrl = uiState.settings.websiteUrl,
            shopName = uiState.settings.shopName,
            onDismiss = { showQRDialog = false }
        )
    }

    // Booking Dialog
    if (bookingProduct != null) {
        val product = bookingProduct!!
        BookingDialog(
            product = product,
            isBooking = uiState.bookingInProgress,
            onDismiss = { bookingProduct = null },
            onConfirmBooking = { quantity, customerPhone, customerName ->
                shopViewModel.bookProduct(
                    product = product,
                    quantity = quantity,
                    customerPhone = customerPhone,
                    customerName = customerName,
                    onSuccess = { booking ->
                        bookingProduct = null
                        confirmedBooking = booking

                        // Pre-filled WhatsApp message as requested in Section 4
                        val customerNameVal = if (customerName.isNotBlank()) customerName.trim() else "[Optional]"
                        val message = """
G GIFTS SHOP - NEW CUSTOMER BOOKING

Booking ID: ${booking.bookingId}
Customer Name: $customerNameVal
Customer Phone: ${booking.customerPhone}
Product: ${booking.productName}
Quantity: ${booking.quantity}
Unit Price: ₹${"%.0f".format(booking.unitPrice)}
Total Amount: ₹${"%.0f".format(booking.totalAmount)}
Pickup: Store Pickup Only

Please confirm my booking.
""".trimIndent()

                        openWhatsAppChat(context, storeWhatsApp, message)
                    },
                    onError = {
                        // Handled via uiState error
                    }
                )
            }
        )
    }

    // Booking Request Prepared Dialog
    if (confirmedBooking != null) {
        val b = confirmedBooking!!
        AlertDialog(
            onDismissRequest = { confirmedBooking = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF1976D2))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Booking Request Ready")
                }
            },
            text = {
                Column {
                    Text(
                        text = "Booking ID: ${b.bookingId}",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Customer: ${b.customerName} (${b.customerPhone})")
                    Text("Product: ${b.productName} (Qty: ${b.quantity})")
                    Text("Total Amount: ₹${"%.0f".format(b.totalAmount)}", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = Color(0xFFFFF3E0),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("booking_pending_confirmation_notice")
                    ) {
                        Text(
                            text = "Your WhatsApp booking is not confirmed until the shop confirms it.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("📍 Store Pickup Only (No Home Delivery)", fontWeight = FontWeight.Bold)
                            Text(
                                "G Gifts Toys Sports Decoration Store\nVeera Reddy Nagar, Boduppal, Telangana\nWhatsApp: $storeWhatsApp",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Review and send the pre-filled WhatsApp message to notify the store.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = { confirmedBooking = null }) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
fun HomeScreen(
    viewModel: ShopViewModel,
    onProductClick: (Product) -> Unit,
    onBookClick: (Product) -> Unit,
    onAddToCart: (Product) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val searchQuery = state.searchQuery
    val selectedCategory = state.selectedCategory

    // Filter active products
    val filteredProducts = remember(state.products, searchQuery, selectedCategory) {
        state.products.filter { product ->
            product.isEnabled &&
            (selectedCategory == "All" || product.category.equals(selectedCategory, ignoreCase = true)) &&
            (searchQuery.isBlank() || product.name.contains(searchQuery, ignoreCase = true) || product.description.contains(searchQuery, ignoreCase = true))
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // STORE PICKUP ONLY BANNER (Requirement 4)
        item {
            StorePickupBanner()
        }

        // Search bar (Requirement 1)
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text(ShopStrings.searchPlaceholder) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Search")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("product_search_bar"),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
        }

        // Category filter chips (Requirement 1)
        item {
            val categoriesList = listOf("All") + state.categories.map { it.name }
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categoriesList) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { viewModel.setSelectedCategory(category) },
                        label = { Text(category) },
                        modifier = Modifier.testTag("category_chip_$category")
                    )
                }
            }
        }

        // Section header
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedCategory == "All") "All Products (${filteredProducts.size})" else "$selectedCategory (${filteredProducts.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Direct Booking • No Login",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Error state
        if (state.error != null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(state.error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                        TextButton(onClick = { viewModel.refreshProducts() }) {
                            Text("Retry")
                        }
                    }
                }
            }
        }

        // Loading state
        if (state.isLoading && state.products.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Loading store products from Firestore...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else if (filteredProducts.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Inventory2,
                            contentDescription = null,
                            modifier = Modifier.size(54.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = when {
                                searchQuery.isNotBlank() -> "No products match \"$searchQuery\""
                                selectedCategory != "All" -> "No products found in category \"$selectedCategory\""
                                else -> "No active products found in the store.\nProducts added in Admin Panel will appear here automatically."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        if (searchQuery.isNotBlank() || selectedCategory != "All") {
                            FilledTonalButton(onClick = {
                                viewModel.setSearchQuery("")
                                viewModel.setSelectedCategory("All")
                            }) {
                                Text("View All Products")
                            }
                        } else {
                            FilledTonalButton(onClick = { viewModel.refreshProducts() }) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Refresh Store")
                            }
                        }
                    }
                }
            }
        } else {
            items(filteredProducts, key = { it.id }) { product ->
                ProductItemCard(
                    product = product,
                    onClick = { onProductClick(product) },
                    onBookClick = { onBookClick(product) },
                    onAddToCart = { onAddToCart(product) }
                )
            }
        }
    }
}

@Composable
fun StorePickupBanner() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = ShopStrings.storePickupOnly,
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (ShopStrings.isTelugu) "జీరో డెలివరీ ఛార్జీలు" else "No Delivery Charges",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "G Gifts Toys Sports Decoration Store",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Address: " + ShopStrings.storeAddress,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = ShopStrings.storePickupNotice,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ProductItemCard(
    product: Product,
    onClick: () -> Unit,
    onBookClick: () -> Unit,
    onAddToCart: () -> Unit
) {
    val isOutOfStock = product.stock <= 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick)
            .testTag("product_card_${product.id}"),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Product image with fallback
            val imageUrl = product.images.firstOrNull()?.trim()
            if (imageUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CardGiftcard,
                        contentDescription = product.name,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            } else {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = product.name,
                    modifier = Modifier
                        .size(90.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                if (product.category.isNotBlank()) {
                    Text(
                        text = product.category.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (product.description.isNotBlank()) {
                    Text(
                        text = product.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Price and offers
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "₹${"%.0f".format(product.sellingPrice)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (product.originalPrice > product.sellingPrice) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "₹${"%.0f".format(product.originalPrice)}",
                            style = MaterialTheme.typography.bodySmall,
                            textDecoration = TextDecoration.LineThrough,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val discountPct = (((product.originalPrice - product.sellingPrice) / product.originalPrice) * 100).toInt()
                        if (discountPct > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$discountPct% OFF",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (product.offerTag.isNotBlank()) {
                    Text(
                        text = "🎁 ${product.offerTag}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Stock availability
                Text(
                    text = if (isOutOfStock) "Out of Stock" else "In Stock (${product.stock} available)",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isOutOfStock) MaterialTheme.colorScheme.error else Color(0xFF2E7D32),
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Add to Cart & Quick Book Now buttons
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilledTonalButton(
                    onClick = onAddToCart,
                    enabled = !isOutOfStock,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("add_to_cart_button_${product.id}")
                ) {
                    Icon(Icons.Default.AddShoppingCart, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isOutOfStock) "Out" else "Add Cart",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                Button(
                    onClick = onBookClick,
                    enabled = !isOutOfStock,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("book_now_button_${product.id}")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isOutOfStock) (if (ShopStrings.isTelugu) "స్టాక్ లేదు" else "Sold Out") else ShopStrings.bookNow,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
fun BookingDialog(
    product: Product,
    isBooking: Boolean,
    onDismiss: () -> Unit,
    onConfirmBooking: (quantity: Int, phone: String, customerName: String) -> Unit
) {
    var quantity by remember { mutableStateOf(1) }
    var customerPhone by remember { mutableStateOf("") }
    var customerName by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }

    val totalAmount = product.sellingPrice * quantity

    AlertDialog(
        onDismissRequest = { if (!isBooking) onDismiss() },
        title = {
            Column {
                Text(
                    text = "Book Product",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Store Pickup Only • Boduppal, Telangana",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Product brief
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Unit Price: ₹${"%.0f".format(product.sellingPrice)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Quantity selector (Requirement 2)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Select Quantity:", fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { if (quantity > 1) quantity-- },
                            enabled = quantity > 1 && !isBooking
                        ) {
                            Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Decrease")
                        }
                        Text(
                            text = quantity.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        IconButton(
                            onClick = { if (quantity < product.stock.coerceAtLeast(10)) quantity++ },
                            enabled = !isBooking
                        ) {
                            Icon(Icons.Default.AddCircleOutline, contentDescription = "Increase")
                        }
                    }
                }

                // Total Amount
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Amount:", fontWeight = FontWeight.Bold)
                    Text(
                        text = "₹${"%.0f".format(totalAmount)}",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32),
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Customer Name
                OutlinedTextField(
                    value = customerName,
                    onValueChange = {
                        customerName = it
                        nameError = null
                    },
                    label = { Text("Customer Name") },
                    placeholder = { Text("Enter your full name") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    isError = nameError != null,
                    supportingText = nameError?.let { err -> { Text(err, color = MaterialTheme.colorScheme.error) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("booking_name_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Phone number input (Requirement 2 & 3: 10-digit mobile number)
                OutlinedTextField(
                    value = customerPhone,
                    onValueChange = { input ->
                        if (input.length <= 10 && input.all { it.isDigit() }) {
                            customerPhone = input
                            phoneError = null
                        }
                    },
                    label = { Text("Customer Mobile Number") },
                    placeholder = { Text("Enter 10-digit mobile number") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    isError = phoneError != null,
                    supportingText = {
                        if (phoneError != null) {
                            Text(phoneError!!, color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("No password or Google login required.")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("booking_phone_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Store Pickup & Confirmation Notice
                Surface(
                    color = Color(0xFFFFF3E0),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Storefront,
                            contentDescription = null,
                            tint = Color(0xFFE65100),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Store Pickup Only (Veera Reddy Nagar, Boduppal, Telangana). Your WhatsApp booking is not confirmed until the shop confirms it.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF5D4037)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedName = customerName.trim()
                    if (trimmedName.isEmpty()) {
                        nameError = "Please enter your name."
                    } else if (customerPhone.length < 10) {
                        phoneError = "Please enter a valid 10-digit mobile number."
                    } else {
                        onConfirmBooking(quantity, customerPhone, trimmedName)
                    }
                },
                enabled = !isBooking,
                modifier = Modifier.testTag("confirm_booking_button")
            ) {
                if (isBooking) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Saving...")
                } else {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Book via WhatsApp")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBooking) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CategoriesScreen(
    viewModel: ShopViewModel,
    onCategorySelect: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Shop by Category", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Select a category to view active store products", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onCategorySelect("All") },
                shape = RoundedCornerShape(12.dp)
            ) {
                ListItem(
                    headlineContent = { Text("All Products", fontWeight = FontWeight.Bold) },
                    supportingContent = { Text("View everything available in G Gifts") },
                    leadingContent = { Icon(Icons.Default.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                )
            }
        }

        items(state.categories, key = { it.id }) { category ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onCategorySelect(category.name) },
                shape = RoundedCornerShape(12.dp)
            ) {
                ListItem(
                    headlineContent = { Text(category.name, fontWeight = FontWeight.Bold) },
                    supportingContent = { Text("Browse ${category.name} collection") },
                    leadingContent = { Icon(Icons.Default.Category, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) }
                )
            }
        }
    }
}

@Composable
fun StorePickupInfoScreen(
    settings: com.example.model.ShopSettings,
    context: Context,
    onOpenQR: (() -> Unit)? = null
) {
    val whatsAppNumber = settings.whatsAppNumber.ifBlank { "7036436305" }
    val phone = settings.contactNumber.ifBlank { "7036436305" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Storefront,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = ShopStrings.storePickupOnly,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = if (ShopStrings.isTelugu) "హోమ్ డెలివరీ లేదు • జీరో డెలివరీ ఛార్జీలు" else "No Home Delivery • Zero Delivery Charges",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF2E7D32),
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (ShopStrings.isTelugu) "స్టోర్ చిరునామా:" else "Store Address:",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "G Gifts Toys Sports Decoration Store\nVeera Reddy Nagar, Boduppal,\nTelangana, India.",
                    style = MaterialTheme.typography.bodyLarge
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Text(
                    text = if (ShopStrings.isTelugu) "వ్యాపార సమయాలు:" else "Business Timings:",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(text = settings.businessTimings.ifBlank { "10:00 AM - 10:00 PM (Daily)" }, style = MaterialTheme.typography.bodyMedium)

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Text(
                    text = if (ShopStrings.isTelugu) "స్టోర్ సంప్రదింపులు:" else "Store Contact:",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(text = "Phone: $phone\nWhatsApp: $whatsAppNumber", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (onOpenQR != null) {
            FilledTonalButton(
                onClick = onOpenQR,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Default.QrCode, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(ShopStrings.shopQRCode + " (Scan / Share)")
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        Button(
            onClick = {
                openWhatsAppChat(context, whatsAppNumber, "Hello G Gifts Store! I am visiting your store at Boduppal.")
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.Default.Chat, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Chat on WhatsApp ($whatsAppNumber)")
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = {
                try {
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                } catch (_: Exception) {}
            },
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.Default.Call, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Call Store ($phone)")
        }
    }
}

@Composable
fun ProfileScreen(onSignOut: () -> Unit, onOpenAdminPanel: (() -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.AccountCircle,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text("G Gifts Account & Admin", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Direct product booking is open to everyone without login.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(32.dp))

        if (onOpenAdminPanel != null) {
            OutlinedButton(
                onClick = onOpenAdminPanel,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_store_admin_access_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Shield, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Store Administrator Access", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("profile_sign_out_button"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sign Out / Switch Account")
        }
    }
}

@Composable
fun CartCheckoutScreen(
    viewModel: ShopViewModel,
    storeWhatsApp: String,
    onBrowseShop: () -> Unit,
    onOrderPrepared: (Order, Boolean) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val cart = state.cart
    val totalAmount = cart.sumOf { it.price * it.quantity }

    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var deliveryAddress by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var addressError by remember { mutableStateOf<String?>(null) }
    var submitError by remember { mutableStateOf<String?>(null) }

    if (cart.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.ShoppingCart,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Your Shopping Cart is Empty",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Add products from the store to review order summary and place an order on WhatsApp.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(18.dp))
                Button(onClick = onBrowseShop) {
                    Text("Browse Products")
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Your Cart (${cart.sumOf { it.quantity }} items)",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = { viewModel.clearCart() }) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        items(cart, key = { it.productId }) { item ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cart_item_${item.productId}"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Unit Price: ₹${"%.0f".format(item.price)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Subtotal: ₹${"%.0f".format(item.price * item.quantity)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.updateCartQuantity(item, -1) },
                            enabled = item.quantity > 1
                        ) {
                            Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Decrease quantity")
                        }
                        Text(
                            text = item.quantity.toString(),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        IconButton(
                            onClick = { viewModel.updateCartQuantity(item, 1) }
                        ) {
                            Icon(Icons.Default.AddCircleOutline, contentDescription = "Increase quantity")
                        }
                        IconButton(
                            onClick = { viewModel.removeFromCart(item) },
                            modifier = Modifier.testTag("remove_cart_item_${item.productId}")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove item", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        // Complete Order Summary Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Order Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    cart.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${item.name} × ${item.quantity}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text("₹${"%.0f".format(item.price * item.quantity)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Pickup Mode", style = MaterialTheme.typography.bodySmall)
                        Text("Store Pickup Only (₹0)", style = MaterialTheme.typography.bodySmall, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total Amount", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = "₹${"%.0f".format(totalAmount)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            }
        }

        // Customer Checkout Form: Name, Mobile Number, Address
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Customer Checkout Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = customerName,
                        onValueChange = { customerName = it; nameError = null; submitError = null },
                        label = { Text("Customer Name *") },
                        placeholder = { Text("Enter your full name") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        isError = nameError != null,
                        supportingText = nameError?.let { err -> { Text(err, color = MaterialTheme.colorScheme.error) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("checkout_name_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = customerPhone,
                        onValueChange = { input ->
                            if (input.length <= 10 && input.all { it.isDigit() }) {
                                customerPhone = input
                                phoneError = null
                                submitError = null
                            }
                        },
                        label = { Text("Mobile Number (10 digits) *") },
                        placeholder = { Text("Enter 10-digit mobile number") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        isError = phoneError != null,
                        supportingText = phoneError?.let { err -> { Text(err, color = MaterialTheme.colorScheme.error) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("checkout_phone_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = deliveryAddress,
                        onValueChange = { deliveryAddress = it; addressError = null; submitError = null },
                        label = { Text("Customer Address *") },
                        placeholder = { Text("House no., Street, Area / Colony") },
                        leadingIcon = { Icon(Icons.Default.Home, contentDescription = null) },
                        isError = addressError != null,
                        supportingText = addressError?.let { err -> { Text(err, color = MaterialTheme.colorScheme.error) } },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth().testTag("checkout_address_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        color = Color(0xFFFFF3E0),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Note: Clicking Place Order opens WhatsApp ($storeWhatsApp). Your order is not submitted until you send the WhatsApp message.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFE65100),
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    if (submitError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(submitError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val tName = customerName.trim()
                            val tAddr = deliveryAddress.trim()
                            if (tName.isEmpty()) {
                                nameError = "Please enter your name."
                                return@Button
                            }
                            if (customerPhone.length < 10) {
                                phoneError = "Please enter a valid 10-digit mobile number."
                                return@Button
                            }
                            if (tAddr.isEmpty()) {
                                addressError = "Please enter your address."
                                return@Button
                            }
                            viewModel.placeOrder(
                                customerName = tName,
                                customerPhone = customerPhone,
                                deliveryAddress = tAddr,
                                onSuccess = { order, savedToDb ->
                                    onOrderPrepared(order, savedToDb)
                                },
                                onError = { msg -> submitError = msg }
                            )
                        },
                        enabled = !state.bookingInProgress,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("place_order_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (state.bookingInProgress) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Preparing Order...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Place Order via WhatsApp (₹${"%.0f".format(totalAmount)})", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

fun buildWhatsAppOrderMessage(order: Order, storeAddress: String): String {
    val lines = buildString {
        appendLine("G GIFTS STORE - NEW CUSTOMER ORDER")
        appendLine()
        appendLine("Order Ref: ${order.id}")
        appendLine("Customer Name: ${order.customerName}")
        appendLine("Mobile Number: ${order.customerPhone}")
        appendLine("Address: ${order.deliveryAddress}")
        appendLine("Pickup: Store Pickup Only (${storeAddress.ifBlank { "Veera Reddy Nagar, Boduppal, Telangana" }})")
        appendLine()
        appendLine("Items:")
        order.items.forEachIndexed { idx, item ->
            val lineTotal = item.price * item.quantity
            appendLine("${idx + 1}. ${item.name} x ${item.quantity} @ ₹${"%.0f".format(item.price)} = ₹${"%.0f".format(lineTotal)}")
        }
        appendLine()
        appendLine("Total Amount: ₹${"%.0f".format(order.totalAmount)}")
        appendLine()
        append("Please confirm my order.")
    }
    return lines
}

fun openWhatsAppChat(context: Context, whatsAppNumber: String, message: String) {
    val cleanNumber = whatsAppNumber.replace("+", "").replace(" ", "").replace("-", "")
    val formattedNumber = if (cleanNumber.length == 10) "91$cleanNumber" else cleanNumber
    val encoded = Uri.encode(message)
    val url = "https://wa.me/$formattedNumber?text=$encoded"
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(browserIntent)
        } catch (_: Exception) {}
    }
}
