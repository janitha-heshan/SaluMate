package com.university.salumate;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class UpdateDressActivity extends AppCompatActivity {

    EditText txtName, txtDays, txtPrice;
    Button btnConfirm, btnBack;
    long dressId;
    DBHandler db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update_dress);

        txtName = findViewById(R.id.txt_UpdateDressName);
        txtDays = findViewById(R.id.txtNumber_UpdateNumberofDays);
        txtPrice = findViewById(R.id.txtDecimal_UpdateEstimatedprice);
        btnConfirm = findViewById(R.id.btn_UpdateConfirmDress);
        btnBack = findViewById(R.id.btn_UpdateBackDress);

        db = new DBHandler(this);

        // Get dress ID from Intent
        dressId = getIntent().getLongExtra("dress_id", -1);
        Toast.makeText(this, " Fix Logic Dress ID : "+dressId, Toast.LENGTH_SHORT).show();

        if (dressId != -1) {
            loadDressDetails(dressId);
        }

        // Confirm button logic
        btnConfirm.setOnClickListener(v -> {
            String name = txtName.getText().toString().trim();
            String days = txtDays.getText().toString().trim();
            String priceStr = txtPrice.getText().toString().trim();

            if (name.isEmpty() || days.isEmpty() || priceStr.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            double price = Double.parseDouble(priceStr);
            boolean updated = db.updateDress(dressId, name, days, price);

            if (updated) {
                Toast.makeText(this, "Dress updated successfully", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, AllDressesActivity.class));
                finish();
            } else {
                Toast.makeText(this, "Update failed", Toast.LENGTH_SHORT).show();
            }
        });

        // Back button logic
        btnBack.setOnClickListener(v -> {
            startActivity(new Intent(this, AllDressesActivity.class));
            finish();
        });
    }

    private void loadDressDetails(long id) {
        Cursor cursor = db.getDressById(id);
        if (cursor != null && cursor.moveToFirst()) {
            txtName.setText(cursor.getString(cursor.getColumnIndexOrThrow("dress_name")));
            txtDays.setText(cursor.getString(cursor.getColumnIndexOrThrow("estimated_time")));
            txtPrice.setText(String.valueOf(cursor.getDouble(cursor.getColumnIndexOrThrow("estimated_price"))));
            cursor.close();
        }
    }
}
