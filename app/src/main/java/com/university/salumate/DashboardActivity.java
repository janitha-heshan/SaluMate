package com.university.salumate;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * DashboardActivity — The main hub of the SaluMate application.
 *
 * <p>Displays at-a-glance business metrics (total revenue, monthly sales)
 * and provides quick-action tiles for navigating to Orders, Dresses,
 * Reports, and Customers. Also renders the 5 most urgent upcoming orders
 * in a compact list sorted by due date.</p>
 *
 * <p>All metrics are refreshed every time the user returns to this screen
 * via {@link #onResume()}.</p>
 */
public class DashboardActivity extends AppCompatActivity {

    /** Database access handler wrapping all SQLite operations. */
    private DBHandler dbHandler;

    /** TextViews bound to the revenue metric cards in the layout. */
    private TextView txtTotalRevenue, txtMonthlySales;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        dbHandler = new DBHandler(this);

        // Bind revenue metric TextViews
        txtTotalRevenue = findViewById(R.id.dashTotalRevenue);
        txtMonthlySales = findViewById(R.id.dashMonthlySales);

        // "All Orders" quick-action tile
        View addCustomerButton = findViewById(R.id.addCustomer);
        addCustomerButton.setOnClickListener(v -> {
            startActivity(new Intent(DashboardActivity.this, AllOrdersActivity.class));
        });

        // "All Dresses" quick-action tile
        View btnAllDresses = findViewById(R.id.btn_AllDresses);
        btnAllDresses.setOnClickListener(v -> {
            startActivity(new Intent(DashboardActivity.this, AllDressesActivity.class));
        });

        // "Reports" quick-action tile
        View btnReports = findViewById(R.id.btn_Reports);
        btnReports.setOnClickListener(v -> {
            startActivity(new Intent(DashboardActivity.this, ReportsActivity.class));
        });

        // "Customers" quick-action tile
        View btnCustomers = findViewById(R.id.btn_Customers);
        btnCustomers.setOnClickListener(v -> {
            startActivity(new Intent(DashboardActivity.this, AllCustomersActivity.class));
        });
    }

    /**
     * Called every time this activity becomes visible.
     * Refreshes all financial metrics and the recent orders list
     * so any changes made in other screens are immediately reflected.
     */
    @Override
    protected void onResume() {
        super.onResume();
        loadMetrics();
        loadRecentOrders();
    }

    /**
     * Queries the SQLite database for aggregate revenue figures and
     * populates the two metric cards on the dashboard header.
     * <ul>
     *   <li>Total Revenue — SUM of all order prices ever recorded</li>
     *   <li>Monthly Sales — SUM of orders created in the current calendar month</li>
     * </ul>
     */
    private void loadMetrics() {
        SQLiteDatabase db = dbHandler.getReadableDatabase();

        // --- Total Revenue (all time) ---
        Cursor c1 = db.rawQuery("SELECT SUM(total_price) FROM Orders", null);
        if (c1 != null && c1.moveToFirst()) {
            double total = c1.getDouble(0);
            txtTotalRevenue.setText(String.format("LKR %.2f", total));
            c1.close();
        }

        // --- Monthly Sales (current month only) ---
        Cursor c2 = db.rawQuery(
                "SELECT SUM(total_price) FROM Orders " +
                "WHERE strftime('%Y-%m', created_at) = strftime('%Y-%m', 'now')",
                null);
        if (c2 != null && c2.moveToFirst()) {
            double monthly = c2.getDouble(0);
            txtMonthlySales.setText(String.format("LKR %.2f", monthly));
            c2.close();
        }
    }

    /**
     * Fetches the 5 most urgent pending orders (sorted by earliest due date)
     * and binds them to the recent orders ListView using an {@link OrderAdapter}.
     * Tapping any row navigates to {@link OrderDetailsActivity} for that order.
     */
    private void loadRecentOrders() {
        ListView listViewOrders = findViewById(R.id.listViewOrders);
        ArrayList<HashMap<String, String>> orderList = new ArrayList<>();

        // Fetch top 5 orders; NULLs (no due date) sorted last
        Cursor cursor = dbHandler.getReadableDatabase().rawQuery(
                "SELECT order_id, order_status, total_price, created_at, due_date " +
                "FROM Orders ORDER BY due_date IS NULL, due_date ASC LIMIT 5",
                null);

        if (cursor != null && cursor.moveToFirst()) {
            do {
                // Map each column to a named key for the adapter to consume
                HashMap<String, String> map = new HashMap<>();
                map.put("order_id",    cursor.getString(0));
                map.put("status",      cursor.getString(1));
                map.put("total",       cursor.getString(2));
                map.put("created_at",  cursor.getString(3));
                map.put("due_date",    cursor.getString(4));
                orderList.add(map);
            } while (cursor.moveToNext());
            cursor.close();
        }

        // Bind the list and set a click listener to open order details
        OrderAdapter adapter = new OrderAdapter(this, orderList, dbHandler);
        listViewOrders.setAdapter(adapter);

        listViewOrders.setOnItemClickListener((parent, view, position, id) -> {
            HashMap<String, String> item = orderList.get(position);
            Intent intent = new Intent(DashboardActivity.this, OrderDetailsActivity.class);
            intent.putExtra("order_id", Long.parseLong(item.get("order_id")));
            startActivity(intent);
        });
    }
}
