package com.example.bookmyticket.features.settings;

import com.example.bookmyticket.R;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "UserPrefs";
    private static final String KEY_NOTIFICATIONS = "notificationsEnabled";
    private Switch notificationsSwitch;
    private LinearLayout privacyPolicyLayout, termsOfServiceLayout, contactSupportLayout;
    private TextView logoutButton, appVersionText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
if(getSupportActionBar()!=null){
    getSupportActionBar().hide();
}
        // Initialize views
        notificationsSwitch = findViewById(R.id.notifications_switch);
        privacyPolicyLayout = findViewById(R.id.privacy_policy_layout);
        termsOfServiceLayout = findViewById(R.id.terms_of_service_layout);
        contactSupportLayout = findViewById(R.id.contact_support_layout);

        appVersionText = findViewById(R.id.app_version_text);

        // Set app version
        appVersionText.setText("Version 3.8.1");

        // Load notification preference
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean notificationsEnabled = prefs.getBoolean(KEY_NOTIFICATIONS, true);
        notificationsSwitch.setChecked(notificationsEnabled);
        findViewById(R.id.back_button).setOnClickListener(v -> finish());
        // Notification switch listener
        notificationsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(KEY_NOTIFICATIONS, isChecked);
            editor.apply();
        });

        // Privacy Policy click listener
        privacyPolicyLayout.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/privacy-policy"));
            startActivity(intent);
        });

        // Terms of Service click listener
        termsOfServiceLayout.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/terms-of-service"));
            startActivity(intent);
        });

        // Contact Support click listener
        contactSupportLayout.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:support@example.com"));
            intent.putExtra(Intent.EXTRA_SUBJECT, "Support Request");
            startActivity(Intent.createChooser(intent, "Send Email"));
        });

    }
}
