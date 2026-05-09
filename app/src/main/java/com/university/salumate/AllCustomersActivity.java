package com.university.salumate;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * AllCustomersActivity — Displays the full customer directory.
 *
 * <p>Renders all registered customers in a scrollable {@link ListView} using
 * a {@link CustomerAdapter}. Provides navigation to create a new customer
 * ({@link CustomerActivity}) and back to the Dashboard.</p>
 *
 * <p>The list is refreshed each time the activity resumes so that any
 * additions or edits made in child screens are immediately visible.</p>
 */
public class AllCustomersActivity extends AppCompatActivity {

    /** The scrollable list showing all registered customers. */
    private ListView listView;

    /** In-memory list of {@link Customer} objects driving the adapter. */
    private List<Customer> customerList = new ArrayList<>();

    /** Adapter bridging the customer list data to the ListView rows. */
    private CustomerAdapter adapter;

    /** Database access handler for all customer queries. */
    private DBHandler db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_customers);

        listView = findViewById(R.id.listCustomers);
        db = new DBHandler(this);

        // "New Customer" button navigates to the customer creation form
        findViewById(R.id.btn_NewCustomer).setOnClickListener(v ->
            startActivity(new Intent(this, CustomerActivity.class))
        );

        // Back button simply closes this activity and returns to the caller
        findViewById(R.id.btn_BackToDashboard).setOnClickListener(v -> finish());
    }

    /**
     * Reload the customer list every time the screen becomes visible.
     * This handles the case where the user creates or edits a customer
     * in a child activity and then returns here.
     */
    @Override
    protected void onResume() {
        super.onResume();
        loadCustomers();
    }

    /**
     * Queries the database for all customers and refreshes the ListView.
     * Clears the in-memory list first to prevent duplicates on repeated calls.
     */
    private void loadCustomers() {
        customerList.clear();

        Cursor cursor = db.getAllCustomers();
        if (cursor != null && cursor.moveToFirst()) {
            do {
                long   id    = cursor.getLong(cursor.getColumnIndexOrThrow("customer_id"));
                String name  = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                String phone = cursor.getString(cursor.getColumnIndexOrThrow("phone_number"));
                customerList.add(new Customer(id, name, phone));
            } while (cursor.moveToNext());
            cursor.close();
        }

        // Bind the updated list to a fresh adapter
        adapter = new CustomerAdapter(this, customerList, db);
        listView.setAdapter(adapter);
    }
}
