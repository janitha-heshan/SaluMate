package com.university.salumate;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.ListView;
import android.widget.AdapterView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class DashboardActivity extends AppCompatActivity {

    private DBHandler dbHandler;
    private TextView txtTotalRevenue, txtMonthlySales;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_dashboard);

        dbHandler = new DBHandler(this);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        txtTotalRevenue = findViewById(R.id.dashTotalRevenue);
        txtMonthlySales = findViewById(R.id.dashMonthlySales);

        View addCustomerButton = findViewById(R.id.addCustomer);
        addCustomerButton.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, AllOrdersActivity.class);
            startActivity(intent);
        });

        View btnAllDresses = findViewById(R.id.btn_AllDresses);
        btnAllDresses.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, AllDressesActivity.class);
            startActivity(intent);
        });

        View btnReports = findViewById(R.id.btn_Reports);
        btnReports.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, ReportsActivity.class);
            startActivity(intent);
        });

        View btnCustomers = findViewById(R.id.btn_Customers);
        btnCustomers.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardActivity.this, AllCustomersActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadMetrics();
        loadRecentOrders();
    }

    private void loadMetrics() {
        SQLiteDatabase db = dbHandler.getReadableDatabase();

        // Total Revenue
        Cursor c1 = db.rawQuery("SELECT SUM(total_price) FROM Orders", null);
        if (c1 != null && c1.moveToFirst()) {
            double total = c1.getDouble(0);
            txtTotalRevenue.setText(String.format("LKR %.2f", total));
            c1.close();
        }

        // Monthly Sales
        Cursor c2 = db.rawQuery(
                "SELECT SUM(total_price) FROM Orders WHERE strftime('%Y-%m', created_at) = strftime('%Y-%m', 'now')",
                null);
        if (c2 != null && c2.moveToFirst()) {
            double monthly = c2.getDouble(0);
            txtMonthlySales.setText(String.format("LKR %.2f", monthly));
            c2.close();
        }
    }

    private void loadRecentOrders() {
        ListView listViewOrders = findViewById(R.id.listViewOrders);
        ArrayList<HashMap<String, String>> orderList = new ArrayList<>();
        Cursor cursor = dbHandler.getReadableDatabase().rawQuery(
                "SELECT order_id, order_status, total_price, created_at, due_date FROM Orders ORDER BY due_date IS NULL, due_date ASC LIMIT 5",
                null);
        if (cursor != null && cursor.moveToFirst()) {
            do {
                HashMap<String, String> map = new HashMap<>();
                map.put("order_id", cursor.getString(0));
                map.put("status", cursor.getString(1));
                map.put("total", cursor.getString(2));
                map.put("created_at", cursor.getString(3));
                map.put("due_date", cursor.getString(4));
                orderList.add(map);
            } while (cursor.moveToNext());
            cursor.close();
        }
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
