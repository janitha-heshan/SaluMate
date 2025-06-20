package com.university.salumate;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.MapsInitializer;
import com.university.salumate.DBHandler;

public class CustomerActivity extends AppCompatActivity {

    private EditText nameField, phoneField, addressField;
    private MapView mapView;
    private GoogleMap map;
    private LatLng selectedLocation;
    private Button btnCreate, btnUpdate, btnDelete, btnBack;

    private DBHandler dbHandler;
    private long customerId = -1; // if set, we're updating/deleting

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer);

        dbHandler = new DBHandler(this);

        nameField = findViewById(R.id.CustomerName);
        phoneField = findViewById(R.id.editTextPhone);
        addressField = findViewById(R.id.editTextAddress);
        mapView = findViewById(R.id.mapView);

        btnCreate = findViewById(R.id.btn_Create);
        btnUpdate = findViewById(R.id.btn_Update);
        btnDelete = findViewById(R.id.btn_delete);
        btnBack = findViewById(R.id.btn_back);

        mapView.onCreate(savedInstanceState);
        mapView.onResume();

        try {
            MapsInitializer.initialize(this);
        } catch (Exception e) {
            e.printStackTrace();
        }

        mapView.getMapAsync(googleMap -> {
            map = googleMap;
            map.setOnMapClickListener(latLng -> {
                selectedLocation = latLng;
                map.clear();
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15));
            });
        });

        // Check if we are updating an existing customer
        Intent intent = getIntent();
        if (intent.hasExtra("customer_id")) {
            customerId = intent.getLongExtra("customer_id", -1);
            loadCustomerData(customerId);
        }

        btnCreate.setOnClickListener(v -> {
            if (validateInput()) {
                long id = dbHandler.addCustomer(
                        nameField.getText().toString(),
                        phoneField.getText().toString(),
                        addressField.getText().toString(),
                        selectedLocation != null ? selectedLocation.latitude : null,
                        selectedLocation != null ? selectedLocation.longitude : null
                );
                Toast.makeText(this, "Customer created with ID: " + id, Toast.LENGTH_SHORT).show();
                finish();
            }
        });

        btnUpdate.setOnClickListener(v -> {
            if (customerId != -1 && validateInput()) {
                dbHandler.getWritableDatabase().execSQL(
                        "UPDATE Customers SET name=?, phone_number=?, address=?, latitude=?, longitude=? WHERE customer_id=?",
                        new Object[]{
                                nameField.getText().toString(),
                                phoneField.getText().toString(),
                                addressField.getText().toString(),
                                selectedLocation != null ? selectedLocation.latitude : null,
                                selectedLocation != null ? selectedLocation.longitude : null,
                                customerId
                        }
                );
                Toast.makeText(this, "Customer updated", Toast.LENGTH_SHORT).show();
                finish();
            }
        });

        btnDelete.setOnClickListener(v -> {
            if (customerId != -1) {
                dbHandler.getWritableDatabase().execSQL("DELETE FROM Customers WHERE customer_id=?", new Object[]{customerId});
                Toast.makeText(this, "Customer deleted", Toast.LENGTH_SHORT).show();
                finish();
            }
        });

        btnBack.setOnClickListener(v -> onBackPressed());
    }

    private void loadCustomerData(long id) {
        Cursor cursor = dbHandler.getCustomerById(id);
        if (cursor != null && cursor.moveToFirst()) {
            nameField.setText(cursor.getString(cursor.getColumnIndexOrThrow("name")));
            phoneField.setText(cursor.getString(cursor.getColumnIndexOrThrow("phone_number")));
            addressField.setText(cursor.getString(cursor.getColumnIndexOrThrow("address")));

            double lat = cursor.getDouble(cursor.getColumnIndexOrThrow("latitude"));
            double lng = cursor.getDouble(cursor.getColumnIndexOrThrow("longitude"));
            selectedLocation = new LatLng(lat, lng);

            mapView.getMapAsync(googleMap -> {
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(selectedLocation, 15));
            });
        }
        if (cursor != null) cursor.close();
    }

    private boolean validateInput() {
        if (nameField.getText().toString().trim().isEmpty() ||
                phoneField.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "Name and Phone are required", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    // Required lifecycle methods for MapView
    @Override protected void onResume() { super.onResume(); mapView.onResume(); }
    @Override protected void onPause() { super.onPause(); mapView.onPause(); }
    @Override protected void onDestroy() { super.onDestroy(); mapView.onDestroy(); }
    @Override public void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); }
    @Override protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }
}
