package com.university.salumate;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import java.util.concurrent.Executor;

/**
 * MainActivity — Entry point of the SaluMate application.
 *
 * This activity displays the welcome / login screen and handles
 * biometric (fingerprint) authentication before granting access
 * to the main Dashboard. Authentication is triggered automatically
 * on launch and can also be re-triggered via the login button.
 */
public class MainActivity extends AppCompatActivity {

    /** Runs biometric callbacks on the main thread. */
    private Executor executor;

    /** Android BiometricPrompt API handle. */
    private BiometricPrompt biometricPrompt;

    /** Configuration metadata for the biometric dialog (title, subtitle, etc.) */
    private BiometricPrompt.PromptInfo promptInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Use the main (UI) thread executor so callbacks run on the UI thread
        executor = ContextCompat.getMainExecutor(this);

        // Build the biometric prompt with its three possible callback outcomes
        biometricPrompt = new BiometricPrompt(MainActivity.this,
                executor, new BiometricPrompt.AuthenticationCallback() {

            /** Called when authentication encounters a non-recoverable error. */
            @Override
            public void onAuthenticationError(int errorCode,
                                              @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                Toast.makeText(getApplicationContext(),
                        "Authentication error: " + errString, Toast.LENGTH_SHORT).show();
            }

            /**
             * Called on successful biometric verification.
             * Navigates directly to the DashboardActivity and closes this screen
             * so the user cannot navigate back to the login screen with the back key.
             */
            @Override
            public void onAuthenticationSucceeded(
                    @NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                Intent intent = new Intent(MainActivity.this, DashboardActivity.class);
                startActivity(intent);
                finish(); // Removes MainActivity from the back-stack
            }

            /** Called when a fingerprint is recognized but does not match. */
            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Toast.makeText(getApplicationContext(), "Authentication failed",
                        Toast.LENGTH_SHORT).show();
            }
        });

        // Configure the biometric dialog presented to the user
        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Biometric Login — SaluMate")
                .setSubtitle("Verify your fingerprint to continue")
                .setNegativeButtonText("Cancel")
                .build();

        // Auto-prompt biometrics when the activity starts for a seamless UX
        biometricPrompt.authenticate(promptInfo);

        // The login button also re-triggers the biometric prompt if dismissed
        Button btnBiometrics = findViewById(R.id.btnBiometricLogin);
        if (btnBiometrics != null) {
            btnBiometrics.setOnClickListener(v -> biometricPrompt.authenticate(promptInfo));
        }
    }
}
