# G Gifts Shopping Store (Customer Website + Android App)

**Store Name:** G Gifts Shopping Store  
**Location:** Veera Reddy Nagar, Boduppal, Telangana  
**WhatsApp & Call Booking:** `7036436305` (`https://wa.me/917036436305`)  
**Pickup Mode:** Store Pickup Only (No Home Delivery)

---

## Features Included
1. **Free Customer Shopping Website (`public/index.html`)**
   - Opens directly without requiring customer login.
   - Displays all active products, product photos, selling prices, MRP offers (`% OFF` + offer labels), categories, stock availability, and Search.
   - **Full Shopping Cart & Checkout:** Add to Cart, update quantities (`+`/`-`), remove items, review complete Order Summary & Total Amount, and enter Customer Name, Mobile Number, and Address.
   - **WhatsApp Order & Booking (`Place Order` / `Book Now`):** Saves orders/bookings to Firestore (`/orders` or `/bookings`) when possible, opens `https://wa.me/917036436305?text=...` with customer details, itemized products, quantities, prices, and total, and displays: *"Your order is not submitted until you send the WhatsApp message."*
   - Deep-link product details (`/?product=<productId>`) so product QR codes open directly on mobile browsers.
   - Zero customer photo upload or customization tools. Customers cannot access the Admin Panel.

2. **QR Code Booking (Store + Individual Products)**
   - Validates that the saved `websiteUrl` is a real, publicly accessible `https://` storefront (blocks empty URLs, temporary AI Studio preview URLs, localhost/LAN IPs, Firebase preview channels, and `/admin` routes).
   - Shows `"Publish the app first to generate a working Customer Store QR Code."` until a published public URL is configured.
   - Generates one permanent Customer Store QR Code and optional individual Product QR Codes (`/?product=<id>`).
   - Supports **Download**, **Print** (Android `PrintManager` + web `window.print()`), **Share**, and **Open Link**.

3. **Single-Owner Admin Dashboard (Android + Separate `public/admin.html`)**
   - Strictly **one authorized Admin account** (`nrganna1@gmail.com` via Google Sign-In; public/multi-admin registration blocked).
   - Add, edit, delete products; upload product photos (Admin only) or paste free HTTPS image URLs; manage prices, MRP/offers, stock, and Store visibility.
   - View and update both Cart Checkout Orders (`/orders`) and Quick Bookings (`/bookings`) (`Pending`, `Confirmed`, `Ready for Pickup`, `Completed`, `Cancelled`) and message customers via WhatsApp (`https://wa.me/...`).
   - Customer order/booking records are never publicly readable (`firestore.rules`).

---

## Free Firebase Spark Plan Deployment Guide

All static storefront hosting, Firestore product/booking operations, Google Admin Sign-In, QR generation, and WhatsApp `wa.me` booking links run within the **Firebase Spark (Free) Plan**:

### 1. Install & Authenticate Firebase CLI
```bash
npm install -g firebase-tools
firebase login
firebase use g-gifts-shopping-store
```

### 2. Deploy Security Rules & Public Customer Website (Spark Plan Free)
```bash
firebase deploy --only firestore:rules,hosting
```
> **Important Note on Firebase Storage Billing:**  
> Newer Firebase projects require upgrading to the **Blaze (pay-as-you-go)** plan before provisioning/deploying `firebase storage` rules or uploading binary files to Firebase Storage (though Blaze still includes a free monthly tier). **Do NOT enable billing unless you need Storage uploads** — if you want to stay 100% on the **Free Spark Plan**, deploy only `firestore:rules,hosting` and enter free public `https://` image URLs when adding or editing products in Admin Panel.  
> If Storage is already enabled on your project, deploy with:
> ```bash
> firebase deploy --only firestore:rules,storage,hosting
> ```

### 3. Activate Your Permanent QR Codes
1. Open the published website URL output by `firebase deploy` (for example `https://g-gifts-shopping-store.web.app`).
2. Sign in to the Admin Panel (Android app or `https://g-gifts-shopping-store.web.app/admin.html`) with `nrganna1@gmail.com`.
3. Open **Admin > Settings**, enter your published `https://` Customer Store URL, and tap **Save Store Settings**.
4. **Download** or **Print** your permanent Customer Store QR Code and individual Product QR Codes, then scan from another phone on mobile data to verify!

---

## Android Studio Build Steps
1. Open this project in Android Studio (SDK Platform 36).
2. Confirm `app/google-services.json` and `app/src/main/res/values/firebase_applet_config.xml` match project `g-gifts-shopping-store`.
3. Add your debug/release SHA-1 & SHA-256 certificates in Firebase Console for Android Google Sign-In.
4. Build APK via **Build > Build Bundle(s) / APK(s) > Build APK(s)** (`app/build/outputs/apk/debug/app-debug.apk`).
