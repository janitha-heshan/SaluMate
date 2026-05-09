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

public class CreateOrderActivity extends AppCompatActivity {

    private ViewFlipper wizardFlipper;
    private Spinner spinnerCustomer, spinnerBeneficiary, spinnerDress;
    private LinearLayout measurementsContainer;
    private TextView txtOrderSummary;
    private EditText editAdvancePayment, editDueDate;

    private DBHandler dbHandler;
    
    private List<Long> customerIds = new ArrayList<>();
    private List<String> customerNames = new ArrayList<>();
    
    private List<Long> beneficiaryIds = new ArrayList<>();
    private List<String> beneficiaryNames = new ArrayList<>();
    
    private List<Long> dressIds = new ArrayList<>();
    private List<String> dressNames = new ArrayList<>();
    private List<Double> dressPrices = new ArrayList<>();
    private List<Long> dressMeasurementTemplateIds = new ArrayList<>();

    private Map<Long, EditText> activeMeasurementFields = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_order);

        dbHandler = new DBHandler(this);
        
        wizardFlipper = findViewById(R.id.orderWizardFlipper);
        spinnerCustomer = findViewById(R.id.spinnerOrderCustomer);
        spinnerBeneficiary = findViewById(R.id.spinnerOrderBeneficiary);
        spinnerDress = findViewById(R.id.spinnerOrderDress);
        measurementsContainer = findViewById(R.id.orderMeasurementsContainer);
        txtOrderSummary = findViewById(R.id.txtOrderSummary);
        editAdvancePayment = findViewById(R.id.editAdvancePayment);
        editDueDate = findViewById(R.id.editDueDate);

        editDueDate.setOnClickListener(v -> {
            java.util.Calendar calendar = java.util.Calendar.getInstance();
            int year = calendar.get(java.util.Calendar.YEAR);
            int month = calendar.get(java.util.Calendar.MONTH);
            int day = calendar.get(java.util.Calendar.DAY_OF_MONTH);

            new android.app.DatePickerDialog(this, (view, y, m, d) -> {
                String date = String.format("%04d-%02d-%02d", y, m + 1, d);
                editDueDate.setText(date);
            }, year, month, day).show();
        });

        Button btnNextDress = findViewById(R.id.btnNextDress);
        Button btnPrevCustomer = findViewById(R.id.btnPrevCustomer);
        Button btnNextMeasurements = findViewById(R.id.btnNextMeasurements);
        Button btnPrevDress = findViewById(R.id.btnPrevDress);
        Button btnNextPayment = findViewById(R.id.btnNextPayment);
        Button btnPrevMeasurements = findViewById(R.id.btnPrevMeasurements);
        Button btnPlaceOrder = findViewById(R.id.btnPlaceOrder);
        Button btnAddNewBeneficiary = findViewById(R.id.btnAddNewBeneficiary);
        Button btnAddNewDress = findViewById(R.id.btnAddNewDress);

        loadCustomers();
        loadDresses();

        spinnerCustomer.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                if(customerIds.size() > position && customerIds.get(position) != -1L) {
                    loadBeneficiaries(customerIds.get(position));
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        btnAddNewBeneficiary.setOnClickListener(v -> {
            int cIdx = spinnerCustomer.getSelectedItemPosition();
            if(cIdx >= 0 && customerIds.get(cIdx) != -1L) {
                Intent intent = new Intent(this, BeneficiaryActivity.class);
                intent.putExtra("customer_id", customerIds.get(cIdx));
                intent.putExtra("standalone", true);
                startActivity(intent);
            } else {
                Toast.makeText(this, "Select a valid customer first", Toast.LENGTH_SHORT).show();
            }
        });

        btnAddNewDress.setOnClickListener(v -> {
            Intent intent = new Intent(this, CreateDressActivity.class);
            startActivity(intent);
        });

        btnNextDress.setOnClickListener(v -> {
            if(spinnerCustomer.getSelectedItemPosition() < 0 || customerIds.isEmpty() || customerIds.get(0) == -1L) {
                Toast.makeText(this, "Valid Customer required", Toast.LENGTH_SHORT).show();
            } else {
                wizardFlipper.showNext();
            }
        });
        
        btnPrevCustomer.setOnClickListener(v -> wizardFlipper.showPrevious());
        
        btnNextMeasurements.setOnClickListener(v -> {
            if(spinnerDress.getSelectedItemPosition() < 0 || dressIds.isEmpty() || dressIds.get(0) == -1L) {
                Toast.makeText(this, "Valid Dress Template required", Toast.LENGTH_SHORT).show();
                return;
            }
            long templateId = dressMeasurementTemplateIds.get(spinnerDress.getSelectedItemPosition());
            generateMeasurementFields(templateId);
            wizardFlipper.showNext();
        });
        
        btnPrevDress.setOnClickListener(v -> wizardFlipper.showPrevious());
        
        btnNextPayment.setOnClickListener(v -> {
            updateOrderSummary();
            wizardFlipper.showNext();
        });
        
        btnPrevMeasurements.setOnClickListener(v -> wizardFlipper.showPrevious());
        
        btnPlaceOrder.setOnClickListener(v -> placeOrder());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(spinnerCustomer != null && spinnerCustomer.getSelectedItemPosition() >= 0 && !customerIds.isEmpty()) {
            if(customerIds.get(spinnerCustomer.getSelectedItemPosition()) != -1L) {
                loadBeneficiaries(customerIds.get(spinnerCustomer.getSelectedItemPosition()));
            }
        }
        
        long selectedDressId = -1L;
        if (spinnerDress != null && spinnerDress.getSelectedItemPosition() >= 0 && !dressIds.isEmpty()) {
            selectedDressId = dressIds.get(spinnerDress.getSelectedItemPosition());
        }
        
        dressIds.clear();
        dressNames.clear();
        dressPrices.clear();
        dressMeasurementTemplateIds.clear();
        
        loadDresses();
        
        if (selectedDressId != -1L) {
            for (int i = 0; i < dressIds.size(); i++) {
                if (dressIds.get(i) == selectedDressId) {
                    spinnerDress.setSelection(i);
                    break;
                }
            }
        }
    }

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
            customerNames.add("No Customers Found. Please create one.");
            customerIds.add(-1L);
        }
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, customerNames);
        spinnerCustomer.setAdapter(adapter);
    }
    
    private void loadBeneficiaries(long customerId) {
        beneficiaryIds.clear();
        beneficiaryNames.clear();
        
        beneficiaryIds.add(-1L);
        beneficiaryNames.add("Self (Primary Customer)");

        if (customerId != -1L) {
            Cursor cursor = dbHandler.getReadableDatabase().rawQuery("SELECT beneficiary_id, name FROM Beneficiaries WHERE customer_id = ?", new String[]{String.valueOf(customerId)});
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    beneficiaryIds.add(cursor.getLong(0));
                    beneficiaryNames.add(cursor.getString(1));
                } while (cursor.moveToNext());
                cursor.close();
            }
        }
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, beneficiaryNames);
        spinnerBeneficiary.setAdapter(adapter);
    }

    private void loadDresses() {
        SQLiteDatabase db = dbHandler.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT dress_template_id, dress_name, estimated_price, measurement_template_id FROM DressTemplates", null);
        
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
            dressNames.add("No Dress Templates Found.");
            dressIds.add(-1L);
            dressPrices.add(0.0);
            dressMeasurementTemplateIds.add(-1L);
        }
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, dressNames);
        spinnerDress.setAdapter(adapter);
    }

    private void generateMeasurementFields(long templateId) {
        measurementsContainer.removeAllViews();
        activeMeasurementFields.clear();

        if (templateId == -1 || templateId == 0) {
            TextView errorText = new TextView(this);
            errorText.setText("This dress has no linked Measurement Template.");
            measurementsContainer.addView(errorText);
            return;
        }

        SQLiteDatabase db = dbHandler.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT field_id, field_name FROM MeasurementFields WHERE measurement_template_id = ?", new String[]{String.valueOf(templateId)});

        if (cursor != null && cursor.moveToFirst()) {
            do {
                long fieldId = cursor.getLong(0);
                String fieldName = cursor.getString(1);

                TextView label = new TextView(this);
                label.setText(fieldName);
                label.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                label.setPadding(0, 16, 0, 8);

                EditText input = new EditText(this);
                input.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                input.setHint(fieldName + " in inches");

                measurementsContainer.addView(label);
                measurementsContainer.addView(input);

                activeMeasurementFields.put(fieldId, input);

            } while (cursor.moveToNext());
            cursor.close();
        } else {
            TextView emptyText = new TextView(this);
            emptyText.setText("No fields defined in the selected Measurement Template.");
            measurementsContainer.addView(emptyText);
        }
    }

    private void updateOrderSummary() {
        int cIdx = spinnerCustomer.getSelectedItemPosition();
        int dIdx = spinnerDress.getSelectedItemPosition();

        if(cIdx >= 0 && dIdx >= 0) {
            String customerName = customerNames.get(cIdx);
            String dressName = dressNames.get(dIdx);
            double price = dressPrices.get(dIdx);

            String summary = "Customer: " + customerName + "\n" +
                             "Dress: " + dressName + "\n" +
                             "Total Estimate: LKR " + String.format("%.2f", price);
            txtOrderSummary.setText(summary);
        }
    }

    private void placeOrder() {
        int cIdx = spinnerCustomer.getSelectedItemPosition();
        int dIdx = spinnerDress.getSelectedItemPosition();

        long customerId = customerIds.get(cIdx);
        long dressId = dressIds.get(dIdx);
        double totalPrice = dressPrices.get(dIdx);

        int bIdx = spinnerBeneficiary.getSelectedItemPosition();
        long beneficiaryId = -1L;
        if (bIdx >= 0 && beneficiaryIds.size() > bIdx) {
            beneficiaryId = beneficiaryIds.get(bIdx);
        }

        String advanceText = editAdvancePayment.getText().toString();
        double advance = advanceText.isEmpty() ? 0.0 : Double.parseDouble(advanceText);

        SQLiteDatabase db = dbHandler.getWritableDatabase();
        db.beginTransaction();
        try {
            // 1. Create Order
            ContentValues orderVals = new ContentValues();
            orderVals.put("customer_id", customerId);
            orderVals.put("total_price", totalPrice);
            orderVals.put("paid_amount", advance);
            orderVals.put("payment_due", totalPrice - advance);
            orderVals.put("due_date", editDueDate.getText().toString());
            orderVals.put("order_status", "Pending");
            long orderId = db.insert("Orders", null, orderVals);

            // 2. Create OrderItem
            ContentValues itemVals = new ContentValues();
            itemVals.put("order_id", orderId);
            if (beneficiaryId != -1L) {
                itemVals.put("beneficiary_id", beneficiaryId);
            }
            itemVals.put("dress_template_id", dressId);
            itemVals.put("price", totalPrice);
            long orderItemId = db.insert("OrderItems", null, itemVals);

            // 3. Create active OrderMeasurements referencing OrderItem
            for (Map.Entry<Long, EditText> entry : activeMeasurementFields.entrySet()) {
                long fieldId = entry.getKey();
                String val = entry.getValue().getText().toString();
                double numericVal = val.isEmpty() ? 0.0 : Double.parseDouble(val);
                
                ContentValues mVals = new ContentValues();
                mVals.put("order_item_id", orderItemId);
                mVals.put("field_id", fieldId);
                mVals.put("value", numericVal);
                db.insert("OrderMeasurements", null, mVals);
            }

            db.setTransactionSuccessful();
            Toast.makeText(this, "Order Placed Successfully!", Toast.LENGTH_SHORT).show();
            finish();

        } catch(Exception e) {
            Toast.makeText(this, "Failed to place order: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            db.endTransaction();
            db.close();
        }
    }
}
