package com.university.salumate;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

/**
 * ChangePasswordActivity — Allows the shop owner to change their login password.
 *
 * <p>Validates that the current password is correct before accepting a new one.
 * Both password fields must match and the new password must be at least 4 characters.</p>
 */
public class ChangePasswordActivity extends AppCompatActivity {

    private DBHandler dbHandler;
    private TextInputEditText etCurrentPassword, etNewPassword, etConfirmNewPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);

        dbHandler = new DBHandler(this);

        // Configure toolbar with back navigation
        Toolbar toolbar = findViewById(R.id.toolbarChangePwd);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        etCurrentPassword    = findViewById(R.id.etCurrentPassword);
        etNewPassword        = findViewById(R.id.etNewPassword);
        etConfirmNewPassword = findViewById(R.id.etConfirmNewPassword);

        MaterialButton btnSave = findViewById(R.id.btnSavePassword);
        btnSave.setOnClickListener(v -> attemptChangePassword());
    }

    /**
     * Reads all three fields, validates them, then calls
     * {@link DBHandler#changePassword(String, String)} to persist the change.
     */
    private void attemptChangePassword() {
        String current = etCurrentPassword.getText() != null
                ? etCurrentPassword.getText().toString() : "";
        String newPwd  = etNewPassword.getText() != null
                ? etNewPassword.getText().toString() : "";
        String confirm = etConfirmNewPassword.getText() != null
                ? etConfirmNewPassword.getText().toString() : "";

        if (current.isEmpty() || newPwd.isEmpty() || confirm.isEmpty()) {
            Toast.makeText(this, "All fields are required.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (newPwd.length() < 4) {
            Toast.makeText(this, "New password must be at least 4 characters.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (!newPwd.equals(confirm)) {
            Toast.makeText(this, "New passwords do not match.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (newPwd.equals(current)) {
            Toast.makeText(this, "New password must differ from the current one.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (dbHandler.changePassword(current, newPwd)) {
            Toast.makeText(this, "Password changed successfully!", Toast.LENGTH_LONG).show();
            finish();
        } else {
            Toast.makeText(this, "Current password is incorrect.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
