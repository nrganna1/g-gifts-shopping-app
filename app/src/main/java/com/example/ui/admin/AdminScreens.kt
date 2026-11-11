package com.example.ui.admin

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.viewinterop.AndroidView
import com.example.MainActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.GRepository
import com.example.model.*
import com.example.ui.common.ProductQRCodeDialog
import com.example.ui.common.StoreQRCodeDialog
import com.example.util.QRCodeHelper
import com.example.util.StoreUrlValidationResult
import com.example.viewmodel.AdminViewModel
import com.example.viewmodel.ViewModelFactory
import com.example.util.PhotoPickerHelper
import com.example.util.PreviewEnvironmentHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminMainScreen(
    repository: GRepository,
    onSignOut: () -> Unit,
    onViewCustomerStore: (() -> Unit)? = null
) {
    val adminViewModel: AdminViewModel = viewModel(factory = ViewModelFactory(repository))
    val uiState by adminViewModel.uiState.collectAsState()
    var currentScreen by remember { mutableStateOf("bookings") }
    var showQRCodeDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("G Gifts Admin Panel", fontWeight = FontWeight.Bold)
                        Text(
                            "Store: Boduppal, Hyderabad",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showQRCodeDialog = true }) {
                        Icon(Icons.Default.QrCode, contentDescription = "Shop QR Code", tint = MaterialTheme.colorScheme.primary)
                    }
                    if (onViewCustomerStore != null) {
                        FilledTonalButton(
                            onClick = onViewCustomerStore,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Customer Store")
                        }
                    }
                    IconButton(onClick = onSignOut) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign Out")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = {
                        val pendingBookings = uiState.bookings.count { it.status.equals("Pending", ignoreCase = true) }
                        val pendingOrders = uiState.orders.count {
                            it.orderStatus.equals("pending", ignoreCase = true) ||
                                it.orderStatus.equals("pending_whatsapp", ignoreCase = true)
                        }
                        val pendingCount = pendingBookings + pendingOrders
                        BadgedBox(badge = {
                            if (pendingCount > 0) {
                                Badge { Text(pendingCount.toString()) }
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = "Orders & Bookings")
                        }
                    },
                    label = { Text("Orders") },
                    selected = currentScreen == "bookings",
                    onClick = { currentScreen = "bookings" }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Inventory, contentDescription = "Products") },
                    label = { Text("Products") },
                    selected = currentScreen == "products",
                    onClick = { currentScreen = "products" }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Category, contentDescription = "Categories") },
                    label = { Text("Categories") },
                    selected = currentScreen == "categories",
                    onClick = { currentScreen = "categories" }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                    label = { Text("Dashboard") },
                    selected = currentScreen == "dashboard",
                    onClick = { currentScreen = "dashboard" }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    selected = currentScreen == "settings",
                    onClick = { currentScreen = "settings" }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentScreen) {
                "bookings" -> AdminBookingsScreen(adminViewModel)
                "products" -> AdminProducts(adminViewModel)
                "categories" -> AdminCategories(adminViewModel)
                "dashboard" -> AdminDashboard(adminViewModel)
                "settings" -> AdminSettings(adminViewModel)
            }
        }
    }

    if (showQRCodeDialog) {
        StoreQRCodeDialog(
            websiteUrl = uiState.settings.websiteUrl,
            shopName = uiState.settings.shopName,
            onDismiss = { showQRCodeDialog = false }
        )
    }
}

@Composable
fun AdminDashboard(viewModel: AdminViewModel) {
    val state by viewModel.uiState.collectAsState()
    val totalBookings = state.bookings.size + state.orders.size
    val pendingBookings = state.bookings.count { it.status.equals("Pending", ignoreCase = true) } +
        state.orders.count { it.orderStatus.equals("pending", ignoreCase = true) || it.orderStatus.equals("pending_whatsapp", ignoreCase = true) }
    val confirmedBookings = state.bookings.count { it.status.equals("Confirmed", ignoreCase = true) || it.status.equals("Ready for Pickup", ignoreCase = true) } +
        state.orders.count { it.orderStatus.equals("Confirmed", ignoreCase = true) || it.orderStatus.equals("Ready for Pickup", ignoreCase = true) }
    val totalRevenue = state.bookings.filter { !it.status.equals("Cancelled", ignoreCase = true) }.sumOf { it.totalAmount } +
        state.orders.filter { !it.orderStatus.equals("Cancelled", ignoreCase = true) }.sumOf { it.totalAmount }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Business Overview", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Store: Veera Reddy Nagar, Boduppal • Single Admin Only", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                StatCard("Orders & Bookings", totalBookings.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                Spacer(modifier = Modifier.width(8.dp))
                StatCard("Pending Action", pendingBookings.toString(), MaterialTheme.colorScheme.error, Modifier.weight(1f))
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                StatCard("Confirmed/Ready", confirmedBookings.toString(), Color(0xFF2E7D32), Modifier.weight(1f))
                Spacer(modifier = Modifier.width(8.dp))
                StatCard("Est. Revenue", "₹${"%.0f".format(totalRevenue)}", MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                StatCard("Active Products", state.products.count { it.isEnabled }.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                Spacer(modifier = Modifier.width(8.dp))
                StatCard("Categories", state.categories.size.toString(), MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, accentColor: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = accentColor)
        }
    }
}

@Composable
fun AdminBookingsScreen(viewModel: AdminViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var statusFilter by remember { mutableStateOf("All") }
    var viewType by remember { mutableStateOf("Orders") } // "Orders" or "Bookings"

    val filteredOrders = remember(state.orders, statusFilter) {
        when (statusFilter) {
            "All" -> state.orders
            "Pending" -> state.orders.filter {
                it.orderStatus.equals("pending", ignoreCase = true) ||
                    it.orderStatus.equals("pending_whatsapp", ignoreCase = true)
            }
            else -> state.orders.filter { it.orderStatus.equals(statusFilter, ignoreCase = true) }
        }
    }

    val filteredBookings = remember(state.bookings, statusFilter) {
        when (statusFilter) {
            "All" -> state.bookings
            else -> state.bookings.filter { it.status.equals(statusFilter, ignoreCase = true) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Orders vs Quick Bookings Toggle
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = viewType == "Orders",
                onClick = { viewType = "Orders" },
                label = { Text("Cart Orders (${state.orders.size})") },
                modifier = Modifier.testTag("admin_tab_orders")
            )
            FilterChip(
                selected = viewType == "Bookings",
                onClick = { viewType = "Bookings" },
                label = { Text("Quick Bookings (${state.bookings.size})") },
                modifier = Modifier.testTag("admin_tab_bookings")
            )
        }

        // Status filter tabs
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filters = listOf("All", "Pending", "Confirmed", "Ready for Pickup", "Completed", "Cancelled")
            items(filters) { filter ->
                FilterChip(
                    selected = statusFilter == filter,
                    onClick = { statusFilter = filter },
                    label = { Text(filter) }
                )
            }
        }

        if (viewType == "Orders") {
            if (filteredOrders.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.ShoppingBag,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            if (state.orders.isEmpty()) "No customer cart orders yet." else "No orders matching $statusFilter",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredOrders, key = { it.id }) { order ->
                        OrderAdminCard(
                            order = order,
                            onUpdateStatus = { newStatus ->
                                viewModel.updateOrderStatus(order.id, newStatus)
                            },
                            onOpenWhatsApp = {
                                val msg = "Hello ${order.customerName.ifBlank { "Customer" }}, G Gifts Store Boduppal regarding Order #${order.id} (Total: ₹${"%.0f".format(order.totalAmount)}). Status: ${order.orderStatus}."
                                val cleanNumber = order.customerPhone.replace("+", "").replace(" ", "").replace("-", "")
                                val formattedNumber = if (cleanNumber.length == 10) "91$cleanNumber" else cleanNumber
                                val url = "https://wa.me/$formattedNumber?text=${Uri.encode(msg)}"
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                } catch (_: Exception) {}
                            },
                            onCallCustomer = {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${order.customerPhone}")))
                                } catch (_: Exception) {}
                            }
                        )
                    }
                }
            }
        } else if (filteredBookings.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Inbox,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        if (state.bookings.isEmpty()) "No quick bookings yet." else "No bookings matching $statusFilter",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredBookings, key = { it.id.ifBlank { it.bookingId } }) { booking ->
                    BookingCard(
                        booking = booking,
                        onUpdateStatus = { newStatus ->
                            viewModel.updateBookingStatus(booking.id, newStatus)
                        },
                        onOpenWhatsApp = {
                            val msg = "Hello ${booking.customerName.ifBlank { "Customer" }}, G Gifts Toys Sports Decoration Store regarding booking #${booking.bookingId} (${booking.productName}, Qty: ${booking.quantity}, Total: ₹${"%.0f".format(booking.totalAmount)}). Status: ${booking.status}. Store Pickup: Veera Reddy Nagar, Boduppal, Telangana."
                            val cleanNumber = booking.customerPhone.replace("+", "").replace(" ", "").replace("-", "")
                            val formattedNumber = if (cleanNumber.length == 10) "91$cleanNumber" else cleanNumber
                            val url = "https://wa.me/$formattedNumber?text=${Uri.encode(msg)}"
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            } catch (_: Exception) {}
                        },
                        onCallCustomer = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${booking.customerPhone}")))
                            } catch (_: Exception) {}
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun OrderAdminCard(
    order: Order,
    onUpdateStatus: (String) -> Unit,
    onOpenWhatsApp: () -> Unit,
    onCallCustomer: () -> Unit
) {
    val displayStatus = if (order.orderStatus == "pending_whatsapp") "Pending WhatsApp" else order.orderStatus.replaceFirstChar { it.uppercase() }
    val statusColor = when (order.orderStatus.lowercase()) {
        "confirmed" -> Color(0xFF1976D2)
        "ready for pickup" -> Color(0xFFE65100)
        "completed" -> Color(0xFF2E7D32)
        "cancelled" -> MaterialTheme.colorScheme.error
        else -> Color(0xFFF57F17)
    }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("admin_order_card_${order.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Order: ${order.id}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    val dateStr = order.createdAt?.toDate()?.let {
                        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(it)
                    } ?: "Just now"
                    Text(dateStr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(color = statusColor.copy(alpha = 0.15f), shape = RoundedCornerShape(16.dp)) {
                    Text(
                        text = displayStatus,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

            if (order.customerName.isNotBlank()) {
                Text("Customer: ${order.customerName}", fontWeight = FontWeight.SemiBold)
            }
            if (order.customerPhone.isNotBlank()) {
                Text("Mobile: ${order.customerPhone}", fontWeight = FontWeight.SemiBold)
            }
            if (order.deliveryAddress.isNotBlank()) {
                Text("Address: ${order.deliveryAddress}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(8.dp))
            order.items.forEach { item ->
                Text(
                    text = "• ${item.name} × ${item.quantity} @ ₹${"%.0f".format(item.price)} = ₹${"%.0f".format(item.price * item.quantity)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Total Amount: ₹${"%.0f".format(order.totalAmount)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2E7D32)
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onOpenWhatsApp,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("WhatsApp", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = onCallCustomer,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Call", style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text("Update Order Status:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(onClick = { onUpdateStatus("Confirmed") }, modifier = Modifier.weight(1f)) {
                    Text("Confirm", style = MaterialTheme.typography.labelSmall)
                }
                FilledTonalButton(
                    onClick = { onUpdateStatus("Ready for Pickup") },
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFFFF3E0), contentColor = Color(0xFFE65100)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Ready", style = MaterialTheme.typography.labelSmall)
                }
                FilledTonalButton(
                    onClick = { onUpdateStatus("Completed") },
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFE8F5E9), contentColor = Color(0xFF2E7D32)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Done", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = { onUpdateStatus("Cancelled") },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun BookingCard(
    booking: Booking,
    onUpdateStatus: (String) -> Unit,
    onOpenWhatsApp: () -> Unit,
    onCallCustomer: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("admin_booking_card_${booking.bookingId}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Booking ID and Status chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Booking: ${booking.bookingId}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    val dateStr = booking.createdAt?.toDate()?.let {
                        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(it)
                    } ?: "Just now"
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val statusColor = when (booking.status.lowercase()) {
                    "confirmed" -> Color(0xFF1976D2)
                    "ready for pickup" -> Color(0xFFE65100)
                    "completed" -> Color(0xFF2E7D32)
                    "cancelled" -> MaterialTheme.colorScheme.error
                    else -> Color(0xFFF57F17) // Pending
                }

                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = booking.status,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Customer details
            if (booking.customerName.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Customer: ${booking.customerName}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Phone: ${booking.customerPhone}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Product & Quantity & Total
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ShoppingBag, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${booking.productName}  •  Qty: ${booking.quantity}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CurrencyRupee, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF2E7D32))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Total: ₹${"%.2f".format(booking.totalAmount)}  (₹${booking.unitPrice} each)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Store Pickup: Veera Reddy Nagar, Boduppal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Customized Gift Specific Details (Feature 3)
            if (booking.isCustomized) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Brush, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Customized Gift (${booking.productName})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (booking.designId.isNotBlank()) {
                            Text("Design ID: ${booking.designId}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        }
                        if (booking.customText.isNotBlank()) {
                            Text("Text Imprint: \"${booking.customText}\"", style = MaterialTheme.typography.bodySmall)
                        }
                        if (booking.customImageUrl.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = booking.customImageUrl,
                                    contentDescription = "Customer Photo",
                                    modifier = Modifier.size(60.dp).clip(RoundedCornerShape(6.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                val context = LocalContext.current
                                TextButton(
                                    onClick = {
                                        try {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(booking.customImageUrl)))
                                        } catch (_: Exception) {}
                                    }
                                ) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Open Photo", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Customer contact action buttons
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onOpenWhatsApp,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("WhatsApp", style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = onCallCustomer,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Call", style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Status Update Actions
            Text("Update Booking Status:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!booking.status.equals("Confirmed", ignoreCase = true)) {
                    FilledTonalButton(
                        onClick = { onUpdateStatus("Confirmed") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Confirm", style = MaterialTheme.typography.labelSmall)
                    }
                }

                if (!booking.status.equals("Ready for Pickup", ignoreCase = true)) {
                    FilledTonalButton(
                        onClick = { onUpdateStatus("Ready for Pickup") },
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFFFF3E0), contentColor = Color(0xFFE65100)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Ready", style = MaterialTheme.typography.labelSmall)
                    }
                }

                if (!booking.status.equals("Completed", ignoreCase = true)) {
                    FilledTonalButton(
                        onClick = { onUpdateStatus("Completed") },
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFE8F5E9), contentColor = Color(0xFF2E7D32)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Complete", style = MaterialTheme.typography.labelSmall)
                    }
                }

                if (!booking.status.equals("Cancelled", ignoreCase = true)) {
                    OutlinedButton(
                        onClick = { onUpdateStatus("Cancelled") },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reject", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun AdminProducts(viewModel: AdminViewModel) {
    val state by viewModel.uiState.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var qrProduct by remember { mutableStateOf<Product?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { editingProduct = Product(); showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Product")
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (state.error != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = state.error!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
            items(state.products, key = { it.id }) { product ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = product.images.firstOrNull() ?: "",
                            contentDescription = product.name,
                            modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(product.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(
                                buildString {
                                    append("₹${"%.0f".format(product.sellingPrice)}")
                                    if (product.originalPrice > product.sellingPrice) {
                                        val pct = (((product.originalPrice - product.sellingPrice) / product.originalPrice) * 100).toInt()
                                        append(" (MRP ₹${"%.0f".format(product.originalPrice)}, $pct% OFF)")
                                    }
                                    append(" • Stock: ${product.stock}")
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (product.offerTag.isNotBlank()) {
                                Text(
                                    text = "Offer: ${product.offerTag}",
                                    color = MaterialTheme.colorScheme.tertiary,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                if (product.isEnabled) "Status: Available in Store" else "Status: Hidden",
                                color = if (product.isEnabled) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        IconButton(
                            onClick = { qrProduct = product },
                            modifier = Modifier.testTag("product_qr_btn_${product.id}")
                        ) {
                            Icon(Icons.Default.QrCode2, contentDescription = "Product QR Code", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { editingProduct = product; showDialog = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { viewModel.deleteProduct(product.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        val currentEditingProduct = editingProduct
        if (currentEditingProduct != null) {
            ProductEditDialog(
                product = currentEditingProduct,
                categories = state.categories,
                onDismiss = { showDialog = false },
                onSave = { viewModel.saveProduct(it); showDialog = false },
                onUploadImage = { uri -> viewModel.uploadProductImage(uri) }
            )
        }
    }

    if (qrProduct != null) {
        ProductQRCodeDialog(
            product = qrProduct!!,
            websiteUrl = state.settings.websiteUrl,
            shopName = state.settings.shopName,
            onDismiss = { qrProduct = null }
        )
    }
}

@Composable
fun ProductEditDialog(
    product: Product,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (Product) -> Unit,
    onUploadImage: (suspend (Uri) -> Result<String>)? = null
) {
    var name by remember { mutableStateOf(product.name) }
    var desc by remember { mutableStateOf(product.description) }
    var price by remember { mutableStateOf(if (product.sellingPrice > 0) product.sellingPrice.toString() else "") }
    var originalPrice by remember { mutableStateOf(if (product.originalPrice > 0) product.originalPrice.toString() else "") }
    var offerTag by remember { mutableStateOf(product.offerTag) }
    var stock by remember { mutableStateOf(product.stock.toString()) }
    var selectedCategory by remember { mutableStateOf(product.category) }
    var isFeatured by remember { mutableStateOf(product.isFeatured) }
    var isBestSeller by remember { mutableStateOf(product.isBestSeller) }
    var isEnabled by remember { mutableStateOf(product.isEnabled) }
    var imagesText by remember { mutableStateOf(product.images.joinToString(", ")) }

    var isUploadingImage by remember { mutableStateOf(false) }
    var uploadStatusMessage by remember { mutableStateOf<String?>(null) }
    var isUploadError by remember { mutableStateOf(false) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var selectedFileSize by remember { mutableStateOf<Long?>(null) }
    var selectedMimeType by remember { mutableStateOf<String?>(null) }
    var selectedPreviewUri by remember { mutableStateOf<Uri?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val handleImageUri: (Uri) -> Unit = { uri ->
        selectedPreviewUri = uri
        val details = PhotoPickerHelper.getFileDetails(context, uri)
        selectedFileName = details?.first
        selectedFileSize = details?.second
        selectedMimeType = details?.third

        if (onUploadImage != null) {
            isUploadingImage = true
            uploadStatusMessage = "Uploading ${selectedFileName ?: "photo"} ($selectedMimeType) to Firebase Storage..."
            isUploadError = false
            coroutineScope.launch {
                val uploadResult = onUploadImage(uri)
                isUploadingImage = false
                uploadResult.onSuccess { downloadUrl ->
                    uploadStatusMessage = "✓ ${selectedFileName ?: "Image"} uploaded successfully!"
                    isUploadError = false
                    val currentList = imagesText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    imagesText = (listOf(downloadUrl) + currentList).distinct().joinToString(", ")
                }.onFailure { err ->
                    isUploadError = true
                    val msg = err.localizedMessage.orEmpty()
                    val mightNeedBlaze = msg.contains("billing", ignoreCase = true) ||
                        msg.contains("blaze", ignoreCase = true) ||
                        msg.contains("upgrade", ignoreCase = true) ||
                        msg.contains("402") ||
                        msg.contains("bucket", ignoreCase = true)
                    uploadStatusMessage = if (mightNeedBlaze) {
                        "Firebase Storage upload failed ($msg). Note: Firebase Storage requires Blaze billing on some projects. You can paste a free public HTTPS Image URL below without enabling billing."
                    } else {
                        "Storage upload error: $msg. Or paste a direct HTTPS Image URL below."
                    }
                }
            }
        }
    }

    val handleMultipleImageUris: (List<Uri>) -> Unit = { uris ->
        if (uris.isNotEmpty()) {
            if (uris.size == 1) {
                handleImageUri(uris.first())
            } else {
                selectedPreviewUri = uris.first()
                val firstDetails = PhotoPickerHelper.getFileDetails(context, uris.first())
                selectedFileName = "${uris.size} photos selected (${firstDetails?.first})"
                selectedFileSize = uris.sumOf { u: Uri -> PhotoPickerHelper.getFileDetails(context, u)?.second ?: 0L }
                selectedMimeType = firstDetails?.third ?: "image/*"

                if (onUploadImage != null) {
                    isUploadingImage = true
                    uploadStatusMessage = "Uploading ${uris.size} photos to Firebase Storage..."
                    isUploadError = false
                    coroutineScope.launch {
                        val uploadedUrls = mutableListOf<String>()
                        var errors = 0
                        for ((idx, u) in uris.withIndex()) {
                            uploadStatusMessage = "Uploading (${idx + 1}/${uris.size})..."
                            val res = onUploadImage(u)
                            res.onSuccess { uploadedUrls.add(it) }
                               .onFailure { errors++ }
                        }
                        isUploadingImage = false
                        if (uploadedUrls.isNotEmpty()) {
                            val currentList = imagesText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            imagesText = (uploadedUrls + currentList).distinct().joinToString(", ")
                            uploadStatusMessage = if (errors == 0) {
                                "✓ ${uploadedUrls.size} images uploaded successfully!"
                            } else {
                                "✓ ${uploadedUrls.size} uploaded (${errors} failed)"
                            }
                            isUploadError = errors > 0
                        } else {
                            isUploadError = true
                            uploadStatusMessage = "Upload failed for all selected photos."
                        }
                    }
                }
            }
        }
    }

    val pickVisualMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                handleImageUri(uri)
            } else {
                uploadStatusMessage = "Selection cancelled."
            }
        }
    )

    val pickMultipleVisualMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                handleMultipleImageUris(uris)
            } else {
                uploadStatusMessage = "Selection cancelled."
            }
        }
    )

    val legacyGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val selectedUris = PhotoPickerHelper.extractUris(result.data)
                if (selectedUris.isNotEmpty()) {
                    handleMultipleImageUris(selectedUris)
                } else {
                    val singleUri = result.data?.data
                    if (singleUri != null) {
                        handleImageUri(singleUri)
                    }
                }
            } else if (result.resultCode == android.app.Activity.RESULT_CANCELED) {
                uploadStatusMessage = "Selection cancelled."
            }
        }
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (product.id.isEmpty()) "Add Product" else "Edit Product", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Product Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("product_name_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth().testTag("product_desc_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it },
                        label = { Text("Selling Price (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("product_price_input")
                    )
                    OutlinedTextField(
                        value = originalPrice,
                        onValueChange = { originalPrice = it },
                        label = { Text("MRP (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = offerTag,
                    onValueChange = { offerTag = it },
                    label = { Text("Offer Label (Optional, e.g. Festival Offer / 20% OFF)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("product_offer_input")
                )
                val mrpVal = originalPrice.toDoubleOrNull() ?: 0.0
                val sellVal = price.toDoubleOrNull() ?: 0.0
                if (mrpVal > sellVal && sellVal > 0.0) {
                    val autoDiscount = (((mrpVal - sellVal) / mrpVal) * 100).toInt()
                    Text(
                        text = "Auto-calculated Offer: $autoDiscount% OFF",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = stock,
                    onValueChange = { stock = it },
                    label = { Text("Available Stock Quantity") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("product_stock_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Product Image Upload Section (Requirement 1, 2, 3)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Product Images & Upload",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "Select photo from Phone Gallery or browse files (JPG, JPEG, PNG, WEBP, GIF)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Selection Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 1. Phone Gallery Direct Picker
                            Button(
                                onClick = {
                                    legacyGalleryLauncher.launch(PhotoPickerHelper.createGalleryPickIntent(allowMultiple = true))
                                },
                                enabled = !isUploadingImage,
                                modifier = Modifier.weight(1f).testTag("upload_product_gallery_btn"),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Gallery", style = MaterialTheme.typography.labelMedium)
                            }

                            // 2. System Photo Picker
                            FilledTonalButton(
                                onClick = {
                                    if (PhotoPickerHelper.isSystemPhotoPickerAvailable(context)) {
                                        pickMultipleVisualMediaLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    } else {
                                        legacyGalleryLauncher.launch(PhotoPickerHelper.createPhotoChooserIntent(context, "Select Product Photos", allowMultiple = true))
                                    }
                                },
                                enabled = !isUploadingImage,
                                modifier = Modifier.weight(1f).testTag("upload_product_system_picker_btn"),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Photo Picker", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 3. HTML File Input Component (<input type="file" accept="image/*"> via AndroidView/WebView)
                        // Directly executes file selection synchronously on user tap and triggers WebChromeClient.onShowFileChooser
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    "Native Browser File Input (<input type=\"file\" accept=\"image/*\">):",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                AndroidView(
                                    factory = { ctx ->
                                        WebView(ctx).apply {
                                            setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                                            settings.javaScriptEnabled = true
                                            settings.allowFileAccess = true
                                            settings.domStorageEnabled = true
                                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                            webViewClient = MainActivity.instance?.createWebViewClient() ?: object : WebViewClient() {
                                                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                                                    try {
                                                        (view?.parent as? ViewGroup)?.removeView(view)
                                                        view?.destroy()
                                                    } catch (_: Exception) {}
                                                    return true
                                                }
                                            }
                                            webChromeClient = MainActivity.instance?.createWebChromeClient(
                                                onUriSelected = { uri -> handleImageUri(uri) },
                                                onUrisSelected = { uris -> handleMultipleImageUris(uris) }
                                            ) ?: object : WebChromeClient() {
                                                override fun onShowFileChooser(
                                                    wv: WebView?,
                                                    filePathCallback: android.webkit.ValueCallback<Array<Uri>>?,
                                                    params: FileChooserParams?
                                                ): Boolean {
                                                    val chooser = PhotoPickerHelper.createPhotoChooserIntent(ctx, "Select Product Photo", allowMultiple = true)
                                                    legacyGalleryLauncher.launch(chooser)
                                                    return true
                                                }
                                            }
                                            val html = """
                                                <!DOCTYPE html>
                                                <html>
                                                <head>
                                                <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
                                                <style>
                                                  * { box-sizing: border-box; margin: 0; padding: 0; }
                                                  body { background: transparent; font-family: Roboto, -apple-system, sans-serif; padding: 2px; }
                                                  .btn-wrap { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
                                                  .file-btn {
                                                    display: inline-flex; align-items: center; justify-content: center;
                                                    background: #6750A4; color: #FFFFFF; font-weight: 600; font-size: 13px;
                                                    padding: 8px 14px; border-radius: 6px; border: none; cursor: pointer;
                                                  }
                                                  .file-btn:active { background: #4F378B; }
                                                  .tag { font-size: 11px; color: #49454F; }
                                                  #preview { max-width: 44px; max-height: 44px; border-radius: 4px; display: none; object-fit: cover; border: 1px solid #79747E; }
                                                </style>
                                                </head>
                                                <body>
                                                  <input type="file" id="nativeFileInput" accept="image/*" multiple style="display:none;" />
                                                  <div class="btn-wrap">
                                                    <button type="button" class="file-btn" onclick="document.getElementById('nativeFileInput').click();">
                                                      Browse Desktop / Phone Photos
                                                    </button>
                                                    <img id="preview" alt="Preview" />
                                                    <span id="nameTag" class="tag"></span>
                                                  </div>
                                                  <script>
                                                    const input = document.getElementById('nativeFileInput');
                                                    input.addEventListener('change', function(e) {
                                                      const files = e.target.files;
                                                      if (files && files.length > 0) {
                                                        const file = files[0];
                                                        try {
                                                          const url = URL.createObjectURL(file);
                                                          const preview = document.getElementById('preview');
                                                          preview.src = url;
                                                          preview.style.display = 'block';
                                                        } catch(err) {}
                                                        const info = file.name + ' (' + (file.type || 'image/*') + ', ' + Math.round(file.size/1024) + ' KB)' + (files.length > 1 ? ' +' + (files.length - 1) + ' more' : '');
                                                        document.getElementById('nameTag').innerText = info;
                                                      }
                                                    });
                                                  </script>
                                                </body>
                                                </html>
                                            """.trimIndent()
                                            loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                                        }
                                    },
                                    onRelease = { webView ->
                                        try {
                                            webView.stopLoading()
                                            webView.destroy()
                                        } catch (_: Exception) {}
                                    },
                                    modifier = Modifier.fillMaxWidth().height(54.dp)
                                )
                            }
                        }

                        if (selectedPreviewUri != null || selectedFileName != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (selectedPreviewUri != null) {
                                    AsyncImage(
                                        model = selectedPreviewUri,
                                        contentDescription = "Selected Photo Preview",
                                        modifier = Modifier.size(54.dp).clip(RoundedCornerShape(4.dp)).background(Color.LightGray),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Selected Photo Debug Info:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "File: ${selectedFileName ?: "photo"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "MIME: ${selectedMimeType ?: "image/*"} • Size: ${(selectedFileSize ?: 0) / 1024} KB",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        
                        if (isUploadingImage) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(uploadStatusMessage ?: "Uploading...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        } else if (uploadStatusMessage != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = uploadStatusMessage!!,
                                color = if (isUploadError) MaterialTheme.colorScheme.error else Color(0xFF2E7D32),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Display list of all product images with delete and replace options
                        val imgList = imagesText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        if (imgList.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Product Photos (${imgList.size}):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                imgList.forEachIndexed { index, imgUrl ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .padding(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = imgUrl,
                                            contentDescription = "Product Image $index",
                                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)).background(Color.White),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(if (index == 0) "Primary Photo" else "Photo ${index + 1}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                            Text(imgUrl, style = MaterialTheme.typography.bodySmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        IconButton(
                                            onClick = {
                                                val updated = imgList.toMutableList().apply { removeAt(index) }
                                                imagesText = updated.joinToString(", ")
                                            }
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete Photo", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = imagesText,
                    onValueChange = { imagesText = it },
                    label = { Text("Image URLs (comma-separated or uploaded)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text("Category:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat.name,
                            onClick = { selectedCategory = cat.name },
                            label = { Text(cat.name) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                AdminToggleRow("Active / Published in Store", isEnabled) { isEnabled = it }
                AdminToggleRow("Featured Product", isFeatured) { isFeatured = it }
                AdminToggleRow("Best Seller", isBestSeller) { isBestSeller = it }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val imgList = imagesText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    val sellVal = price.toDoubleOrNull() ?: 0.0
                    val mrpVal = originalPrice.toDoubleOrNull() ?: 0.0
                    val discountVal = if (mrpVal > sellVal && mrpVal > 0.0) {
                        (((mrpVal - sellVal) / mrpVal) * 100)
                    } else {
                        0.0
                    }
                    onSave(
                        product.copy(
                            name = name,
                            description = desc,
                            originalPrice = mrpVal,
                            sellingPrice = sellVal,
                            discount = discountVal,
                            stock = stock.toIntOrNull() ?: 0,
                            category = selectedCategory,
                            images = imgList,
                            offerTag = offerTag.trim(),
                            isFeatured = isFeatured,
                            isBestSeller = isBestSeller,
                            isEnabled = isEnabled,
                            customizable = false
                        )
                    )
                },
                modifier = Modifier.testTag("admin_save_product_btn")
            ) {
                Text("Save Product")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AdminToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun AdminCategories(viewModel: AdminViewModel) {
    val state by viewModel.uiState.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { editingCategory = Category(); showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Category")
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            items(state.categories, key = { it.id }) { category ->
                ListItem(
                    headlineContent = { Text(category.name, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Order: ${category.order}") },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { editingCategory = category; showDialog = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit")
                            }
                            IconButton(onClick = { viewModel.deleteCategory(category.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                )
                HorizontalDivider()
            }
        }
    }

    if (showDialog) {
        val currentEditingCategory = editingCategory
        if (currentEditingCategory != null) {
            CategoryEditDialog(
                category = currentEditingCategory,
                onDismiss = { showDialog = false },
                onSave = { viewModel.saveCategory(it); showDialog = false }
            )
        }
    }
}

@Composable
fun CategoryEditDialog(category: Category, onDismiss: () -> Unit, onSave: (Category) -> Unit) {
    var name by remember { mutableStateOf(category.name) }
    var order by remember { mutableStateOf(category.order.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (category.id.isEmpty()) "Add Category" else "Edit Category") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Category Name") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = order, onValueChange = { order = it }, label = { Text("Display Order") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { onSave(category.copy(name = name, order = order.toIntOrNull() ?: 0)) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AdminSettings(viewModel: AdminViewModel) {
    val state by viewModel.uiState.collectAsState()
    var shopName by remember { mutableStateOf(state.settings.shopName) }
    var address by remember { mutableStateOf(state.settings.shopAddress) }
    var contactNumber by remember { mutableStateOf(state.settings.contactNumber) }
    var whatsAppNumber by remember { mutableStateOf(state.settings.whatsAppNumber) }
    var timings by remember { mutableStateOf(state.settings.businessTimings) }
    var websiteUrl by remember { mutableStateOf(state.settings.websiteUrl) }
    var savedMessage by remember { mutableStateOf(false) }
    var urlSaveError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.settings) {
        shopName = state.settings.shopName
        address = state.settings.shopAddress
        contactNumber = state.settings.contactNumber
        whatsAppNumber = state.settings.whatsAppNumber
        timings = state.settings.businessTimings
        websiteUrl = state.settings.websiteUrl
    }

    val inputUrlValidation = remember(websiteUrl) {
        QRCodeHelper.validateCustomerStoreUrl(websiteUrl)
    }
    val savedUrlValidation = remember(state.settings.websiteUrl) {
        QRCodeHelper.validateCustomerStoreUrl(state.settings.websiteUrl)
    }
    var counterQrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(savedUrlValidation) {
        counterQrBitmap = if (savedUrlValidation is StoreUrlValidationResult.Valid) {
            QRCodeHelper.generateCustomerStoreQRCodeBitmap(savedUrlValidation.url, size = 650)
        } else {
            null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Store Configuration", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Store Pickup, WhatsApp, and Published Customer Store URL settings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = shopName,
            onValueChange = { shopName = it },
            label = { Text("Shop Name") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text("Store Pickup Address") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = whatsAppNumber,
            onValueChange = { whatsAppNumber = it },
            label = { Text("Store WhatsApp Number (for Booking Messages)") },
            placeholder = { Text("7036436305") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = contactNumber,
            onValueChange = { contactNumber = it },
            label = { Text("Store Contact Number (Phone Calls)") },
            placeholder = { Text("7036436305") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = timings,
            onValueChange = { timings = it },
            label = { Text("Store Timings") },
            placeholder = { Text("10:00 AM - 10:00 PM") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = websiteUrl,
            onValueChange = {
                websiteUrl = it
                savedMessage = false
                urlSaveError = null
            },
            label = { Text("Published Customer Store URL (HTTPS)") },
            placeholder = { Text("https://ggifts-store.web.app") },
            singleLine = true,
            isError = websiteUrl.isNotBlank() && inputUrlValidation !is StoreUrlValidationResult.Valid,
            trailingIcon = {
                if (websiteUrl.isNotBlank()) {
                    IconButton(onClick = { websiteUrl = ""; urlSaveError = null }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear URL")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("admin_website_url_input")
        )
        Spacer(modifier = Modifier.height(4.dp))
        when (inputUrlValidation) {
            is StoreUrlValidationResult.Valid -> {
                Text(
                    text = "✓ Valid published Customer Store URL. Save settings to generate a fresh QR code.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF2E7D32)
                )
            }
            is StoreUrlValidationResult.Unpublished -> {
                Text(
                    text = if (websiteUrl.isBlank()) {
                        QRCodeHelper.UNPUBLISHED_QR_MESSAGE
                    } else {
                        "Temporary AI Studio preview or local URL blocked. ${QRCodeHelper.UNPUBLISHED_QR_MESSAGE}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFE65100)
                )
            }
            is StoreUrlValidationResult.Invalid -> {
                Text(
                    text = inputUrlValidation.message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                val trimmedUrl = websiteUrl.trim()
                if (trimmedUrl.isNotEmpty() && inputUrlValidation !is StoreUrlValidationResult.Valid) {
                    savedMessage = false
                    urlSaveError = when (inputUrlValidation) {
                        is StoreUrlValidationResult.Unpublished -> inputUrlValidation.message
                        is StoreUrlValidationResult.Invalid -> inputUrlValidation.message
                        else -> QRCodeHelper.UNPUBLISHED_QR_MESSAGE
                    }
                } else {
                    val urlToSave = if (inputUrlValidation is StoreUrlValidationResult.Valid) {
                        inputUrlValidation.url
                    } else {
                        ""
                    }
                    urlSaveError = null
                    viewModel.saveSettings(
                        state.settings.copy(
                            shopName = shopName,
                            shopAddress = address,
                            contactNumber = contactNumber,
                            whatsAppNumber = whatsAppNumber,
                            businessTimings = timings,
                            websiteUrl = urlToSave,
                            deliveryCharges = 0.0,
                            pickupAvailable = true
                        )
                    )
                    savedMessage = true
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("admin_save_settings_btn")
        ) {
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save Store Settings")
        }

        if (urlSaveError != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = urlSaveError!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("admin_url_save_error")
            )
        }

        if (savedMessage) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "✓ Settings saved successfully!",
                color = Color(0xFF2E7D32),
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Shop QR Code Section for Store Counter (Validated Published Customer Store URL only)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("admin_counter_qr_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.QrCode, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Permanent Customer Store QR Code", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Print and place this QR code at your store cash counter or entrance. Customers scan with any Android or iPhone camera to open the Customer Store directly without login.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

                val context = LocalContext.current
                when (savedUrlValidation) {
                    is StoreUrlValidationResult.Valid -> {
                        val validUrl = savedUrlValidation.url
                        Box(
                            modifier = Modifier
                                .size(190.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (counterQrBitmap != null) {
                                Image(
                                    bitmap = counterQrBitmap!!.asImageBitmap(),
                                    contentDescription = "Customer Store Counter QR Code",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("admin_counter_qr_image")
                                )
                            } else {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Published Store URL: $validUrl",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    counterQrBitmap?.let { bmp ->
                                        QRCodeHelper.saveBitmapToGallery(context, bmp, "GGifts_Counter_QR")
                                    }
                                },
                                enabled = counterQrBitmap != null,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Download QR")
                            }
                            FilledTonalButton(
                                onClick = {
                                    counterQrBitmap?.let { bmp ->
                                        QRCodeHelper.printQRCode(
                                            context = context,
                                            bitmap = bmp,
                                            title = state.settings.shopName,
                                            subtitle = "Permanent Customer Store QR Code",
                                            url = validUrl
                                        )
                                    }
                                },
                                enabled = counterQrBitmap != null,
                                modifier = Modifier.weight(1f).testTag("admin_print_qr_button")
                            ) {
                                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Print QR")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    counterQrBitmap?.let { bmp ->
                                        QRCodeHelper.shareQRCode(
                                            context = context,
                                            bitmap = bmp,
                                            title = state.settings.shopName,
                                            message = "Scan to shop at G Gifts Store, Boduppal: $validUrl"
                                        )
                                    }
                                },
                                enabled = counterQrBitmap != null,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share QR")
                            }
                            TextButton(
                                onClick = { QRCodeHelper.openStoreUrlInBrowser(context, validUrl) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Open Link")
                            }
                        }
                    }

                    is StoreUrlValidationResult.Unpublished -> {
                        Surface(
                            color = Color(0xFFFFF3E0),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFFE65100).copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.WarningAmber, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = savedUrlValidation.message,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE65100),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.testTag("admin_qr_unpublished_message")
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Temporary AI Studio preview URLs are not used. Enter your published public HTTPS Customer Store URL above and tap Save Store Settings to generate a fresh QR code.",
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
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = savedUrlValidation.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Free Spark Plan Hosting & Billing Transparency Card
        Card(
            modifier = Modifier.fillMaxWidth().testTag("admin_spark_plan_notice"),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Free Hosting & Spark Plan Info",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B5E20)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "• 100% Free (Spark Plan): Firebase Hosting static Customer Store (https://gen-lang-client-0989190352.web.app), Cloud Firestore catalog/bookings, Google Admin Sign-In, WhatsApp wa.me links, and QR codes.\n• Note on Paid Plans: Newer Firebase projects require upgrading to the Blaze plan before uploading binary files to Firebase Storage. Billing is NEVER enabled automatically — to stay strictly on the Free Spark plan, paste free HTTPS image links directly in Product Edit.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF1B5E20)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Diagnostic Photo Picker & Browser File Input Test Section (Requirement 15)
        AdminDiagnosticsCard()
    }
}

@Composable
fun AdminDiagnosticsCard() {
    val context = LocalContext.current
    val isCloud = remember { PreviewEnvironmentHelper.isCloudPreviewEnvironment() }
    val isGalleryEmpty = remember { PreviewEnvironmentHelper.isDeviceGalleryEmpty(context) }
    var diagnosticLog by remember { mutableStateOf("Ready to test. Tap any button below to verify image selection.") }
    var lastPreviewUri by remember { mutableStateOf<Uri?>(null) }

    val testPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
        onResult = { uris: List<Uri> ->
            if (uris.isNotEmpty()) {
                lastPreviewUri = uris.first()
                val detailsList: List<Triple<String, Long, String>?> = uris.map { u: Uri -> PhotoPickerHelper.getFileDetails(context, u) }
                val summary = detailsList.mapIndexed { idx, d ->
                    val name = d?.first ?: "photo"
                    val mime = d?.third ?: "image/*"
                    val sizeKb = (d?.second ?: 0L) / 1024
                    "${idx + 1}. $name ($mime, $sizeKb KB)"
                }.joinToString("\n")
                diagnosticLog = "✓ System Photo Picker SUCCESS: ${uris.size} photo(s) selected:\n$summary"
            } else {
                diagnosticLog = "System Photo Picker was closed without selecting files."
            }
        }
    )

    val testGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val uris = PhotoPickerHelper.extractUris(result.data)
                if (uris.isNotEmpty()) {
                    lastPreviewUri = uris.first()
                    val detailsList: List<Triple<String, Long, String>?> = uris.map { u: Uri -> PhotoPickerHelper.getFileDetails(context, u) }
                    val summary = detailsList.mapIndexed { idx, d ->
                        val name = d?.first ?: "photo"
                        val mime = d?.third ?: "image/*"
                        val sizeKb = (d?.second ?: 0L) / 1024
                        "${idx + 1}. $name ($mime, $sizeKb KB)"
                    }.joinToString("\n")
                    diagnosticLog = "✓ Android Gallery SUCCESS: ${uris.size} photo(s) selected:\n$summary"
                } else {
                    val single = result.data?.data
                    if (single != null) {
                        lastPreviewUri = single
                        val d = PhotoPickerHelper.getFileDetails(context, single)
                        val name = d?.first ?: "photo"
                        val mime = d?.third ?: "image/*"
                        val sizeKb = (d?.second ?: 0L) / 1024
                        diagnosticLog = "✓ Android Gallery SUCCESS: 1 photo selected:\n1. $name ($mime, $sizeKb KB)"
                    } else {
                        diagnosticLog = "Gallery intent finished with RESULT_OK but no URI was returned."
                    }
                }
            } else {
                diagnosticLog = "Gallery selection was cancelled."
            }
        }
    )

    Card(
        modifier = Modifier.fillMaxWidth().testTag("admin_photo_diagnostics_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Photo Picker & Browser Diagnostics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Run instant diagnostics to test Android system photo picker, native gallery intent, and browser HTML file input (<input type='file' accept='image/*'>).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                color = if (isCloud) Color(0xFFFFF3E0) else Color(0xFFE8F5E9),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = if (isCloud) "Environment: Cloud Streaming Preview (Android Emulator)" else "Environment: Physical Android Device / Installed APK",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isCloud) Color(0xFFE65100) else Color(0xFF2E7D32)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isCloud) {
                            if (isGalleryEmpty) {
                                "Notice: The virtual emulator disk currently contains 0 photos in its internal media store. On an Android phone with real photos, Gallery/Albums show all photos immediately. You can test native HTML file input below with Desktop files."
                            } else {
                                "Virtual emulator media store is indexed and available."
                            }
                        } else {
                            "All device albums and Google Photos are directly accessible."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Diagnostic Action Buttons
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        testGalleryLauncher.launch(PhotoPickerHelper.createGalleryPickIntent(allowMultiple = true))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Test Gallery", style = MaterialTheme.typography.labelMedium)
                }

                FilledTonalButton(
                    onClick = {
                        if (PhotoPickerHelper.isSystemPhotoPickerAvailable(context)) {
                            testPhotoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        } else {
                            testGalleryLauncher.launch(PhotoPickerHelper.createPhotoChooserIntent(context, "Select Photos", allowMultiple = true))
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Test Picker", style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Test HTML File Input (<input type="file" accept="image/*">)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        "Test Native HTML File Input (<input type=\"file\" accept=\"image/*\">):",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                                settings.javaScriptEnabled = true
                                settings.allowFileAccess = true
                                settings.domStorageEnabled = true
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                webViewClient = MainActivity.instance?.createWebViewClient {
                                    diagnosticLog = "WebView renderer recovered cleanly."
                                } ?: object : WebViewClient() {
                                    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                                        try {
                                            (view?.parent as? ViewGroup)?.removeView(view)
                                            view?.destroy()
                                        } catch (_: Exception) {}
                                        return true
                                    }
                                }
                                webChromeClient = MainActivity.instance?.createWebChromeClient(
                                    onUriSelected = { uri ->
                                        lastPreviewUri = uri
                                        val d = PhotoPickerHelper.getFileDetails(ctx, uri)
                                        diagnosticLog = "✓ Native WebChromeClient callback invoked with URI:\n${d?.first} (${d?.third}, ${(d?.second ?: 0) / 1024} KB)\nURI: $uri"
                                    },
                                    onUrisSelected = { uris: List<Uri> ->
                                        if (uris.isNotEmpty()) {
                                            lastPreviewUri = uris.first()
                                            val detailsList: List<Triple<String, Long, String>?> = uris.map { u: Uri -> PhotoPickerHelper.getFileDetails(ctx, u) }
                                            diagnosticLog = "✓ WebChromeClient multi-file callback invoked (${uris.size} files):\n" +
                                                    detailsList.mapIndexed { idx, d ->
                                                        val name = d?.first ?: "photo"
                                                        val mime = d?.third ?: "image/*"
                                                        val sizeKb = (d?.second ?: 0L) / 1024
                                                        "${idx + 1}. $name ($mime, $sizeKb KB)"
                                                    }.joinToString("\n")
                                        }
                                    }
                                ) ?: object : WebChromeClient() {
                                    override fun onShowFileChooser(
                                        wv: WebView?,
                                        filePathCallback: android.webkit.ValueCallback<Array<Uri>>?,
                                        params: FileChooserParams?
                                    ): Boolean {
                                        val chooser = PhotoPickerHelper.createPhotoChooserIntent(ctx, "Test File Chooser", allowMultiple = true)
                                        testGalleryLauncher.launch(chooser)
                                        return true
                                    }
                                }
                                val html = """
                                    <!DOCTYPE html>
                                    <html>
                                    <head>
                                    <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
                                    <style>
                                      * { box-sizing: border-box; margin: 0; padding: 0; }
                                      body { background: transparent; font-family: Roboto, -apple-system, sans-serif; padding: 2px; }
                                      .btn {
                                        background: #006A6A; color: #fff; font-weight: 600; font-size: 13px;
                                        padding: 8px 14px; border-radius: 6px; border: none; cursor: pointer;
                                      }
                                      .btn:active { background: #004F4F; }
                                      .tag { font-size: 11px; color: #49454F; margin-left: 8px; }
                                      #prev { max-width: 44px; max-height: 44px; border-radius: 4px; display: none; object-fit: cover; }
                                    </style>
                                    </head>
                                    <body>
                                      <input type="file" id="diagFileInput" accept="image/*" multiple style="display:none;" />
                                      <div style="display:flex; align-items:center;">
                                        <button type="button" class="btn" onclick="document.getElementById('diagFileInput').click();">
                                          Test Browser File Input
                                        </button>
                                        <img id="prev" style="margin-left:8px;" />
                                        <span id="diagInfo" class="tag"></span>
                                      </div>
                                      <script>
                                        const inp = document.getElementById('diagFileInput');
                                        inp.addEventListener('change', function(e) {
                                          const files = e.target.files;
                                          if (files && files.length > 0) {
                                            const f = files[0];
                                            try {
                                              const u = URL.createObjectURL(f);
                                              const p = document.getElementById('prev');
                                              p.src = u;
                                              p.style.display = 'inline-block';
                                            } catch(err) {}
                                            document.getElementById('diagInfo').innerText = f.name + ' (' + (f.type || 'image/*') + ', ' + Math.round(f.size/1024) + ' KB)';
                                          }
                                        });
                                      </script>
                                    </body>
                                    </html>
                                """.trimIndent()
                                loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                            }
                        },
                        onRelease = { webView ->
                            try {
                                webView.stopLoading()
                                webView.destroy()
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Diagnostic Results Container
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                    if (lastPreviewUri != null) {
                        AsyncImage(
                            model = lastPreviewUri,
                            contentDescription = "Diagnostic Preview",
                            modifier = Modifier.size(50.dp).clip(RoundedCornerShape(4.dp)).background(Color.LightGray),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Diagnostic Output Log:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = diagnosticLog,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

