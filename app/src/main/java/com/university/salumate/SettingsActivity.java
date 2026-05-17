package com.university.salumate;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;

/**
 * SettingsActivity — Security settings for the SaluMate shop owner.
 *
 * <p>Three action rows are shown or hidden depending on whether password
 * credentials have already been configured:
 * <ul>
 *   <li><b>Change Password</b> — opens {@link ChangePasswordActivity}.</li>
 *   <li><b>Security Question</b> — inline dialog using a dedicated layout; shows current question pre-filled.</li>
 *   <li><b>Setup Credentials</b> — only visible when no password is set; navigates to MainActivity to trigger setup.</li>
 * </ul>
 */
public class SettingsActivity extends AppCompatActivity {

    private DBHandler dbHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        dbHandler = new DBHandler(this);

        Toolbar toolbar = findViewById(R.id.toolbarSettings);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        refreshUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUi(); // Refresh after returning from ChangePasswordActivity
    }

    /**
     * Wires all rows and toggles visibility based on whether credentials are configured.
     * Also pre-populates the current security question text in its subtitle.
     */
    private void refreshUi() {
        boolean passwordSet = dbHandler.isPasswordSet();

        // ── Change Password row ───────────────────────────────────────────────
        MaterialCardView cardChangePwd = findViewById(R.id.cardChangePassword);
        cardChangePwd.setVisibility(passwordSet ? View.VISIBLE : View.GONE);
        cardChangePwd.setOnClickListener(v ->
                startActivity(new Intent(this, ChangePasswordActivity.class)));

        // ── Security Question row ─────────────────────────────────────────────
        MaterialCardView cardSecQ = findViewById(R.id.cardSecurityQuestion);
        cardSecQ.setVisibility(passwordSet ? View.VISIBLE : View.GONE);
        if (passwordSet) {
            // Show the current question as subtitle so the user knows what's stored
            String currentQ = dbHandler.getSecurityQuestion();
            TextView txtCurrentQ = findViewById(R.id.txtCurrentQuestion);
            txtCurrentQ.setText(currentQ != null && !currentQ.isEmpty()
                    ? currentQ : "No question set yet — tap to add one");
            cardSecQ.setOnClickListener(v -> showUpdateSecurityQuestionDialog());
        }

        // ── Setup nudge card (only when no password configured yet) ───────────
        MaterialCardView cardSetup = findViewById(R.id.cardSetupCredentials);
        cardSetup.setVisibility(passwordSet ? View.GONE : View.VISIBLE);
        if (!passwordSet) {
            cardSetup.setOnClickListener(v -> {
                // Re-launch MainActivity which handles first-time setup
                Intent i = new Intent(this, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(i);
            });
        }
    }

    /**
     * Shows a dedicated dialog (dialog_update_security_question.xml) to update
     * the security question and answer.
     * Pre-fills the question field with the currently stored question.
     * Requires the current password to authorise the change.
     */
    private void showUpdateSecurityQuestionDialog() {
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_update_security_question, null);

        TextInputEditText etCurrentPwd = dialogView.findViewById(R.id.etCurrentPwdForQ);
        TextInputEditText etNewQuestion = dialogView.findViewById(R.id.etNewQuestion);
        TextInputEditText etNewAnswer   = dialogView.findViewById(R.id.etNewAnswer);

        // Pre-fill the current question so the user can see/edit it rather than retyping from scratch
        String currentQ = dbHandler.getSecurityQuestion();
        if (currentQ != null && !currentQ.isEmpty()) {
            etNewQuestion.setText(currentQ);
            etNewQuestion.setSelection(currentQ.length()); // Move cursor to end
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Update Security Question")
                .setMessage("Enter your current password to authorise this change.")
                .setView(dialogView)
                .setPositiveButton("Save", null) // null prevents auto-dismiss on validation failure
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {
            Button btnSave = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            btnSave.setTextColor(getColor(R.color.secondary));

            btnSave.setOnClickListener(v -> {
                String currentPwd = etCurrentPwd.getText() != null
                        ? etCurrentPwd.getText().toString() : "";
                String newQ       = etNewQuestion.getText() != null
                        ? etNewQuestion.getText().toString().trim() : "";
                String newA       = etNewAnswer.getText() != null
                        ? etNewAnswer.getText().toString().trim() : "";

                if (currentPwd.isEmpty()) {
                    Toast.makeText(this, "Please enter your current password.", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (newQ.isEmpty() || newA.isEmpty()) {
                    Toast.makeText(this, "Question and answer cannot be empty.", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (dbHandler.updateSecurityQuestion(currentPwd, newQ, newA)) {
                    dialog.dismiss();
                    Toast.makeText(this, "Security question updated.", Toast.LENGTH_SHORT).show();
                    refreshUi(); // Refresh subtitle to show new question
                } else {
                    Toast.makeText(this, "Incorrect current password.", Toast.LENGTH_SHORT).show();
                }
            });

            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                    .setTextColor(getColor(R.color.text_secondary));
        });

        dialog.show();
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
