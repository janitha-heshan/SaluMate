package com.university.salumate;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

public class CustomerActivity extends AppCompatActivity {

    private EditText nameField, phoneField, addressField;
    private MapView mapView;
    private GoogleMap gMap;
    private LatLng selectedLatLng = null;

    private DBHandler dbHandler; //  Declare DBHandler

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer);

        // Bind views
        nameField = findViewById(R.id.CustomerName);
        phoneField = findViewById(R.id.editTextPhone);
        addressField = findViewById(R.id.editTextAddress);
        mapView = findViewById(R.id.mapView);
        Button createButton = findViewById(R.id.btn_Create);

        // Initialize DBHandler
        dbHandler = new DBHandler(this);

        // Initialize map
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(map -> {
            gMap = map;
            MapsInitializer.initialize(getApplicationContext());

            gMap.setOnMapClickListener(latLng -> {
                gMap.clear();
                gMap.addMarker(new MarkerOptions().position(latLng).title("Selected Location"));
                selectedLatLng = latLng;
                Toast.makeText(getApplicationContext(),
                        "Selected Lat: " + latLng.latitude + ", Lng: " + latLng.longitude,
                        Toast.LENGTH_SHORT).show();
            });
        });

        // Handle Create button click
        createButton.setOnClickListener(view -> {
            String name = nameField.getText().toString().trim();
            String phone = phoneField.getText().toString().trim();
            String address = addressField.getText().toString().trim();

            if (name.isEmpty() || phone.isEmpty()) {
                Toast.makeText(this, "Name and phone number are required.", Toast.LENGTH_SHORT).show();
                return;
            }

            Double latitude = selectedLatLng != null ? selectedLatLng.latitude : null;
            Double longitude = selectedLatLng != null ? selectedLatLng.longitude : null;

            // Save to DB
            long customerId = dbHandler.addCustomer(name, phone, address, latitude, longitude);

            if (customerId != -1) {
                Toast.makeText(this, "Customer added successfully (ID: " + customerId + ")", Toast.LENGTH_LONG).show();
                nameField.setText("");
                phoneField.setText("");
                addressField.setText("");
                if (gMap != null) gMap.clear();
                selectedLatLng = null;
            } else {
                Toast.makeText(this, "Failed to add customer.", Toast.LENGTH_SHORT).show();
            }

            Intent intent = new Intent(CustomerActivity.this, CreateOrderActivity.class);
            startActivity(intent);
        });
    }

    // MapView lifecycle methods
    @Override protected void onResume() { super.onResume(); mapView.onResume(); }
    @Override protected void onPause() { super.onPause(); mapView.onPause(); }
    @Override protected void onDestroy() { super.onDestroy(); mapView.onDestroy(); }

}
