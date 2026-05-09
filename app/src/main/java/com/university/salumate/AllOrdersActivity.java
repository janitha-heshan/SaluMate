package com.university.salumate;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.HashMap;

public class AllOrdersActivity extends AppCompatActivity {
    private ListView listView;
    private ArrayList<HashMap<String, String>> orderList = new ArrayList<>();
    private OrderAdapter adapter;
    private DBHandler db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_orders);

        listView = findViewById(R.id.listOrders);
        db = new DBHandler(this);

        findViewById(R.id.btn_NewOrder).setOnClickListener(v -> {
            startActivity(new Intent(this, CreateOrderActivity.class));
        });

        findViewById(R.id.btn_BackToDashboard).setOnClickListener(v -> {
            finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadOrders();
    }

    private void loadOrders() {
        orderList.clear();
        Cursor cursor = db.getReadableDatabase().rawQuery("SELECT order_id, order_status, total_price, created_at, due_date FROM Orders ORDER BY due_date IS NULL, due_date ASC", null);
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
        adapter = new OrderAdapter(this, orderList, db);
        listView.setAdapter(adapter);
    }
}
