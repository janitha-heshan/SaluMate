package com.university.salumate;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ListView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.List;

public class CreateOrderActivity extends AppCompatActivity {

    private ListView customerListView;
    private Button btnNewCustomer, btnBack;
    private DBHandler dbHandler;
    private List<Customer> customerList;
    private CustomerAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_order);

        customerListView = findViewById(R.id.listView2);
        btnNewCustomer = findViewById(R.id.btn_NewCustomer);
        btnBack = findViewById(R.id.btn_BackToDashboard);

        dbHandler = new DBHandler(this);
        loadCustomers();

        btnNewCustomer.setOnClickListener(v -> {
            startActivity(new Intent(this, CustomerActivity.class));
        });

        btnBack.setOnClickListener(v -> {
            startActivity(new Intent(this, DashboardActivity.class));
            finish();
        });
    }

    private void loadCustomers() {
        customerList = new ArrayList<>();
        Cursor cursor = dbHandler.getAllCustomers();
        if (cursor != null && cursor.moveToFirst()) {
            do {
                long id = cursor.getLong(cursor.getColumnIndex("customer_id"));
                String name = cursor.getString(cursor.getColumnIndex("name"));
                String phone = cursor.getString(cursor.getColumnIndex("phone_number"));
                customerList.add(new Customer(id, name, phone));
            } while (cursor.moveToNext());
            cursor.close();
        }

        adapter = new CustomerAdapter(this, customerList, dbHandler);
        customerListView.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCustomers(); // Reload list when returning from another activity
    }
}