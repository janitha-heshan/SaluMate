package com.university.salumate;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class CreateDressActivity extends AppCompatActivity {

    private EditText txtDressName, txtNumberOfDays, txtEstimatedPrice;
    private Spinner spinnerMeasurementTemplate;
    private Button btnConfirmDress, btnBackDress;
    private DBHandler dbHandler;
    private List<Long> templateIds;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_dress);

        txtDressName = findViewById(R.id.txt_DressName);
        txtNumberOfDays = findViewById(R.id.txtNumber_NumberofDays);
        txtEstimatedPrice = findViewById(R.id.txtDecimal_Estimatedprice);
        spinnerMeasurementTemplate = findViewById(R.id.spinnerMeasurementTemplate);
        btnConfirmDress = findViewById(R.id.btn_ConfirmDress);
        btnBackDress = findViewById(R.id.btn_BackDress);
        Button btnAddNewMeasurementTemplate = findViewById(R.id.btnAddNewMeasurementTemplate);

        dbHandler = new DBHandler(this);
        templateIds = new ArrayList<>();
        
        loadMeasurementTemplates();

        btnAddNewMeasurementTemplate.setOnClickListener(v -> {
            Intent intent = new Intent(this, MeasurementTemplateActivity.class);
            startActivity(intent);
        });

        btnConfirmDress.setOnClickListener(v -> insertDress());
        
        btnBackDress.setOnClickListener(v -> {
            startActivity(new Intent(CreateDressActivity.this, AllDressesActivity.class));
            finish();
        });
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        long selectedId = -1L;
        if (spinnerMeasurementTemplate != null && spinnerMeasurementTemplate.getSelectedItemPosition() >= 0 && !templateIds.isEmpty()) {
            selectedId = templateIds.get(spinnerMeasurementTemplate.getSelectedItemPosition());
        }
        
        templateIds.clear();
        loadMeasurementTemplates();
        
        if (selectedId != -1L) {
            for (int i = 0; i < templateIds.size(); i++) {
                if (templateIds.get(i) == selectedId) {
                    spinnerMeasurementTemplate.setSelection(i);
                    break;
                }
            }
        }
    }
    
    private void loadMeasurementTemplates() {
        SQLiteDatabase db = dbHandler.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT measurement_template_id, template_name FROM MeasurementTemplates", null);
        
        List<String> templateNames = new ArrayList<>();
        
        if (cursor != null && cursor.moveToFirst()) {
            do {
                templateIds.add(cursor.getLong(0));
                templateNames.add(cursor.getString(1));
            } while (cursor.moveToNext());
            cursor.close();
        }
        
        if (templateNames.isEmpty()) {
            templateNames.add("No Measurement Templates Found");
            templateIds.add(-1L);
        }
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, templateNames);
        spinnerMeasurementTemplate.setAdapter(adapter);
    }

    private void insertDress() {
        String name = txtDressName.getText().toString().trim();
        String estimatedTime = txtNumberOfDays.getText().toString().trim();
        String estimatedPriceStr = txtEstimatedPrice.getText().toString().trim();

        if (name.isEmpty() || estimatedTime.isEmpty() || estimatedPriceStr.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }
        
        int selectedPosition = spinnerMeasurementTemplate.getSelectedItemPosition();
        long measurementTemplateId = -1;
        if (selectedPosition >= 0 && selectedPosition < templateIds.size()) {
            measurementTemplateId = templateIds.get(selectedPosition);
        }
        
        if (measurementTemplateId == -1) {
            Toast.makeText(this, "Please create a measurement template first.", Toast.LENGTH_SHORT).show();
            // Start MeasurementTemplateActivity so the user can create one
            startActivity(new Intent(CreateDressActivity.this, MeasurementTemplateActivity.class));
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
        values.put("measurement_template_id", measurementTemplateId);

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
