package com.university.salumate;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

/**
 * MeasurementTemplateActivity — Create or Edit a Measurement Template.
 *
 * <p>When launched with no extras: creates a new template (unchanged behaviour).
 * When launched with a {@code template_id} Intent extra: loads the existing
 * template and its fields for editing.</p>
 */
public class MeasurementTemplateActivity extends AppCompatActivity {

    private TextInputEditText templateNameField;
    private LinearLayout fieldsContainer;
    private DBHandler dbHandler;

    /** Parallel lists tracking field IDs and their input widgets. */
    private final List<Long>     fieldIds     = new ArrayList<>();
    private final List<EditText> fieldInputs  = new ArrayList<>();

    /** The template being edited, or -1 for create mode. */
    private long editingTemplateId = -1L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_measurement_template);

        dbHandler       = new DBHandler(this);
        templateNameField = findViewById(R.id.editTemplateName);
        fieldsContainer = findViewById(R.id.fieldsContainer);

        MaterialButton btnAddField    = findViewById(R.id.btnAddField);
        MaterialButton btnSaveTemplate = findViewById(R.id.btnSaveTemplate);
        MaterialToolbar toolbar        = findViewById(R.id.toolbarTemplate);

        btnAddField.setOnClickListener(v -> addFieldRow(-1L, ""));
        btnSaveTemplate.setOnClickListener(v -> saveTemplate());

        // Check if we are in edit mode
        if (getIntent().hasExtra("template_id")) {
            editingTemplateId = getIntent().getLongExtra("template_id", -1L);
            toolbar.setTitle("Edit Template");
            loadTemplateForEditing(editingTemplateId);
        } else {
            toolbar.setTitle("New Template");
            addFieldRow(-1L, ""); // Start with one blank row
        }
    }

    // ── Edit Mode: pre-fill data ─────────────────────────────────────────────

    private void loadTemplateForEditing(long templateId) {
        SQLiteDatabase db = dbHandler.getReadableDatabase();

        // Load template name
        Cursor tc = db.rawQuery(
            "SELECT template_name FROM MeasurementTemplates WHERE measurement_template_id = ?",
            new String[]{String.valueOf(templateId)});
        if (tc != null && tc.moveToFirst()) {
            templateNameField.setText(tc.getString(0));
            tc.close();
        }

        // Load existing fields
        Cursor fc = db.rawQuery(
            "SELECT field_id, field_name FROM MeasurementFields " +
            "WHERE measurement_template_id = ? ORDER BY field_id ASC",
            new String[]{String.valueOf(templateId)});
        if (fc != null && fc.moveToFirst()) {
            do {
                long   fieldId   = fc.getLong(0);
                String fieldName = fc.getString(1);
                addFieldRow(fieldId, fieldName);
            } while (fc.moveToNext());
            fc.close();
        } else {
            addFieldRow(-1L, ""); // Ensure at least one row
        }
    }

    // ── Field Row Builder ────────────────────────────────────────────────────

    /**
     * Adds a field row to the UI.
     *
     * @param existingFieldId The DB field_id if editing an existing field, or -1 for new.
     * @param existingValue   Pre-filled text for existing fields.
     */
    private void addFieldRow(long existingFieldId, String existingValue) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        row.setPadding(0, 8, 0, 8);

        EditText input = new EditText(this);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        input.setLayoutParams(inputParams);
        input.setHint("Field name (e.g. Chest, Waist)");
        input.setTextColor(getResources().getColor(R.color.text_primary, null));
        input.setHintTextColor(getResources().getColor(R.color.text_secondary, null));
        if (!existingValue.isEmpty()) input.setText(existingValue);

        // Remove-row button
        TextView btnRemove = new TextView(this);
        btnRemove.setText("  ✕  ");
        btnRemove.setTextColor(getResources().getColor(R.color.delete, null));
        btnRemove.setTextSize(16f);
        btnRemove.setOnClickListener(v -> {
            fieldsContainer.removeView(row);
            int idx = fieldInputs.indexOf(input);
            if (idx >= 0) {
                fieldInputs.remove(idx);
                fieldIds.remove(idx);
            }
        });

        row.addView(input);
        row.addView(btnRemove);
        fieldsContainer.addView(row);

        fieldIds.add(existingFieldId);
        fieldInputs.add(input);
    }

    // ── Save / Update Logic ──────────────────────────────────────────────────

    private void saveTemplate() {
        String templateName = templateNameField.getText() != null
            ? templateNameField.getText().toString().trim() : "";
        if (templateName.isEmpty()) {
            Toast.makeText(this, "Please enter a template name.", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> validNames = new ArrayList<>();
        List<Long>   validIds   = new ArrayList<>();
        for (int i = 0; i < fieldInputs.size(); i++) {
            String val = fieldInputs.get(i).getText().toString().trim();
            if (!val.isEmpty()) {
                validNames.add(val);
                validIds.add(fieldIds.get(i));
            }
        }

        if (validNames.isEmpty()) {
            Toast.makeText(this, "Add at least one measurement field.", Toast.LENGTH_SHORT).show();
            return;
        }

        SQLiteDatabase db = dbHandler.getWritableDatabase();
        db.beginTransaction();
        try {
            if (editingTemplateId == -1L) {
                // ── Create mode ──────────────────────────────────────────────
                ContentValues tv = new ContentValues();
                tv.put("template_name", templateName);
                tv.put("created_by", 1);
                long newId = db.insert("MeasurementTemplates", null, tv);

                for (String name : validNames) {
                    ContentValues fv = new ContentValues();
                    fv.put("measurement_template_id", newId);
                    fv.put("field_name", name);
                    fv.put("unit", "inches");
                    db.insert("MeasurementFields", null, fv);
                }
                Toast.makeText(this, "Template created!", Toast.LENGTH_SHORT).show();

            } else {
                // ── Edit mode ────────────────────────────────────────────────
                ContentValues tv = new ContentValues();
                tv.put("template_name", templateName);
                tv.put("updated_at", new java.text.SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                    .format(new java.util.Date()));
                db.update("MeasurementTemplates", tv,
                    "measurement_template_id = ?",
                    new String[]{String.valueOf(editingTemplateId)});

                // Delete all old fields and re-insert updated list
                db.execSQL("DELETE FROM MeasurementFields WHERE measurement_template_id = ?",
                    new String[]{String.valueOf(editingTemplateId)});
                for (String name : validNames) {
                    ContentValues fv = new ContentValues();
                    fv.put("measurement_template_id", editingTemplateId);
                    fv.put("field_name", name);
                    fv.put("unit", "inches");
                    db.insert("MeasurementFields", null, fv);
                }
                Toast.makeText(this, "Template updated!", Toast.LENGTH_SHORT).show();
            }
            db.setTransactionSuccessful();
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            db.endTransaction();
        }
    }
}
