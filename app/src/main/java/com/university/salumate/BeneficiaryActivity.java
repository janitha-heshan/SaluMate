package com.university.salumate;

import android.content.ContentValues;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class BeneficiaryActivity extends AppCompatActivity {

    private EditText nameField;
    private RadioGroup rgGender, rgRelation;
    private DBHandler dbHandler;
    private long customerId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_beneficiary);

        dbHandler = new DBHandler(this);
        
        // Assume customer_id is passed when launching this activity after customer creation
        customerId = getIntent().getLongExtra("customer_id", -1);

        nameField = findViewById(R.id.editBeneficiaryName);
        rgGender = findViewById(R.id.rgGender);
        rgRelation = findViewById(R.id.rgRelation);

        MaterialButton btnSave = findViewById(R.id.btnSaveBeneficiary);
        
        btnSave.setOnClickListener(v -> saveBeneficiary());
    }
    
    private void saveBeneficiary() {
        if (customerId == -1) {
            Toast.makeText(this, "No valid customer selected to associate beneficiary.", Toast.LENGTH_SHORT).show();
            return;
        }

        String name = nameField.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter beneficiary name.", Toast.LENGTH_SHORT).show();
            return;
        }

        int selectedGenderId = rgGender.getCheckedRadioButtonId();
        String gender = "Unknown";
        if (selectedGenderId != -1) {
            RadioButton rb = findViewById(selectedGenderId);
            gender = rb.getText().toString();
        }

        int selectedRelationId = rgRelation.getCheckedRadioButtonId();
        String relation = "Self";
        if (selectedRelationId != -1) {
            RadioButton rb = findViewById(selectedRelationId);
            relation = rb.getText().toString();
        }

        // Insert directly using DBHandler writable database since DBHandler doesn't have an explicit addBeneficiary method yet
        SQLiteDatabase db = dbHandler.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("customer_id", customerId);
        values.put("name", name);
        values.put("gender", gender);
        values.put("relation", relation);
        
        long id = db.insert("Beneficiaries", null, values);
        db.close();

        if (id != -1) {
            Toast.makeText(this, "Beneficiary saved!", Toast.LENGTH_SHORT).show();
            
            if (getIntent().getBooleanExtra("standalone", false)) {
                finish();
            } else {
                Intent intent = new Intent(BeneficiaryActivity.this, CreateOrderActivity.class);
                intent.putExtra("customer_id", customerId);
                intent.putExtra("beneficiary_id", id);
                startActivity(intent);
                finish();
            }
        } else {
            Toast.makeText(this, "Error saving beneficiary.", Toast.LENGTH_SHORT).show();
        }
    }
}
