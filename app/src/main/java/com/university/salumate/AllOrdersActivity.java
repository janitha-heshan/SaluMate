package com.university.salumate;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * AllOrdersActivity — Displays the complete order history for the shop.
 *
 * <p>Shows all orders in a scrollable {@link ListView} sorted by due date
 * (earliest deadlines first, orders without a due date last). Tapping any
 * order row opens {@link OrderDetailsActivity} for that order.</p>
 *
 * <p>Provides a "New Order" button to launch the multi-step order creation
 * wizard ({@link CreateOrderActivity}). The list automatically refreshes
 * every time the screen becomes visible via {@link #onResume()}.</p>
 */
public class AllOrdersActivity extends AppCompatActivity {

    /** The scrollable list of all orders. */
    private ListView listView;

    /**
     * In-memory list of order data maps. Each map contains keys:
     * {@code order_id, status, total, created_at, due_date}.
     */
    private ArrayList<HashMap<String, String>> orderList = new ArrayList<>();

    /** Adapter that binds the order list data to the ListView rows. */
    private OrderAdapter adapter;

    /** Database access handler for all order queries. */
    private DBHandler db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_orders);

        listView = findViewById(R.id.listOrders);
        db = new DBHandler(this);

        // Launch the Create Order wizard
        findViewById(R.id.btn_NewOrder).setOnClickListener(v ->
            startActivity(new Intent(this, CreateOrderActivity.class))
        );

        // Back closes this activity and returns to the caller (Dashboard)
        findViewById(R.id.btn_BackToDashboard).setOnClickListener(v -> finish());
    }

    /**
     * Refresh the order list each time this screen becomes visible so that
     * newly created or edited orders are immediately reflected.
     */
    @Override
    protected void onResume() {
        super.onResume();
        loadOrders();
    }

    /**
     * Queries the database for all orders, sorted by urgency (earliest due date first),
     * and binds the results to the ListView via an {@link OrderAdapter}.
     * Clears the in-memory list first to prevent duplicate entries on repeated calls.
     */
    private void loadOrders() {
        orderList.clear();

        // Query all orders; NULL due_dates are sorted last via SQLite's NULL-last trick
        Cursor cursor = db.getReadableDatabase().rawQuery(
                "SELECT order_id, order_status, total_price, created_at, due_date " +
                "FROM Orders ORDER BY due_date IS NULL, due_date ASC", null);

        if (cursor != null && cursor.moveToFirst()) {
            do {
                // Map column values to named keys consumed by OrderAdapter
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

        // Bind and apply the adapter; clicking a row opens order details
        adapter = new OrderAdapter(this, orderList, db);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            HashMap<String, String> item = orderList.get(position);
            Intent intent = new Intent(AllOrdersActivity.this, OrderDetailsActivity.class);
            intent.putExtra("order_id", Long.parseLong(item.get("order_id")));
            startActivity(intent);
        });
    }
}
