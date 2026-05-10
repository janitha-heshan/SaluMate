package com.university.salumate;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;

/**
 * AllOrdersActivity — Displays the complete order history for the shop.
 *
 * <p>Supports searching by Customer Name or Contact Number and filtering
 * by a date range (created_at). Results are sorted by urgency (due date).</p>
 */
public class AllOrdersActivity extends AppCompatActivity {

    private ListView listView;
    private ArrayList<HashMap<String, String>> orderList = new ArrayList<>();
    private OrderAdapter adapter;
    private DBHandler db;

    /** Active search text from the search bar (empty = no filter). */
    private String searchQuery = "";

    /** Start/end date strings in "YYYY-MM-DD" format, empty = not set. */
    private String startDate = "";
    private String endDate   = "";

    private MaterialButton btnStartDate;
    private MaterialButton btnEndDate;

    private final SimpleDateFormat displayFmt = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
    private final SimpleDateFormat dbFmt      = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_orders);

        listView     = findViewById(R.id.listOrders);
        btnStartDate = findViewById(R.id.btnStartDate);
        btnEndDate   = findViewById(R.id.btnEndDate);
        db           = new DBHandler(this);

        // ── Navigation buttons ─────────────────────────────────────────────
        findViewById(R.id.btn_NewOrder).setOnClickListener(v ->
            startActivity(new Intent(this, CreateOrderActivity.class))
        );
        findViewById(R.id.btn_BackToDashboard).setOnClickListener(v -> finish());

        // ── Search bar – filter as the user types ──────────────────────────
        EditText etSearch = findViewById(R.id.etSearchOrders);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                searchQuery = s.toString().trim();
                loadOrders();
            }
        });

        // ── Date pickers ───────────────────────────────────────────────────
        btnStartDate.setOnClickListener(v -> showDatePicker(true));
        btnEndDate.setOnClickListener(v   -> showDatePicker(false));

        // ── Clear all filters ──────────────────────────────────────────────
        ImageButton btnClear = findViewById(R.id.btnClearFilters);
        btnClear.setOnClickListener(v -> {
            etSearch.setText("");
            searchQuery = "";
            startDate   = "";
            endDate     = "";
            btnStartDate.setText("From Date");
            btnEndDate.setText("To Date");
            loadOrders();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadOrders();
    }

    // ── Date picker helper ─────────────────────────────────────────────────

    private void showDatePicker(boolean isStart) {
        Calendar cal = Calendar.getInstance();
        DatePickerDialog dpd = new DatePickerDialog(this,
            (view, year, month, dayOfMonth) -> {
                Calendar selected = Calendar.getInstance();
                selected.set(year, month, dayOfMonth);
                String dbStr      = dbFmt.format(selected.getTime());
                String displayStr = displayFmt.format(selected.getTime());

                if (isStart) {
                    startDate = dbStr;
                    btnStartDate.setText(displayStr);
                    // Guard: start must not be after end
                    if (!endDate.isEmpty() && dbStr.compareTo(endDate) > 0) {
                        endDate = "";
                        btnEndDate.setText("To Date");
                        Toast.makeText(this, "Start date reset: was after end date.", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    endDate = dbStr;
                    btnEndDate.setText(displayStr);
                    if (!startDate.isEmpty() && dbStr.compareTo(startDate) < 0) {
                        startDate = "";
                        btnStartDate.setText("From Date");
                        Toast.makeText(this, "End date reset: was before start date.", Toast.LENGTH_SHORT).show();
                    }
                }
                loadOrders();
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        dpd.show();
    }

    // ── Data loading ───────────────────────────────────────────────────────

    /**
     * Queries the database for orders matching the active filters.
     * JOINs with Customers so that name and phone can be searched.
     * Filters are combined with AND logic.
     */
    private void loadOrders() {
        orderList.clear();

        StringBuilder sql = new StringBuilder(
            "SELECT o.order_id, o.order_status, o.total_price, o.created_at, o.due_date, " +
            "c.name, c.phone_number " +
            "FROM Orders o " +
            "LEFT JOIN Customers c ON o.customer_id = c.customer_id " +
            "WHERE 1=1"
        );

        ArrayList<String> args = new ArrayList<>();

        // Search by customer name OR phone
        if (!searchQuery.isEmpty()) {
            sql.append(" AND (c.name LIKE ? OR c.phone_number LIKE ?)");
            args.add("%" + searchQuery + "%");
            args.add("%" + searchQuery + "%");
        }

        // Date range – filter by order creation date
        if (!startDate.isEmpty()) {
            sql.append(" AND DATE(o.created_at) >= ?");
            args.add(startDate);
        }
        if (!endDate.isEmpty()) {
            sql.append(" AND DATE(o.created_at) <= ?");
            args.add(endDate);
        }

        sql.append(" ORDER BY o.due_date IS NULL, o.due_date ASC");

        Cursor cursor = db.getReadableDatabase().rawQuery(
            sql.toString(), args.toArray(new String[0]));

        if (cursor != null && cursor.moveToFirst()) {
            do {
                HashMap<String, String> map = new HashMap<>();
                map.put("order_id",      cursor.getString(0));
                map.put("status",        cursor.getString(1));
                map.put("total",         cursor.getString(2));
                map.put("created_at",    cursor.getString(3));
                map.put("due_date",      cursor.getString(4));
                map.put("customer_name", cursor.getString(5) != null ? cursor.getString(5) : "");
                orderList.add(map);
            } while (cursor.moveToNext());
            cursor.close();
        }

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
