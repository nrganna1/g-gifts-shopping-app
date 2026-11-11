package com.example

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.GRepository
import com.example.model.Product
import com.example.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AdminAuthAndProductRuleTest : FirestoreEmulatorTestBase() {

    private suspend inline fun <reified T : Throwable> assertFailsWithSuspend(
        block: suspend () -> Unit
    ): T {
        try {
            block()
        } catch (t: Throwable) {
            if (t is T) return t
            throw AssertionError("Expected ${T::class.java.simpleName} but caught ${t::class.java.simpleName}: ${t.message}", t)
        }
        fail("Expected ${T::class.java.simpleName} to be thrown")
        throw IllegalStateException("Unreachable")
    }

    @Test
    fun newAdmin_verified_canSignInSyncAndManageProducts() = runBlocking {
        val uid = signInTestUser(NEW_ADMIN_EMAIL, markVerified = true)
        val repository = GRepository(context, firestore, auth)

        assertTrue(repository.isAdmin())
        assertTrue(repository.isSuperAdminEmail(NEW_ADMIN_EMAIL))

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.ensureSuperAdmin(NEW_ADMIN_EMAIL, uid, "Store Owner")
        }
        assertTrue(withTimeout(DEFAULT_TIMEOUT_MS) { repository.hasAdmins() })

        // Add product
        val createdId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addProduct(
                Product(
                    name = "Cricket Bat Kashmir Willow",
                    description = "Full size wooden cricket bat",
                    originalPrice = 1200.0,
                    sellingPrice = 899.0,
                    stock = 12,
                    category = "Sports",
                    images = listOf("https://example.com/bat.jpg"),
                    isEnabled = true
                )
            )
        }
        assertTrue(createdId.isNotBlank())

        // Update product
        withTimeout(DEFAULT_TIMEOUT_MS) {
            val doc = firestore.collection("products").document(createdId).get().await()
            val saved = repository.parseProductFromSnapshot(doc)
            assertNotNull(saved)
            repository.updateProduct(saved!!.copy(sellingPrice = 849.0, stock = 10))
        }

        val updatedSnapshot = withTimeout(DEFAULT_TIMEOUT_MS) {
            firestore.collection("products").document(createdId).get().await()
        }
        assertEquals(849.0, updatedSnapshot.getDouble("sellingPrice") ?: 0.0, 0.01)
        assertEquals(10L, updatedSnapshot.getLong("stock"))

        // Observe products works for storefront
        val list = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeProducts().first { items -> items.any { it.id == createdId } }
        }
        assertTrue(list.any { it.id == createdId && it.sellingPrice == 849.0 })

        // Delete product
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.deleteProduct(createdId)
        }
        val afterDelete = withTimeout(DEFAULT_TIMEOUT_MS) {
            firestore.collection("products").document(createdId).get().await()
        }
        assertFalse(afterDelete.exists())
    }

    @Test
    fun oldAdmin_nrganna2015_completelyRevokedAndDowngradedToCustomer() = runBlocking {
        // Seed product as new admin first
        signInTestUser(NEW_ADMIN_EMAIL, markVerified = true)
        val adminRepo = GRepository(context, firestore, auth)
        val existingProductId = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.addProduct(
                Product(
                    name = "Existing Teddy Bear",
                    sellingPrice = 599.0,
                    stock = 5,
                    category = "Toys"
                )
            )
        }

        // Now sign in as old admin email
        val oldUid = signInTestUser(OLD_ADMIN_EMAIL, markVerified = true)
        val oldAdminRepo = GRepository(context, firestore, auth)

        assertFalse(oldAdminRepo.isSuperAdminEmail(OLD_ADMIN_EMAIL))
        assertFalse(oldAdminRepo.isAdmin())

        // Repository blocks admin actions
        assertFailsWithSuspend<SecurityException> {
            oldAdminRepo.addProduct(Product(name = "Blocked", sellingPrice = 100.0))
        }
        assertFailsWithSuspend<SecurityException> {
            oldAdminRepo.updateProduct(Product(id = existingProductId, name = "Hacked", sellingPrice = 1.0))
        }
        assertFailsWithSuspend<SecurityException> {
            oldAdminRepo.deleteProduct(existingProductId)
        }
        assertFailsWithSuspend<SecurityException> {
            oldAdminRepo.ensureSuperAdmin(OLD_ADMIN_EMAIL, oldUid, "Old Admin")
        }

        // Direct Firestore writes by old admin are also rejected by firestore.rules
        val fsException = assertFailsWithSuspend<FirebaseFirestoreException> {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("products").document(existingProductId)
                    .update("sellingPrice", 1.0).await()
            }
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsException.code)

        // Saving user profile forces customer role without breaking existing product
        withTimeout(DEFAULT_TIMEOUT_MS) {
            oldAdminRepo.saveUserProfile(
                UserProfile(
                    id = oldUid,
                    displayName = "Old Admin User",
                    email = OLD_ADMIN_EMAIL,
                    role = "admin" // Attempted escalation must be sanitized to customer
                )
            )
        }
        val userDoc = withTimeout(DEFAULT_TIMEOUT_MS) {
            firestore.collection("users").document(oldUid).get().await()
        }
        assertEquals("customer", userDoc.getString("role"))

        // Existing product was NOT deleted
        val stillExists = withTimeout(DEFAULT_TIMEOUT_MS) {
            firestore.collection("products").document(existingProductId).get().await()
        }
        assertTrue(stillExists.exists())
    }

    @Test
    fun customerAndUnauthenticated_cannotAccessAdminOrModifyCatalog() = runBlocking {
        signInTestUser(CUSTOMER_EMAIL, markVerified = true)
        val customerRepo = GRepository(context, firestore, auth)
        assertFalse(customerRepo.isAdmin())

        assertFailsWithSuspend<SecurityException> {
            customerRepo.addProduct(Product(name = "Customer Product", sellingPrice = 50.0))
        }

        val ruleEx = assertFailsWithSuspend<FirebaseFirestoreException> {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("products").document("cust_hack")
                    .set(mapOf("name" to "Hack", "sellingPrice" to 1.0)).await()
            }
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ruleEx.code)

        // Unauthenticated
        auth.signOut()
        assertFalse(customerRepo.isAdmin())
        val unauthEx = assertFailsWithSuspend<FirebaseFirestoreException> {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("orders").get().await()
            }
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, unauthEx.code)
    }

    @Test
    fun unverifiedNewAdminEmail_cannotGainAdminAccess() = runBlocking {
        signInTestUser(UNVERIFIED_ADMIN_EMAIL, markVerified = false)
        val repo = GRepository(context, firestore, auth)
        assertFalse(repo.isAdmin())

        assertFailsWithSuspend<SecurityException> {
            repo.addProduct(Product(name = "Unverified Blocked", sellingPrice = 99.0))
        }

        val fsEx = assertFailsWithSuspend<FirebaseFirestoreException> {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("products").document("unverified_hack")
                    .set(mapOf("name" to "Spoof", "sellingPrice" to 99.0)).await()
            }
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx.code)
    }

    private companion object {
        const val NEW_ADMIN_EMAIL = "nrganna1@gmail.com"
        const val UNVERIFIED_ADMIN_EMAIL = "nrganna1@gmail.com"
        const val OLD_ADMIN_EMAIL = "nrganna2015@gmail.com"
        const val CUSTOMER_EMAIL = "shopper@gmail.com"
        const val DEFAULT_TIMEOUT_MS = 6000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
