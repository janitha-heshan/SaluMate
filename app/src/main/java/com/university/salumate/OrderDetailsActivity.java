package com.university.salumate;

import android.content.ContentValues;
import android.database.Cursor;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * OrderDetailsActivity — Full detail view for a single tailoring order.
 *
 * <p>Displays all information about an order including:</p>
 * <ul>
 *   <li>Order ID, customer name, and current status (Pending / In Progress / Completed)</li>
 *   <li>Financial summary: total price, advance paid and remaining balance</li>
 *   <li>Line items (each dress, assigned beneficiary, and price), with delete support</li>
 *   <li>Reference images (gallery or camera) with editable notes</li>
 * </ul>
 *
 * <p>Status transitions cycle forward: Pending → In Progress → Completed → Pending.
 * Tapping "Update Status" advances to the next state in this sequence.</p>
 *
 * <p>Required intent extra: {@code order_id} (long). If not provided, the activity
 * shows a Toast and immediately finishes.</p>
 */
public class OrderDetailsActivity extends AppCompatActivity {

    // ─── Header TextViews ─────────────────────────────────────────────────────
    /** Displays "Order #ID" as the screen title. */
    private TextView txtOrderTitle;
    /** Displays the name of the customer who placed the order. */
    private TextView txtOrderCustomer;
    /** Displays the current order status (Pending / In Progress / Completed). */
    private TextView txtOrderStatus;
    /** Displays a three-line financial summary: Total, Advance Paid, Balance Due. */
    private TextView txtOrderFinancials;

    // ─── State ────────────────────────────────────────────────────────────────
    /** The primary key of the order being viewed. */
    private long orderId;
    /** The customer_id linked to this order, used when adding new line items. */
    private long customerId = -1L;
    /** The live status string; tracks current state for cyclic status updates. */
    private String currentStatus = "";

    /** Database access helper. */
    private DBHandler dbHandler;

    // ─── Image Activity Result Launchers ──────────────────────────────────────
    /** Launches the device gallery to pick a reference image. */
    private androidx.activity.result.ActivityResultLauncher<String> galleryLauncher;
    /** Launches the device camera to take a quick reference photo. */
    private androidx.activity.result.ActivityResultLauncher<Void>   cameraLauncher;

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_details);

        dbHandler = new DBHandler(this);

        // Bind all header TextViews
        txtOrderTitle      = findViewById(R.id.txtOrderTitle);
        txtOrderCustomer   = findViewById(R.id.txtOrderCustomer);
        txtOrderStatus     = findViewById(R.id.txtOrderStatus);
        txtOrderFinancials = findViewById(R.id.txtOrderFinancials);

        Button btnBack         = findViewById(R.id.btnBack);
        Button btnUpdateStatus = findViewById(R.id.btnUpdateStatus);
        Button btnAddItem      = findViewById(R.id.btnAddItem);
        Button btnAddImage     = findViewById(R.id.btnAddImage);

        // Retrieve the order ID passed by the calling activity (Dashboard or All Orders)
        orderId = getIntent().getLongExtra("order_id", -1);
        if (orderId != -1) {
            loadOrderDetails();
            loadReferenceImages();
        } else {
            Toast.makeText(this, "Invalid Order ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // ── Gallery launcher: picks an image from the device gallery ──────────
        galleryLauncher = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        // Copy into app-internal storage to ensure persistent access
                        android.net.Uri savedUri = copyUriToInternal(uri);
                        if (savedUri != null) promptForImageNoteAndSave(savedUri.toString());
                    }
                });

        // ── Camera launcher: captures a thumbnail via the system camera ────────
        cameraLauncher = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.TakePicturePreview(),
                bitmap -> {
                    if (bitmap != null) {
                        android.net.Uri savedUri = saveBitmapToInternal((android.graphics.Bitmap) bitmap);
                        if (savedUri != null) promptForImageNoteAndSave(savedUri.toString());
                    }
                });

        // "Add Image" button: user chooses between Gallery and Camera
        btnAddImage.setOnClickListener(v -> {
            String[] options = {"Gallery", "Camera"};
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Add Reference Image")
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) galleryLauncher.launch("image/*");
                        else            cameraLauncher.launch(null);
                    }).show();
        });

        // Back button: closes the detail view and returns to the list
        btnBack.setOnClickListener(v -> finish());

        // "Add Item" button: opens the dialog to add a new dress line item
        btnAddItem.setOnClickListener(v -> showAddItemDialog());

        // "Update Status" button: advances to the next status in the cycle
        btnUpdateStatus.setOnClickListener(v -> {
            // Determine next status in the Pending → In Progress → Completed cycle
            String nextStatus = "Pending";
            if ("Pending".equals(currentStatus))      nextStatus = "In Progress";
            else if ("In Progress".equals(currentStatus)) nextStatus = "Completed";
            // "Completed" wraps back to "Pending" (allows correction if accidentally advanced)

            dbHandler.getWritableDatabase().execSQL(
                    "UPDATE Orders SET order_status = ? WHERE order_id = ?",
                    new String[]{nextStatus, String.valueOf(orderId)});
            Toast.makeText(this, "Status updated to: " + nextStatus, Toast.LENGTH_SHORT).show();
            loadOrderDetails(); // Refresh the header to reflect the new status
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data Loading
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Queries the database for all header-level data for this order (customer name,
     * pricing, status) and refreshes the TextViews and line-items list.
     * Uses a JOIN so the customer name can be displayed without a second query.
     */
    private void loadOrderDetails() {
        Cursor cursor = dbHandler.getReadableDatabase().rawQuery(
                "SELECT o.order_id, c.name, o.total_price, o.paid_amount, " +
                "o.order_status, o.customer_id " +
                "FROM Orders o " +
                "JOIN Customers c ON o.customer_id = c.customer_id " +
                "WHERE o.order_id = ?",
                new String[]{String.valueOf(orderId)});

        if (cursor != null && cursor.moveToFirst()) {
            String customerName = cursor.getString(1);
            double total        = cursor.getDouble(2);
            double advance      = cursor.getDouble(3);
            currentStatus       = cursor.getString(4);
            customerId          = cursor.getLong(5);
            double balance      = total - advance; // Remaining payment due

            txtOrderTitle.setText("Order #" + orderId);
            txtOrderCustomer.setText("Customer: " + customerName);
            txtOrderStatus.setText("Status: " + currentStatus);
            txtOrderFinancials.setText(String.format(
                    "Total: LKR %.2f\nAdvance Paid: LKR %.2f\nBalance Due: LKR %.2f",
                    total, advance, balance));

            cursor.close();
            loadOrderItems(); // Refresh the line items below the header
        }
    }

    /**
     * Queries and dynamically renders all OrderItems for this order into the
     * {@code layoutOrderItems} LinearLayout. Each row shows the dress name,
     * assigned beneficiary, item price, and a delete button.
     */
    private void loadOrderItems() {
        LinearLayout layoutOrderItems = findViewById(R.id.layoutOrderItems);
        layoutOrderItems.removeAllViews(); // Clear before re-populating

        // JOIN with DressTemplates and Beneficiaries to get human-readable names
        Cursor c = dbHandler.getReadableDatabase().rawQuery(
                "SELECT oi.order_item_id, d.dress_name, b.name, oi.price " +
                "FROM OrderItems oi " +
                "JOIN DressTemplates d ON oi.dress_template_id = d.dress_template_id " +
                "LEFT JOIN Beneficiaries b ON oi.beneficiary_id = b.beneficiary_id " +
                "WHERE oi.order_id = ?",
                new String[]{String.valueOf(orderId)});

        if (c != null && c.moveToFirst()) {
            do {
                long   itemId = c.getLong(0);
                String dress  = c.getString(1);
                String ben    = c.getString(2);
                if (ben == null) ben = "Primary Customer"; // NULL beneficiary = order is for the customer directly
                double price  = c.getDouble(3);

                // Build a horizontal row: text info on the left, delete button on the right
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setLayoutParams(new LinearLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
                row.setPadding(0, 16, 0, 16);

                TextView txtInfo = new TextView(this);
                txtInfo.setText(dress + "\nFor: " + ben + "\nCost: LKR " + price);
                txtInfo.setTextSize(14);
                txtInfo.setLayoutParams(new LinearLayout.LayoutParams(0,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1));

                Button btnDel = new Button(this);
                btnDel.setText("✕");
                btnDel.setOnClickListener(v -> deleteOrderItem(itemId));

                row.addView(txtInfo);
                row.addView(btnDel);
                layoutOrderItems.addView(row);

            } while (c.moveToNext());
            c.close();
        }
    }

    /**
     * Deletes a single OrderItem and its associated measurement records,
     * then adjusts the parent order's total_price and payment_due accordingly.
     *
     * @param itemId The order_item_id of the item to remove.
     */
    private void deleteOrderItem(long itemId) {
        android.database.sqlite.SQLiteDatabase db = dbHandler.getWritableDatabase();

        // Fetch the item's price before deleting so we can deduct it from the order total
        Cursor c = db.rawQuery("SELECT price FROM OrderItems WHERE order_item_id = ?",
                new String[]{String.valueOf(itemId)});
        if (c != null && c.moveToFirst()) {
            double priceToDeduct = c.getDouble(0);
            c.close();

            // Delete measurements first (FK constraint: child before parent)
            db.execSQL("DELETE FROM OrderMeasurements WHERE order_item_id = ?",
                    new String[]{String.valueOf(itemId)});
            // Delete the item itself
            db.execSQL("DELETE FROM OrderItems WHERE order_item_id = ?",
                    new String[]{String.valueOf(itemId)});
            // Reduce order totals
            db.execSQL("UPDATE Orders SET total_price = total_price - ?, " +
                            "payment_due = payment_due - ? WHERE order_id = ?",
                    new String[]{String.valueOf(priceToDeduct),
                                 String.valueOf(priceToDeduct),
                                 String.valueOf(orderId)});

            loadOrderDetails(); // Refresh the full detail view
        }
    }

    /**
     * Presents an AlertDialog allowing the user to add a new dress line item to
     * the current order. Spinners allow selection of dress template and beneficiary.
     * On confirm, the item is inserted and the order total is updated.
     */
    private void showAddItemDialog() {
        java.util.List<Long>   dressIds    = new java.util.ArrayList<>();
        java.util.List<String> dressNames  = new java.util.ArrayList<>();
        java.util.List<Double> dressPrices = new java.util.ArrayList<>();
        java.util.List<Long>   benIds      = new java.util.ArrayList<>();
        java.util.List<String> benNames    = new java.util.ArrayList<>();

        // Pre-populate beneficiary list with "Self" as the default option
        benIds.add(-1L);
        benNames.add("Self (Primary Customer)");

        android.database.sqlite.SQLiteDatabase db = dbHandler.getReadableDatabase();

        // Load beneficiaries for the owning customer
        Cursor cb = db.rawQuery(
                "SELECT beneficiary_id, name FROM Beneficiaries WHERE customer_id = ?",
                new String[]{String.valueOf(customerId)});
        if (cb != null && cb.moveToFirst()) {
            do { benIds.add(cb.getLong(0)); benNames.add(cb.getString(1)); }
            while (cb.moveToNext());
            cb.close();
        }

        // Load all available dress templates
        Cursor cd = db.rawQuery(
                "SELECT dress_template_id, dress_name, estimated_price FROM DressTemplates", null);
        if (cd != null && cd.moveToFirst()) {
            do {
                dressIds.add(cd.getLong(0));
                dressNames.add(cd.getString(1));
                dressPrices.add(cd.getDouble(2));
            } while (cd.moveToNext());
            cd.close();
        }

        // Build the dialog layout with two spinners
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 16, 32, 16);

        TextView lblDress = new TextView(this);
        lblDress.setText("Dress Template:");
        android.widget.Spinner spinDress = new android.widget.Spinner(this);
        spinDress.setAdapter(new android.widget.ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, dressNames));

        TextView lblBen = new TextView(this);
        lblBen.setText("Beneficiary:");
        android.widget.Spinner spinBen = new android.widget.Spinner(this);
        spinBen.setAdapter(new android.widget.ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, benNames));

        layout.addView(lblDress); layout.addView(spinDress);
        layout.addView(lblBen);   layout.addView(spinBen);

        new android.app.AlertDialog.Builder(this)
                .setTitle("Add Item to Order")
                .setView(layout)
                .setPositiveButton("Add", (dialog, which) -> {
                    int dIdx = spinDress.getSelectedItemPosition();
                    int bIdx = spinBen.getSelectedItemPosition();

                    if (dIdx >= 0) {
                        long   did    = dressIds.get(dIdx);
                        double dprice = dressPrices.get(dIdx);
                        long   bid    = benIds.get(bIdx);

                        // Insert the new OrderItem
                        android.database.sqlite.SQLiteDatabase wDb = dbHandler.getWritableDatabase();
                        ContentValues cv = new ContentValues();
                        cv.put("order_id",          orderId);
                        cv.put("dress_template_id",  did);
                        cv.put("price",              dprice);
                        if (bid != -1L) cv.put("beneficiary_id", bid); // Omit if "Self"

                        wDb.insert("OrderItems", null, cv);

                        // Increase order total to reflect the new item cost
                        wDb.execSQL("UPDATE Orders SET total_price = total_price + ?, " +
                                "payment_due = payment_due + ? WHERE order_id = ?",
                                new String[]{String.valueOf(dprice), String.valueOf(dprice),
                                             String.valueOf(orderId)});

                        loadOrderDetails(); // Refresh the UI
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Reference Image Handling
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Loads all reference images for this order from the database and renders them
     * as horizontal rows in the {@code layoutReferenceImages} LinearLayout.
     * Each row shows a thumbnail, the notes text, an Edit button, and a Delete button.
     */
    private void loadReferenceImages() {
        LinearLayout layoutRefImages = findViewById(R.id.layoutReferenceImages);
        layoutRefImages.removeAllViews(); // Clear before re-populating

        Cursor c = dbHandler.getReadableDatabase().rawQuery(
                "SELECT image_id, image_path, notes FROM ReferenceImages WHERE order_id = ?",
                new String[]{String.valueOf(orderId)});

        if (c != null && c.moveToFirst()) {
            do {
                long   imageId = c.getLong(0);
                String path    = c.getString(1);
                String notes   = c.getString(2);

                // Build a horizontal row: thumbnail | notes text | edit/delete buttons
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setLayoutParams(new LinearLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
                row.setPadding(0, 16, 0, 16);

                // Thumbnail image (150×150 dp, center-cropped)
                android.widget.ImageView img = new android.widget.ImageView(this);
                img.setLayoutParams(new LinearLayout.LayoutParams(150, 150));
                img.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                try { img.setImageURI(android.net.Uri.parse(path)); }
                catch (Exception ignored) { /* image no longer accessible — show broken state */ }

                // Notes text column
                LinearLayout textCol = new LinearLayout(this);
                textCol.setOrientation(LinearLayout.VERTICAL);
                textCol.setLayoutParams(new LinearLayout.LayoutParams(0,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                textCol.setPadding(16, 0, 16, 0);

                android.widget.TextView txtNote = new android.widget.TextView(this);
                txtNote.setText(notes);
                textCol.addView(txtNote);

                // Edit/Delete button column
                LinearLayout btnCol = new LinearLayout(this);
                btnCol.setOrientation(LinearLayout.VERTICAL);

                Button btnEdit = new Button(this);
                btnEdit.setText("Edit");
                btnEdit.setOnClickListener(v -> promptEditImageNote(imageId, notes));

                Button btnDel = new Button(this);
                btnDel.setText("✕");
                btnDel.setOnClickListener(v -> {
                    // Remove the image record and refresh the image gallery section
                    dbHandler.getWritableDatabase().execSQL(
                            "DELETE FROM ReferenceImages WHERE image_id = ?",
                            new String[]{String.valueOf(imageId)});
                    loadReferenceImages();
                });

                btnCol.addView(btnEdit);
                btnCol.addView(btnDel);

                row.addView(img);
                row.addView(textCol);
                row.addView(btnCol);
                layoutRefImages.addView(row);

            } while (c.moveToNext());
            c.close();
        }
    }

    /**
     * Shows a dialog prompting the user to enter optional notes for a newly
     * added reference image, then saves the image record to the database.
     *
     * @param imagePath The internal URI string of the saved image file.
     */
    private void promptForImageNoteAndSave(String imagePath) {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Optional note about this image…");

        new android.app.AlertDialog.Builder(this)
                .setTitle("Image Notes")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String notes = input.getText().toString();
                    ContentValues cv = new ContentValues();
                    cv.put("order_id",   orderId);
                    cv.put("image_path", imagePath);
                    cv.put("notes",      notes);
                    dbHandler.getWritableDatabase().insert("ReferenceImages", null, cv);
                    loadReferenceImages();
                })
                .show();
    }

    /**
     * Shows a pre-filled dialog for editing the notes on an existing reference image.
     *
     * @param imageId     The image_id of the record to update.
     * @param currentNote The existing note text to pre-fill in the dialog input.
     */
    private void promptEditImageNote(long imageId, String currentNote) {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setText(currentNote);

        new android.app.AlertDialog.Builder(this)
                .setTitle("Edit Image Notes")
                .setView(input)
                .setPositiveButton("Update", (dialog, which) -> {
                    ContentValues cv = new ContentValues();
                    cv.put("notes", input.getText().toString());
                    dbHandler.getWritableDatabase().update(
                            "ReferenceImages", cv,
                            "image_id = ?", new String[]{String.valueOf(imageId)});
                    loadReferenceImages();
                })
                .show();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // File I/O Utilities
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Copies an externally-selected image (via gallery content URI) into the app's
     * private internal storage so the file remains accessible after the user clears
     * the selection or the original is moved/deleted.
     *
     * @param sourceUri The content URI of the source image.
     * @return A {@code file://} URI pointing to the app-internal copy, or null on error.
     */
    private android.net.Uri copyUriToInternal(android.net.Uri sourceUri) {
        try {
            java.io.InputStream in  = getContentResolver().openInputStream(sourceUri);
            java.io.File file = new java.io.File(getFilesDir(),
                    "ref_img_" + System.currentTimeMillis() + ".png");
            java.io.FileOutputStream out = new java.io.FileOutputStream(file);
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
            in.close();
            out.close();
            return android.net.Uri.fromFile(file);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Saves a camera-captured {@link android.graphics.Bitmap} to the app's private internal
     * storage as a PNG file.
     *
     * @param bitmap The in-memory bitmap returned by the camera launcher.
     * @return A {@code file://} URI pointing to the saved PNG file, or null on error.
     */
    private android.net.Uri saveBitmapToInternal(android.graphics.Bitmap bitmap) {
        try {
            java.io.File file = new java.io.File(getFilesDir(),
                    "ref_img_" + System.currentTimeMillis() + ".png");
            java.io.FileOutputStream out = new java.io.FileOutputStream(file);
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();
            return android.net.Uri.fromFile(file);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
