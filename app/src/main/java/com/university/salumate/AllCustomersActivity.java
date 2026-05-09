package com.university.salumate;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class AllCustomersActivity extends AppCompatActivity {
    private ListView listView;
    private List<Customer> customerList = new ArrayList<>();
    private CustomerAdapter adapter;
    private DBHandler db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_customers);

        listView = findViewById(R.id.listCustomers);
        db = new DBHandler(this);

        findViewById(R.id.btn_NewCustomer).setOnClickListener(v -> {
            startActivity(new Intent(this, CustomerActivity.class));
        });

        findViewById(R.id.btn_BackToDashboard).setOnClickListener(v -> {
            finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCustomers();
    }

    private void loadCustomers() {
        customerList.clear();
        Cursor cursor = db.getAllCustomers();
        if (cursor != null && cursor.moveToFirst()) {
            do {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow("customer_id"));
                String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                String phone = cursor.getString(cursor.getColumnIndexOrThrow("phone_number"));
                Customer c = new Customer(id, name, phone);
                customerList.add(c);
            } while (cursor.moveToNext());
            cursor.close();
        }
        adapter = new CustomerAdapter(this, customerList, db);
        listView.setAdapter(adapter);
    }
}
