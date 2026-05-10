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
 * AllMeasurementTemplatesActivity — Full CRUD list screen for Measurement Templates.
 *
 * <p>Displays all templates as MaterialCardViews with template name, field count,
 * and linked dress names. Supports real-time search by template name OR linked dress name.
 * Each card has Edit and Delete actions.</p>
 */
public class AllMeasurementTemplatesActivity extends AppCompatActivity {

    private LinearLayout layoutTemplatesList;
    private DBHandler db;
    private String searchQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_measurement_templates);

        db = new DBHandler(this);
        layoutTemplatesList = findViewById(R.id.layoutTemplatesList);

        // FAB → open create screen
        ExtendedFloatingActionButton fab = findViewById(R.id.fabNewTemplate);
        fab.setOnClickListener(v ->
            startActivity(new Intent(this, MeasurementTemplateActivity.class))
        );

        // Real-time search
        EditText etSearch = findViewById(R.id.etSearchTemplates);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                searchQuery = s.toString().trim();
                loadTemplates();
            }
        });

        // Clear search
        ImageButton btnClear = findViewById(R.id.btnClearTemplateSearch);
        btnClear.setOnClickListener(v -> {
            etSearch.setText("");
            searchQuery = "";
            loadTemplates();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTemplates();
    }

    // ── Data Loading ──────────────────────────────────────────────────────────

    private void loadTemplates() {
        layoutTemplatesList.removeAllViews();

        // Join with DressTemplates to allow search by dress name too.
        // GROUP BY prevents duplicates when multiple dresses share a template.
        StringBuilder sql = new StringBuilder(
            "SELECT mt.measurement_template_id, mt.template_name, " +
            "COUNT(DISTINCT mf.field_id) AS field_count, " +
            "GROUP_CONCAT(DISTINCT dt.dress_name) AS linked_dresses " +
            "FROM MeasurementTemplates mt " +
            "LEFT JOIN MeasurementFields mf ON mf.measurement_template_id = mt.measurement_template_id " +
            "LEFT JOIN DressTemplates dt ON dt.measurement_template_id = mt.measurement_template_id " +
            "WHERE 1=1"
        );

        java.util.ArrayList<String> args = new java.util.ArrayList<>();
        if (!searchQuery.isEmpty()) {
            sql.append(" AND (mt.template_name LIKE ? OR dt.dress_name LIKE ?)");
            args.add("%" + searchQuery + "%");
            args.add("%" + searchQuery + "%");
        }
        sql.append(" GROUP BY mt.measurement_template_id ORDER BY mt.template_name ASC");

        Cursor c = db.getReadableDatabase().rawQuery(sql.toString(), args.toArray(new String[0]));

        if (c != null && c.moveToFirst()) {
            do {
                long   templateId     = c.getLong(0);
                String templateName   = c.getString(1);
                int    fieldCount     = c.getInt(2);
                String linkedDresses  = c.isNull(3) ? null : c.getString(3);
                addTemplateCard(templateId, templateName, fieldCount, linkedDresses);
            } while (c.moveToNext());
            c.close();
        } else {
            // Empty state
            TextView empty = new TextView(this);
            empty.setText("No templates found.\nTap + to create your first template.");
            empty.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            empty.setTextSize(15f);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, 80, 0, 0);
            layoutTemplatesList.addView(empty);
        }
    }

    // ── Card Builder ─────────────────────────────────────────────────────────

    private void addTemplateCard(long templateId, String templateName, int fieldCount, String linkedDresses) {
        int dp4  = dp(4);
        int dp8  = dp(8);
        int dp12 = dp(12);
        int dp16 = dp(16);

        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, dp12);
        card.setLayoutParams(cardParams);
        card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.background_card));
        card.setRadius(dp(16));
        card.setCardElevation(0);
        card.setStrokeColor(ContextCompat.getColor(this, R.color.divider));
        card.setStrokeWidth(dp(1));

        LinearLayout cardContent = new LinearLayout(this);
        cardContent.setOrientation(LinearLayout.HORIZONTAL);
        cardContent.setPadding(dp16, dp16, dp8, dp16);
        cardContent.setGravity(Gravity.CENTER_VERTICAL);

        // ── Left: info column ──────────────────────────────────────────────
        LinearLayout infoCol = new LinearLayout(this);
        infoCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        infoCol.setLayoutParams(infoParams);

        TextView txtName = new TextView(this);
        txtName.setText(templateName);
        txtName.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        txtName.setTextSize(16f);
        txtName.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView txtFields = new TextView(this);
        txtFields.setText(fieldCount + " measurement field" + (fieldCount != 1 ? "s" : ""));
        txtFields.setTextColor(ContextCompat.getColor(this, R.color.primary));
        txtFields.setTextSize(13f);
        txtFields.setPadding(0, dp4, 0, 0);

        infoCol.addView(txtName);
        infoCol.addView(txtFields);

        if (linkedDresses != null && !linkedDresses.isEmpty()) {
            TextView txtDresses = new TextView(this);
            txtDresses.setText("Used by: " + linkedDresses);
            txtDresses.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            txtDresses.setTextSize(12f);
            txtDresses.setPadding(0, dp4, 0, 0);
            infoCol.addView(txtDresses);
        }

        // ── Right: action buttons ──────────────────────────────────────────
        LinearLayout btnCol = new LinearLayout(this);
        btnCol.setOrientation(LinearLayout.HORIZONTAL);
        btnCol.setGravity(Gravity.CENTER_VERTICAL);

        MaterialButton btnEdit = new MaterialButton(this,
            null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnEdit.setText("Edit");
        btnEdit.setTextSize(12f);
        btnEdit.setTextColor(ContextCompat.getColor(this, R.color.primary));
        btnEdit.setStrokeColor(android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.primary)));
        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        editParams.setMargins(0, 0, dp8, 0);
        btnEdit.setLayoutParams(editParams);
        btnEdit.setOnClickListener(v -> openEditTemplate(templateId));

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

        cardContent.addView(infoCol);
        cardContent.addView(btnCol);
        card.addView(cardContent);
        layoutTemplatesList.addView(card);
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void openEditTemplate(long templateId) {
        Intent intent = new Intent(this, MeasurementTemplateActivity.class);
        intent.putExtra("template_id", templateId);
        startActivity(intent);
    }

    private void confirmDelete(long templateId, String templateName, String linkedDresses) {
        String message = "Delete \"" + templateName + "\"?";
        if (linkedDresses != null && !linkedDresses.isEmpty()) {
            message += "\n\n⚠️ This template is used by: " + linkedDresses +
                       "\nDeleting it will unlink those dress templates.";
        }

        new MaterialAlertDialogBuilder(this)
            .setTitle("Delete Template")
            .setMessage(message)
            .setPositiveButton("Delete", (d, w) -> {
                android.database.sqlite.SQLiteDatabase wDb = db.getWritableDatabase();
                wDb.execSQL("DELETE FROM MeasurementFields WHERE measurement_template_id = ?",
                    new String[]{String.valueOf(templateId)});
                wDb.execSQL("DELETE FROM MeasurementTemplates WHERE measurement_template_id = ?",
                    new String[]{String.valueOf(templateId)});
                Toast.makeText(this, "Template deleted.", Toast.LENGTH_SHORT).show();
                loadTemplates();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
