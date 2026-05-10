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
 * <h3>Dual-Mode Operation</h3>
 * <ul>
 *   <li><b>Create mode (default):</b> Launched with no Intent extras from
 *       {@link AllMeasurementTemplatesActivity} FAB. Presents an empty form.
 *       On save, a new row is inserted into {@code MeasurementTemplates} along
 *       with one row per field in {@code MeasurementFields}.</li>
 *   <li><b>Edit mode:</b> Launched with a {@code "template_id"} Long extra from
 *       {@link AllMeasurementTemplatesActivity#openEditTemplate}. Pre-fills the
 *       form with the existing template name and its measurement fields.
 *       On save, the template name is updated and all old fields are deleted
 *       and re-inserted (a clean replace strategy).</li>
 * </ul>
 *
 * <h3>Layout</h3>
 * <p>Defined in {@code res/layout/activity_measurement_template.xml}.
 * Key views:
 * <ul>
 *   <li>{@code toolbarTemplate}  — Dynamic title: "New Template" or "Edit Template"</li>
 *   <li>{@code editTemplateName} — {@link TextInputEditText} for the template name</li>
 *   <li>{@code fieldsContainer}  — {@link LinearLayout} where field rows are added dynamically</li>
 *   <li>{@code btnAddField}      — Adds a new blank field row</li>
 *   <li>{@code btnSaveTemplate}  — Triggers {@link #saveTemplate()}</li>
 * </ul>
 * </p>
 *
 * <h3>Database Tables Used</h3>
 * <ul>
 *   <li>{@code MeasurementTemplates} — stores template_name, created_by, timestamps</li>
 *   <li>{@code MeasurementFields}    — stores field_id, measurement_template_id, field_name, unit</li>
 * </ul>
 *
 * <h3>Save Strategy (Edit mode)</h3>
 * <p>Rather than attempting diff-based updates on individual fields, the edit
 * save performs a clean DELETE + INSERT of all fields within a single transaction,
 * ensuring simplicity and consistency even if field order changes.</p>
 */
public class MeasurementTemplateActivity extends AppCompatActivity {

    /**
     * The {@link TextInputEditText} bound to {@code editTemplateName} in the layout.
     * Captures the human-readable name of the template (e.g. "Men's Shirt").
     */
    private TextInputEditText templateNameField;

    /**
     * The container {@link LinearLayout} ({@code fieldsContainer} in layout)
     * into which dynamic field rows are appended by {@link #addFieldRow}.
     */
    private LinearLayout fieldsContainer;

    /**
     * Central database helper providing access to the SQLite database.
     * @see DBHandler
     */
    private DBHandler dbHandler;

    /**
     * Parallel list tracking the original database {@code field_id} for each field row.
     * Index {@code i} corresponds to {@code fieldInputs.get(i)}.
     * Value is {@code -1L} for newly added rows that don't yet exist in the DB.
     */
    private final List<Long>     fieldIds    = new ArrayList<>();

    /**
     * Parallel list of {@link EditText} widgets, one per field row in the UI.
     * The text value of each widget is the field name to save.
     */
    private final List<EditText> fieldInputs = new ArrayList<>();

    /**
     * The primary key of the template being edited.
     * {@code -1L} signals create mode (no existing template to load).
     * Set from the {@code "template_id"} Intent extra in {@link #onCreate}.
     */
    private long editingTemplateId = -1L;

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Layout: res/layout/activity_measurement_template.xml
        setContentView(R.layout.activity_measurement_template);

        dbHandler         = new DBHandler(this);
        templateNameField = findViewById(R.id.editTemplateName);
        fieldsContainer   = findViewById(R.id.fieldsContainer);

        MaterialButton  btnAddField     = findViewById(R.id.btnAddField);
        MaterialButton  btnSaveTemplate = findViewById(R.id.btnSaveTemplate);
        MaterialToolbar toolbar         = findViewById(R.id.toolbarTemplate);

        // "+ Add Field" adds a new blank row to fieldsContainer
        btnAddField.setOnClickListener(v -> addFieldRow(-1L, ""));

        // "Save Template" commits data to the database (create or update path)
        btnSaveTemplate.setOnClickListener(v -> saveTemplate());

        // ── Mode detection ─────────────────────────────────────────────────
        // If a "template_id" extra is present, switch to edit mode.
        // Otherwise, start in create mode with one blank field row.
        if (getIntent().hasExtra("template_id")) {
            editingTemplateId = getIntent().getLongExtra("template_id", -1L);
            toolbar.setTitle("Edit Template");
            loadTemplateForEditing(editingTemplateId); // Pre-fill form from DB
        } else {
            toolbar.setTitle("New Template");
            addFieldRow(-1L, ""); // Provide one blank row to start with
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Edit Mode: Pre-fill form from database
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Queries the database for the given template and pre-fills the form UI.
     *
     * <h4>Queries executed:</h4>
     * <ol>
     *   <li>SELECT template_name FROM MeasurementTemplates WHERE measurement_template_id = ?</li>
     *   <li>SELECT field_id, field_name FROM MeasurementFields
     *       WHERE measurement_template_id = ? ORDER BY field_id ASC</li>
     * </ol>
     *
     * @param templateId The primary key of the template to load for editing.
     */
    private void loadTemplateForEditing(long templateId) {
        SQLiteDatabase db = dbHandler.getReadableDatabase();

        // Step 1: Load and display the template name
        Cursor tc = db.rawQuery(
            "SELECT template_name FROM MeasurementTemplates WHERE measurement_template_id = ?",
            new String[]{String.valueOf(templateId)});
        if (tc != null && tc.moveToFirst()) {
            templateNameField.setText(tc.getString(0));
            tc.close();
        }

        // Step 2: Load existing MeasurementFields and render a pre-filled row for each
        // fieldId is stored alongside each EditText so it can be referenced during save
        Cursor fc = db.rawQuery(
            "SELECT field_id, field_name FROM MeasurementFields " +
            "WHERE measurement_template_id = ? ORDER BY field_id ASC",
            new String[]{String.valueOf(templateId)});
        if (fc != null && fc.moveToFirst()) {
            do {
                long   fieldId   = fc.getLong(0);   // MeasurementFields.field_id
                String fieldName = fc.getString(1); // MeasurementFields.field_name
                addFieldRow(fieldId, fieldName);
            } while (fc.moveToNext());
            fc.close();
        } else {
            // Template exists but has no fields yet — provide a blank row
            addFieldRow(-1L, "");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Field Row Builder
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Appends a single measurement field row to {@link #fieldsContainer}.
     *
     * <p>Each row consists of:</p>
     * <ul>
     *   <li>An {@link EditText} for the field name (e.g. "Chest", "Waist")</li>
     *   <li>A red "✕" {@link TextView} that removes this row from the UI and its
     *       corresponding entries from {@link #fieldIds} and {@link #fieldInputs}</li>
     * </ul>
     *
     * <p>The {@code existingFieldId} and the new {@link EditText} are appended to
     * {@link #fieldIds} and {@link #fieldInputs} respectively, keeping the two
     * lists in sync for retrieval during {@link #saveTemplate()}.</p>
     *
     * @param existingFieldId The DB {@code field_id} of the existing field (edit mode),
     *                        or {@code -1L} for a brand-new field row.
     * @param existingValue   The pre-filled field name text, or empty string for new rows.
     */
    private void addFieldRow(long existingFieldId, String existingValue) {
        // Outer horizontal row: [EditText] [✕]
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        row.setPadding(0, 8, 0, 8);

        // Field name input — theme-coloured text and hint
        EditText input = new EditText(this);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f); // weight=1 to fill row
        input.setLayoutParams(inputParams);
        input.setHint("Field name (e.g. Chest, Waist)");
        input.setTextColor(getResources().getColor(R.color.text_primary, null));
        input.setHintTextColor(getResources().getColor(R.color.text_secondary, null));
        if (!existingValue.isEmpty()) input.setText(existingValue); // Pre-fill for edit mode

        // Remove button — tapping removes this row from the UI and the parallel tracking lists
        TextView btnRemove = new TextView(this);
        btnRemove.setText("  ✕  ");
        btnRemove.setTextColor(getResources().getColor(R.color.delete, null)); // @color/delete (pink)
        btnRemove.setTextSize(16f);
        btnRemove.setOnClickListener(v -> {
            fieldsContainer.removeView(row); // Remove from UI
            int idx = fieldInputs.indexOf(input);
            if (idx >= 0) {
                fieldInputs.remove(idx); // Keep parallel list in sync
                fieldIds.remove(idx);
            }
        });

        row.addView(input);
        row.addView(btnRemove);
        fieldsContainer.addView(row);

        // Register this field in the parallel tracking lists
        fieldIds.add(existingFieldId);    // -1L = new; positive = existing DB field_id
        fieldInputs.add(input);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Save / Update Logic
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Validates the form and commits the template data to the database.
     *
     * <h4>Validation</h4>
     * <ul>
     *   <li>Template name must not be empty.</li>
     *   <li>At least one non-empty field name must be present.</li>
     * </ul>
     *
     * <h4>Create path ({@link #editingTemplateId} == -1L)</h4>
     * <ol>
     *   <li>INSERT INTO MeasurementTemplates (template_name, created_by) VALUES (?, 1)</li>
     *   <li>INSERT INTO MeasurementFields (measurement_template_id, field_name, unit)
     *       for each valid field name.</li>
     * </ol>
     *
     * <h4>Edit path ({@link #editingTemplateId} > 0)</h4>
     * <ol>
     *   <li>UPDATE MeasurementTemplates SET template_name = ?, updated_at = ? WHERE ...</li>
     *   <li>DELETE FROM MeasurementFields WHERE measurement_template_id = ?</li>
     *   <li>Re-INSERT all current field values (clean-replace strategy).</li>
     * </ol>
     *
     * <p>All operations run within a single {@link SQLiteDatabase#beginTransaction()} /
     * {@link SQLiteDatabase#setTransactionSuccessful()} block to ensure atomicity.</p>
     */
    private void saveTemplate() {
        // ── Validate template name ─────────────────────────────────────────
        String templateName = templateNameField.getText() != null
            ? templateNameField.getText().toString().trim() : "";
        if (templateName.isEmpty()) {
            Toast.makeText(this, "Please enter a template name.", Toast.LENGTH_SHORT).show();
            return;
        }

        // ── Collect valid (non-empty) field names ──────────────────────────
        // Parallel lists maintain the same order as the UI rows
        List<String> validNames = new ArrayList<>();
        List<Long>   validIds   = new ArrayList<>();
        for (int i = 0; i < fieldInputs.size(); i++) {
            String val = fieldInputs.get(i).getText().toString().trim();
            if (!val.isEmpty()) {
                validNames.add(val);
                validIds.add(fieldIds.get(i)); // Preserve original field_id (or -1 for new)
            }
        }

        if (validNames.isEmpty()) {
            Toast.makeText(this, "Add at least one measurement field.", Toast.LENGTH_SHORT).show();
            return;
        }

        SQLiteDatabase db = dbHandler.getWritableDatabase();
        db.beginTransaction(); // Ensures atomicity — all changes succeed or none do
        try {
            if (editingTemplateId == -1L) {
                // ── CREATE MODE ────────────────────────────────────────────
                // Insert the template header row
                ContentValues tv = new ContentValues();
                tv.put("template_name", templateName);
                tv.put("created_by", 1); // Hardcoded to user ID 1 (single-user app)
                long newId = db.insert("MeasurementTemplates", null, tv);

                // Insert one MeasurementFields row per valid field name
                for (String name : validNames) {
                    ContentValues fv = new ContentValues();
                    fv.put("measurement_template_id", newId);
                    fv.put("field_name", name);
                    fv.put("unit", "inches"); // Default unit for all fields
                    db.insert("MeasurementFields", null, fv);
                }
                Toast.makeText(this, "Template created!", Toast.LENGTH_SHORT).show();

            } else {
                // ── EDIT MODE ──────────────────────────────────────────────
                // Update the template name and timestamp
                ContentValues tv = new ContentValues();
                tv.put("template_name", templateName);
                tv.put("updated_at", new java.text.SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                    .format(new java.util.Date()));
                db.update("MeasurementTemplates", tv,
                    "measurement_template_id = ?",
                    new String[]{String.valueOf(editingTemplateId)});

                // Clean-replace all fields: delete existing, then re-insert current list.
                // This avoids complex diff logic and handles reordering cleanly.
                db.execSQL("DELETE FROM MeasurementFields WHERE measurement_template_id = ?",
                    new String[]{String.valueOf(editingTemplateId)});
                for (String name : validNames) {
                    ContentValues fv = new ContentValues();
                    fv.put("measurement_template_id", editingTemplateId);
                    fv.put("field_name", name);
                    fv.put("unit", "inches"); // Unit remains "inches" by default
                    db.insert("MeasurementFields", null, fv);
                }
                Toast.makeText(this, "Template updated!", Toast.LENGTH_SHORT).show();
            }

            db.setTransactionSuccessful(); // Commit the transaction
            finish(); // Return to AllMeasurementTemplatesActivity (triggers onResume → list refresh)

        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            db.endTransaction(); // Always ends the transaction (rolls back if not successful)
        }
    }
}
