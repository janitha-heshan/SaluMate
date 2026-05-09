package com.university.salumate;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * AllDressesActivity — Displays the dress template catalogue.
 *
 * <p>Shows all saved dress templates in a scrollable {@link ListView} using a
 * {@link DressAdapter}. Each row displays the dress name and last-updated date.
 * Provides navigation to create new dress templates ({@link CreateDressActivity})
 * and back to the Dashboard.</p>
 *
 * <p>Note: The "New" button is currently bound to the id {@code btn_NewCustomer}
 * (re-used from the all-customers layout) — this is a known legacy naming quirk
 * and does not affect functionality.</p>
 */
public class AllDressesActivity extends AppCompatActivity {

    /** ListView that displays the list of dress template cards. */
    private ListView listView;

    /**
     * In-memory list of dress data maps. Each map holds the keys:
     * {@code dress_template_id}, {@code dress_name}, {@code updated_at}.
     */
    private ArrayList<HashMap<String, String>> dressesList = new ArrayList<>();

    /** Adapter that binds dress data maps to the ListView rows. */
    private DressAdapter adapter;

    /** Database access helper for dress template queries. */
    private DBHandler db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_dresses);

        listView = findViewById(R.id.listDresses);
        db = new DBHandler(this);

        loadDresses();

        // "New Dress" button — navigates to the dress template creation form
        findViewById(R.id.btn_NewCustomer).setOnClickListener(v ->
            startActivity(new Intent(this, CreateDressActivity.class))
        );

        // Back button — returns to the Dashboard
        findViewById(R.id.btn_BackToDashboard).setOnClickListener(v -> {
            startActivity(new Intent(this, DashboardActivity.class));
            finish();
        });
    }

    /**
     * Queries all dress templates from the database and refreshes the ListView.
     * Clears the existing list before populating to avoid duplicates.
     */
    private void loadDresses() {
        dressesList.clear();
        Cursor cursor = db.getAllDresses();

        // Guard against a null cursor (e.g. empty database on first run)
        if (cursor != null && cursor.moveToFirst()) {
            do {
                HashMap<String, String> item = new HashMap<>();
                item.put("dress_template_id",
                        cursor.getString(cursor.getColumnIndexOrThrow("dress_template_id")));
                item.put("dress_name",
                        cursor.getString(cursor.getColumnIndexOrThrow("dress_name")));
                item.put("updated_at",
                        cursor.getString(cursor.getColumnIndexOrThrow("updated_at")));
                dressesList.add(item);
            } while (cursor.moveToNext());
            cursor.close();
        }

        adapter = new DressAdapter(this, dressesList);
        listView.setAdapter(adapter);
    }
}
