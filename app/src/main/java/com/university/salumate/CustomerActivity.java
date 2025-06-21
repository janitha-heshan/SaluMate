package com.university.salumate;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.LatLng;

public class CustomerActivity extends AppCompatActivity {

    private EditText nameField, phoneField, addressField;
    private MapView mapView;
    private GoogleMap gMap;
    private LatLng selectedLatLng = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer);

        nameField = findViewById(R.id.CustomerName);
        phoneField = findViewById(R.id.editTextPhone);
        addressField = findViewById(R.id.editTextAddress);
        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);

        // Initialize map
        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(@NonNull GoogleMap googleMap) {
                gMap = googleMap;
                MapsInitializer.initialize(getApplicationContext());

                // Allow user to drop pin on map
                gMap.setOnMapClickListener(latLng -> {
                    gMap.clear(); // remove previous markers
                    gMap.addMarker(new MarkerOptions().position(latLng).title("Selected Location"));
                    selectedLatLng = latLng;
                    Toast.makeText(getApplicationContext(),
                            "Lat: " + latLng.latitude + ", Lng: " + latLng.longitude,
                            Toast.LENGTH_SHORT).show();
                });
            }
        });

        // Handle button click to get data
        Button createButton = findViewById(R.id.btn_Create);
        createButton.setOnClickListener(v -> {
            String name = nameField.getText().toString().trim();
            String phone = phoneField.getText().toString().trim();
            String address = addressField.getText().toString().trim();

            if (selectedLatLng == null) {
                Toast.makeText(this, "Please select a location on the map", Toast.LENGTH_SHORT).show();
                return;
            }

            double latitude = selectedLatLng.latitude;
            double longitude = selectedLatLng.longitude;

            // Display or use data
            String message = "Name: " + name + "\nPhone: " + phone + "\nAddress: " + address +
                    "\nLat: " + latitude + "\nLng: " + longitude;
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();

            // You can now store this data in DB or send to server
        });
    }

    // Lifecycle methods for MapView
    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

//    @Override
//    protected void onLowMemory() {
//        super.onLowMemory();
//        mapView.onLowMemory();
//    }
}
