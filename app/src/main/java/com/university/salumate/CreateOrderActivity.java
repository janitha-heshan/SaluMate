package com.university.salumate;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CreateOrderActivity — Multi-step wizard for placing a new tailoring order.
 *
 * <h3>Wizard Steps</h3>
 * <ol>
 *   <li><b>Step 1 – Customer</b>: Select an existing customer and optionally a
 *       beneficiary (family member). Also allows quick creation of a new beneficiary
 *       without leaving the wizard.</li>
 *   <li><b>Step 2 – Dress</b>: Choose a dress template. Supports on-the-fly creation
 *       of new dress templates via a shortcut button.</li>
 *   <li><b>Step 3 – Measurements</b>: Dynamically generated input fields are rendered
 *       based on the measurement template linked to the selected dress.</li>
 *   <li><b>Step 4 – Payment &amp; Summary</b>: Enter advance payment, set a due date,
 *       and review the full order summary before final submission.</li>
 * </ol>
 *
 * <p>The wizard uses a {@link ViewFlipper} to animate between steps.
 * All data is committed to SQLite in a single atomic transaction on "Place Order".</p>
 */
public class CreateOrderActivity extends AppCompatActivity {

    // ─── Wizard UI ────────────────────────────────────────────────────────────
    /** Flips between the 4 wizard steps without reloading the activity. */
    private ViewFlipper wizardFlipper;

    /** Step 1 dropdowns for customer and their beneficiary. */
    private Spinner spinnerCustomer, spinnerBeneficiary;

    /** Step 2 dropdown for selecting a dress template. */
    private Spinner spinnerDress;

    /** Step 3 container; measurement input fields are added here dynamically. */
    private LinearLayout measurementsContainer;

    /** Step 4 read-only order summary text. */
    private TextView txtOrderSummary;

    /** Step 4 advance payment input and date picker field. */
    private EditText editAdvancePayment, editDueDate;

    // ─── Database ─────────────────────────────────────────────────────────────
    /** Central database helper. */
    private DBHandler dbHandler;

    // ─── Spinner Data Backing Lists ───────────────────────────────────────────
    /** Parallel IDs and display names for the customer spinner. */
    private List<Long>   customerIds   = new ArrayList<>();
    private List<String> customerNames = new ArrayList<>();

    /** Parallel IDs and display names for the beneficiary spinner. */
    private List<Long>   beneficiaryIds   = new ArrayList<>();
    private List<String> beneficiaryNames = new ArrayList<>();

    /** Parallel IDs, names, prices, and linked measurement template IDs for dress spinner. */
    private List<Long>   dressIds                     = new ArrayList<>();
    private List<String> dressNames                   = new ArrayList<>();
    private List<Double> dressPrices                  = new ArrayList<>();
    private List<Long>   dressMeasurementTemplateIds  = new ArrayList<>();

    /**
     * Maps each measurement field_id to its corresponding EditText input widget.
     * Populated dynamically in Step 3 and consumed during order submission.
     */
    private Map<Long, EditText> activeMeasurementFields = new HashMap<>();

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_order);

        dbHandler = new DBHandler(this);

        // Bind wizard container and all step-specific views
        wizardFlipper        = findViewById(R.id.orderWizardFlipper);
        spinnerCustomer      = findViewById(R.id.spinnerOrderCustomer);
        spinnerBeneficiary   = findViewById(R.id.spinnerOrderBeneficiary);
        spinnerDress         = findViewById(R.id.spinnerOrderDress);
        measurementsContainer = findViewById(R.id.orderMeasurementsContainer);
        txtOrderSummary      = findViewById(R.id.txtOrderSummary);
        editAdvancePayment   = findViewById(R.id.editAdvancePayment);
        editDueDate          = findViewById(R.id.editDueDate);

        // Open a date picker when the due date field is tapped
        editDueDate.setOnClickListener(v -> {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            new android.app.DatePickerDialog(this, (view, y, m, d) -> {
                // Format as ISO 8601 date string (YYYY-MM-DD)
                editDueDate.setText(String.format("%04d-%02d-%02d", y, m + 1, d));
            }, cal.get(java.util.Calendar.YEAR),
               cal.get(java.util.Calendar.MONTH),
               cal.get(java.util.Calendar.DAY_OF_MONTH)).show();
        });

        // Wire up wizard navigation buttons
        Button btnNextDress         = findViewById(R.id.btnNextDress);
        Button btnPrevCustomer      = findViewById(R.id.btnPrevCustomer);
        Button btnNextMeasurements  = findViewById(R.id.btnNextMeasurements);
        Button btnPrevDress         = findViewById(R.id.btnPrevDress);
        Button btnNextPayment       = findViewById(R.id.btnNextPayment);
        Button btnPrevMeasurements  = findViewById(R.id.btnPrevMeasurements);
        Button btnPlaceOrder        = findViewById(R.id.btnPlaceOrder);
        Button btnAddNewBeneficiary = findViewById(R.id.btnAddNewBeneficiary);
        Button btnAddNewDress       = findViewById(R.id.btnAddNewDress);

        // Initial data loads for Step 1 and Step 2 spinners
        loadCustomers();
        loadDresses();

        // Reload beneficiaries whenever the selected customer changes
        spinnerCustomer.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent,
                                       android.view.View view, int position, long id) {
                if (customerIds.size() > position && customerIds.get(position) != -1L) {
                    loadBeneficiaries(customerIds.get(position));
                }
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        // "Add Beneficiary" shortcut — opens BeneficiaryActivity for the selected customer
        btnAddNewBeneficiary.setOnClickListener(v -> {
            int cIdx = spinnerCustomer.getSelectedItemPosition();
            if (cIdx >= 0 && customerIds.get(cIdx) != -1L) {
                Intent intent = new Intent(this, BeneficiaryActivity.class);
                intent.putExtra("customer_id", customerIds.get(cIdx));
                intent.putExtra("standalone", true);
                startActivity(intent);
            } else {
                Toast.makeText(this, "Select a valid customer first", Toast.LENGTH_SHORT).show();
            }
        });

        // "Add Dress Template" shortcut — opens CreateDressActivity inline
        btnAddNewDress.setOnClickListener(v ->
            startActivity(new Intent(this, CreateDressActivity.class))
        );

        // Step 1 → Step 2: validate a customer is selected before advancing
        btnNextDress.setOnClickListener(v -> {
            if (customerIds.isEmpty() || customerIds.get(0) == -1L) {
                Toast.makeText(this, "Valid Customer required", Toast.LENGTH_SHORT).show();
            } else {
                wizardFlipper.showNext();
            }
        });

        // Step 2 → Step 1 (back)
        btnPrevCustomer.setOnClickListener(v -> wizardFlipper.showPrevious());

        // Step 2 → Step 3: validate dress and generate measurement fields
        btnNextMeasurements.setOnClickListener(v -> {
            if (dressIds.isEmpty() || dressIds.get(0) == -1L) {
                Toast.makeText(this, "Valid Dress Template required", Toast.LENGTH_SHORT).show();
                return;
            }
            long templateId = dressMeasurementTemplateIds.get(spinnerDress.getSelectedItemPosition());
            generateMeasurementFields(templateId); // Render dynamic input fields
            wizardFlipper.showNext();
        });

        // Step 3 → Step 2 (back)
        btnPrevDress.setOnClickListener(v -> wizardFlipper.showPrevious());

        // Step 3 → Step 4: compose the human-readable summary
        btnNextPayment.setOnClickListener(v -> {
            updateOrderSummary();
            wizardFlipper.showNext();
        });

        // Step 4 → Step 3 (back)
        btnPrevMeasurements.setOnClickListener(v -> wizardFlipper.showPrevious());

        // Step 4 "Place Order": commit all data to the database
        btnPlaceOrder.setOnClickListener(v -> placeOrder());
    }

    /**
     * Refreshes spinner data when the user returns from a child creation screen
     * (e.g. after creating a new beneficiary or dress template inline).
     * Preserves the previously selected dress so the user doesn't lose their progress.
     */
    @Override
    protected void onResume() {
        super.onResume();;

        // Refresh beneficiaries for the currently selected customer
        if (spinnerCustomer != null && !customerIds.isEmpty()) {
            int cPos = spinnerCustomer.getSelectedItemPosition();
            if (cPos >= 0 && customerIds.get(cPos) != -1L) {
                loadBeneficiaries(customerIds.get(cPos));
            }
        }

        // Remember which dress was selected before navigating away
        long selectedDressId = -1L;
        if (spinnerDress != null && !dressIds.isEmpty()) {
            int dPos = spinnerDress.getSelectedItemPosition();
            if (dPos >= 0) selectedDressId = dressIds.get(dPos);
        }

        // Reload the dress list (a new template may have been created)
        dressIds.clear();
        dressNames.clear();
        dressPrices.clear();
        dressMeasurementTemplateIds.clear();
        loadDresses();

        // Restore the previously selected dress if it still exists
        if (selectedDressId != -1L) {
            for (int i = 0; i < dressIds.size(); i++) {
                if (dressIds.get(i).equals(selectedDressId)) {
                    spinnerDress.setSelection(i);
                    break;
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data Loading Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Loads all customers from the database into the customer spinner.
     * Displays a placeholder entry if no customers exist yet.
     */
    private void loadCustomers() {
        Cursor cursor = dbHandler.getAllCustomers();
        if (cursor != null && cursor.moveToFirst()) {
            do {
                customerIds.add(cursor.getLong(cursor.getColumnIndexOrThrow("customer_id")));
                customerNames.add(cursor.getString(cursor.getColumnIndexOrThrow("name")));
            } while (cursor.moveToNext());
            cursor.close();
        }

        if (customerNames.isEmpty()) {
            // Placeholder shown when no customers have been created yet
            customerNames.add("No customers found. Please create one first.");
            customerIds.add(-1L);
        }

        spinnerCustomer.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, customerNames));
    }

    /**
     * Loads beneficiaries for the given customer and populates the beneficiary spinner.
     * Always pre-populates "Self" as the first option (order is for the customer directly).
     *
     * @param customerId The customer whose beneficiaries should be loaded.
     */
    private void loadBeneficiaries(long customerId) {
        beneficiaryIds.clear();
        beneficiaryNames.clear();

        // Default — order is for the primary customer themselves
        beneficiaryIds.add(-1L);
        beneficiaryNames.add("Self (Primary Customer)");

        if (customerId != -1L) {
            Cursor cursor = dbHandler.getReadableDatabase().rawQuery(
                    "SELECT beneficiary_id, name FROM Beneficiaries WHERE customer_id = ?",
                    new String[]{String.valueOf(customerId)});
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    beneficiaryIds.add(cursor.getLong(0));
                    beneficiaryNames.add(cursor.getString(1));
                } while (cursor.moveToNext());
                cursor.close();
            }
        }

        spinnerBeneficiary.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, beneficiaryNames));
    }

    /**
     * Loads all dress templates and populates the dress spinner.
     * Captures price and linked measurement template ID alongside each item.
     */
    private void loadDresses() {
        Cursor cursor = dbHandler.getReadableDatabase().rawQuery(
                "SELECT dress_template_id, dress_name, estimated_price, measurement_template_id " +
                "FROM DressTemplates", null);

        if (cursor != null && cursor.moveToFirst()) {
            do {
                dressIds.add(cursor.getLong(0));
                dressNames.add(cursor.getString(1));
                dressPrices.add(cursor.getDouble(2));
                dressMeasurementTemplateIds.add(cursor.getLong(3));
            } while (cursor.moveToNext());
            cursor.close();
        }

        if (dressNames.isEmpty()) {
            // Placeholder shown when no dress templates exist yet
            dressNames.add("No dress templates found.");
            dressIds.add(-1L);
            dressPrices.add(0.0);
            dressMeasurementTemplateIds.add(-1L);
        }

        spinnerDress.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, dressNames));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step 3 — Dynamic Measurement Field Generation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Clears the measurement container and dynamically inflates a labelled
     * {@link EditText} for each field defined in the specified measurement template.
     * The resulting EditText widgets are stored in {@link #activeMeasurementFields}
     * so their values can be read during order submission.
     *
     * @param templateId The measurement_template_id whose fields should be rendered.
     */
    private void generateMeasurementFields(long templateId) {
        measurementsContainer.removeAllViews();
        activeMeasurementFields.clear();

        if (templateId <= 0) {
            // No template linked — show an informational message instead
            TextView errorText = new TextView(this);
            errorText.setText("This dress has no linked Measurement Template.");
            measurementsContainer.addView(errorText);
            return;
        }

        Cursor cursor = dbHandler.getReadableDatabase().rawQuery(
                "SELECT field_id, field_name FROM MeasurementFields " +
                "WHERE measurement_template_id = ?",
                new String[]{String.valueOf(templateId)});

        if (cursor != null && cursor.moveToFirst()) {
            do {
                long   fieldId   = cursor.getLong(0);
                String fieldName = cursor.getString(1);

                // Field label
                TextView label = new TextView(this);
                label.setText(fieldName);
                label.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
                label.setPadding(0, 16, 0, 8);

                // Measurement value input — numeric with decimal support
                EditText input = new EditText(this);
                input.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
                input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                input.setHint(fieldName + " (inches)");

                measurementsContainer.addView(label);
                measurementsContainer.addView(input);

                // Track this input widget by its field_id for later retrieval
                activeMeasurementFields.put(fieldId, input);

            } while (cursor.moveToNext());
            cursor.close();
        } else {
            // Template exists but has no fields defined
            TextView emptyText = new TextView(this);
            emptyText.setText("No fields defined in this Measurement Template.");
            measurementsContainer.addView(emptyText);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step 4 — Order Summary & Submission
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Composes and displays a human-readable order summary in Step 4, showing
     * the selected customer name, dress name, and estimated total price.
     */
    private void updateOrderSummary() {
        int cIdx = spinnerCustomer.getSelectedItemPosition();
        int dIdx = spinnerDress.getSelectedItemPosition();

        if (cIdx >= 0 && dIdx >= 0) {
            String customerName = customerNames.get(cIdx);
            String dressName    = dressNames.get(dIdx);
            double price        = dressPrices.get(dIdx);

            txtOrderSummary.setText(
                "Customer: " + customerName + "\n" +
                "Dress: "    + dressName    + "\n" +
                "Total Estimate: LKR " + String.format("%.2f", price));
        }
    }

    /**
     * Commits the complete order to the database within a single atomic transaction.
     *
     * <h3>Transaction Steps</h3>
     * <ol>
     *   <li>Insert a row into {@code Orders} with pricing, payment, and due date.</li>
     *   <li>Insert a row into {@code OrderItems} linking the order to the selected dress
     *       and (optionally) a beneficiary.</li>
     *   <li>Insert a row into {@code OrderMeasurements} for each active measurement
     *       field captured in Step 3.</li>
     * </ol>
     *
     * <p>On success, the activity finishes and returns to the caller.
     * On failure, the transaction is rolled back and an error toast is shown.</p>
     */
    private void placeOrder() {
        int  cIdx       = spinnerCustomer.getSelectedItemPosition();
        int  dIdx       = spinnerDress.getSelectedItemPosition();
        long customerId = customerIds.get(cIdx);
        long dressId    = dressIds.get(dIdx);
        double totalPrice = dressPrices.get(dIdx);

        // Resolve beneficiary — -1L means "Self" (primary customer)
        long beneficiaryId = -1L;
        int  bIdx = spinnerBeneficiary.getSelectedItemPosition();
        if (bIdx >= 0 && beneficiaryIds.size() > bIdx) {
            beneficiaryId = beneficiaryIds.get(bIdx);
        }

        // Parse the advance payment amount (defaults to 0 if field left blank)
        String advanceText = editAdvancePayment.getText().toString();
        double advance = advanceText.isEmpty() ? 0.0 : Double.parseDouble(advanceText);

        SQLiteDatabase db = dbHandler.getWritableDatabase();
        db.beginTransaction();
        try {
            // Step 1: Insert the order header
            ContentValues orderVals = new ContentValues();
            orderVals.put("customer_id",  customerId);
            orderVals.put("total_price",  totalPrice);
            orderVals.put("paid_amount",  advance);
            orderVals.put("payment_due",  totalPrice - advance); // Remaining balance
            orderVals.put("due_date",     editDueDate.getText().toString());
            orderVals.put("order_status", "Pending");
            long orderId = db.insert("Orders", null, orderVals);

            // Step 2: Insert the order line item
            ContentValues itemVals = new ContentValues();
            itemVals.put("order_id",          orderId);
            itemVals.put("dress_template_id",  dressId);
            itemVals.put("price",              totalPrice);
            if (beneficiaryId != -1L) {
                itemVals.put("beneficiary_id", beneficiaryId); // Only set if not "Self"
            }
            long orderItemId = db.insert("OrderItems", null, itemVals);

            // Step 3: Insert each captured measurement value
            for (Map.Entry<Long, EditText> entry : activeMeasurementFields.entrySet()) {
                long   fieldId    = entry.getKey();
                String rawValue   = entry.getValue().getText().toString();
                double numericVal = rawValue.isEmpty() ? 0.0 : Double.parseDouble(rawValue);

                ContentValues mVals = new ContentValues();
                mVals.put("order_item_id", orderItemId);
                mVals.put("field_id",      fieldId);
                mVals.put("value",         numericVal);
                db.insert("OrderMeasurements", null, mVals);
            }

            db.setTransactionSuccessful();
            Toast.makeText(this, "Order placed successfully!", Toast.LENGTH_SHORT).show();

            // Navigate directly to the new order's detail screen
            Intent intent = new Intent(this, OrderDetailsActivity.class);
            intent.putExtra("order_id", orderId);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish(); // Close the wizard so back-press returns to the order list

        } catch (Exception e) {
            Toast.makeText(this, "Failed to place order: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        } finally {
            db.endTransaction();
            db.close();
        }
    }
}
