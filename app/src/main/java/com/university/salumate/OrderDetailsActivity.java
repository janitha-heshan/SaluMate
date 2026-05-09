package com.university.salumate;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class OrderDetailsActivity extends AppCompatActivity {

    private TextView txtOrderTitle, txtOrderCustomer, txtOrderStatus, txtOrderFinancials;
    private DBHandler dbHandler;
    private long orderId;
    private long customerId = -1L;
    private String currentStatus = "";

    private androidx.activity.result.ActivityResultLauncher<String> galleryLauncher;
    private androidx.activity.result.ActivityResultLauncher<Void> cameraLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_details);

        dbHandler = new DBHandler(this);
        
        txtOrderTitle = findViewById(R.id.txtOrderTitle);
        txtOrderCustomer = findViewById(R.id.txtOrderCustomer);
        txtOrderStatus = findViewById(R.id.txtOrderStatus);
        txtOrderFinancials = findViewById(R.id.txtOrderFinancials);

        Button btnBack = findViewById(R.id.btnBack);
        Button btnUpdateStatus = findViewById(R.id.btnUpdateStatus);
        Button btnAddItem = findViewById(R.id.btnAddItem);
        Button btnAddImage = findViewById(R.id.btnAddImage);

        // Passed via intent from dashboard order list item click
        orderId = getIntent().getLongExtra("order_id", -1);
        
        if (orderId != -1) {
            loadOrderDetails();
            loadReferenceImages();
        } else {
            Toast.makeText(this, "Invalid Order ID", Toast.LENGTH_SHORT).show();
            finish();
        }

        galleryLauncher = registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                android.net.Uri savedUri = copyUriToInternal(uri);
                if (savedUri != null) promptForImageNoteAndSave(savedUri.toString());
            }
        });

        cameraLauncher = registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.TakePicturePreview(), bitmap -> {
            if (bitmap != null) {
                android.net.Uri savedUri = saveBitmapToInternal((android.graphics.Bitmap) bitmap);
                if (savedUri != null) promptForImageNoteAndSave(savedUri.toString());
            }
        });

        btnAddImage.setOnClickListener(v -> {
            String[] options = {"Gallery", "Camera"};
            new android.app.AlertDialog.Builder(this)
                .setTitle("Select Image Source")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) galleryLauncher.launch("image/*");
                    else cameraLauncher.launch(null);
                }).show();
        });

        btnBack.setOnClickListener(v -> finish());
        btnAddItem.setOnClickListener(v -> showAddItemDialog());
        
        btnUpdateStatus.setOnClickListener(v -> {
            String nextStatus = "Pending";
            if ("Pending".equals(currentStatus)) nextStatus = "In Progress";
            else if ("In Progress".equals(currentStatus)) nextStatus = "Completed";
            else if ("Completed".equals(currentStatus)) nextStatus = "Pending"; // Allow looping if accidental

            dbHandler.getWritableDatabase().execSQL("UPDATE Orders SET order_status = ? WHERE order_id = ?", new String[]{nextStatus, String.valueOf(orderId)});
            Toast.makeText(this, "Order Status Updated to: " + nextStatus, Toast.LENGTH_SHORT).show();
            loadOrderDetails();
        });
    }

    private void loadOrderDetails() {
        Cursor cursor = dbHandler.getReadableDatabase().rawQuery(
            "SELECT o.order_id, c.name, o.total_price, o.paid_amount, o.order_status, o.customer_id " +
            "FROM Orders o " +
            "JOIN Customers c ON o.customer_id = c.customer_id " +
            "WHERE o.order_id = ?", new String[]{String.valueOf(orderId)});
            
        if (cursor != null && cursor.moveToFirst()) {
            String customerName = cursor.getString(1);
            double total = cursor.getDouble(2);
            double advance = cursor.getDouble(3);
            currentStatus = cursor.getString(4);
            customerId = cursor.getLong(5);
            double due = total - advance;
            
            txtOrderTitle.setText("Order #" + orderId);
            txtOrderCustomer.setText("Customer: " + customerName);
            txtOrderStatus.setText("Status: " + currentStatus);
            txtOrderFinancials.setText(String.format("Total: LKR %.2f\nAdvance: LKR %.2f\nDue: LKR %.2f", total, advance, due));
            
            cursor.close();
            loadOrderItems();
        }
    }

    private void loadOrderItems() {
        android.widget.LinearLayout layoutOrderItems = findViewById(R.id.layoutOrderItems);
        layoutOrderItems.removeAllViews();
        Cursor c = dbHandler.getReadableDatabase().rawQuery(
            "SELECT oi.order_item_id, d.dress_name, b.name, oi.price " +
            "FROM OrderItems oi " +
            "JOIN DressTemplates d ON oi.dress_template_id = d.dress_template_id " +
            "LEFT JOIN Beneficiaries b ON oi.beneficiary_id = b.beneficiary_id " +
            "WHERE oi.order_id = ?", new String[]{String.valueOf(orderId)});
            
        if(c != null && c.moveToFirst()) {
            do {
                long itemId = c.getLong(0);
                String dress = c.getString(1);
                String ben = c.getString(2);
                if (ben == null) ben = "Primary Customer";
                double price = c.getDouble(3);
                
                android.widget.LinearLayout row = new android.widget.LinearLayout(this);
                row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                row.setLayoutParams(new android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
                row.setPadding(0, 16, 0, 16);
                
                TextView txtInfo = new TextView(this);
                txtInfo.setText(dress + "\nFor: " + ben + "\nCost: LKR " + price);
                txtInfo.setTextSize(14);
                txtInfo.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                
                Button btnDel = new Button(this);
                btnDel.setText("X");
                btnDel.setOnClickListener(v -> deleteOrderItem(itemId));
                
                row.addView(txtInfo);
                row.addView(btnDel);
                layoutOrderItems.addView(row);
                
            } while (c.moveToNext());
            c.close();
        }
    }

    private void deleteOrderItem(long itemId) {
        android.database.sqlite.SQLiteDatabase db = dbHandler.getWritableDatabase();
        Cursor c = db.rawQuery("SELECT price FROM OrderItems WHERE order_item_id = ?", new String[]{String.valueOf(itemId)});
        if(c != null && c.moveToFirst()) {
            double priceToSubtract = c.getDouble(0);
            c.close();
            db.execSQL("DELETE FROM OrderMeasurements WHERE order_item_id = ?", new String[]{String.valueOf(itemId)});
            db.execSQL("DELETE FROM OrderItems WHERE order_item_id = ?", new String[]{String.valueOf(itemId)});
            db.execSQL("UPDATE Orders SET total_price = total_price - ?, payment_due = payment_due - ? WHERE order_id = ?", new String[]{String.valueOf(priceToSubtract), String.valueOf(priceToSubtract), String.valueOf(orderId)});
            loadOrderDetails();
        }
    }

    private void showAddItemDialog() {
        java.util.List<Long> dressIds = new java.util.ArrayList<>();
        java.util.List<String> dressNames = new java.util.ArrayList<>();
        java.util.List<Double> dressPrices = new java.util.ArrayList<>();
        
        java.util.List<Long> benIds = new java.util.ArrayList<>();
        java.util.List<String> benNames = new java.util.ArrayList<>();
        
        benIds.add(-1L);
        benNames.add("Self (Primary)");
        
        android.database.sqlite.SQLiteDatabase db = dbHandler.getReadableDatabase();
        Cursor cb = db.rawQuery("SELECT beneficiary_id, name FROM Beneficiaries WHERE customer_id = ?", new String[]{String.valueOf(customerId)});
        if(cb != null && cb.moveToFirst()) {
            do { benIds.add(cb.getLong(0)); benNames.add(cb.getString(1)); } while (cb.moveToNext());
            cb.close();
        }
        
        Cursor cd = db.rawQuery("SELECT dress_template_id, dress_name, estimated_price FROM DressTemplates", null);
        if(cd != null && cd.moveToFirst()) {
            do { dressIds.add(cd.getLong(0)); dressNames.add(cd.getString(1)); dressPrices.add(cd.getDouble(2)); } while (cd.moveToNext());
            cd.close();
        }
        
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Add Item to Order");
        
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(32, 16, 32, 16);
        
        TextView lblDress = new TextView(this); lblDress.setText("Dress Template:");
        android.widget.Spinner spinDress = new android.widget.Spinner(this);
        spinDress.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, dressNames));
        
        TextView lblBen = new TextView(this); lblBen.setText("Beneficiary:");
        android.widget.Spinner spinBen = new android.widget.Spinner(this);
        spinBen.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, benNames));
        
        layout.addView(lblDress); layout.addView(spinDress);
        layout.addView(lblBen); layout.addView(spinBen);
        builder.setView(layout);
        
        builder.setPositiveButton("Add", (dialog, which) -> {
            int dIdx = spinDress.getSelectedItemPosition();
            int bIdx = spinBen.getSelectedItemPosition();
            if(dIdx >= 0) {
                long did = dressIds.get(dIdx);
                double dprice = dressPrices.get(dIdx);
                long bid = benIds.get(bIdx);
                
                android.database.sqlite.SQLiteDatabase wDb = dbHandler.getWritableDatabase();
                android.content.ContentValues cv = new android.content.ContentValues();
                cv.put("order_id", orderId);
                if(bid != -1L) cv.put("beneficiary_id", bid);
                cv.put("dress_template_id", did);
                cv.put("price", dprice);
                wDb.insert("OrderItems", null, cv);
                wDb.execSQL("UPDATE Orders SET total_price = total_price + ?, payment_due = payment_due + ? WHERE order_id = ?", new String[]{String.valueOf(dprice), String.valueOf(dprice), String.valueOf(orderId)});
                loadOrderDetails();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void loadReferenceImages() {
        android.widget.LinearLayout layoutRefImages = findViewById(R.id.layoutReferenceImages);
        layoutRefImages.removeAllViews();
        Cursor c = dbHandler.getReadableDatabase().rawQuery("SELECT image_id, image_path, notes FROM ReferenceImages WHERE order_id = ?", new String[]{String.valueOf(orderId)});
        if (c != null && c.moveToFirst()) {
            do {
                long imageId = c.getLong(0);
                String path = c.getString(1);
                String notes = c.getString(2);
                
                android.widget.LinearLayout row = new android.widget.LinearLayout(this);
                row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                row.setLayoutParams(new android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
                row.setPadding(0, 16, 0, 16);
                
                android.widget.ImageView img = new android.widget.ImageView(this);
                img.setLayoutParams(new android.widget.LinearLayout.LayoutParams(150, 150));
                img.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                try { img.setImageURI(android.net.Uri.parse(path)); } catch (Exception ignored) {}
                
                android.widget.LinearLayout textCol = new android.widget.LinearLayout(this);
                textCol.setOrientation(android.widget.LinearLayout.VERTICAL);
                textCol.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                textCol.setPadding(16, 0, 16, 0);
                
                TextView txtNote = new TextView(this);
                txtNote.setText(notes);
                textCol.addView(txtNote);
                
                android.widget.LinearLayout btnCol = new android.widget.LinearLayout(this);
                btnCol.setOrientation(android.widget.LinearLayout.VERTICAL);
                
                Button btnEdit = new Button(this);
                btnEdit.setText("Edit");
                btnEdit.setOnClickListener(v -> promptEditImageNote(imageId, notes));
                
                Button btnDel = new Button(this);
                btnDel.setText("X");
                btnDel.setOnClickListener(v -> {
                    dbHandler.getWritableDatabase().execSQL("DELETE FROM ReferenceImages WHERE image_id = ?", new String[]{String.valueOf(imageId)});
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

    private void promptForImageNoteAndSave(String imagePath) {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Write down considerations...");
        new android.app.AlertDialog.Builder(this)
            .setTitle("Add Notes")
            .setView(input)
            .setPositiveButton("Save", (dialog, which) -> {
                String notes = input.getText().toString();
                android.content.ContentValues cv = new android.content.ContentValues();
                cv.put("order_id", orderId);
                cv.put("image_path", imagePath);
                cv.put("notes", notes);
                dbHandler.getWritableDatabase().insert("ReferenceImages", null, cv);
                loadReferenceImages();
            })
            .show();
    }

    private void promptEditImageNote(long imageId, String currentNote) {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setText(currentNote);
        new android.app.AlertDialog.Builder(this)
            .setTitle("Edit Notes")
            .setView(input)
            .setPositiveButton("Update", (dialog, which) -> {
                String newNote = input.getText().toString();
                android.content.ContentValues cv = new android.content.ContentValues();
                cv.put("notes", newNote);
                dbHandler.getWritableDatabase().update("ReferenceImages", cv, "image_id = ?", new String[]{String.valueOf(imageId)});
                loadReferenceImages();
            }).show();
    }

    private android.net.Uri copyUriToInternal(android.net.Uri sourceUri) {
        try {
            java.io.InputStream in = getContentResolver().openInputStream(sourceUri);
            java.io.File file = new java.io.File(getFilesDir(), "ref_img_" + System.currentTimeMillis() + ".png");
            java.io.FileOutputStream out = new java.io.FileOutputStream(file);
            byte[] buf = new byte[1024]; int len;
            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
            in.close(); out.close();
            return android.net.Uri.fromFile(file);
        } catch (Exception e) { e.printStackTrace(); return null; }
    }

    private android.net.Uri saveBitmapToInternal(android.graphics.Bitmap bitmap) {
        try {
            java.io.File file = new java.io.File(getFilesDir(), "ref_img_" + System.currentTimeMillis() + ".png");
            java.io.FileOutputStream out = new java.io.FileOutputStream(file);
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
            out.flush(); out.close();
            return android.net.Uri.fromFile(file);
        } catch (Exception e) { e.printStackTrace(); return null; }
    }
}
