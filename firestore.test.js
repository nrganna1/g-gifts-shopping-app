const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const NEW_ADMIN_EMAIL = "nrganna1@gmail.com";
const OLD_ADMIN_EMAIL = "nrganna2015@gmail.com";
const CUSTOMER_EMAIL = "customer@gmail.com";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function dbForUser(uid, email, emailVerified = true) {
  return testEnv
    .authenticatedContext(uid, { email, email_verified: emailVerified })
    .firestore();
}

test("New Admin (nrganna1@gmail.com, verified): can access admin records and manage products", async () => {
  const adminDb = dbForUser("admin_uid_1", NEW_ADMIN_EMAIL, true);

  await assertSucceeds(
    adminDb.collection("admins").doc(NEW_ADMIN_EMAIL).set({
      email: NEW_ADMIN_EMAIL,
      role: "super_admin",
      isSuperAdmin: true,
    })
  );

  await assertSucceeds(
    adminDb.collection("products").doc("prod_1").set({
      name: "Cricket Bat Pro",
      sellingPrice: 799,
      originalPrice: 999,
      stock: 15,
      category: "Sports",
      description: "Kashmir willow bat",
      images: ["https://example.com/bat.jpg"],
      isEnabled: true,
    })
  );

  await assertSucceeds(
    adminDb.collection("products").doc("prod_1").update({
      sellingPrice: 749,
      stock: 12,
    })
  );

  await assertSucceeds(adminDb.collection("products").doc("prod_1").delete());
});

test("Email Spoof Attack: nrganna1@gmail.com with email_verified=false is DENIED admin writes", async () => {
  const spoofDb = dbForUser("spoof_uid", NEW_ADMIN_EMAIL, false);

  await assertFails(
    spoofDb.collection("products").doc("spoof_prod").set({
      name: "Spoof Product",
      sellingPrice: 100,
    })
  );
  await assertFails(spoofDb.collection("admins").doc(NEW_ADMIN_EMAIL).get());
  await assertFails(spoofDb.collection("orders").get());
});

test("Old Admin (nrganna2015@gmail.com): completely revoked even if legacy /admins doc exists", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("admins").doc(OLD_ADMIN_EMAIL).set({
      email: OLD_ADMIN_EMAIL,
      role: "super_admin",
    });
    await db.collection("users").doc("old_admin_uid").set({
      id: "old_admin_uid",
      email: OLD_ADMIN_EMAIL,
      role: "admin",
    });
    await db.collection("products").doc("existing_prod").set({
      name: "Existing Gift Box",
      sellingPrice: 299,
      stock: 10,
      isEnabled: true,
    });
    await db.collection("orders").doc("existing_order").set({
      userId: "cust_uid_99",
      customerName: "Sita",
      customerPhone: "9876543210",
      deliveryAddress: "Boduppal",
      items: [{ productId: "existing_prod", name: "Existing Gift Box", quantity: 1, price: 299 }],
      totalAmount: 299,
      orderStatus: "pending_whatsapp",
    });
  });

  const oldAdminDb = dbForUser("old_admin_uid", OLD_ADMIN_EMAIL, true);

  // Old admin cannot read /admins or modify products, categories, settings, or orders
  await assertFails(oldAdminDb.collection("admins").doc(OLD_ADMIN_EMAIL).get());
  await assertFails(oldAdminDb.collection("admins").doc(NEW_ADMIN_EMAIL).get());
  await assertFails(
    oldAdminDb.collection("products").doc("new_prod").set({
      name: "Unauthorized Product",
      sellingPrice: 500,
    })
  );
  await assertFails(
    oldAdminDb.collection("products").doc("existing_prod").update({ sellingPrice: 1 })
  );
  await assertFails(oldAdminDb.collection("products").doc("existing_prod").delete());
  await assertFails(oldAdminDb.collection("orders").doc("existing_order").get());
  await assertFails(
    oldAdminDb.collection("orders").doc("existing_order").update({ orderStatus: "Confirmed" })
  );
  await assertFails(
    oldAdminDb.collection("settings").doc("global").set({ whatsAppNumber: "0000000000" })
  );

  // Old admin CANNOT escalate or keep admin role on /users/{uid}
  await assertFails(
    oldAdminDb.collection("users").doc("old_admin_uid").set({
      id: "old_admin_uid",
      email: OLD_ADMIN_EMAIL,
      role: "admin",
    })
  );

  // Old admin CAN downgrade their own profile to role=customer
  await assertSucceeds(
    oldAdminDb.collection("users").doc("old_admin_uid").set(
      {
        id: "old_admin_uid",
        email: OLD_ADMIN_EMAIL,
        role: "customer",
      },
      { merge: true }
    )
  );
});

test("Customer & Unauthenticated Users: can browse catalog and place orders, cannot access Admin", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("products").doc("p1").set({
      name: "Teddy Bear",
      sellingPrice: 499,
      stock: 5,
      isEnabled: true,
    });
    await db.collection("orders").doc("other_order").set({
      userId: "someone_else",
      customerName: "Ravi",
      customerPhone: "9876543210",
      deliveryAddress: "Hyderabad",
      items: [{ productId: "p1", name: "Teddy Bear", quantity: 1, price: 499 }],
      totalAmount: 499,
      orderStatus: "pending_whatsapp",
    });
  });

  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const custDb = dbForUser("cust_1", CUSTOMER_EMAIL, true);

  // Public reads work
  await assertSucceeds(unauthDb.collection("products").doc("p1").get());
  await assertSucceeds(custDb.collection("products").doc("p1").get());

  // Customer/unauth cannot write products or escalate role
  await assertFails(
    custDb.collection("products").doc("p2").set({ name: "Hack", sellingPrice: 1 })
  );
  await assertFails(
    unauthDb.collection("products").doc("p2").set({ name: "Hack", sellingPrice: 1 })
  );
  await assertFails(
    custDb.collection("users").doc("cust_1").set({ email: CUSTOMER_EMAIL, role: "admin" })
  );

  // Customer cannot read someone else's order
  await assertFails(custDb.collection("orders").doc("other_order").get());
  await assertFails(unauthDb.collection("orders").doc("other_order").get());

  // Guest/Customer can submit valid WhatsApp cart order (existing store feature)
  await assertSucceeds(
    unauthDb.collection("orders").add({
      userId: "guest",
      customerName: "Anitha",
      customerPhone: "9876543210",
      deliveryAddress: "Veera Reddy Nagar, Boduppal",
      items: [{ productId: "p1", name: "Teddy Bear", quantity: 1, price: 499 }],
      totalAmount: 499,
      orderStatus: "pending_whatsapp",
    })
  );

  // New Admin CAN read all customer orders and existing products without data loss
  const adminDb = dbForUser("admin_uid_1", NEW_ADMIN_EMAIL, true);
  await assertSucceeds(adminDb.collection("orders").doc("other_order").get());
  await assertSucceeds(adminDb.collection("products").doc("p1").get());
});
