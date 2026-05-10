package com.university.salumate;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

/**
 * AllMeasurementTemplatesActivity — Full CRUD management screen for Measurement Templates.
 *
 * <h3>Purpose</h3>
 * <p>This screen is the central hub for viewing, searching, editing, and deleting
 * all Measurement Templates stored in the local SQLite database ({@link DBHandler}).
 * Templates define named sets of measurement fields (e.g. "Men's Shirt" with fields
 * Chest, Waist, Sleeve). Each template can be linked to one or more {@code DressTemplates}.</p>
 *
 * <h3>Navigation</h3>
 * <ul>
 *   <li>Launched from: {@link DashboardActivity} via the "📐 Measurement Templates" quick-action card.</li>
 *   <li>Edit button → launches {@link MeasurementTemplateActivity} with a {@code template_id} extra.</li>
 *   <li>FAB "New Template" → launches {@link MeasurementTemplateActivity} with no extras (create mode).</li>
 * </ul>
 *
 * <h3>Layout</h3>
 * <p>Defined in {@code res/layout/activity_all_measurement_templates.xml}.
 * Key views: {@code etSearchTemplates}, {@code btnClearTemplateSearch},
 * {@code layoutTemplatesList} (dynamic card container), {@code fabNewTemplate}.</p>
 *
 * <h3>Database Tables Used</h3>
 * <ul>
 *   <li>{@code MeasurementTemplates} — master template records (template_id, template_name)</li>
 *   <li>{@code MeasurementFields}    — individual fields within a template (field_id, field_name)</li>
 *   <li>{@code DressTemplates}       — joined to resolve linked dress names for search + display</li>
 * </ul>
 *
 * <h3>Search Behaviour</h3>
 * <p>Uses a dynamic SQL query with {@code LIKE} clauses on both
 * {@code MeasurementTemplates.template_name} and {@code DressTemplates.dress_name}.
 * {@code GROUP BY mt.measurement_template_id} prevents duplicate rows when a single
 * template is linked to multiple dresses. {@code GROUP_CONCAT} aggregates linked dress
 * names into a single comma-separated string per row.</p>
 */
public class AllMeasurementTemplatesActivity extends AppCompatActivity {

    /**
     * Dynamic container where template cards are inflated programmatically.
     * Defined in {@code res/layout/activity_all_measurement_templates.xml}.
     */
    private LinearLayout layoutTemplatesList;

    /**
     * Central database helper for all SQLite operations.
     * @see DBHandler
     */
    private DBHandler db;

    /**
     * The current live search query string. Updated by {@link TextWatcher}
     * as the user types. Empty string means "no filter applied".
     */
    private String searchQuery = "";

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Layout: res/layout/activity_all_measurement_templates.xml
        setContentView(R.layout.activity_all_measurement_templates);

        db = new DBHandler(this);
        layoutTemplatesList = findViewById(R.id.layoutTemplatesList);

        // ── FAB: "New Template" ────────────────────────────────────────────
        // Opens MeasurementTemplateActivity with no extras → create mode.
        ExtendedFloatingActionButton fab = findViewById(R.id.fabNewTemplate);
        fab.setOnClickListener(v ->
            startActivity(new Intent(this, MeasurementTemplateActivity.class))
        );

        // ── Search bar ─────────────────────────────────────────────────────
        // Triggers loadTemplates() on every keystroke via TextWatcher.
        // Searches both MeasurementTemplates.template_name and DressTemplates.dress_name.
        EditText etSearch = findViewById(R.id.etSearchTemplates);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                searchQuery = s.toString().trim();
                loadTemplates(); // Re-query database with updated filter
            }
        });

        // ── Clear Button ───────────────────────────────────────────────────
        // Resets the search text and reloads the full unfiltered list.
        ImageButton btnClear = findViewById(R.id.btnClearTemplateSearch);
        btnClear.setOnClickListener(v -> {
            etSearch.setText("");
            searchQuery = "";
            loadTemplates();
        });
    }

    /**
     * Refresh the template list every time the user returns to this screen
     * (e.g. after creating or editing a template in {@link MeasurementTemplateActivity}).
     */
    @Override
    protected void onResume() {
        super.onResume();
        loadTemplates();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data Loading
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Queries the database for all {@code MeasurementTemplates}, optionally filtered
     * by the current {@link #searchQuery}, then builds a card for each result.
     *
     * <h4>SQL Strategy</h4>
     * <pre>
     *   SELECT mt.measurement_template_id, mt.template_name,
     *          COUNT(DISTINCT mf.field_id)       AS field_count,
     *          GROUP_CONCAT(DISTINCT dt.dress_name) AS linked_dresses
     *   FROM MeasurementTemplates mt
     *   LEFT JOIN MeasurementFields mf ON mf.measurement_template_id = mt.measurement_template_id
     *   LEFT JOIN DressTemplates    dt ON dt.measurement_template_id = mt.measurement_template_id
     *   WHERE (mt.template_name LIKE ? OR dt.dress_name LIKE ?)   -- only if searchQuery set
     *   GROUP BY mt.measurement_template_id
     *   ORDER BY mt.template_name ASC
     * </pre>
     *
     * <p>If no templates are found (or the database is empty), an empty-state
     * {@link TextView} is displayed guiding the user to create their first template.</p>
     */
    private void loadTemplates() {
        // Clear previously rendered cards before re-populating
        layoutTemplatesList.removeAllViews();

        // Build dynamic query — LEFT JOINs allow templates with no fields/dresses to still appear.
        // GROUP BY prevents duplicate rows when multiple dresses reference the same template.
        // GROUP_CONCAT aggregates all linked dress names into one comma-separated string.
        StringBuilder sql = new StringBuilder(
            "SELECT mt.measurement_template_id, mt.template_name, " +
            "COUNT(DISTINCT mf.field_id) AS field_count, " +
            "GROUP_CONCAT(DISTINCT dt.dress_name) AS linked_dresses " +
            "FROM MeasurementTemplates mt " +
            "LEFT JOIN MeasurementFields mf ON mf.measurement_template_id = mt.measurement_template_id " +
            "LEFT JOIN DressTemplates dt ON dt.measurement_template_id = mt.measurement_template_id " +
            "WHERE 1=1" // Always-true anchor enables clean dynamic AND appending below
        );

        java.util.ArrayList<String> args = new java.util.ArrayList<>();
        if (!searchQuery.isEmpty()) {
            // Search across both template name and linked dress names simultaneously
            sql.append(" AND (mt.template_name LIKE ? OR dt.dress_name LIKE ?)");
            args.add("%" + searchQuery + "%");
            args.add("%" + searchQuery + "%");
        }
        sql.append(" GROUP BY mt.measurement_template_id ORDER BY mt.template_name ASC");

        Cursor c = db.getReadableDatabase().rawQuery(sql.toString(), args.toArray(new String[0]));

        if (c != null && c.moveToFirst()) {
            do {
                // Column order matches the SELECT clause above
                long   templateId    = c.getLong(0);   // MeasurementTemplates.measurement_template_id
                String templateName  = c.getString(1); // MeasurementTemplates.template_name
                int    fieldCount    = c.getInt(2);    // COUNT(DISTINCT mf.field_id)
                String linkedDresses = c.isNull(3) ? null : c.getString(3); // GROUP_CONCAT result
                addTemplateCard(templateId, templateName, fieldCount, linkedDresses);
            } while (c.moveToNext());
            c.close();
        } else {
            // Empty state: shown when no templates exist or no results match the search
            TextView empty = new TextView(this);
            empty.setText("No templates found.\nTap + to create your first template.");
            empty.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            empty.setTextSize(15f);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, 80, 0, 0);
            layoutTemplatesList.addView(empty);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Card Builder
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Inflates and adds a single {@link MaterialCardView} representing one template
     * to the {@link #layoutTemplatesList} container.
     *
     * <h4>Card Structure</h4>
     * <pre>
     *   [  Template Name (bold, white)          ]  [ Edit ]  [ ✕ ]
     *   [  N measurement fields (cyan)          ]
     *   [  Used by: DressA, DressB (muted grey) ]
     * </pre>
     *
     * <p>Colours are sourced from {@code res/values/colors.xml}:
     * {@code background_card}, {@code divider}, {@code text_primary},
     * {@code primary} (cyan), {@code text_secondary}, {@code delete} (pink).</p>
     *
     * @param templateId    The primary key from {@code MeasurementTemplates}.
     * @param templateName  The human-readable name of this template.
     * @param fieldCount    Total number of {@code MeasurementFields} linked to this template.
     * @param linkedDresses Comma-separated dress names from {@code DressTemplates},
     *                      or {@code null} if this template is not yet used by any dress.
     */
    private void addTemplateCard(long templateId, String templateName, int fieldCount, String linkedDresses) {
        // Pre-compute density-independent pixel offsets used throughout the card
        int dp4  = dp(4);
        int dp8  = dp(8);
        int dp12 = dp(12);
        int dp16 = dp(16);

        // ── Outer card ─────────────────────────────────────────────────────
        // Styled to match the global dark-theme card style (background_card + divider border)
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, dp12); // Space between cards
        card.setLayoutParams(cardParams);
        card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.background_card));
        card.setRadius(dp(16));
        card.setCardElevation(0); // Flat Material Design 3 style
        card.setStrokeColor(ContextCompat.getColor(this, R.color.divider));
        card.setStrokeWidth(dp(1));

        // ── Card content: horizontal row ───────────────────────────────────
        LinearLayout cardContent = new LinearLayout(this);
        cardContent.setOrientation(LinearLayout.HORIZONTAL);
        cardContent.setPadding(dp16, dp16, dp8, dp16);
        cardContent.setGravity(Gravity.CENTER_VERTICAL);

        // ── Left column: template info ────────────────────────────────────
        LinearLayout infoCol = new LinearLayout(this);
        infoCol.setOrientation(LinearLayout.VERTICAL);
        // weight=1 makes this column expand to fill all available horizontal space
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        infoCol.setLayoutParams(infoParams);

        // Template name — bold white text (@color/text_primary)
        TextView txtName = new TextView(this);
        txtName.setText(templateName);
        txtName.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        txtName.setTextSize(16f);
        txtName.setTypeface(null, android.graphics.Typeface.BOLD);

        // Field count — cyan accent (@color/primary) to draw attention
        TextView txtFields = new TextView(this);
        txtFields.setText(fieldCount + " measurement field" + (fieldCount != 1 ? "s" : ""));
        txtFields.setTextColor(ContextCompat.getColor(this, R.color.primary));
        txtFields.setTextSize(13f);
        txtFields.setPadding(0, dp4, 0, 0);

        infoCol.addView(txtName);
        infoCol.addView(txtFields);

        // Linked dresses row — only shown if this template is referenced by ≥1 DressTemplate
        // linkedDresses is the GROUP_CONCAT result from the SQL query in loadTemplates()
        if (linkedDresses != null && !linkedDresses.isEmpty()) {
            TextView txtDresses = new TextView(this);
            txtDresses.setText("Used by: " + linkedDresses);
            txtDresses.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            txtDresses.setTextSize(12f);
            txtDresses.setPadding(0, dp4, 0, 0);
            infoCol.addView(txtDresses);
        }

        // ── Right column: Edit + Delete buttons ────────────────────────────
        LinearLayout btnCol = new LinearLayout(this);
        btnCol.setOrientation(LinearLayout.HORIZONTAL);
        btnCol.setGravity(Gravity.CENTER_VERTICAL);

        // Edit button — outlined style, cyan colour (@color/primary)
        // Opens MeasurementTemplateActivity with template_id → edit mode
        MaterialButton btnEdit = new MaterialButton(this,
            null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnEdit.setText("Edit");
        btnEdit.setTextSize(12f);
        btnEdit.setTextColor(ContextCompat.getColor(this, R.color.primary));
        btnEdit.setStrokeColor(android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.primary)));
        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        editParams.setMargins(0, 0, dp8, 0); // Gap before the Delete button
        btnEdit.setLayoutParams(editParams);
        btnEdit.setOnClickListener(v -> openEditTemplate(templateId));

        // Delete button — outlined style, pink/red colour (@color/delete)
        // Shows a Material confirmation dialog before permanently deleting
        MaterialButton btnDelete = new MaterialButton(this,
            null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnDelete.setText("✕");
        btnDelete.setTextSize(12f);
        btnDelete.setTextColor(ContextCompat.getColor(this, R.color.delete));
        btnDelete.setStrokeColor(android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.delete)));
        btnDelete.setOnClickListener(v -> confirmDelete(templateId, templateName, linkedDresses));

        btnCol.addView(btnEdit);
        btnCol.addView(btnDelete);

        // Assemble the card
        cardContent.addView(infoCol);
        cardContent.addView(btnCol);
        card.addView(cardContent);
        layoutTemplatesList.addView(card);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Actions
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Navigates to {@link MeasurementTemplateActivity} in <b>edit mode</b>
     * by passing the given {@code template_id} as an Intent extra.
     *
     * <p>In {@link MeasurementTemplateActivity#onCreate}, the presence of
     * {@code "template_id"} triggers pre-filling of the form with the existing
     * template name and its associated {@code MeasurementFields} rows.</p>
     *
     * @param templateId The primary key of the template to edit.
     */
    private void openEditTemplate(long templateId) {
        Intent intent = new Intent(this, MeasurementTemplateActivity.class);
        // Passing "template_id" signals MeasurementTemplateActivity to enter edit mode
        intent.putExtra("template_id", templateId);
        startActivity(intent);
    }

    /**
     * Shows a {@link MaterialAlertDialogBuilder} confirmation dialog before permanently
     * deleting a template and all its associated {@code MeasurementFields} from the database.
     *
     * <h4>Safety Warning</h4>
     * <p>If the template is currently linked to any {@code DressTemplates} (indicated by
     * a non-null {@code linkedDresses} string), an additional ⚠️ warning is shown
     * informing the user that those dress templates will be unlinked.
     * Note: The deletion does NOT cascade to {@code DressTemplates} itself —
     * it only removes the template record and its {@code MeasurementFields} rows.
     * The {@code DressTemplates.measurement_template_id} column will simply become stale.</p>
     *
     * <h4>Delete Sequence</h4>
     * <ol>
     *   <li>DELETE FROM MeasurementFields WHERE measurement_template_id = ?</li>
     *   <li>DELETE FROM MeasurementTemplates WHERE measurement_template_id = ?</li>
     * </ol>
     *
     * @param templateId    Primary key of the template to delete.
     * @param templateName  Human-readable name shown in the dialog title.
     * @param linkedDresses Comma-separated dress names (from GROUP_CONCAT), or null if unused.
     */
    private void confirmDelete(long templateId, String templateName, String linkedDresses) {
        String message = "Delete \"" + templateName + "\"?";

        // Append extra warning if this template is actively used by dress templates
        if (linkedDresses != null && !linkedDresses.isEmpty()) {
            message += "\n\n⚠️ This template is used by: " + linkedDresses +
                       "\nDeleting it will unlink those dress templates.";
        }

        new MaterialAlertDialogBuilder(this)
            .setTitle("Delete Template")
            .setMessage(message)
            .setPositiveButton("Delete", (d, w) -> {
                android.database.sqlite.SQLiteDatabase wDb = db.getWritableDatabase();
                // Delete child rows first (MeasurementFields) to respect FK constraints
                wDb.execSQL("DELETE FROM MeasurementFields WHERE measurement_template_id = ?",
                    new String[]{String.valueOf(templateId)});
                // Then delete the parent template record
                wDb.execSQL("DELETE FROM MeasurementTemplates WHERE measurement_template_id = ?",
                    new String[]{String.valueOf(templateId)});
                Toast.makeText(this, "Template deleted.", Toast.LENGTH_SHORT).show();
                loadTemplates(); // Refresh the list to reflect the deletion
            })
            .setNegativeButton("Cancel", null) // Do nothing on cancel
            .show();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utility
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Converts a density-independent pixel value (dp) to raw screen pixels (px)
     * using the current display's density scale factor.
     *
     * @param value The dp value to convert.
     * @return The equivalent pixel value for the current screen density.
     */
    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
