package com.university.salumate;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
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
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.textfield.TextInputEditText;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * UpdateCustomerActivity — Edit form for modifying an existing customer record.
 *
 * <p>Pre-populates all form fields with the customer's current data when opened.
 * Includes a Google Maps embedded view for updating or correcting the customer's
 * stored geo-location pin. Also displays the customer's beneficiary list with
 * the ability to add new beneficiaries or delete existing ones.</p>
 *
 * <h3>Map Features</h3>
 * <ul>
 *   <li><b>Stored Location</b>: Camera centres on the customer's saved lat/lng on open.</li>
 *   <li><b>Address Search</b>: 🔍 button geocodes an address and drops a new pin.</li>
 *   <li><b>My Location</b>: 📍 button re-centres the camera on the device's current GPS.</li>
 *   <li><b>Map Tap</b>: User can tap any point to re-pin the customer's location.</li>
 * </ul>
 *
 * <p>Required intent extra: {@code customer_id} (long)</p>
 */
public class UpdateCustomerActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST = 102;
    private static final float DEFAULT_ZOOM = 15f;

    // Form fields
    private EditText nameField, phoneField, addressField;

    // Map
    private MapView mapView;
    private GoogleMap gMap;
    private LatLng customerLocation;

    // Location search
    private TextInputEditText etLocationSearch;
    private TextView txtLocationStatus;

    // Location provider
    private FusedLocationProviderClient fusedLocationClient;

    // Data
    private long customerId;
    private DBHandler dbHandler;
    private LinearLayout layoutBeneficiaries;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update_customer);

        nameField        = findViewById(R.id.CustomerName);
        phoneField       = findViewById(R.id.editTextPhone);
        addressField     = findViewById(R.id.editTextAddress);
        mapView          = findViewById(R.id.mapView);
        etLocationSearch = findViewById(R.id.etLocationSearch);
        txtLocationStatus= findViewById(R.id.txtLocationStatus);
        dbHandler        = new DBHandler(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        customerId          = getIntent().getLongExtra("customer_id", -1);
        layoutBeneficiaries = findViewById(R.id.layoutBeneficiaries);

        // Load customer data to pre-fill form (sets customerLocation from DB)
        if (customerId != -1) {
            loadCustomerData(customerId);
        }

        // Initialise MapView
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(googleMap -> {
            gMap = googleMap;

            // Enable My Location blue dot if permission granted
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                gMap.setMyLocationEnabled(true);
            }

            // Centre on stored location (if valid) or current device location
            if (customerLocation != null
                    && (customerLocation.latitude != 0 || customerLocation.longitude != 0)) {
                gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(customerLocation, DEFAULT_ZOOM));
                gMap.addMarker(new MarkerOptions()
                        .position(customerLocation)
                        .title("Stored Location"));
                updateStatusLabel(customerLocation, null);
            } else {
                // No stored location — centre on current device GPS as a helpful default
                moveToCurrentLocation(true);
                txtLocationStatus.setText("Tap map to update pin · or search above");
            }

            // Tap map → update pin
            gMap.setOnMapClickListener(latLng -> {
                customerLocation = latLng;
                gMap.clear();
                gMap.addMarker(new MarkerOptions()
                        .position(latLng)
                        .title("Updated Location"));
                updateStatusLabel(latLng, null);
            });
        });

        // 🔍 Search button
        findViewById(R.id.btnSearchLocation).setOnClickListener(v -> searchLocation());

        // 📍 My Location button
        findViewById(R.id.btnMyLocation).setOnClickListener(v -> moveToCurrentLocation(false));

        // Keyboard "Search" action
        etLocationSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchLocation();
                return true;
            }
            return false;
        });

        // "Save & Back" — persist changes and close
        findViewById(R.id.btn_BackDress).setOnClickListener(v -> {
            updateCustomerData();
            finish();
        });

        // "Go to Dashboard" — navigate without saving
        findViewById(R.id.btn_ConfirmDress).setOnClickListener(v -> {
            Intent i = new Intent(UpdateCustomerActivity.this, DashboardActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
        });

        // "Add Beneficiary" — open BeneficiaryActivity for this customer
        findViewById(R.id.btnAddBeneficiary).setOnClickListener(v -> {
            Intent intent = new Intent(UpdateCustomerActivity.this, BeneficiaryActivity.class);
            intent.putExtra("customer_id", customerId);
            intent.putExtra("standalone", true);
            startActivity(intent);
        });

        // Request permission if not yet granted
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
     * Moves the camera to the device's last known location.
     * @param isDefault {@code true} when called as a silent default (no toast).
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
     * Geocodes the address in {@code etLocationSearch} and moves the camera/pin there.
     */
    private void searchLocation() {
        String query = etLocationSearch.getText() != null
                ? etLocationSearch.getText().toString().trim() : "";
        if (query.isEmpty()) {
            Toast.makeText(this, "Enter an address or place name to search.", Toast.LENGTH_SHORT).show();
            return;
        }

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

                customerLocation = found;
                updateStatusLabel(found, best.getAddressLine(0));
            } else {
                Toast.makeText(this, "Location not found. Try a different search.", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Toast.makeText(this, "Search failed — check your internet connection.", Toast.LENGTH_SHORT).show();
        }
    }

    /** Updates the status label with formatted coordinates and optional address line. */
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
            if (gMap != null) {
                try { gMap.setMyLocationEnabled(true); } catch (SecurityException ignored) {}
                // Only move to current location if no stored pin was set
                if (customerLocation == null
                        || (customerLocation.latitude == 0 && customerLocation.longitude == 0)) {
                    moveToCurrentLocation(true);
                }
            }
        }
    }

    // ─── Data Loading ─────────────────────────────────────────────────────────

    /**
     * Queries the database for the specified customer's current data and
     * pre-fills the form fields and sets {@code customerLocation} from stored GPS.
     */
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

    /**
     * Dynamically inflates a row into {@code layoutBeneficiaries} for each beneficiary
     * linked to this customer. Each row shows the beneficiary name + relation, and a
     * delete button that removes the record immediately on tap.
     */
    private void loadBeneficiaries() {
        layoutBeneficiaries.removeAllViews();

        Cursor cur = dbHandler.getReadableDatabase().rawQuery(
                "SELECT beneficiary_id, name, gender, relation " +
                "FROM Beneficiaries WHERE customer_id = ?",
                new String[]{String.valueOf(customerId)});

        if (cur != null && cur.moveToFirst()) {
            do {
                long   bId   = cur.getLong(0);
                String bName = cur.getString(1);
                String bRel  = cur.getString(3);

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setLayoutParams(new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                row.setPadding(0, 16, 0, 16);

                TextView txtInfo = new TextView(this);
                txtInfo.setText(bName + " (" + bRel + ")");
                txtInfo.setTextSize(16);
                txtInfo.setLayoutParams(new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

                Button btnDel = new Button(this);
                btnDel.setText("✕");
                btnDel.setOnClickListener(v -> {
                    dbHandler.getWritableDatabase().execSQL(
                            "DELETE FROM Beneficiaries WHERE beneficiary_id = ?",
                            new String[]{String.valueOf(bId)});
                    loadBeneficiaries();
                });

                row.addView(txtInfo);
                row.addView(btnDel);
                layoutBeneficiaries.addView(row);

            } while (cur.moveToNext());
            cur.close();
        }
    }

    /**
     * Reads the current form values and the map pin location, then calls
     * {@link DBHandler#updateCustomer} to persist the changes to the database.
     */
    private void updateCustomerData() {
        String name    = nameField.getText().toString().trim();
        String phone   = phoneField.getText().toString().trim();
        String address = addressField.getText().toString().trim();

        double lat = customerLocation != null ? customerLocation.latitude  : 0;
        double lng = customerLocation != null ? customerLocation.longitude : 0;

        boolean updated = dbHandler.updateCustomer(customerId, name, phone, address, lat, lng);
        Toast.makeText(this,
                updated ? "Customer updated successfully." : "Update failed. Please try again.",
                Toast.LENGTH_SHORT).show();
    }

    // ─── MapView Lifecycle Pass-through ───────────────────────────────────────

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        if (customerId != -1) loadBeneficiaries();
    }

    @Override protected void onPause()   { super.onPause();   mapView.onPause();   }
    @Override protected void onDestroy() { super.onDestroy(); mapView.onDestroy(); }
    @Override public void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); }
}
