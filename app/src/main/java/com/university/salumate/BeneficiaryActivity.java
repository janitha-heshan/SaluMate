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

/**
 * BeneficiaryActivity — Form for creating a new beneficiary linked to a customer.
 *
 * <p>A beneficiary is a family member or dependent for whom a customer also places
 * tailoring orders (e.g. a spouse or child). This activity captures the beneficiary's
 * name, gender, and relationship to the customer.</p>
 *
 * <h3>Launch Modes</h3>
 * <ul>
 *   <li><b>Standalone</b> ({@code extras.standalone = true}): Called from the Order Wizard
 *       to quickly add a beneficiary mid-flow. On save, simply finishes and returns to
 *       the wizard via {@link #onResume()} state restoration.</li>
 *   <li><b>Normal</b>: After saving, navigates to {@link CreateOrderActivity} with the
 *       newly created beneficiary and customer IDs pre-set.</li>
 * </ul>
 *
 * <p>Required intent extra: {@code customer_id} (long) — the owner of this beneficiary.</p>
 */
public class BeneficiaryActivity extends AppCompatActivity {

    /** Input field for the beneficiary's full name. */
    private EditText nameField;

    /** Radio group for selecting biological gender. */
    private RadioGroup rgGender;

    /** Radio group for selecting relationship to the primary customer. */
    private RadioGroup rgRelation;

    /** Database access helper for inserting the beneficiary record. */
    private DBHandler dbHandler;

    /**
     * The customer_id this beneficiary belongs to.
     * Passed via intent extra; defaults to -1 if not provided.
     */
    private long customerId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_beneficiary);

        dbHandler = new DBHandler(this);

        // Retrieve the owner customer's ID from the launching intent
        customerId = getIntent().getLongExtra("customer_id", -1);

        // Bind form input views
        nameField  = findViewById(R.id.editBeneficiaryName);
        rgGender   = findViewById(R.id.rgGender);
        rgRelation = findViewById(R.id.rgRelation);

        // Save button — validates inputs and persists the beneficiary record
        MaterialButton btnSave = findViewById(R.id.btnSaveBeneficiary);
        btnSave.setOnClickListener(v -> saveBeneficiary());
    }

    /**
     * Validates all input fields, inserts the beneficiary into the database,
     * and navigates back appropriately based on the launch mode.
     *
     * <p>Validation rules:</p>
     * <ul>
     *   <li>A valid customer_id must have been provided via intent extra.</li>
     *   <li>The beneficiary's name field cannot be empty.</li>
     * </ul>
     */
    private void saveBeneficiary() {
        // Guard: a customer must already be selected to associate this beneficiary
        if (customerId == -1) {
            Toast.makeText(this, "No customer selected. Cannot add beneficiary.", Toast.LENGTH_SHORT).show();
            return;
        }

        String name = nameField.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter the beneficiary's name.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Resolve gender from the selected radio button (defaults to "Unknown")
        String gender = "Unknown";
        int selectedGenderId = rgGender.getCheckedRadioButtonId();
        if (selectedGenderId != -1) {
            gender = ((RadioButton) findViewById(selectedGenderId)).getText().toString();
        }

        // Resolve relationship from the selected radio button (defaults to "Self")
        String relation = "Self";
        int selectedRelationId = rgRelation.getCheckedRadioButtonId();
        if (selectedRelationId != -1) {
            relation = ((RadioButton) findViewById(selectedRelationId)).getText().toString();
        }

        // Insert the beneficiary record using the writable database directly
        // (DBHandler does not yet expose an explicit addBeneficiary() helper)
        SQLiteDatabase db = dbHandler.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("customer_id", customerId);
        values.put("name",        name);
        values.put("gender",      gender);
        values.put("relation",    relation);
        long newId = db.insert("Beneficiaries", null, values);
        db.close();

        if (newId != -1) {
            Toast.makeText(this, "Beneficiary saved successfully!", Toast.LENGTH_SHORT).show();

            // "standalone" = was launched from within the Order Wizard mid-flow
            if (getIntent().getBooleanExtra("standalone", false)) {
                // Simply finish — the wizard's onResume() will reload beneficiaries
                finish();
            } else {
                // Navigate to order creation with the new beneficiary pre-selected
                Intent intent = new Intent(BeneficiaryActivity.this, CreateOrderActivity.class);
                intent.putExtra("customer_id",    customerId);
                intent.putExtra("beneficiary_id", newId);
                startActivity(intent);
                finish();
            }
        } else {
            Toast.makeText(this, "Failed to save beneficiary. Please try again.", Toast.LENGTH_SHORT).show();
        }
    }
}
