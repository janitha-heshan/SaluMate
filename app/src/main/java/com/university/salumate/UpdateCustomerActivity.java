package com.university.salumate;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

public class UpdateCustomerActivity extends AppCompatActivity {

    private EditText nameField, phoneField, addressField;
    private MapView mapView;
    private GoogleMap gMap;
    private LatLng customerLocation;
    private long customerId;
    private DBHandler dbHandler;
    private LinearLayout layoutBeneficiaries;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update_customer);

        nameField = findViewById(R.id.CustomerName);
        phoneField = findViewById(R.id.editTextPhone);
        addressField = findViewById(R.id.editTextAddress);
        mapView = findViewById(R.id.mapView);
        dbHandler = new DBHandler(this);

        customerId = getIntent().getLongExtra("customer_id", -1);
        mapView.onCreate(savedInstanceState);
        layoutBeneficiaries = findViewById(R.id.layoutBeneficiaries);

        if (customerId != -1) {
            loadCustomerData(customerId);
        }

        mapView.getMapAsync(googleMap -> {
            gMap = googleMap;
            if (customerLocation != null) {
                gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(customerLocation, 15));
                gMap.addMarker(new MarkerOptions().position(customerLocation).title("Customer Location"));
            }

            gMap.setOnMapClickListener(latLng -> {
                customerLocation = latLng;
                gMap.clear();
                gMap.addMarker(new MarkerOptions().position(latLng).title("Selected Location"));
            });
        });

        findViewById(R.id.btn_BackDress).setOnClickListener(v -> {
            updateCustomerData();
            finish();
        });
        findViewById(R.id.btn_ConfirmDress).setOnClickListener(v -> {
            Intent i = new Intent(UpdateCustomerActivity.this, DashboardActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
        });
        
        findViewById(R.id.btnAddBeneficiary).setOnClickListener(v -> {
            Intent intent = new Intent(UpdateCustomerActivity.this, BeneficiaryActivity.class);
            intent.putExtra("customer_id", customerId);
            intent.putExtra("standalone", true);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        if (customerId != -1) {
            loadBeneficiaries();
        }
    }

    private void loadCustomerData(long id) {
        Cursor cursor = dbHandler.getCustomerById(id);
        if (cursor != null && cursor.moveToFirst()) {
            nameField.setText(cursor.getString(cursor.getColumnIndexOrThrow("name")));
            phoneField.setText(cursor.getString(cursor.getColumnIndexOrThrow("phone_number")));
            addressField.setText(cursor.getString(cursor.getColumnIndexOrThrow("address")));

            double lat = cursor.getDouble(cursor.getColumnIndexOrThrow("latitude"));
            double lng = cursor.getDouble(cursor.getColumnIndexOrThrow("longitude"));
            customerLocation = new LatLng(lat, lng);

            cursor.close();
            loadBeneficiaries();
        }
    }

    private void loadBeneficiaries() {
        layoutBeneficiaries.removeAllViews();
        Cursor cur = dbHandler.getReadableDatabase().rawQuery("SELECT beneficiary_id, name, gender, relation FROM Beneficiaries WHERE customer_id = ?", new String[]{String.valueOf(customerId)});
        if (cur != null && cur.moveToFirst()) {
            do {
                long bId = cur.getLong(0);
                String bName = cur.getString(1);
                String bRel = cur.getString(3);
                
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                row.setPadding(0, 16, 0, 16);
                
                TextView txtInfo = new TextView(this);
                txtInfo.setText(bName + " (" + bRel + ")");
                txtInfo.setTextSize(16);
                txtInfo.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                
                Button btnDel = new Button(this);
                btnDel.setText("Del");
                btnDel.setOnClickListener(v -> {
                    dbHandler.getWritableDatabase().execSQL("DELETE FROM Beneficiaries WHERE beneficiary_id = ?", new String[]{String.valueOf(bId)});
                    loadBeneficiaries();
                });
                
                row.addView(txtInfo);
                row.addView(btnDel);
                layoutBeneficiaries.addView(row);
                
            } while (cur.moveToNext());
            cur.close();
        }
    }

    private void updateCustomerData() {
        String name = nameField.getText().toString().trim();
        String phone = phoneField.getText().toString().trim();
        String address = addressField.getText().toString().trim();

        double lat = customerLocation != null ? customerLocation.latitude : 0;
        double lng = customerLocation != null ? customerLocation.longitude : 0;

        boolean updated = dbHandler.updateCustomer(customerId, name, phone, address, lat, lng);
        if (updated) {
            Toast.makeText(this, "Customer updated successfully", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Update failed", Toast.LENGTH_SHORT).show();
        }
    }

    // Required lifecycle methods for MapView
    @Override protected void onDestroy() { super.onDestroy(); mapView.onDestroy(); }
    //@Override public void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); }
}
