package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.GRepository
import com.example.ui.admin.AdminMainScreen
import com.example.ui.customer.CustomerMainScreen
import com.example.viewmodel.AuthState
import com.example.viewmodel.AuthViewModel
import com.example.viewmodel.ViewModelFactory

@Composable
fun MainApp() {
    val context = LocalContext.current
    val repository = remember { GRepository(context) }
    val authViewModel: AuthViewModel = viewModel(factory = ViewModelFactory(repository))
    val authState by authViewModel.authState.collectAsState()
    val adminAccessError by authViewModel.adminAccessError.collectAsState()

    var adminPreviewAsCustomer by remember { mutableStateOf(false) }
    var showAdminAuthScreen by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (val state = authState) {
            is AuthState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Loading G Gifts Store...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            is AuthState.Authenticated -> {
                if (state.role == "admin") {
                    if (adminPreviewAsCustomer) {
                        BackHandler { adminPreviewAsCustomer = false }
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Top banner for admin preview mode
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "👑 Admin Preview Mode (Store Pickup)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    TextButton(
                                        onClick = { adminPreviewAsCustomer = false }
                                    ) {
                                        Text("Open Admin Panel")
                                    }
                                }
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                CustomerMainScreen(
                                    repository = repository,
                                    onSignOut = { authViewModel.signOut(context) },
                                    onOpenAdminPanel = { adminPreviewAsCustomer = false }
                                )
                            }
                        }
                    } else {
                        AdminMainScreen(
                            repository = repository,
                            onSignOut = {
                                showAdminAuthScreen = false
                                authViewModel.signOut(context)
                            },
                            onViewCustomerStore = { adminPreviewAsCustomer = true }
                        )
                    }
                } else {
                    if (showAdminAuthScreen) {
                        BackHandler {
                            authViewModel.clearAdminError()
                            showAdminAuthScreen = false
                        }
                        val isAdminVerifying by authViewModel.isAdminVerifying.collectAsState()
                        AuthScreen(
                            onSignIn = { authViewModel.signIn(context) },
                            onAdminAccessClick = { authViewModel.handleStoreAdministratorAccess(context) },
                            isAdminVerifying = isAdminVerifying,
                            ownerEmail = authViewModel.authorizedOwnerEmail,
                            error = adminAccessError,
                            onDismiss = {
                                authViewModel.clearAdminError()
                                showAdminAuthScreen = false
                            }
                        )
                    } else {
                        CustomerMainScreen(
                            repository = repository,
                            onSignOut = { authViewModel.signOut(context) },
                            onOpenAdminPanel = {
                                authViewModel.clearAdminError()
                                showAdminAuthScreen = true
                            }
                        )
                    }
                }
            }
            is AuthState.InitialSetupNeeded -> {
                if (showAdminAuthScreen) {
                    BackHandler {
                        authViewModel.clearAdminError()
                        showAdminAuthScreen = false
                    }
                    val isSettingUp by authViewModel.isSettingUpAdmin.collectAsState()
                    val setupError by authViewModel.setupError.collectAsState()
                    val setupSuccess by authViewModel.setupSuccess.collectAsState()

                    AdminSetupScreen(
                        ownerEmail = state.ownerEmail,
                        isSettingUp = isSettingUp,
                        setupError = setupError,
                        setupSuccess = setupSuccess,
                        onSetup = { authViewModel.setUpInitialAdmin() },
                        onSignOut = {
                            authViewModel.signOut(context)
                            showAdminAuthScreen = false
                        }
                    )
                } else {
                    CustomerMainScreen(
                        repository = repository,
                        onSignOut = { authViewModel.signOut(context) },
                        onOpenAdminPanel = { showAdminAuthScreen = true }
                    )
                }
            }
            is AuthState.Unauthenticated, is AuthState.Error -> {
                if (showAdminAuthScreen) {
                    BackHandler {
                        authViewModel.clearAdminError()
                        showAdminAuthScreen = false
                    }
                    val isAdminVerifying by authViewModel.isAdminVerifying.collectAsState()
                    AuthScreen(
                        onSignIn = { authViewModel.signIn(context) },
                        onAdminAccessClick = { authViewModel.handleStoreAdministratorAccess(context) },
                        isAdminVerifying = isAdminVerifying,
                        ownerEmail = authViewModel.authorizedOwnerEmail,
                        error = adminAccessError ?: (state as? AuthState.Error)?.message,
                        onDismiss = {
                            authViewModel.clearAdminError()
                            showAdminAuthScreen = false
                        }
                    )
                } else {
                    // DIRECT PRODUCT DISPLAY (Open app directly without mandatory login)
                    CustomerMainScreen(
                        repository = repository,
                        onSignOut = { authViewModel.signOut(context) },
                        onOpenAdminPanel = {
                            authViewModel.clearAdminError()
                            showAdminAuthScreen = true
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AuthScreen(
    onSignIn: () -> Unit,
    onAdminAccessClick: () -> Unit,
    isAdminVerifying: Boolean = false,
    ownerEmail: String,
    error: String? = null,
    onDismiss: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (onDismiss != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                TextButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Back to Shopping Store")
                }
            }
        }

        // App Icon / Logo avatar
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.CardGiftcard,
                contentDescription = "G Gifts Logo",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "G Gifts Shopping App",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Toys • Sports • Decoration • Stationery",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Veera Reddy Nagar, Boduppal, Hyderabad",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(36.dp))

        // Button 1: Sign in with Google (Customer login)
        Button(
            onClick = onSignIn,
            enabled = !isAdminVerifying,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("google_sign_in_button"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
            Spacer(modifier = Modifier.width(10.dp))
            Text("Sign in with Google", fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Button 2: Store Administrator Access (Admin Authentication flow)
        OutlinedButton(
            onClick = onAdminAccessClick,
            enabled = !isAdminVerifying,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("store_admin_access_button"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                contentColor = MaterialTheme.colorScheme.primary
            ),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        ) {
            if (isAdminVerifying) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Verifying Store Administrator...", fontWeight = FontWeight.SemiBold)
            } else {
                Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("Store Administrator Access", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Owner notice card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Authorized Super Admin: $ownerEmail",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Shop owner: Click 'Store Administrator Access' with Google account $ownerEmail to enter the Admin Dashboard. Unauthorized accounts will receive an Access Denied message.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (error != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_error_card"),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
fun AdminSetupScreen(
    ownerEmail: String,
    isSettingUp: Boolean,
    setupError: String?,
    setupSuccess: String?,
    onSetup: () -> Unit,
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.AdminPanelSettings,
                contentDescription = "Admin Setup Icon",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Initial Admin Setup",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Owner verified badge
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Authorized Owner: $ownerEmail",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "You are authenticated as the authorized store owner. Click below to complete initial administrative setup for G Gifts.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(28.dp))

        if (setupSuccess != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFE8F5E9)
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF2E7D32)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "$setupSuccess! Opening Admin Dashboard...",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B5E20)
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        if (setupError != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = setupError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        Button(
            onClick = onSetup,
            enabled = !isSettingUp && setupSuccess == null,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isSettingUp) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("Initializing Admin...")
            } else {
                Icon(Icons.Default.VerifiedUser, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Complete Admin Setup", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
            onClick = onSignOut,
            enabled = !isSettingUp
        ) {
            Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Sign Out")
        }
    }
}
