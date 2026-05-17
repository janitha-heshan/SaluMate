package com.university.salumate;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * DBHandler — Central SQLite database access object for SaluMate.
 *
 * <p>Extends {@link SQLiteOpenHelper} to manage the creation and version-upgrade
 * lifecycle of the local {@code salumate.db} database. All table definitions,
 * CRUD helpers, and query utilities for the app are consolidated here.</p>
 *
 * <h3>Schema Overview</h3>
 * <pre>
 *  Users                 — Shop owner/user account info
 *  MeasurementTemplates  — Named sets of measurement fields (e.g. "Men's Suit")
 *  MeasurementFields     — Individual fields within a template (e.g. "Chest", "Waist")
 *  DressTemplates        — Reusable dress/garment configurations with price & time estimates
 *  Customers             — Customer directory with contact and geo-location data
 *  Beneficiaries         — Dependants linked to a customer (family members, etc.)
 *  Orders                — Customer orders with payment and status tracking
 *  OrderItems            — Line items within an order, each referencing a dress template
 *  OrderMeasurements     — Per-item measurement values captured during order creation
 *  ReferenceImages       — Optional garment reference images attached to an order
 * </pre>
 *
 * <p><b>Version History:</b></p>
 * <ul>
 *   <li>v1–4: Initial schema iterations</li>
 *   <li>v5: Added {@code image_path} and {@code notes} columns to ReferenceImages</li>
 * </ul>
 */
public class DBHandler extends SQLiteOpenHelper {

    private static final String DATABASE_NAME    = "salumate.db";
    private static final int    DATABASE_VERSION = 6;

    // ─── Table Name Constants ────────────────────────────────────────────────
    private static final String TABLE_USERS                = "Users";
    private static final String TABLE_MEASUREMENT_TEMPLATES = "MeasurementTemplates";
    private static final String TABLE_MEASUREMENT_FIELDS   = "MeasurementFields";
    private static final String TABLE_DRESS_TEMPLATES      = "DressTemplates";
    private static final String TABLE_CUSTOMERS            = "Customers";
    private static final String TABLE_BENEFICIARIES        = "Beneficiaries";
    private static final String TABLE_ORDERS               = "Orders";
    private static final String TABLE_ORDER_ITEMS          = "OrderItems";
    private static final String TABLE_ORDER_MEASUREMENTS   = "OrderMeasurements";
    private static final String TABLE_REFERENCE_IMAGES     = "ReferenceImages";

    /**
     * Constructs a new DBHandler for the given application context.
     * @param context The Android context used to locate the database file.
     */
    public DBHandler(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle — Schema Creation & Upgrade
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Called the very first time the database file is created.
     * Executes all CREATE TABLE statements in dependency order
     * (parent tables before child tables that reference them via FK).
     */
    @Override
    public void onCreate(SQLiteDatabase db) {

        // Shop owner / user account table (v6 adds username/password/security Q&A columns)
        String CREATE_USERS =
            "CREATE TABLE " + TABLE_USERS + " (" +
            "user_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "name TEXT NOT NULL, " +
            "shop_name TEXT, " +
            "phone_number TEXT NOT NULL, " +
            "biometric_enabled INTEGER DEFAULT 0, " +
            "username TEXT, " +
            "password TEXT, " +
            "security_question TEXT, " +
            "security_answer TEXT, " +
            "created_at TEXT DEFAULT CURRENT_TIMESTAMP, " +
            "updated_at TEXT DEFAULT CURRENT_TIMESTAMP);"
        ;

        // Named measurement template (e.g. "Men's Shirt" has fields Chest, Sleeve…)
        String CREATE_MEASUREMENT_TEMPLATES =
            "CREATE TABLE " + TABLE_MEASUREMENT_TEMPLATES + " (" +
            "measurement_template_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "template_name TEXT NOT NULL, " +
            "created_by INTEGER, " +
            "created_at TEXT DEFAULT CURRENT_TIMESTAMP, " +
            "updated_at TEXT DEFAULT CURRENT_TIMESTAMP);";

        // Individual measurement field within a template (e.g. Chest = 40 in)
        String CREATE_MEASUREMENT_FIELDS =
            "CREATE TABLE " + TABLE_MEASUREMENT_FIELDS + " (" +
            "field_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "measurement_template_id INTEGER NOT NULL, " +
            "field_name TEXT NOT NULL, " +
            "unit TEXT, " +
            "FOREIGN KEY(measurement_template_id) REFERENCES " +
                TABLE_MEASUREMENT_TEMPLATES + "(measurement_template_id));";

        // Reusable dress/garment template defining estimated time, price & linked measurements
        String CREATE_DRESS_TEMPLATES =
            "CREATE TABLE " + TABLE_DRESS_TEMPLATES + " (" +
            "dress_template_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "dress_name TEXT NOT NULL, " +
            "estimated_time TEXT, " +
            "estimated_price REAL, " +
            "measurement_template_id INTEGER, " +
            "created_at TEXT DEFAULT CURRENT_TIMESTAMP, " +
            "updated_at TEXT DEFAULT CURRENT_TIMESTAMP);";

        // Customer directory — includes optional lat/lng for geo-based features
        String CREATE_CUSTOMERS =
            "CREATE TABLE " + TABLE_CUSTOMERS + " (" +
            "customer_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "name TEXT NOT NULL, " +
            "phone_number TEXT NOT NULL, " +
            "address TEXT, " +
            "latitude REAL, " +
            "longitude REAL);";

        // Dependants of a customer (family members who also receive orders)
        String CREATE_BENEFICIARIES =
            "CREATE TABLE " + TABLE_BENEFICIARIES + " (" +
            "beneficiary_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "customer_id INTEGER NOT NULL, " +
            "name TEXT NOT NULL, " +
            "gender TEXT, " +
            "relation TEXT, " +
            "FOREIGN KEY(customer_id) REFERENCES " + TABLE_CUSTOMERS + "(customer_id));";

        // An order placed by a customer — tracks pricing, payment progress, and deadline
        String CREATE_ORDERS =
            "CREATE TABLE " + TABLE_ORDERS + " (" +
            "order_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "customer_id INTEGER NOT NULL, " +
            "total_price REAL, " +
            "paid_amount REAL, " +
            "payment_due REAL, " +
            "due_date TEXT, " +
            "order_status TEXT, " +
            "created_at TEXT DEFAULT CURRENT_TIMESTAMP, " +
            "updated_at TEXT DEFAULT CURRENT_TIMESTAMP, " +
            "FOREIGN KEY(customer_id) REFERENCES " + TABLE_CUSTOMERS + "(customer_id));";

        // Each line item within an order: which dress for which beneficiary
        String CREATE_ORDER_ITEMS =
            "CREATE TABLE " + TABLE_ORDER_ITEMS + " (" +
            "order_item_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "order_id INTEGER NOT NULL, " +
            "beneficiary_id INTEGER, " +
            "dress_template_id INTEGER NOT NULL, " +
            "price REAL, " +
            "FOREIGN KEY(order_id) REFERENCES " + TABLE_ORDERS + "(order_id), " +
            "FOREIGN KEY(beneficiary_id) REFERENCES " + TABLE_BENEFICIARIES + "(beneficiary_id), " +
            "FOREIGN KEY(dress_template_id) REFERENCES " + TABLE_DRESS_TEMPLATES + "(dress_template_id));";

        // Per-item measurement values (e.g. Chest = 40.5) recorded at order time
        String CREATE_ORDER_MEASUREMENTS =
            "CREATE TABLE " + TABLE_ORDER_MEASUREMENTS + " (" +
            "order_measurement_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "order_item_id INTEGER NOT NULL, " +
            "field_id INTEGER NOT NULL, " +
            "value REAL, " +
            "FOREIGN KEY(order_item_id) REFERENCES " + TABLE_ORDER_ITEMS + "(order_item_id), " +
            "FOREIGN KEY(field_id) REFERENCES " + TABLE_MEASUREMENT_FIELDS + "(field_id));";

        // Reference images (gallery or camera) attached to an order with optional notes
        String CREATE_REFERENCE_IMAGES =
            "CREATE TABLE " + TABLE_REFERENCE_IMAGES + " (" +
            "image_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "order_id INTEGER NOT NULL, " +
            "image_path TEXT NOT NULL, " +
            "notes TEXT, " +
            "uploaded_at TEXT DEFAULT CURRENT_TIMESTAMP, " +
            "FOREIGN KEY(order_id) REFERENCES " + TABLE_ORDERS + "(order_id));";

        // Execute DDL statements in dependency order (parents first)
        db.execSQL(CREATE_USERS);
        db.execSQL(CREATE_MEASUREMENT_TEMPLATES);
        db.execSQL(CREATE_MEASUREMENT_FIELDS);
        db.execSQL(CREATE_DRESS_TEMPLATES);
        db.execSQL(CREATE_CUSTOMERS);
        db.execSQL(CREATE_BENEFICIARIES);
        db.execSQL(CREATE_ORDERS);
        db.execSQL(CREATE_ORDER_ITEMS);
        db.execSQL(CREATE_ORDER_MEASUREMENTS);
        db.execSQL(CREATE_REFERENCE_IMAGES);
    }

    /**
     * Called when the stored {@link #DATABASE_VERSION} is higher than the version
     * of the existing database file on disk. Drops all tables in reverse dependency
     * order (children first) to respect FK constraints, then recreates the schema.
     *
     * <p><b>Note:</b> This is a destructive migration. All existing data is lost
     * on upgrade. For production deployments, implement incremental ALTER TABLE
     * migrations instead.</p>
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion == 5 && newVersion == 6) {
            // Safe incremental migration: add auth columns without destroying data
            db.execSQL("ALTER TABLE " + TABLE_USERS + " ADD COLUMN username TEXT");
            db.execSQL("ALTER TABLE " + TABLE_USERS + " ADD COLUMN password TEXT");
            db.execSQL("ALTER TABLE " + TABLE_USERS + " ADD COLUMN security_question TEXT");
            db.execSQL("ALTER TABLE " + TABLE_USERS + " ADD COLUMN security_answer TEXT");
            return;
        }
        // Destructive fallback for all other version combinations
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_REFERENCE_IMAGES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ORDER_MEASUREMENTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ORDER_ITEMS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ORDERS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_BENEFICIARIES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CUSTOMERS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_DRESS_TEMPLATES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MEASUREMENT_FIELDS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MEASUREMENT_TEMPLATES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
        onCreate(db);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Customer CRUD Operations
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Inserts a new customer record into the database.
     *
     * @param name      Full name of the customer.
     * @param phone     Contact phone number.
     * @param address   Street / home address (optional).
     * @param latitude  GPS latitude for geo features (null if not captured).
     * @param longitude GPS longitude for geo features (null if not captured).
     * @return The row ID of the newly inserted customer, or -1 on failure.
     */
    public long addCustomer(String name, String phone, String address,
                            Double latitude, Double longitude) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("name",         name);
        values.put("phone_number", phone);
        values.put("address",      address);
        if (latitude  != null) values.put("latitude",  latitude);
        if (longitude != null) values.put("longitude", longitude);
        long id = db.insert(TABLE_CUSTOMERS, null, values);
        db.close();
        return id;
    }

    /**
     * Fetches a single customer record by primary key.
     *
     * @param customerId The customer's primary key.
     * @return A {@link Cursor} positioned at the matching row, or null.
     */
    public Cursor getCustomerById(long customerId) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(TABLE_CUSTOMERS, null,
                "customer_id=?", new String[]{String.valueOf(customerId)},
                null, null, null);
    }

    /**
     * Returns all customers sorted alphabetically by name.
     *
     * @return A {@link Cursor} over the full Customers table.
     */
    public Cursor getAllCustomers() {
        SQLiteDatabase db = this.getReadableDatabase();
        // Use the constant TABLE_CUSTOMERS for consistency with the rest of the class
        return db.query(TABLE_CUSTOMERS, null, null, null, null, null, "name ASC");
    }

    /**
     * Permanently removes a customer and their associated record.
     *
     * @param customerId The ID of the customer to delete.
     */
    public void deleteCustomer(long customerId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_CUSTOMERS, "customer_id=?", new String[]{String.valueOf(customerId)});
        db.close();
    }

    /**
     * Updates an existing customer's contact and location details.
     *
     * @param id      Primary key of the customer to update.
     * @param name    Updated full name.
     * @param phone   Updated phone number.
     * @param address Updated street address.
     * @param lat     Updated GPS latitude.
     * @param lng     Updated GPS longitude.
     * @return {@code true} if at least one row was modified.
     */
    public boolean updateCustomer(long id, String name, String phone,
                                  String address, double lat, double lng) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("name",         name);
        values.put("phone_number", phone);
        values.put("address",      address);
        values.put("latitude",     lat);
        values.put("longitude",    lng);
        int rows = db.update(TABLE_CUSTOMERS, values,
                "customer_id=?", new String[]{String.valueOf(id)});
        db.close();
        return rows > 0;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Dress Template CRUD Operations
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns all dress templates stored in the database.
     *
     * @return A {@link Cursor} over the full DressTemplates table.
     */
    public Cursor getAllDresses() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_DRESS_TEMPLATES, null);
    }

    /**
     * Deletes a dress template by its primary key.
     *
     * @param id The dress_template_id of the record to remove.
     * @return {@code true} if the row was found and deleted.
     */
    public boolean deleteDress(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.delete(TABLE_DRESS_TEMPLATES,
                "dress_template_id=?", new String[]{String.valueOf(id)}) > 0;
    }

    /**
     * Fetches a single dress template by primary key.
     *
     * @param id The dress_template_id to look up.
     * @return A {@link Cursor} positioned at the matching row, or null.
     */
    public Cursor getDressById(long id) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery(
                "SELECT * FROM " + TABLE_DRESS_TEMPLATES + " WHERE dress_template_id=?",
                new String[]{String.valueOf(id)});
    }

    /**
     * Updates the editable fields of an existing dress template.
     *
     * @param id    The dress_template_id of the record to update.
     * @param name  New dress/garment name.
     * @param time  New estimated completion time (stored as free-text, e.g. "7 days").
     * @param price New estimated price in LKR.
     * @return {@code true} if the row was found and updated successfully.
     */
    public boolean updateDress(long id, String name, String time, double price) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("dress_name",      name);
        values.put("estimated_time",  time);
        values.put("estimated_price", price);
        int rows = db.update(TABLE_DRESS_TEMPLATES, values,
                "dress_template_id=?", new String[]{String.valueOf(id)});
        db.close();
        return rows > 0;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Auth & Credentials Operations
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * SHA-256 hashes a string. Used for storing passwords and security answers.
     * Returns a hex-encoded digest string.
     */
    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return input; // Fallback — SHA-256 is always available on Android
        }
    }

    /**
     * Returns true if a password has already been configured for the shop owner account.
     */
    public boolean isPasswordSet() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT password FROM " + TABLE_USERS + " WHERE password IS NOT NULL LIMIT 1", null);
        boolean set = c != null && c.moveToFirst() && c.getString(0) != null;
        if (c != null) c.close();
        return set;
    }

    /**
     * Creates or updates the shop owner's login credentials and security Q&A.
     * If no Users row exists, one is inserted with placeholder profile data.
     *
     * @param username The chosen login username.
     * @param password Plain-text password (will be SHA-256 hashed before storage).
     * @param question The security question text for account recovery.
     * @param answer   The security answer (stored as lowercase SHA-256 hash).
     */
    public void setupCredentials(String username, String password,
                                 String question, String answer) {
        SQLiteDatabase db = this.getWritableDatabase();
        String hashedPassword = sha256(password);
        String hashedAnswer   = sha256(answer.trim().toLowerCase());

        Cursor c = db.rawQuery("SELECT user_id FROM " + TABLE_USERS + " LIMIT 1", null);
        boolean rowExists = c != null && c.moveToFirst();
        if (c != null) c.close();

        ContentValues values = new ContentValues();
        values.put("username",          username);
        values.put("password",          hashedPassword);
        values.put("security_question", question);
        values.put("security_answer",   hashedAnswer);
        values.put("updated_at",        "datetime('now')");

        if (rowExists) {
            db.update(TABLE_USERS, values, null, null); // Update first (only) row
        } else {
            // No profile row yet — insert with placeholder profile fields
            values.put("name",         username);
            values.put("phone_number", "not_set");
            values.put("biometric_enabled", 0);
            db.insert(TABLE_USERS, null, values);
        }
        db.close();
    }

    /**
     * Verifies a username + password pair against the stored credentials.
     *
     * @param username The username to check.
     * @param password The plain-text password to verify.
     * @return {@code true} if the credentials match.
     */
    public boolean verifyCredentials(String username, String password) {
        SQLiteDatabase db = this.getReadableDatabase();
        String hashed = sha256(password);
        Cursor c = db.rawQuery(
                "SELECT user_id FROM " + TABLE_USERS +
                " WHERE LOWER(username) = LOWER(?) AND password = ?",
                new String[]{username, hashed});
        boolean ok = c != null && c.moveToFirst();
        if (c != null) c.close();
        return ok;
    }

    /**
     * Returns the stored security question text, or null if not set up yet.
     */
    public String getSecurityQuestion() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT security_question FROM " + TABLE_USERS +
                " WHERE security_question IS NOT NULL LIMIT 1", null);
        String q = null;
        if (c != null && c.moveToFirst()) q = c.getString(0);
        if (c != null) c.close();
        return q;
    }

    /**
     * Returns the stored username, or null if not configured.
     */
    public String getUsername() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT username FROM " + TABLE_USERS + " LIMIT 1", null);
        String u = null;
        if (c != null && c.moveToFirst()) u = c.getString(0);
        if (c != null) c.close();
        return u;
    }

    /**
     * Changes the password after verifying the old password is correct.
     *
     * @param oldPassword Plain-text current password.
     * @param newPassword Plain-text new password.
     * @return {@code true} if the old password matched and the update succeeded.
     */
    public boolean changePassword(String oldPassword, String newPassword) {
        SQLiteDatabase db = this.getWritableDatabase();
        String hashedOld = sha256(oldPassword);
        String hashedNew = sha256(newPassword);

        Cursor c = db.rawQuery(
                "SELECT user_id FROM " + TABLE_USERS + " WHERE password = ?",
                new String[]{hashedOld});
        boolean match = c != null && c.moveToFirst();
        if (c != null) c.close();

        if (!match) { db.close(); return false; }

        ContentValues values = new ContentValues();
        values.put("password", hashedNew);
        db.update(TABLE_USERS, values, "password = ?", new String[]{hashedOld});
        db.close();
        return true;
    }

    /**
     * Resets the password after verifying the security answer.
     *
     * @param answer      Plain-text security answer (case-insensitive).
     * @param newPassword Plain-text new password.
     * @return {@code true} if the answer matched and the password was reset.
     */
    public boolean resetPasswordViaAnswer(String answer, String newPassword) {
        SQLiteDatabase db = this.getWritableDatabase();
        String hashedAnswer = sha256(answer.trim().toLowerCase());

        Cursor c = db.rawQuery(
                "SELECT user_id FROM " + TABLE_USERS + " WHERE security_answer = ?",
                new String[]{hashedAnswer});
        boolean match = c != null && c.moveToFirst();
        if (c != null) c.close();

        if (!match) { db.close(); return false; }

        ContentValues values = new ContentValues();
        values.put("password", sha256(newPassword));
        db.update(TABLE_USERS, values, "security_answer = ?", new String[]{hashedAnswer});
        db.close();
        return true;
    }

    /**
     * Updates only the security question and answer (used from Settings).
     *
     * @param currentPassword Must match stored password to authorise this change.
     * @param newQuestion     The new security question text.
     * @param newAnswer       The new security answer (stored hashed).
     * @return {@code true} if the password verified and the update succeeded.
     */
    public boolean updateSecurityQuestion(String currentPassword,
                                          String newQuestion, String newAnswer) {
        SQLiteDatabase db = this.getWritableDatabase();
        String hashedPwd = sha256(currentPassword);

        Cursor c = db.rawQuery(
                "SELECT user_id FROM " + TABLE_USERS + " WHERE password = ?",
                new String[]{hashedPwd});
        boolean match = c != null && c.moveToFirst();
        if (c != null) c.close();

        if (!match) { db.close(); return false; }

        ContentValues values = new ContentValues();
        values.put("security_question", newQuestion);
        values.put("security_answer",   sha256(newAnswer.trim().toLowerCase()));
        db.update(TABLE_USERS, values, "password = ?", new String[]{hashedPwd});
        db.close();
        return true;
    }
}
