package com.university.salumate;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.widget.ListView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.HashMap;

public class AllDressesActivity extends AppCompatActivity {

    ListView listView;
    ArrayList<HashMap<String, String>> dressesList = new ArrayList<>();
    DressAdapter adapter;
    DBHandler db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_dresses);

        listView = findViewById(R.id.listDresses);
        db = new DBHandler(this);

        loadDresses();

        findViewById(R.id.btn_NewCustomer).setOnClickListener(v -> {
            startActivity(new Intent(this, CreateDressActivity.class));
        });

        findViewById(R.id.btn_BackToDashboard).setOnClickListener(v -> {
            startActivity(new Intent(this, DashboardActivity.class));
            finish();
        });
    }

    private void loadDresses() {
        dressesList.clear();
        Cursor cursor = db.getAllDresses();
        if (cursor.moveToFirst()) {
            do {
                HashMap<String, String> item = new HashMap<>();
                item.put("dress_template_id", cursor.getString(cursor.getColumnIndexOrThrow("dress_template_id")));
                item.put("dress_name", cursor.getString(cursor.getColumnIndexOrThrow("dress_name")));
                item.put("updated_at", cursor.getString(cursor.getColumnIndexOrThrow("updated_at")));
                dressesList.add(item);
            } while (cursor.moveToNext());
        }
        adapter = new DressAdapter(this, dressesList);
        listView.setAdapter(adapter);
    }
}
