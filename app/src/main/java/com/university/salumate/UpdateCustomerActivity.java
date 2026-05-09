package com.university.salumate;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

/**
 * UpdateCustomerActivity — Edit form for modifying an existing customer record.
 *
 * <p>Pre-populates all form fields with the customer's current data when opened.
 * Includes a Google Maps embedded view for updating or correcting the customer's
 * stored geo-location pin. Also displays the customer's beneficiary list with
 * the ability to add new beneficiaries or delete existing ones.</p>
 *
 * <h3>Navigation</h3>
 * <ul>
 *   <li><b>Back / Save</b>: Saves changes and closes this activity (returns to list).</li>
 *   <li><b>Confirm / Dashboard</b>: Returns to the main Dashboard without explicitly
 *       saving — note that this button currently does NOT save changes first; the user
 *       should use the "Back" button to save.</li>
 * </ul>
 *
 * <p>Required intent extra: {@code customer_id} (long)</p>
 */
public class UpdateCustomerActivity extends AppCompatActivity {

    /** Input fields for editing the customer's name, phone, and address. */
    private EditText nameField, phoneField, addressField;

    /** Embedded map view for displaying and updating the customer's location pin. */
    private MapView mapView;

    /** Live GoogleMap instance, initialised asynchronously. */
    private GoogleMap gMap;

    /**
     * The currently selected GPS pin, updated whenever the user taps the map.
     * Pre-populated from the database when the activity first loads.
     */
    private LatLng customerLocation;

    /** The customer_id of the record being edited, passed via intent extra. */
    private long customerId;

    /** Database access helper for all read/write operations. */
    private DBHandler dbHandler;

    /** LinearLayout where beneficiary rows are dynamically inflated. */
    private LinearLayout layoutBeneficiaries;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update_customer);

        // Bind form field views
        nameField    = findViewById(R.id.CustomerName);
        phoneField   = findViewById(R.id.editTextPhone);
        addressField = findViewById(R.id.editTextAddress);
        mapView      = findViewById(R.id.mapView);
        dbHandler    = new DBHandler(this);

        // Retrieve the customer ID passed from the CustomerAdapter's edit action
        customerId        = getIntent().getLongExtra("customer_id", -1);
        layoutBeneficiaries = findViewById(R.id.layoutBeneficiaries);

        // Initialise the MapView lifecycle before async map loading
        mapView.onCreate(savedInstanceState);

        // Load customer data immediately to pre-fill the form fields
        if (customerId != -1) {
            loadCustomerData(customerId);
        }

        // Set up the map once it's ready
        mapView.getMapAsync(googleMap -> {
            gMap = googleMap;

            // If a saved location exists, centre the map on it with a marker
            if (customerLocation != null) {
                gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(customerLocation, 15));
                gMap.addMarker(new MarkerOptions()
                        .position(customerLocation)
                        .title("Current Location"));
            }

            // Allow the user to re-pin the location by tapping the map
            gMap.setOnMapClickListener(latLng -> {
                customerLocation = latLng; // Update the in-memory location
                gMap.clear();
                gMap.addMarker(new MarkerOptions().position(latLng).title("Updated Location"));
            });
        });

        // "Save & Back" button: persists changes then closes this activity
        findViewById(R.id.btn_BackDress).setOnClickListener(v -> {
            updateCustomerData();
            finish();
        });

        // "Go to Dashboard" button: navigates to Dashboard (clears the back-stack)
        // NOTE: Does NOT save form changes. User should press "Save & Back" to persist.
        findViewById(R.id.btn_ConfirmDress).setOnClickListener(v -> {
            Intent i = new Intent(UpdateCustomerActivity.this, DashboardActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
        });

        // "Add Beneficiary" button: opens BeneficiaryActivity in standalone mode
        // (user returns here after saving the beneficiary via onResume reload)
        findViewById(R.id.btnAddBeneficiary).setOnClickListener(v -> {
            Intent intent = new Intent(UpdateCustomerActivity.this, BeneficiaryActivity.class);
            intent.putExtra("customer_id", customerId);
            intent.putExtra("standalone", true);
            startActivity(intent);
        });
    }

    /**
     * Refreshes the MapView and re-loads beneficiaries so that any beneficiary
     * added in {@link BeneficiaryActivity} is immediately visible on return.
     */
    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        if (customerId != -1) {
            loadBeneficiaries(); // Refresh list after potential add from BeneficiaryActivity
        }
    }

    /**
     * Queries the database for the specified customer's current data and
     * pre-fills the form fields and map pin accordingly.
     *
     * @param id The customer_id whose data should be loaded.
     */
    private void loadCustomerData(long id) {
        Cursor cursor = dbHandler.getCustomerById(id);
        if (cursor != null && cursor.moveToFirst()) {
            nameField.setText(cursor.getString(cursor.getColumnIndexOrThrow("name")));
            phoneField.setText(cursor.getString(cursor.getColumnIndexOrThrow("phone_number")));
            addressField.setText(cursor.getString(cursor.getColumnIndexOrThrow("address")));

            // Load stored GPS coordinates for the initial map marker
            double lat = cursor.getDouble(cursor.getColumnIndexOrThrow("latitude"));
            double lng = cursor.getDouble(cursor.getColumnIndexOrThrow("longitude"));
            customerLocation = new LatLng(lat, lng);

            cursor.close();
            loadBeneficiaries(); // Also load the beneficiary list on initial data load
        }
    }

    /**
     * Dynamically inflates a row into {@code layoutBeneficiaries} for each beneficiary
     * linked to this customer. Each row shows the beneficiary name + relation, and a
     * delete button that removes the record immediately on tap.
     */
    private void loadBeneficiaries() {
        layoutBeneficiaries.removeAllViews(); // Clear before re-populating

        Cursor cur = dbHandler.getReadableDatabase().rawQuery(
                "SELECT beneficiary_id, name, gender, relation " +
                "FROM Beneficiaries WHERE customer_id = ?",
                new String[]{String.valueOf(customerId)});

        if (cur != null && cur.moveToFirst()) {
            do {
                long   bId   = cur.getLong(0);
                String bName = cur.getString(1);
                // gender (index 2) not displayed here but available if needed
                String bRel  = cur.getString(3);

                // Build a horizontal row: name label on left, delete button on right
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setLayoutParams(new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                row.setPadding(0, 16, 0, 16);

                TextView txtInfo = new TextView(this);
                txtInfo.setText(bName + " (" + bRel + ")");
                txtInfo.setTextSize(16);
                txtInfo.setLayoutParams(new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1)); // Fill remaining width

                Button btnDel = new Button(this);
                btnDel.setText("✕");
                btnDel.setOnClickListener(v -> {
                    // Delete the beneficiary and refresh the list
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
     * Defaults lat/lng to 0,0 if the user has not set a map pin.
     */
    private void updateCustomerData() {
        String name    = nameField.getText().toString().trim();
        String phone   = phoneField.getText().toString().trim();
        String address = addressField.getText().toString().trim();

        // Default to (0, 0) if no map pin has been selected
        double lat = customerLocation != null ? customerLocation.latitude  : 0;
        double lng = customerLocation != null ? customerLocation.longitude : 0;

        boolean updated = dbHandler.updateCustomer(customerId, name, phone, address, lat, lng);
        Toast.makeText(this,
                updated ? "Customer updated successfully." : "Update failed. Please try again.",
                Toast.LENGTH_SHORT).show();
    }

    // ─── MapView Lifecycle Pass-through Methods ───────────────────────────────

    /** Forward to MapView to prevent memory leaks and ensure correct rendering. */
    @Override protected void onDestroy() { super.onDestroy(); mapView.onDestroy(); }

    /** Forward low-memory signals to MapView so it can release tile caches. */
    @Override public void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); }
}
