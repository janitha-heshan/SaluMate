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

/**
 * CustomerActivity — Form for registering a new customer in the SaluMate system.
 *
 * <p>Captures the customer's name, phone number, street address, and optionally a
 * GPS pin dropped on an embedded Google Map. All data is persisted to the local
 * SQLite database via {@link DBHandler#addCustomer}.</p>
 *
 * <p>After successfully saving the customer, the user is navigated to the
 * {@link CreateOrderActivity} so they can immediately place an order for the
 * newly registered customer.</p>
 *
 * <h3>Map Interaction</h3>
 * <p>The embedded {@link MapView} allows the shop owner to pin a customer's
 * approximate home/delivery location by tapping on the map. This pin is stored
 * as a latitude/longitude pair alongside the customer record for future
 * geo-based features (e.g. delivery routing).</p>
 */
public class CustomerActivity extends AppCompatActivity {

    /** Input field for the customer's full name (required). */
    private EditText nameField;
    /** Input field for the customer's contact phone number (required). */
    private EditText phoneField;
    /** Input field for the customer's street address (optional). */
    private EditText addressField;

    /** Embedded Google Map for selecting the customer's location pin. */
    private MapView mapView;
    /** Live GoogleMap instance wired up asynchronously once the MapView is ready. */
    private GoogleMap gMap;

    /**
     * The GPS coordinates selected by the user on the map.
     * Remains null if the user does not tap the map.
     */
    private LatLng selectedLatLng = null;

    /** Database access helper for persisting the new customer record. */
    private DBHandler dbHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer);

        // Bind all form input fields
        nameField    = findViewById(R.id.CustomerName);
        phoneField   = findViewById(R.id.editTextPhone);
        addressField = findViewById(R.id.editTextAddress);
        mapView      = findViewById(R.id.mapView);
        Button createButton = findViewById(R.id.btn_Create);

        dbHandler = new DBHandler(this);

        // Initialise the embedded MapView (must pass savedInstanceState for lifecycle correctness)
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(map -> {
            gMap = map;
            MapsInitializer.initialize(getApplicationContext());

            // Allow user to drop a pin on the map to capture the customer's location
            gMap.setOnMapClickListener(latLng -> {
                gMap.clear();
                gMap.addMarker(new MarkerOptions().position(latLng).title("Customer Location"));
                selectedLatLng = latLng; // Store the selected coordinates
                Toast.makeText(getApplicationContext(),
                        String.format("Location: %.4f, %.4f", latLng.latitude, latLng.longitude),
                        Toast.LENGTH_SHORT).show();
            });
        });

        // "Create Customer" button — validates inputs and saves to the database
        createButton.setOnClickListener(view -> {
            String name    = nameField.getText().toString().trim();
            String phone   = phoneField.getText().toString().trim();
            String address = addressField.getText().toString().trim();

            // Validate required fields
            if (name.isEmpty() || phone.isEmpty()) {
                Toast.makeText(this, "Name and phone number are required.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Map pin is optional — pass null if not selected
            Double latitude  = selectedLatLng != null ? selectedLatLng.latitude  : null;
            Double longitude = selectedLatLng != null ? selectedLatLng.longitude : null;

            long customerId = dbHandler.addCustomer(name, phone, address, latitude, longitude);

            if (customerId != -1) {
                Toast.makeText(this, "Customer created! (ID: " + customerId + ")", Toast.LENGTH_LONG).show();

                // Reset form fields for potential next entry
                nameField.setText("");
                phoneField.setText("");
                addressField.setText("");
                if (gMap != null) gMap.clear();
                selectedLatLng = null;

                // Navigate to order creation pre-associated with the new customer
                startActivity(new Intent(CustomerActivity.this, CreateOrderActivity.class));
            } else {
                Toast.makeText(this, "Failed to add customer. Please try again.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ─── MapView Lifecycle Pass-through Methods ───────────────────────────────
    // MapView must receive these lifecycle callbacks to function correctly.
    // Failure to forward these results in memory leaks or blank map rendering.

    @Override protected void onResume()  { super.onResume();  mapView.onResume();  }
    @Override protected void onPause()   { super.onPause();   mapView.onPause();   }
    @Override protected void onDestroy() { super.onDestroy(); mapView.onDestroy(); }
}
