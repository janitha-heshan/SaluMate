package com.university.salumate;

import android.content.ContentValues;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class CreateDressActivity extends AppCompatActivity {

    private EditText txtDressName, txtNumberOfDays, txtEstimatedPrice;
    private Button btnConfirmDress, btnBackDress;

    private DBHandler dbHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_dress);

        // Link UI elements
        txtDressName = findViewById(R.id.txt_DressName);
        txtNumberOfDays = findViewById(R.id.txtNumber_NumberofDays);
        txtEstimatedPrice = findViewById(R.id.txtDecimal_Estimatedprice);
        btnConfirmDress = findViewById(R.id.btn_ConfirmDress);
        btnBackDress = findViewById(R.id.btn_BackDress);

        dbHandler = new DBHandler(this);

        // Confirm button inserts dress
        btnConfirmDress.setOnClickListener(v -> insertDress());

        // Back button goes to AllDressesActivity
        btnBackDress.setOnClickListener(v -> {
            startActivity(new Intent(CreateDressActivity.this, AllDressesActivity.class));
            finish();
        });
    }

    private void insertDress() {
        String name = txtDressName.getText().toString().trim();
        String estimatedTime = txtNumberOfDays.getText().toString().trim();
        String estimatedPriceStr = txtEstimatedPrice.getText().toString().trim();

        if (name.isEmpty() || estimatedTime.isEmpty() || estimatedPriceStr.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        double price;
        try {
            price = Double.parseDouble(estimatedPriceStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid price format", Toast.LENGTH_SHORT).show();
            return;
        }

        SQLiteDatabase db = dbHandler.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("dress_name", name);
        values.put("estimated_time", estimatedTime);
        values.put("estimated_price", price);

        long id = db.insert("DressTemplates", null, values);
        db.close();

        if (id > 0) {
            Toast.makeText(this, "Dress created successfully", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(CreateDressActivity.this, AllDressesActivity.class));
            finish();
        } else {
            Toast.makeText(this, "Failed to create dress", Toast.LENGTH_SHORT).show();
        }
    }
}
