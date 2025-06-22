package com.university.salumate;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.widget.EditText;
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

        findViewById(R.id.btn_BackDress).setOnClickListener(v -> updateCustomerData());
        findViewById(R.id.btn_ConfirmDress).setOnClickListener(v -> {
            Intent i = new Intent(UpdateCustomerActivity.this, DashboardActivity.class);
            i.putExtra("customer_id", customerId);
            startActivity(i);
        });
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
    @Override protected void onResume() { super.onResume(); mapView.onResume(); }
    @Override protected void onPause() { super.onPause(); mapView.onPause(); }
    @Override protected void onDestroy() { super.onDestroy(); mapView.onDestroy(); }
    //@Override public void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); }
}
