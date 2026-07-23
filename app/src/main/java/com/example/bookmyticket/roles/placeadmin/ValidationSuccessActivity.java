package com.example.bookmyticket.roles.placeadmin;

import com.example.bookmyticket.R;
import com.example.bookmyticket.model.Ticket;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class ValidationSuccessActivity extends AppCompatActivity {
    private static final String TAG = "ValidationSuccessActivity";
    private boolean isTicketMarked = false;
    private String currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(R.anim.slide_in_up, R.anim.fade_out_fast);
        setContentView(R.layout.activity_validation_success);

        currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null ?
                FirebaseAuth.getInstance().getCurrentUser().getUid() : "scanner_device";

        setupWindowColors();
        setupBackButton();
        checkTicketStatus();
    }

    private void setupWindowColors() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        Window window = getWindow();
        window.setStatusBarColor(Color.parseColor("#FFFFFF"));
        window.setNavigationBarColor(Color.parseColor("#FFFFFF"));
    }

    private void setupBackButton() {
        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finishWithAnimation());
    }

    private void checkTicketStatus() {
        Intent intent = getIntent();
        if (intent == null || intent.getStringExtra("TICKET_ID") == null) {
            showErrorAndFinish("Invalid ticket data");
            return;
        }

        String ticketId = intent.getStringExtra("TICKET_ID");
        Log.d(TAG, "Checking ticket: " + ticketId);

        DatabaseReference payoutRef = FirebaseDatabase.getInstance().getReference("payouts")
                .child(currentUserId)
                .child(ticketId);

        showLoading(true);
        payoutRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                showLoading(false);

                if (!dataSnapshot.exists()) {
                    showErrorAndFinish("Ticket not found in database");
                    return;
                }

                String status = dataSnapshot.child("status").getValue(String.class);
                if ("validated".equals(status)) {
                    Log.d(TAG, "Ticket already validated");
                    showValidationFailed();
                } else {
                    Log.d(TAG, "Valid ticket found - displaying details");
                    displayTicketDetails(intent);
                    if (!isTicketMarked) {
                        markTicketAsValidated(ticketId);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                showLoading(false);
                showErrorAndFinish("Database error: " + databaseError.getMessage());
            }
        });
    }

    private void showValidationFailed() {
        Window window = getWindow();
        window.setStatusBarColor(Color.parseColor("#D32F2F"));
        window.setNavigationBarColor(Color.parseColor("#D32F2F"));

        View rootLayout = findViewById(R.id.rootLayout);
        ImageView icon = findViewById(R.id.icon);
        TextView title = findViewById(R.id.title);
        TextView message = findViewById(R.id.message);

        // Hide ticket details container
        findViewById(R.id.ticketDetailsContainer).setVisibility(View.GONE);
        findViewById(R.id.visitorCountsContainer).setVisibility(View.GONE);

        // Show validation failed message
        rootLayout.setBackgroundColor(Color.parseColor("#FFFFFF"));
        icon.setImageResource(R.drawable.info);
        icon.setColorFilter(Color.parseColor("#D32F2F"));
        title.setText("TICKET ALREADY USED");
        title.setVisibility(View.VISIBLE);
        message.setText("This ticket has already been scanned and cannot be reused.");
        message.setVisibility(View.VISIBLE);
        icon.setVisibility(View.VISIBLE);

        Animation shake = AnimationUtils.loadAnimation(this, R.anim.shake);
        icon.startAnimation(shake);
    }

    private void displayTicketDetails(Intent intent) {
        // Set basic ticket info with null checks
        setTextView(R.id.txtPlace, intent.getStringExtra("PLACE"));
        setTextView(R.id.txtDate, intent.getStringExtra("DATE"));
        setTextView(R.id.txtTime, intent.getStringExtra("TIME"));
        setTextView(R.id.txtTicketId, intent.getStringExtra("TICKET_ID"));

        // Calculate total visitors, excluding CAMERAS
        int totalVisitors = 0;
        String[] visitorKeys = {"ADULTS", "STUDENTS", "SENIORS", "CHILDREN"};
        for (String key : visitorKeys) {
            int count = intent.getIntExtra(key, 0);
            totalVisitors += count;
            Log.d(TAG, "Visitor type: " + key + ", Count: " + count);
        }

        // Log camera count for debugging
        int cameras = intent.getIntExtra("CAMERAS", 0);
        Log.d(TAG, "Total Visitors: " + totalVisitors + ", Cameras: " + cameras);

        // Update UI based on totalVisitors
        TextView totalVisitorsTextView = findViewById(R.id.txtTotalVisitors);
        View visitorCountsContainer = findViewById(R.id.visitorCountsContainer);

        if (totalVisitors > 0) {
            visitorCountsContainer.setVisibility(View.VISIBLE);
            totalVisitorsTextView.setText(String.valueOf(totalVisitors));
        } else {
            visitorCountsContainer.setVisibility(View.GONE);
            totalVisitorsTextView.setText("0");
        }

        // Success animation
        ImageView icon = findViewById(R.id.icon);
        Animation pulse = AnimationUtils.loadAnimation(this, R.anim.pulse);
        icon.startAnimation(pulse);
    }

    private void markTicketAsValidated(String ticketId) {
        if (isTicketMarked || ticketId == null) return;

        isTicketMarked = true;
        Log.d(TAG, "Marking ticket as validated: " + ticketId);

        DatabaseReference payoutRef = FirebaseDatabase.getInstance().getReference("payouts")
                .child(currentUserId)
                .child(ticketId);

        payoutRef.child("status").setValue("validated")
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Successfully updated payout status");

                        // Update tickets node if needed
                        FirebaseDatabase.getInstance().getReference("tickets")
                                .child(currentUserId)
                                .child(ticketId)
                                .child("status")
                                .setValue("used")
                                .addOnCompleteListener(ticketTask -> {
                                    if (ticketTask.isSuccessful()) {
                                        Log.d(TAG, "Successfully updated tickets status");
                                    } else {
                                        Log.e(TAG, "Failed to update tickets status", ticketTask.getException());
                                    }
                                });
                    } else {
                        Log.e(TAG, "Failed to update payout status", task.getException());
                        isTicketMarked = false;
                    }
                });
    }

    private void showLoading(boolean show) {
        findViewById(R.id.progressBar).setVisibility(show ? View.VISIBLE : View.GONE);
        findViewById(R.id.contentLayout).setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void showErrorAndFinish(String message) {
        Log.e(TAG, message);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        finishWithAnimation();
    }

    private void finishWithAnimation() {
        finish();
        overridePendingTransition(R.anim.fade_in_fast, R.anim.slide_out_down);
    }

    private void setTextView(int id, String text) {
        TextView textView = findViewById(id);
        if (textView != null) {
            textView.setText(text != null ? text : "N/A");
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finishWithAnimation();
    }
}
