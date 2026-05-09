package com.university.salumate;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class MeasurementTemplateActivity extends AppCompatActivity {

    private EditText templateNameField;
    private LinearLayout fieldsContainer;
    private DBHandler dbHandler;
    private List<EditText> dynamicFields;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_measurement_template);

        dbHandler = new DBHandler(this);
        dynamicFields = new ArrayList<>();

        templateNameField = findViewById(R.id.editTemplateName);
        fieldsContainer = findViewById(R.id.fieldsContainer);

        MaterialButton btnAddField = findViewById(R.id.btnAddField);
        MaterialButton btnSaveTemplate = findViewById(R.id.btnSaveTemplate);

        btnAddField.setOnClickListener(v -> addFieldRow());
        btnSaveTemplate.setOnClickListener(v -> saveTemplate());
        
        // Add one initial field row
        addFieldRow();
    }

    private void addFieldRow() {
        EditText newField = new EditText(this);
        newField.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        newField.setHint("Field Name (e.g. Waist, Chest)");
        newField.setPadding(0, 32, 0, 32);
        
        fieldsContainer.addView(newField);
        dynamicFields.add(newField);
    }

    private void saveTemplate() {
        String templateName = templateNameField.getText().toString().trim();
        if (templateName.isEmpty()) {
            Toast.makeText(this, "Please enter a template name.", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> validFields = new ArrayList<>();
        for (EditText et : dynamicFields) {
            String val = et.getText().toString().trim();
            if (!val.isEmpty()) {
                validFields.add(val);
            }
        }

        if (validFields.isEmpty()) {
            Toast.makeText(this, "Add at least one valid measurement field.", Toast.LENGTH_SHORT).show();
            return;
        }

        SQLiteDatabase db = dbHandler.getWritableDatabase();
        
        // 1. Insert Template
        ContentValues templateValues = new ContentValues();
        templateValues.put("template_name", templateName);
        templateValues.put("created_by", 1); // Mock user ID
        long templateId = db.insert("MeasurementTemplates", null, templateValues);

        if (templateId != -1) {
            // 2. Insert Fields mapped to templateId
            for (String fieldName : validFields) {
                ContentValues fieldValues = new ContentValues();
                fieldValues.put("measurement_template_id", templateId);
                fieldValues.put("field_name", fieldName);
                fieldValues.put("unit", "inches"); // default
                db.insert("MeasurementFields", null, fieldValues);
            }
            Toast.makeText(this, "Measurement Template Saved!", Toast.LENGTH_SHORT).show();
            db.close();
            finish();
        } else {
            Toast.makeText(this, "Failed to save template.", Toast.LENGTH_SHORT).show();
            db.close();
        }
    }
}
