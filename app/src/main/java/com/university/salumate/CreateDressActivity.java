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

/**
 * CreateDressActivity — Form for creating a new dress template in the catalogue.
 *
 * <p>A dress template is a reusable configuration that defines a garment type
 * (e.g. "Men's Suit"), its estimated completion time (in days), its estimated
 * price (LKR), and the measurement template that specifies which body measurements
 * need to be recorded when a customer orders this dress style.</p>
 *
 * <h3>Measurement Template Link</h3>
 * <p>Each dress template must be linked to a {@code MeasurementTemplate}. If no
 * templates exist yet, the user is automatically redirected to
 * {@link MeasurementTemplateActivity} to create one first. A shortcut button is
 * also always visible for adding a new measurement template inline.</p>
 *
 * <h3>Resume Behaviour</h3>
 * <p>On {@link #onResume()}, the measurement template spinner is refreshed so that
 * any template created during an inline visit to {@link MeasurementTemplateActivity}
 * is immediately available. The previously selected template is restored if possible.</p>
 */
public class CreateDressActivity extends AppCompatActivity {

    /** Input field for the dress/garment name (e.g. "Men's Formal Shirt"). */
    private EditText txtDressName;
    /** Input field for estimated completion time (stored as free text, e.g. "7"). */
    private EditText txtNumberOfDays;
    /** Input field for the estimated price in LKR. */
    private EditText txtEstimatedPrice;

    /** Dropdown linking this dress template to a measurement template. */
    private Spinner spinnerMeasurementTemplate;

    /** Confirm and Back navigation buttons. */
    private Button btnConfirmDress, btnBackDress;

    /** Database access helper for inserts and spinner data loading. */
    private DBHandler dbHandler;

    /**
     * Parallel list of measurement_template_id values backing the spinner.
     * Index-matched to the spinner display names list.
     */
    private List<Long> templateIds;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_dress);

        // Bind all form views
        txtDressName               = findViewById(R.id.txt_DressName);
        txtNumberOfDays            = findViewById(R.id.txtNumber_NumberofDays);
        txtEstimatedPrice          = findViewById(R.id.txtDecimal_Estimatedprice);
        spinnerMeasurementTemplate = findViewById(R.id.spinnerMeasurementTemplate);
        btnConfirmDress            = findViewById(R.id.btn_ConfirmDress);
        btnBackDress               = findViewById(R.id.btn_BackDress);
        Button btnAddNewMeasurementTemplate = findViewById(R.id.btnAddNewMeasurementTemplate);

        dbHandler   = new DBHandler(this);
        templateIds = new ArrayList<>();

        // Populate the measurement template spinner from the database
        loadMeasurementTemplates();

        // Shortcut button: navigate to MeasurementTemplateActivity to create a new template
        btnAddNewMeasurementTemplate.setOnClickListener(v ->
            startActivity(new Intent(this, MeasurementTemplateActivity.class))
        );

        // Confirm button: validate inputs and save the dress template
        btnConfirmDress.setOnClickListener(v -> insertDress());

        // Back button: discard changes and return to the dress catalogue list
        btnBackDress.setOnClickListener(v -> {
            startActivity(new Intent(CreateDressActivity.this, AllDressesActivity.class));
            finish();
        });
    }

    /**
     * Refreshes the measurement template spinner on resume so that any template
     * created in {@link MeasurementTemplateActivity} during an inline creation flow
     * is immediately available for selection. Preserves the previously selected template.
     */
    @Override
    protected void onResume() {
        super.onResume();

        // Remember the currently selected template ID before reloading the list
        long selectedId = -1L;
        if (spinnerMeasurementTemplate != null && !templateIds.isEmpty()) {
            int pos = spinnerMeasurementTemplate.getSelectedItemPosition();
            if (pos >= 0) selectedId = templateIds.get(pos);
        }

        // Reload the list (may include newly created templates)
        templateIds.clear();
        loadMeasurementTemplates();

        // Restore the previously selected template if it still exists
        if (selectedId != -1L) {
            for (int i = 0; i < templateIds.size(); i++) {
                if (templateIds.get(i).equals(selectedId)) {
                    spinnerMeasurementTemplate.setSelection(i);
                    break;
                }
            }
        }
    }

    /**
     * Queries all measurement templates from the database and populates the
     * measurement template spinner. Shows a placeholder if none exist.
     */
    private void loadMeasurementTemplates() {
        SQLiteDatabase db = dbHandler.getReadableDatabase();
        Cursor cursor     = db.rawQuery(
                "SELECT measurement_template_id, template_name FROM MeasurementTemplates", null);

        List<String> templateNames = new ArrayList<>();

        if (cursor != null && cursor.moveToFirst()) {
            do {
                templateIds.add(cursor.getLong(0));
                templateNames.add(cursor.getString(1));
            } while (cursor.moveToNext());
            cursor.close();
        }

        // Placeholder shown when no measurement templates have been created yet
        if (templateNames.isEmpty()) {
            templateNames.add("No measurement templates found. Please create one.");
            templateIds.add(-1L);
        }

        spinnerMeasurementTemplate.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, templateNames));
    }

    /**
     * Validates all form fields and inserts a new {@code DressTemplate} record.
     *
     * <p>Validation rules:</p>
     * <ul>
     *   <li>All three text fields (name, days, price) must be non-empty.</li>
     *   <li>A valid measurement template must be selected (ID != -1).</li>
     *   <li>The price field must be a parseable decimal number.</li>
     * </ul>
     *
     * <p>On success, navigates to {@link AllDressesActivity}. On validation failure,
     * shows a descriptive Toast and stays on this screen.</p>
     */
    private void insertDress() {
        String name             = txtDressName.getText().toString().trim();
        String estimatedTime    = txtNumberOfDays.getText().toString().trim();
        String estimatedPriceStr = txtEstimatedPrice.getText().toString().trim();

        // Validate all required text inputs
        if (name.isEmpty() || estimatedTime.isEmpty() || estimatedPriceStr.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate a real measurement template is selected (not the placeholder)
        int  selectedPosition     = spinnerMeasurementTemplate.getSelectedItemPosition();
        long measurementTemplateId = -1;
        if (selectedPosition >= 0 && selectedPosition < templateIds.size()) {
            measurementTemplateId = templateIds.get(selectedPosition);
        }

        if (measurementTemplateId == -1) {
            Toast.makeText(this, "Please create a measurement template first.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(CreateDressActivity.this, MeasurementTemplateActivity.class));
            return;
        }

        // Parse the price field — show user-friendly error on invalid format
        double price;
        try {
            price = Double.parseDouble(estimatedPriceStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid price format. Please enter a number.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Insert the new dress template record
        SQLiteDatabase db = dbHandler.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("dress_name",             name);
        values.put("estimated_time",         estimatedTime);
        values.put("estimated_price",        price);
        values.put("measurement_template_id", measurementTemplateId);
        long id = db.insert("DressTemplates", null, values);
        db.close();

        if (id > 0) {
            Toast.makeText(this, "Dress template created successfully!", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(CreateDressActivity.this, AllDressesActivity.class));
            finish();
        } else {
            Toast.makeText(this, "Failed to create dress template. Please try again.", Toast.LENGTH_SHORT).show();
        }
    }
}
