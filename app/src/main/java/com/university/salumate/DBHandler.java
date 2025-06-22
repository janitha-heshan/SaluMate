package com.university.salumate;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DBHandler extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "salumate.db";
    private static final int DATABASE_VERSION = 1;

    // Table Names
    private static final String TABLE_USERS = "Users";
    private static final String TABLE_MEASUREMENT_TEMPLATES = "MeasurementTemplates";
    private static final String TABLE_MEASUREMENT_FIELDS = "MeasurementFields";
    private static final String TABLE_DRESS_TEMPLATES = "DressTemplates";
    private static final String TABLE_CUSTOMERS = "Customers";
    private static final String TABLE_BENEFICIARIES = "Beneficiaries";
    private static final String TABLE_ORDERS = "Orders";
    private static final String TABLE_ORDER_MEASUREMENTS = "OrderMeasurements";
    private static final String TABLE_REFERENCE_IMAGES = "ReferenceImages";

    public DBHandler(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Users table
        String CREATE_USERS = "CREATE TABLE " + TABLE_USERS + " (" +
                "user_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL, " +
                "shop_name TEXT, " +
                "phone_number TEXT NOT NULL, " +
                "biometric_enabled INTEGER DEFAULT 0, " +
                "created_at TEXT DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TEXT DEFAULT CURRENT_TIMESTAMP);";

        // MeasurementTemplates table
        String CREATE_MEASUREMENT_TEMPLATES = "CREATE TABLE " + TABLE_MEASUREMENT_TEMPLATES + " (" +
                "measurement_template_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "template_name TEXT NOT NULL, " +
                "created_by INTEGER, " +
                "created_at TEXT DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TEXT DEFAULT CURRENT_TIMESTAMP);";

        // MeasurementFields table
        String CREATE_MEASUREMENT_FIELDS = "CREATE TABLE " + TABLE_MEASUREMENT_FIELDS + " (" +
                "field_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "measurement_template_id INTEGER NOT NULL, " +
                "field_name TEXT NOT NULL, " +
                "unit TEXT, " +
                "FOREIGN KEY(measurement_template_id) REFERENCES " + TABLE_MEASUREMENT_TEMPLATES + "(measurement_template_id));";

        // DressTemplates table
        String CREATE_DRESS_TEMPLATES = "CREATE TABLE " + TABLE_DRESS_TEMPLATES + " (" +
                "dress_template_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "dress_name TEXT NOT NULL, " +
                "estimated_time TEXT, " +
                "estimated_price REAL, " +
                "measurement_template_id INTEGER NOT NULL, " +
                "created_by INTEGER, " +
                "created_at TEXT DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TEXT DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY(measurement_template_id) REFERENCES " + TABLE_MEASUREMENT_TEMPLATES + "(measurement_template_id));";

        // Customers table (updated with latitude & longitude)
        String CREATE_CUSTOMERS = "CREATE TABLE " + TABLE_CUSTOMERS + " (" +
                "customer_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL, " +
                "phone_number TEXT NOT NULL, " +
                "address TEXT, " +
                "latitude REAL, " +
                "longitude REAL);";

        // Beneficiaries table
        String CREATE_BENEFICIARIES = "CREATE TABLE " + TABLE_BENEFICIARIES + " (" +
                "beneficiary_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "customer_id INTEGER NOT NULL, " +
                "name TEXT NOT NULL, " +
                "gender TEXT, " +
                "relation TEXT, " +
                "FOREIGN KEY(customer_id) REFERENCES " + TABLE_CUSTOMERS + "(customer_id));";

        // Orders table
        String CREATE_ORDERS = "CREATE TABLE " + TABLE_ORDERS + " (" +
                "order_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "customer_id INTEGER NOT NULL, " +
                "beneficiary_id INTEGER, " +
                "dress_template_id INTEGER NOT NULL, " +
                "total_price REAL, " +
                "paid_amount REAL, " +
                "payment_due REAL, " +
                "due_date TEXT, " +
                "order_status TEXT, " +
                "created_at TEXT DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TEXT DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY(customer_id) REFERENCES " + TABLE_CUSTOMERS + "(customer_id), " +
                "FOREIGN KEY(beneficiary_id) REFERENCES " + TABLE_BENEFICIARIES + "(beneficiary_id), " +
                "FOREIGN KEY(dress_template_id) REFERENCES " + TABLE_DRESS_TEMPLATES + "(dress_template_id));";

        // OrderMeasurements table
        String CREATE_ORDER_MEASUREMENTS = "CREATE TABLE " + TABLE_ORDER_MEASUREMENTS + " (" +
                "order_measurement_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "order_id INTEGER NOT NULL, " +
                "field_id INTEGER NOT NULL, " +
                "value REAL, " +
                "FOREIGN KEY(order_id) REFERENCES " + TABLE_ORDERS + "(order_id), " +
                "FOREIGN KEY(field_id) REFERENCES " + TABLE_MEASUREMENT_FIELDS + "(field_id));";

        // ReferenceImages table
        String CREATE_REFERENCE_IMAGES = "CREATE TABLE " + TABLE_REFERENCE_IMAGES + " (" +
                "image_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "order_id INTEGER NOT NULL, " +
                "image_url TEXT NOT NULL, " +
                "uploaded_at TEXT DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY(order_id) REFERENCES " + TABLE_ORDERS + "(order_id));";

        // Execute all create statements
        db.execSQL(CREATE_USERS);
        db.execSQL(CREATE_MEASUREMENT_TEMPLATES);
        db.execSQL(CREATE_MEASUREMENT_FIELDS);
        db.execSQL(CREATE_DRESS_TEMPLATES);
        db.execSQL(CREATE_CUSTOMERS);
        db.execSQL(CREATE_BENEFICIARIES);
        db.execSQL(CREATE_ORDERS);
        db.execSQL(CREATE_ORDER_MEASUREMENTS);
        db.execSQL(CREATE_REFERENCE_IMAGES);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Drop all tables on upgrade for simplicity
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_REFERENCE_IMAGES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ORDER_MEASUREMENTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ORDERS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_BENEFICIARIES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CUSTOMERS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_DRESS_TEMPLATES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MEASUREMENT_FIELDS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MEASUREMENT_TEMPLATES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
        onCreate(db);
    }

    // Example: Insert a new customer with lat/lng
    public long addCustomer(String name, String phone, String address, Double latitude, Double longitude) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("phone_number", phone);
        values.put("address", address);
        if (latitude != null) {
            values.put("latitude", latitude);
        }
        if (longitude != null) {
            values.put("longitude", longitude);
        }
        long id = db.insert(TABLE_CUSTOMERS, null, values);
        db.close();
        return id;
    }

    // Example: Get customer by ID
    public Cursor getCustomerById(long customerId) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(TABLE_CUSTOMERS, null, "customer_id=?", new String[]{String.valueOf(customerId)}, null, null, null);
    }

    // Get customers to list view.
    public Cursor getAllCustomers() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query("CUSTOMERS", null, null, null, null, null, "name ASC");
    }

    //delete customer
    public void deleteCustomer(long customerId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete("CUSTOMERS", "customer_id=?", new String[]{String.valueOf(customerId)});
        db.close();
    }
    //update customer
    public boolean updateCustomer(long id, String name, String phone, String address, double lat, double lng) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("phone_number", phone);
        values.put("address", address);
        values.put("latitude", lat);
        values.put("longitude", lng);

        int rows = db.update("Customers", values, "customer_id=?", new String[]{String.valueOf(id)});
        db.close();
        return rows > 0;
    }

    //handling dresses UI
    public Cursor getAllDresses() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM DressTemplates", null);
    }

    public boolean deleteDress(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.delete("DressTemplates", "dress_template_id=?", new String[]{String.valueOf(id)}) > 0;
    }


}
