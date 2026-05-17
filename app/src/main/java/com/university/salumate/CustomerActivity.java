package com.university.salumate;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.textfield.TextInputEditText;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * CustomerActivity — Form for registering a new customer in the SaluMate system.
 *
 * <p>Captures the customer's name, phone number, street address, and a GPS pin
 * selected on an embedded Google Map. All data is persisted to the local SQLite
 * database via {@link DBHandler#addCustomer}.</p>
 *
 * <h3>Map Features</h3>
 * <ul>
 *   <li><b>Current Location</b>: On map ready, the camera zooms to the device's
 *       current GPS location automatically (with a "My Location" 📍 button to re-centre).</li>
 *   <li><b>Address Search</b>: The 🔍 button geocodes the typed address using
 *       {@link Geocoder} and moves the camera to the result, dropping a pin.</li>
 *   <li><b>Map Tap</b>: User can tap any point on the map to drop a custom pin.</li>
 * </ul>
 */
public class CustomerActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST = 101;
    private static final float DEFAULT_ZOOM = 15f;

    // Form fields
    private EditText nameField, phoneField, addressField;

    // Map
    private MapView mapView;
    private GoogleMap gMap;
    private LatLng selectedLatLng = null;

    // Location search
    private TextInputEditText etLocationSearch;
    private TextView txtLocationStatus;

    // Location provider
    private FusedLocationProviderClient fusedLocationClient;

    // DB
    private DBHandler dbHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer);

        nameField        = findViewById(R.id.CustomerName);
        phoneField       = findViewById(R.id.editTextPhone);
        addressField     = findViewById(R.id.editTextAddress);
        mapView          = findViewById(R.id.mapView);
        etLocationSearch = findViewById(R.id.etLocationSearch);
        txtLocationStatus= findViewById(R.id.txtLocationStatus);
        Button createButton = findViewById(R.id.btn_Create);

        dbHandler = new DBHandler(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Initialise MapView
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(map -> {
            gMap = map;
            MapsInitializer.initialize(getApplicationContext());

            // Show blue dot for current location if permission granted
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                gMap.setMyLocationEnabled(true);
            }

            // Tap map → drop a pin and update status
            gMap.setOnMapClickListener(latLng -> {
                gMap.clear();
                gMap.addMarker(new MarkerOptions()
                        .position(latLng)
                        .title("Customer Location"));
                selectedLatLng = latLng;
                updateStatusLabel(latLng, null);
            });

            // Move to current location as default after map loads
            moveToCurrentLocation(true /* isDefault */);
        });

        // 🔍 Search button
        findViewById(R.id.btnSearchLocation).setOnClickListener(v -> searchLocation());

        // 📍 My Location button
        findViewById(R.id.btnMyLocation).setOnClickListener(v -> moveToCurrentLocation(false));

        // Keyboard "Search" action on the search field
        etLocationSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchLocation();
                return true;
            }
            return false;
        });

        // Back button
        Button btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        // Create Customer button
        createButton.setOnClickListener(view -> {
            String name    = nameField.getText().toString().trim();
            String phone   = phoneField.getText().toString().trim();
            String address = addressField.getText().toString().trim();

            if (name.isEmpty() || phone.isEmpty()) {
                Toast.makeText(this, "Name and phone number are required.", Toast.LENGTH_SHORT).show();
                return;
            }

            Double latitude  = selectedLatLng != null ? selectedLatLng.latitude  : null;
            Double longitude = selectedLatLng != null ? selectedLatLng.longitude : null;

            long customerId = dbHandler.addCustomer(name, phone, address, latitude, longitude);

            if (customerId != -1) {
                Toast.makeText(this, "Customer created! (ID: " + customerId + ")", Toast.LENGTH_LONG).show();
                nameField.setText("");
                phoneField.setText("");
                addressField.setText("");
                if (gMap != null) gMap.clear();
                selectedLatLng = null;
                txtLocationStatus.setText("Tap map to pin location · or search above");
                startActivity(new Intent(CustomerActivity.this, CreateOrderActivity.class));
            } else {
                Toast.makeText(this, "Failed to add customer. Please try again.", Toast.LENGTH_SHORT).show();
            }
        });

        // Request location permission if not yet granted
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                                 Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);
        }
    }

    // ─── Location Helpers ─────────────────────────────────────────────────────

    /**
     * Moves the map camera to the device's last known location.
     *
     * @param isDefault {@code true} when called on map load (does not drop a pin,
     *                  just centres the view). {@code false} when triggered by the
     *                  📍 button (shows a toast confirming the re-centring).
     */
    private void moveToCurrentLocation(boolean isDefault) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            if (!isDefault) {
                Toast.makeText(this, "Location permission not granted.", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null && gMap != null) {
                LatLng current = new LatLng(location.getLatitude(), location.getLongitude());
                gMap.animateCamera(CameraUpdateFactory.newLatLngZoom(current, DEFAULT_ZOOM));
                if (!isDefault) {
                    Toast.makeText(this, "Centred on your current location.", Toast.LENGTH_SHORT).show();
                }
            } else if (!isDefault) {
                Toast.makeText(this, "Unable to get current location.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Geocodes the text in {@code etLocationSearch} using {@link Geocoder},
     * moves the camera to the first result, and drops a pin.
     */
    private void searchLocation() {
        String query = etLocationSearch.getText() != null
                ? etLocationSearch.getText().toString().trim() : "";
        if (query.isEmpty()) {
            Toast.makeText(this, "Enter an address or place name to search.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Dismiss keyboard
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(etLocationSearch.getWindowToken(), 0);

        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> results = geocoder.getFromLocationName(query, 5);
            if (results != null && !results.isEmpty()) {
                Address best = results.get(0);
                LatLng found = new LatLng(best.getLatitude(), best.getLongitude());

                if (gMap != null) {
                    gMap.clear();
                    gMap.animateCamera(CameraUpdateFactory.newLatLngZoom(found, DEFAULT_ZOOM));
                    gMap.addMarker(new MarkerOptions()
                            .position(found)
                            .title(best.getFeatureName() != null ? best.getFeatureName() : query));
                }

                selectedLatLng = found;
                updateStatusLabel(found, best.getAddressLine(0));
            } else {
                Toast.makeText(this, "Location not found. Try a different search.", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Toast.makeText(this, "Search failed — check your internet connection.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Updates the status label below the search bar with the selected coordinates
     * and an optional address line from Geocoder results.
     */
    private void updateStatusLabel(LatLng latLng, String addressLine) {
        String coords = String.format(Locale.getDefault(),
                "📍 %.5f, %.5f", latLng.latitude, latLng.longitude);
        if (addressLine != null && !addressLine.isEmpty()) {
            txtLocationStatus.setText(addressLine + "\n" + coords);
        } else {
            txtLocationStatus.setText(coords);
        }
    }

    // ─── Permission Result ────────────────────────────────────────────────────

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Permission just granted — enable My Location layer and move camera
            if (gMap != null) {
                try {
                    gMap.setMyLocationEnabled(true);
                } catch (SecurityException ignored) {}
                moveToCurrentLocation(true);
            }
        }
    }

    // ─── MapView Lifecycle Pass-through ───────────────────────────────────────

    @Override protected void onResume()  { super.onResume();  mapView.onResume();  }
    @Override protected void onPause()   { super.onPause();   mapView.onPause();   }
    @Override protected void onDestroy() { super.onDestroy(); mapView.onDestroy(); }
    @Override public void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); }
}
