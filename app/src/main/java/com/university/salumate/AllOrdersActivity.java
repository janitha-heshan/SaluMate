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
 * AllOrdersActivity — Displays, filters, and navigates the complete order history.
 *
 * <h3>Purpose</h3>
 * <p>Renders all shop orders in a scrollable {@link ListView}, sorted by urgency
 * (earliest due date first; orders without a due date appear last).
 * The list can be dynamically filtered by customer name, contact number,
 * and/or a date range based on order creation date.</p>
 *
 * <h3>Navigation</h3>
 * <ul>
 *   <li>Launched from: {@link DashboardActivity} via the "📋 All Orders" quick-action card.</li>
 *   <li>Tapping a list row opens {@link OrderDetailsActivity} with the selected {@code order_id}.</li>
 *   <li>"+ New Order" opens {@link CreateOrderActivity}.</li>
 *   <li>"Back" calls {@link #finish()} to return to the caller ({@link DashboardActivity}).</li>
 * </ul>
 *
 * <h3>Layout</h3>
 * <p>Defined in {@code res/layout/activity_all_orders.xml}.
 * Key views:
 * <ul>
 *   <li>{@code etSearchOrders}  — real-time text search field</li>
 *   <li>{@code btnClearFilters} — resets all active filters</li>
 *   <li>{@code btnStartDate}    — opens the "From Date" {@link DatePickerDialog}</li>
 *   <li>{@code btnEndDate}      — opens the "To Date" {@link DatePickerDialog}</li>
 *   <li>{@code listOrders}      — {@link ListView} bound by {@link OrderAdapter}</li>
 *   <li>{@code btn_NewOrder}    — navigates to {@link CreateOrderActivity}</li>
 *   <li>{@code btn_BackToDashboard} — finishes this activity</li>
 * </ul>
 * </p>
 *
 * <h3>Filter Logic</h3>
 * <p>All active filters are applied simultaneously with AND logic inside
 * the dynamic SQL query in {@link #loadOrders()}.
 * The base query does a LEFT JOIN with {@code Customers} so that customer name
 * and phone number are accessible for filtering without a separate query.</p>
 *
 * <h3>Database Tables Used</h3>
 * <ul>
 *   <li>{@code Orders}    — order header (order_id, status, total_price, created_at, due_date)</li>
 *   <li>{@code Customers} — joined to expose name and phone_number for search</li>
 * </ul>
 */
public class AllOrdersActivity extends AppCompatActivity {

    /**
     * The list view that renders each order as a row via {@link OrderAdapter}.
     * Defined as {@code listOrders} in {@code res/layout/activity_all_orders.xml}.
     */
    private ListView listView;

    /**
     * In-memory list of order data maps consumed by {@link OrderAdapter}.
     * Each {@link HashMap} contains keys: {@code order_id, status, total,
     * created_at, due_date, customer_name}.
     */
    private ArrayList<HashMap<String, String>> orderList = new ArrayList<>();

    /**
     * Adapter bridging {@link #orderList} data to the {@link #listView} rows.
     * @see OrderAdapter
     */
    private OrderAdapter adapter;

    /**
     * Central database helper for all SQLite read/write operations.
     * @see DBHandler
     */
    private DBHandler db;

    /**
     * The active text filter entered by the user in {@code etSearchOrders}.
     * Compared against {@code Customers.name} and {@code Customers.phone_number}
     * using SQL {@code LIKE} with wildcards. Empty string = no filter.
     */
    private String searchQuery = "";

    /**
     * Start date of the date-range filter in ISO format ({@code "YYYY-MM-DD"}).
     * Compared against {@code DATE(Orders.created_at)} using {@code >=}.
     * Empty string means this bound is not applied.
     */
    private String startDate = "";

    /**
     * End date of the date-range filter in ISO format ({@code "YYYY-MM-DD"}).
     * Compared against {@code DATE(Orders.created_at)} using {@code <=}.
     * Empty string means this bound is not applied.
     */
    private String endDate = "";

    /** Bound to {@code btnStartDate} in layout; shows the selected start date. */
    private MaterialButton btnStartDate;

    /** Bound to {@code btnEndDate} in layout; shows the selected end date. */
    private MaterialButton btnEndDate;

    /**
     * Formats dates for human-readable display on the picker buttons
     * (e.g. "10 May 2026"). Used to update button text after a date is selected.
     */
    private final SimpleDateFormat displayFmt = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

    /**
     * Formats dates for database comparison using SQLite's {@code DATE()} function
     * (e.g. "2026-05-10"). Used when appending filter clauses to the SQL query.
     */
    private final SimpleDateFormat dbFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Layout: res/layout/activity_all_orders.xml
        setContentView(R.layout.activity_all_orders);

        listView     = findViewById(R.id.listOrders);
        btnStartDate = findViewById(R.id.btnStartDate);
        btnEndDate   = findViewById(R.id.btnEndDate);
        db           = new DBHandler(this);

        // ── Navigation buttons ─────────────────────────────────────────────
        // "New Order" launches the 4-step order creation wizard
        findViewById(R.id.btn_NewOrder).setOnClickListener(v ->
            startActivity(new Intent(this, CreateOrderActivity.class))
        );
        // "Back" closes this activity and returns to DashboardActivity
        findViewById(R.id.btn_BackToDashboard).setOnClickListener(v -> finish());

        // ── Search bar ─────────────────────────────────────────────────────
        // Uses a TextWatcher to trigger loadOrders() on every keystroke.
        // Filters by Customers.name OR Customers.phone_number (via LEFT JOIN).
        EditText etSearch = findViewById(R.id.etSearchOrders);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                searchQuery = s.toString().trim();
                loadOrders(); // Re-query with updated search text
            }
        });

        // ── Date range pickers ─────────────────────────────────────────────
        // Each button opens the native Android DatePickerDialog.
        // showDatePicker(true) = start date; showDatePicker(false) = end date.
        btnStartDate.setOnClickListener(v -> showDatePicker(true));
        btnEndDate.setOnClickListener(v   -> showDatePicker(false));

        // ── Clear all filters ──────────────────────────────────────────────
        // Resets search text, start date, and end date, then reloads the full list.
        ImageButton btnClear = findViewById(R.id.btnClearFilters);
        btnClear.setOnClickListener(v -> {
            etSearch.setText("");
            searchQuery = "";
            startDate   = "";
            endDate     = "";
            btnStartDate.setText("From Date"); // Reset button label
            btnEndDate.setText("To Date");
            loadOrders();
        });
    }

    /**
     * Refreshes the order list each time this screen becomes visible,
     * so newly created or edited orders are immediately reflected.
     * Called automatically after returning from {@link CreateOrderActivity}
     * or {@link OrderDetailsActivity}.
     */
    @Override
    protected void onResume() {
        super.onResume();
        loadOrders();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Date Picker Helper
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Displays Android's native {@link DatePickerDialog} for selecting either
     * the start or end date of the filter range. After selection:
     * <ul>
     *   <li>Updates the corresponding button label in human-readable format
     *       (e.g. "10 May 2026") using {@link #displayFmt}.</li>
     *   <li>Stores the ISO date string (e.g. "2026-05-10") in {@link #startDate}
     *       or {@link #endDate} using {@link #dbFmt} for SQL comparison.</li>
     *   <li>Guards against invalid ranges: if start > end or end < start,
     *       the conflicting bound is automatically cleared with a Toast warning.</li>
     *   <li>Calls {@link #loadOrders()} to refresh the list immediately.</li>
     * </ul>
     *
     * @param isStart {@code true} to configure the "From Date" (start bound);
     *                {@code false} to configure the "To Date" (end bound).
     */
    private void showDatePicker(boolean isStart) {
        Calendar cal = Calendar.getInstance();
        DatePickerDialog dpd = new DatePickerDialog(this,
            (view, year, month, dayOfMonth) -> {
                Calendar selected = Calendar.getInstance();
                selected.set(year, month, dayOfMonth);

                String dbStr      = dbFmt.format(selected.getTime());      // "2026-05-10" for SQL
                String displayStr = displayFmt.format(selected.getTime()); // "10 May 2026" for UI

                if (isStart) {
                    startDate = dbStr;
                    btnStartDate.setText(displayStr);
                    // Safety guard: start date must not be after the already-selected end date
                    if (!endDate.isEmpty() && dbStr.compareTo(endDate) > 0) {
                        endDate = "";
                        btnEndDate.setText("To Date");
                        Toast.makeText(this, "Start date reset: was after end date.", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    endDate = dbStr;
                    btnEndDate.setText(displayStr);
                    // Safety guard: end date must not be before the already-selected start date
                    if (!startDate.isEmpty() && dbStr.compareTo(startDate) < 0) {
                        startDate = "";
                        btnStartDate.setText("From Date");
                        Toast.makeText(this, "End date reset: was before start date.", Toast.LENGTH_SHORT).show();
                    }
                }
                loadOrders(); // Refresh list with the newly selected date bound
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        dpd.show();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data Loading
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Queries the database for all orders matching the active filters and
     * binds the results to {@link #listView} via {@link OrderAdapter}.
     *
     * <h4>SQL Structure</h4>
     * <pre>
     *   SELECT o.order_id, o.order_status, o.total_price, o.created_at, o.due_date,
     *          c.name, c.phone_number
     *   FROM Orders o
     *   LEFT JOIN Customers c ON o.customer_id = c.customer_id
     *   WHERE 1=1
     *     [AND (c.name LIKE ? OR c.phone_number LIKE ?)]  -- if searchQuery is set
     *     [AND DATE(o.created_at) >= ?]                  -- if startDate is set
     *     [AND DATE(o.created_at) <= ?]                  -- if endDate is set
     *   ORDER BY o.due_date IS NULL, o.due_date ASC      -- NULL due_dates sorted last
     * </pre>
     *
     * <p>{@code WHERE 1=1} is a clean anchor that makes appending optional
     * {@code AND} clauses straightforward without conditional leading {@code WHERE}.</p>
     *
     * <p>The result map for each row stores:
     * {@code order_id, status, total, created_at, due_date, customer_name}.
     * This map is consumed by {@link OrderAdapter} to build the list rows.</p>
     *
     * <p>Tapping a list row opens {@link OrderDetailsActivity} with the selected
     * {@code order_id} passed as a Long Intent extra.</p>
     */
    private void loadOrders() {
        orderList.clear(); // Avoid duplicate entries on repeated calls

        // Base query: LEFT JOIN with Customers to expose name/phone for filtering
        StringBuilder sql = new StringBuilder(
            "SELECT o.order_id, o.order_status, o.total_price, o.created_at, o.due_date, " +
            "c.name, c.phone_number " +
            "FROM Orders o " +
            "LEFT JOIN Customers c ON o.customer_id = c.customer_id " +
            "WHERE 1=1" // Anchor — allows clean AND-clause appending below
        );

        ArrayList<String> args = new ArrayList<>();

        // Filter by customer name OR phone number simultaneously (OR within AND)
        if (!searchQuery.isEmpty()) {
            sql.append(" AND (c.name LIKE ? OR c.phone_number LIKE ?)");
            args.add("%" + searchQuery + "%");
            args.add("%" + searchQuery + "%");
        }

        // Date range filters — both compare against Orders.created_at using SQLite DATE()
        if (!startDate.isEmpty()) {
            sql.append(" AND DATE(o.created_at) >= ?");
            args.add(startDate); // ISO format "YYYY-MM-DD" from dbFmt
        }
        if (!endDate.isEmpty()) {
            sql.append(" AND DATE(o.created_at) <= ?");
            args.add(endDate); // ISO format "YYYY-MM-DD" from dbFmt
        }

        // Sort by urgency: orders with a due_date first (earliest first), then NULL due_dates
        sql.append(" ORDER BY o.due_date IS NULL, o.due_date ASC");

        Cursor cursor = db.getReadableDatabase().rawQuery(
            sql.toString(), args.toArray(new String[0]));

        if (cursor != null && cursor.moveToFirst()) {
            do {
                // Build a key→value map for each row consumed by OrderAdapter
                HashMap<String, String> map = new HashMap<>();
                map.put("order_id",      cursor.getString(0)); // Orders.order_id
                map.put("status",        cursor.getString(1)); // Orders.order_status
                map.put("total",         cursor.getString(2)); // Orders.total_price
                map.put("created_at",    cursor.getString(3)); // Orders.created_at
                map.put("due_date",      cursor.getString(4)); // Orders.due_date (may be null)
                // Customers.name — falls back to "" if customer was deleted (LEFT JOIN)
                map.put("customer_name", cursor.getString(5) != null ? cursor.getString(5) : "");
                orderList.add(map);
            } while (cursor.moveToNext());
            cursor.close();
        }

        // Bind the updated data list to the ListView via OrderAdapter
        adapter = new OrderAdapter(this, orderList, db);
        listView.setAdapter(adapter);

        // Row click → open OrderDetailsActivity for the tapped order
        listView.setOnItemClickListener((parent, view, position, id) -> {
            HashMap<String, String> item = orderList.get(position);
            Intent intent = new Intent(AllOrdersActivity.this, OrderDetailsActivity.class);
            intent.putExtra("order_id", Long.parseLong(item.get("order_id")));
            startActivity(intent);
        });
    }
}
