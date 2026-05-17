package com.university.salumate;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

/**
 * ForgotPasswordActivity — Account recovery via security question.
 *
 * <p>Loads and displays the stored security question. The user provides their answer
 * and a new password. On success the password is reset and the user is navigated to
 * the Dashboard (clearing the back-stack so they cannot return to the auth screen).</p>
 */
public class ForgotPasswordActivity extends AppCompatActivity {

    private DBHandler dbHandler;
    private TextInputEditText etAnswer, etNewPassword, etConfirmPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        dbHandler = new DBHandler(this);

        // Configure toolbar with back navigation
        Toolbar toolbar = findViewById(R.id.toolbarForgotPwd);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        etAnswer      = findViewById(R.id.etSecurityAnswer);
        etNewPassword = findViewById(R.id.etResetNewPassword);
        etConfirmPassword = findViewById(R.id.etResetConfirmPassword);

        // Load and display the stored security question
        TextView txtQuestion = findViewById(R.id.txtSecurityQuestion);
        String question = dbHandler.getSecurityQuestion();
        if (question != null && !question.isEmpty()) {
            txtQuestion.setText(question);
        } else {
            // No security question set — recovery not possible without it
            txtQuestion.setText("No security question configured.");
            Toast.makeText(this,
                    "No security question found. Please contact your administrator.",
                    Toast.LENGTH_LONG).show();
        }

        MaterialButton btnReset = findViewById(R.id.btnResetPassword);
        btnReset.setOnClickListener(v -> attemptReset());
    }

    /**
     * Validates form input and calls {@link DBHandler#resetPasswordViaAnswer}
     * to verify the security answer and update the password atomically.
     */
    private void attemptReset() {
        String answer  = etAnswer.getText() != null
                ? etAnswer.getText().toString().trim() : "";
        String newPwd  = etNewPassword.getText() != null
                ? etNewPassword.getText().toString() : "";
        String confirm = etConfirmPassword.getText() != null
                ? etConfirmPassword.getText().toString() : "";

        if (answer.isEmpty() || newPwd.isEmpty() || confirm.isEmpty()) {
            Toast.makeText(this, "All fields are required.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (newPwd.length() < 4) {
            Toast.makeText(this, "Password must be at least 4 characters.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (!newPwd.equals(confirm)) {
            Toast.makeText(this, "Passwords do not match.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (dbHandler.resetPasswordViaAnswer(answer, newPwd)) {
            Toast.makeText(this, "Password reset successfully!", Toast.LENGTH_LONG).show();
            // Navigate to Dashboard and clear the entire auth back-stack
            Intent intent = new Intent(this, DashboardActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        } else {
            Toast.makeText(this, "Incorrect answer. Please try again.",
                    Toast.LENGTH_SHORT).show();
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
