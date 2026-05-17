package com.university.salumate;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.google.android.material.textfield.TextInputEditText;

import java.util.concurrent.Executor;

/**
 * MainActivity — Entry point and authentication gate of SaluMate.
 *
 * <p>Supports two login paths:
 * <ol>
 *   <li><b>Biometric</b> — fingerprint prompt shown automatically on launch.</li>
 *   <li><b>Username + Password</b> — fallback via "Use Password Instead" link.
 *       On first use a full credentials + security-question setup dialog is shown.</li>
 * </ol>
 * The "Forgot Password?" link opens {@link ForgotPasswordActivity} for recovery
 * via the pre-configured security question.</p>
 */
public class MainActivity extends AppCompatActivity {

    /** Runs biometric callbacks on the main (UI) thread. */
    private Executor executor;

    /** Android BiometricPrompt API handle. */
    private BiometricPrompt biometricPrompt;

    /** Configuration metadata for the biometric dialog. */
    private BiometricPrompt.PromptInfo promptInfo;

    /** Database access — used for credential checks and setup. */
    private DBHandler dbHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHandler = new DBHandler(this);
        executor  = ContextCompat.getMainExecutor(this);

        // Build biometric prompt with three outcome callbacks
        biometricPrompt = new BiometricPrompt(MainActivity.this,
                executor, new BiometricPrompt.AuthenticationCallback() {

            /**
             * Non-recoverable biometric error.
             * If hardware/enrolment is unavailable, fall back to password login
             * so the user is never completely locked out.
             */
            @Override
            public void onAuthenticationError(int errorCode,
                                              @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                if (errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS
                        || errorCode == BiometricPrompt.ERROR_HW_NOT_PRESENT
                        || errorCode == BiometricPrompt.ERROR_HW_UNAVAILABLE) {
                    // Biometrics not available — silently fall back to password
                    showPasswordLoginDialog();
                } else {
                    Toast.makeText(getApplicationContext(),
                            "Auth error: " + errString, Toast.LENGTH_SHORT).show();
                }
            }

            /** Biometric matched — navigate straight to the Dashboard. */
            @Override
            public void onAuthenticationSucceeded(
                    @NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                navigateToDashboard();
            }

            /** Fingerprint not recognised — leave dialog open for another attempt. */
            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Toast.makeText(getApplicationContext(),
                        "Fingerprint not recognised. Try again.",
                        Toast.LENGTH_SHORT).show();
            }
        });

        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Biometric Login — SaluMate")
                .setSubtitle("Verify your fingerprint to continue")
                .setNegativeButtonText("Cancel")
                .build();

        // Auto-trigger biometrics on launch for seamless entry
        biometricPrompt.authenticate(promptInfo);

        // Biometric button re-triggers the prompt if dismissed
        Button btnBiometrics = findViewById(R.id.btnBiometricLogin);
        if (btnBiometrics != null) {
            btnBiometrics.setOnClickListener(v -> biometricPrompt.authenticate(promptInfo));
        }

        // "Use Password Instead" — shows setup or login dialog
        TextView txtUsePassword = findViewById(R.id.txtUsePassword);
        if (txtUsePassword != null) {
            txtUsePassword.setOnClickListener(v -> showPasswordLoginDialog());
        }

        // "Forgot Password?" — launches recovery screen
        TextView txtForgotPassword = findViewById(R.id.txtForgotPassword);
        if (txtForgotPassword != null) {
            txtForgotPassword.setOnClickListener(v -> {
                if (!dbHandler.isPasswordSet()) {
                    Toast.makeText(this,
                            "No password set up yet. Tap \"Use Password Instead\" first.",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                startActivity(new Intent(MainActivity.this, ForgotPasswordActivity.class));
            });
        }
    }

    // ─── Auth Flow Helpers ────────────────────────────────────────────────────

    /**
     * Entry point for the password auth path.
     * Routes to first-time setup if no credentials exist, or to the login dialog otherwise.
     */
    private void showPasswordLoginDialog() {
        if (!dbHandler.isPasswordSet()) {
            showFirstTimeSetupDialog();
        } else {
            showLoginDialog();
        }
    }

    /**
     * Displays the username + password login dialog.
     * The dialog stays open if validation fails so the user can correct their input.
     */
    private void showLoginDialog() {
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_password_login, null);
        TextInputEditText etUsername = dialogView.findViewById(R.id.etDialogUsername);
        TextInputEditText etPassword = dialogView.findViewById(R.id.etDialogPassword);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Password Login")
                .setView(dialogView)
                .setPositiveButton("Login", null) // null → prevent auto-dismiss
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {
            Button btnLogin = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            btnLogin.setTextColor(getColor(R.color.primary));
            btnLogin.setOnClickListener(v -> {
                String username = etUsername.getText() != null
                        ? etUsername.getText().toString().trim() : "";
                String password = etPassword.getText() != null
                        ? etPassword.getText().toString() : "";

                if (username.isEmpty() || password.isEmpty()) {
                    Toast.makeText(this, "Please enter your username and password.",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                if (dbHandler.verifyCredentials(username, password)) {
                    dialog.dismiss();
                    navigateToDashboard();
                } else {
                    Toast.makeText(this, "Incorrect username or password.",
                            Toast.LENGTH_SHORT).show();
                }
            });
            Button btnCancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            btnCancel.setTextColor(getColor(R.color.text_secondary));
        });

        dialog.show();
    }

    /**
     * Displays the first-time credentials setup dialog.
     * Collects username, password, security question, and security answer.
     * Dialog stays open if validation fails.
     */
    private void showFirstTimeSetupDialog() {
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_setup_credentials, null);

        TextInputEditText etUsername    = dialogView.findViewById(R.id.etSetupUsername);
        TextInputEditText etPassword    = dialogView.findViewById(R.id.etSetupPassword);
        TextInputEditText etConfirm     = dialogView.findViewById(R.id.etSetupConfirmPassword);
        TextInputEditText etQuestion    = dialogView.findViewById(R.id.etSetupQuestion);
        TextInputEditText etAnswer      = dialogView.findViewById(R.id.etSetupAnswer);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Set Up Password Login")
                .setMessage("Create your credentials and a security question for account recovery.")
                .setView(dialogView)
                .setPositiveButton("Save", null) // null → prevent auto-dismiss
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {
            Button btnSave = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            btnSave.setTextColor(getColor(R.color.secondary));
            btnSave.setOnClickListener(v -> {
                String username  = etUsername.getText() != null
                        ? etUsername.getText().toString().trim() : "";
                String password  = etPassword.getText() != null
                        ? etPassword.getText().toString() : "";
                String confirm   = etConfirm.getText() != null
                        ? etConfirm.getText().toString() : "";
                String question  = etQuestion.getText() != null
                        ? etQuestion.getText().toString().trim() : "";
                String answer    = etAnswer.getText() != null
                        ? etAnswer.getText().toString().trim() : "";

                if (username.isEmpty() || password.isEmpty()
                        || question.isEmpty() || answer.isEmpty()) {
                    Toast.makeText(this, "All fields are required.",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                if (password.length() < 4) {
                    Toast.makeText(this, "Password must be at least 4 characters.",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!password.equals(confirm)) {
                    Toast.makeText(this, "Passwords do not match.",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                dbHandler.setupCredentials(username, password, question, answer);
                dialog.dismiss();
                Toast.makeText(this, "Credentials saved! You can now log in.",
                        Toast.LENGTH_LONG).show();
                showLoginDialog();
            });
            Button btnCancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            btnCancel.setTextColor(getColor(R.color.text_secondary));
        });

        dialog.show();
    }

    /** Finishes this activity and opens the Dashboard. */
    private void navigateToDashboard() {
        startActivity(new Intent(MainActivity.this, DashboardActivity.class));
        finish(); // Remove login screen from back-stack
    }
}
