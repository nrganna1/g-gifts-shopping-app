package com.example.viewmodel

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.*
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.R
import com.example.data.GRepository
import com.example.model.UserProfile
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed interface AuthState {
    data object Loading : AuthState
    data class Authenticated(val role: String, val userEmail: String = "") : AuthState
    data class InitialSetupNeeded(val ownerEmail: String) : AuthState
    data object Unauthenticated : AuthState
    data class Error(val message: String) : AuthState
}

class AuthViewModel(private val repository: GRepository) : ViewModel() {
    companion object {
        const val AUTHORITATIVE_ADMIN_EMAIL = "nrganna1@gmail.com"
        const val REMOVED_OLD_ADMIN_EMAIL = "nrganna2015@gmail.com"
        private const val TAG = "AuthViewModel"
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState = _authState.asStateFlow()

    private val auth = Firebase.auth

    // Admin setup in-progress and feedback states
    private val _isSettingUpAdmin = MutableStateFlow(false)
    val isSettingUpAdmin = _isSettingUpAdmin.asStateFlow()

    private val _setupError = MutableStateFlow<String?>(null)
    val setupError = _setupError.asStateFlow()

    private val _setupSuccess = MutableStateFlow<String?>(null)
    val setupSuccess = _setupSuccess.asStateFlow()

    private val _isAdminVerifying = MutableStateFlow(false)
    val isAdminVerifying = _isAdminVerifying.asStateFlow()

    private val _adminAccessError = MutableStateFlow<String?>(null)
    val adminAccessError = _adminAccessError.asStateFlow()

    val authorizedOwnerEmail: String = getAuthorizedEmail()

    init {
        checkCurrentSession()
    }

    private fun getAuthorizedEmail(): String {
        val configEmail = try {
            BuildConfig.ADMIN_EMAIL.trim().lowercase()
        } catch (_: Exception) {
            ""
        }
        return if (configEmail.isNotBlank() && configEmail != REMOVED_OLD_ADMIN_EMAIL) {
            configEmail
        } else {
            AUTHORITATIVE_ADMIN_EMAIL
        }
    }

    fun isSuperAdminEmail(email: String?): Boolean {
        if (email.isNullOrBlank()) return false
        val clean = email.trim().lowercase()
        if (clean == REMOVED_OLD_ADMIN_EMAIL) return false
        return clean == authorizedOwnerEmail
    }

    private fun isVerifiedInFirebaseAuth(user: FirebaseUser): Boolean {
        return user.isEmailVerified || user.providerData.any { it.providerId == "google.com" }
    }

    fun clearAdminError() {
        _adminAccessError.value = null
        if (_authState.value is AuthState.Error) {
            _authState.value = AuthState.Unauthenticated
        }
    }

    fun checkCurrentSession() {
        _authState.value = AuthState.Loading
        val user = auth.currentUser
        if (user != null) {
            val userEmail = user.email?.trim()?.lowercase() ?: ""
            Log.d(TAG, "Active session found: uid=${user.uid}, email=$userEmail")
            evaluateCurrentFirebaseUser(user)
        } else {
            Log.d(TAG, "No active session found")
            _authState.value = AuthState.Unauthenticated
        }
    }

    private fun evaluateCurrentFirebaseUser(user: FirebaseUser) {
        viewModelScope.launch {
            try {
                // Verify account is still registered and active in Firebase Authentication
                try {
                    user.reload().await()
                } catch (invalid: FirebaseAuthInvalidUserException) {
                    Log.w(TAG, "Firebase Auth user no longer valid/registered; signing out", invalid)
                    auth.signOut()
                    _authState.value = AuthState.Unauthenticated
                    return@launch
                } catch (e: Exception) {
                    // Preserve offline resilience if network is temporarily unavailable
                    Log.w(TAG, "Could not reload Firebase Auth user (possibly offline): ${e.message}")
                }

                val refreshed = auth.currentUser ?: run {
                    _authState.value = AuthState.Unauthenticated
                    return@launch
                }
                val email = refreshed.email?.trim()?.lowercase() ?: ""
                val displayName = refreshed.displayName ?: ""
                val isSuperAdmin = (repository.isSuperAdminEmail(email) || isSuperAdminEmail(email)) &&
                    isVerifiedInFirebaseAuth(refreshed)

                Log.d(TAG, "Evaluating role for email=$email, isSuperAdmin=$isSuperAdmin")

                if (isSuperAdmin) {
                    try {
                        refreshed.getIdToken(false).await()
                        repository.ensureSuperAdmin(email, refreshed.uid, displayName)
                    } catch (e: Exception) {
                        Log.w(TAG, "Non-fatal super admin Firestore sync warning: ${e.message}")
                    }
                    _adminAccessError.value = null
                    _authState.value = AuthState.Authenticated(role = "admin", userEmail = email)
                    return@launch
                }

                // Non-admin (including deprecated old admin email nrganna2015@gmail.com): strictly customer role
                try {
                    repository.saveUserProfile(
                        UserProfile(
                            id = refreshed.uid,
                            displayName = displayName,
                            email = email,
                            role = "customer"
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Customer profile sync warning for $email: ${e.message}")
                }
                _authState.value = AuthState.Authenticated(role = "customer", userEmail = email)
            } catch (e: Exception) {
                Log.e(TAG, "Error in evaluateCurrentFirebaseUser", e)
                _authState.value = AuthState.Error("Error verifying Firebase account: ${e.localizedMessage}")
            }
        }
    }

    fun signIn(context: Context) {
        viewModelScope.launch {
            _adminAccessError.value = null
            _authState.value = AuthState.Loading
            val credentialManager = CredentialManager.create(context)
            val clientId = try {
                context.getString(R.string.default_web_client_id)
            } catch (_: Exception) {
                _authState.value = AuthState.Error("Google Sign-In configuration missing: default_web_client_id not found")
                return@launch
            }

            try {
                // Allow picking another Google account cleanly if a previous account was cached
                if (auth.currentUser != null) {
                    auth.signOut()
                    try {
                        credentialManager.clearCredentialState(ClearCredentialStateRequest())
                    } catch (_: Exception) {}
                }

                val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
                val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

                val result = credentialManager.getCredential(context as Activity, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    val authResult = auth.signInWithCredential(authCredential).await()
                    val user = authResult.user
                    if (user != null) {
                        Log.d(TAG, "Sign in successful for ${user.email}")
                        evaluateCurrentFirebaseUser(user)
                    } else {
                        _authState.value = AuthState.Error("User record was empty after sign-in.")
                    }
                } else {
                    _authState.value = AuthState.Error("Unexpected Google credential type.")
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w("Auth", "Sign-in cancelled by user")
                _authState.value = AuthState.Unauthenticated
            } catch (e: Exception) {
                Log.e("Auth", "Sign-in error", e)
                _authState.value = AuthState.Error(e.localizedMessage ?: "Google Sign-In failed")
            }
        }
    }

    fun signOut(context: Context) {
        viewModelScope.launch {
            _adminAccessError.value = null
            try {
                auth.signOut()
                val credentialManager = CredentialManager.create(context)
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.e("Auth", "Sign-out error", e)
            } finally {
                _authState.value = AuthState.Unauthenticated
            }
        }
    }

    fun handleStoreAdministratorAccess(context: Context) {
        viewModelScope.launch {
            _isAdminVerifying.value = true
            _adminAccessError.value = null
            try {
                val existingUser = auth.currentUser
                if (existingUser != null) {
                    val existingEmail = existingUser.email?.trim()?.lowercase() ?: ""
                    if (isSuperAdminEmail(existingEmail) && isVerifiedInFirebaseAuth(existingUser)) {
                        var stillValidInAuth = true
                        try {
                            existingUser.reload().await()
                            existingUser.getIdToken(true).await()
                        } catch (invalid: FirebaseAuthInvalidUserException) {
                            Log.w(TAG, "Cached admin session no longer valid in Firebase Auth", invalid)
                            stillValidInAuth = false
                        } catch (e: Exception) {
                            Log.w(TAG, "Token refresh warning for active admin session: ${e.message}")
                        }
                        if (stillValidInAuth && auth.currentUser != null) {
                            verifyAdminAndNavigate(auth.currentUser!!)
                            return@launch
                        }
                    }
                    // Active session is a customer or deprecated old admin (e.g. nrganna2015@gmail.com):
                    // sign out and clear credential cache so the owner can select nrganna1@gmail.com.
                    Log.d(TAG, "Signing out non-admin session ($existingEmail) before Admin Google Sign-In.")
                    auth.signOut()
                    try {
                        CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
                    } catch (_: Exception) {}
                }

                Log.d(TAG, "Launching Google Sign-In for Store Administrator ($authorizedOwnerEmail).")
                val credentialManager = CredentialManager.create(context)
                val clientId = try {
                    context.getString(R.string.default_web_client_id)
                } catch (_: Exception) {
                    val msg = "Google Sign-In configuration missing: default_web_client_id not found"
                    _adminAccessError.value = msg
                    _authState.value = AuthState.Error(msg)
                    return@launch
                }

                val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
                val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

                val result = credentialManager.getCredential(context as Activity, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    val authResult = auth.signInWithCredential(authCredential).await()
                    val user = authResult.user
                    if (user != null) {
                        Log.d(TAG, "Admin Google Sign-in completed for ${user.email}. Verifying authorization...")
                        verifyAdminAndNavigate(user)
                    } else {
                        val msg = "User record was empty after Firebase Authentication sign-in."
                        _adminAccessError.value = msg
                        _authState.value = AuthState.Error(msg)
                    }
                } else {
                    val msg = "Unexpected Google credential type."
                    _adminAccessError.value = msg
                    _authState.value = AuthState.Error(msg)
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w("Auth", "Admin sign-in cancelled by user")
                val msg = "Google Sign-In cancelled. Please sign in with $authorizedOwnerEmail to access Admin."
                _adminAccessError.value = msg
            } catch (e: Exception) {
                Log.e("Auth", "Error in handleStoreAdministratorAccess", e)
                val msg = e.localizedMessage ?: "Failed to verify Store Administrator"
                _adminAccessError.value = msg
                _authState.value = AuthState.Error(msg)
            } finally {
                _isAdminVerifying.value = false
            }
        }
    }

    private suspend fun verifyAdminAndNavigate(user: FirebaseUser) {
        try {
            user.reload().await()
            user.getIdToken(true).await()
        } catch (invalid: FirebaseAuthInvalidUserException) {
            auth.signOut()
            val msg = "Access Denied: Account is not registered or disabled in Firebase Authentication."
            _adminAccessError.value = msg
            _authState.value = AuthState.Error(msg)
            return
        } catch (e: Exception) {
            Log.w(TAG, "Auth reload/token check warning: ${e.message}")
        }

        val current = auth.currentUser
        if (current == null || current.uid.isBlank()) {
            val msg = "Access Denied: Firebase Authentication session could not be verified."
            _adminAccessError.value = msg
            _authState.value = AuthState.Error(msg)
            return
        }

        val email = current.email?.trim()?.lowercase() ?: ""
        val displayName = current.displayName ?: ""
        val verified = isVerifiedInFirebaseAuth(current)
        val isSingleOwner = isSuperAdminEmail(email) && repository.isSuperAdminEmail(email) && verified

        if (isSingleOwner) {
            Log.d(TAG, "Single authorized Admin verified in Firebase Auth ($email). Syncing backend...")
            try {
                repository.ensureSuperAdmin(email, current.uid, displayName)
            } catch (e: Exception) {
                Log.w(TAG, "Admin metadata sync warning (Firebase Auth verified): ${e.message}")
            }
            _adminAccessError.value = null
            _authState.value = AuthState.Authenticated(role = "admin", userEmail = email)
        } else {
            Log.w(TAG, "Unauthorized admin access attempt by $email (verified=$verified)")
            // Downgrade any old admin profile to customer and sign out so owner can retry with nrganna1@gmail.com
            try {
                repository.saveUserProfile(
                    UserProfile(
                        id = current.uid,
                        displayName = displayName,
                        email = email,
                        role = "customer"
                    )
                )
            } catch (_: Exception) {}
            auth.signOut()
            val msg = "Access Denied: $email is not authorized. Only $authorizedOwnerEmail can access the Admin Dashboard."
            _adminAccessError.value = msg
            _authState.value = AuthState.Error(msg)
        }
    }

    fun setUpInitialAdmin() {
        val user = auth.currentUser
        if (user == null) {
            _setupError.value = "Please sign in with your Google account first."
            return
        }

        viewModelScope.launch {
            _isSettingUpAdmin.value = true
            _setupError.value = null
            _setupSuccess.value = null

            try {
                user.reload().await()
                user.getIdToken(true).await()
                val email = user.email?.trim()?.lowercase() ?: ""
                if (!isSuperAdminEmail(email) || !isVerifiedInFirebaseAuth(user)) {
                    _setupError.value = "Access Denied: Only $authorizedOwnerEmail is authorized for Initial Admin Setup."
                    _isSettingUpAdmin.value = false
                    return@launch
                }

                Log.d(TAG, "Calling repository.ensureSuperAdmin for $email")
                repository.ensureSuperAdmin(email, user.uid, user.displayName ?: "")

                val isNowAdmin = repository.isAdmin()
                if (isNowAdmin) {
                    _setupSuccess.value = "Admin Setup Completed"
                    delay(1200L)
                    _authState.value = AuthState.Authenticated(role = "admin", userEmail = email)
                } else {
                    _setupError.value = "Admin record saved, but status verification failed. Please try again."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Initial admin setup failed", e)
                _setupError.value = e.localizedMessage ?: "Failed to save Admin record to Firestore"
            } finally {
                _isSettingUpAdmin.value = false
            }
        }
    }
}
