package com.example.bookmyticket.roles.placeadmin;

import com.example.bookmyticket.R;
import com.example.bookmyticket.auth.LoginActivity;
import com.example.bookmyticket.features.tickets.TicketHistoryActivity;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class PlaceAdminProfileActivity extends AppCompatActivity {
    private static final String TAG = "PlaceAdminProfileActivity";
    private ImageView profileImageView, backButton, updateProfileBtn;
    private TextView userIdTextView, emailTextView, phoneNumberTextView;
    private CardView linkBankAccountLayout, activityHistoryLayout, logoutLayout;
    private ProgressDialog progressDialog;
    private DatabaseReference databaseReference;
    private DatabaseReference notificationsRef;
    private DatabaseReference payoutsRef, qrHistoryRef, usersRef;
    private ValueEventListener payoutsListener, qrHistoryListener, userListener;
    private FirebaseAuth mAuth;
    private Uri imageUri;
    private SharedPreferences sharedPreferences;
    private boolean isActivityDestroyed = false;
    private boolean isLoggingOut = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // Initialize UI elements
        profileImageView = findViewById(R.id.profile_image);
        backButton = findViewById(R.id.back_button);
        userIdTextView = findViewById(R.id.user_name);
        emailTextView = findViewById(R.id.email);
        phoneNumberTextView = findViewById(R.id.phone_number);
        updateProfileBtn = findViewById(R.id.update_profile);
        linkBankAccountLayout = findViewById(R.id.link_bank_account);
        activityHistoryLayout = findViewById(R.id.activity_history_layout);
        logoutLayout = findViewById(R.id.logout_layout);

        mAuth = FirebaseAuth.getInstance();
        databaseReference = FirebaseDatabase.getInstance().getReference("users");
        notificationsRef = FirebaseDatabase.getInstance().getReference("placeadmin_notifications");
        payoutsRef = FirebaseDatabase.getInstance().getReference("payouts");
        qrHistoryRef = FirebaseDatabase.getInstance().getReference("qr_history");
        usersRef = FirebaseDatabase.getInstance().getReference("users");
        sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Loading profile...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            Log.d(TAG, "Current user UID: " + user.getUid());
            loadUserData(user.getUid());
        } else {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            progressDialog.dismiss();
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Set click listeners
        activityHistoryLayout.setOnClickListener(v -> {
            Intent intent = new Intent(PlaceAdminProfileActivity.this, TicketHistoryActivity.class);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });

        backButton.setOnClickListener(v -> {
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });

        profileImageView.setOnClickListener(v -> selectProfilePicture());
        updateProfileBtn.setOnClickListener(v -> selectProfilePicture());

        linkBankAccountLayout.setOnClickListener(v -> {
            Intent intent = new Intent(PlaceAdminProfileActivity.this, PlaceBankDetailsActivity.class);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });

        logoutLayout.setOnClickListener(v -> logoutUser());

        setSystemColors();
    }

    private void setSystemColors() {
        Window window = getWindow();
        getWindow().setStatusBarColor(Color.parseColor("#EEEEEE"));
        window.setNavigationBarColor(Color.parseColor("#EEEEEE"));
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.getInsetsController().setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            );
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            );
        }
    }

    private void loadUserData(String userId) {
        Log.d(TAG, "Loading data for UID: " + userId);
        databaseReference.child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Log.d(TAG, "Snapshot exists for UID: " + userId);
                    String firebaseUid = snapshot.getKey();
                    String customUid = snapshot.child("uid").getValue(String.class);
                    String email = snapshot.child("email").getValue(String.class);
                    String phoneNumber = snapshot.child("phone").getValue(String.class);
                    String profileImageBase64 = snapshot.child("profileImage").getValue(String.class);

                    Log.d(TAG, "Firebase UID: " + firebaseUid);
                    Log.d(TAG, "Custom UID: " + customUid);
                    Log.d(TAG, "Email: " + email);
                    Log.d(TAG, "Phone: " + phoneNumber);

                    userIdTextView.setText("User ID: " + customUid);
                    emailTextView.setText("Email: " + email);
                    phoneNumberTextView.setText("Phone Number: " + phoneNumber);

                    if (profileImageBase64 != null && !profileImageBase64.isEmpty()) {
                        byte[] decodedString = Base64.decode(profileImageBase64, Base64.DEFAULT);
                        Bitmap decodedBitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                        profileImageView.setImageBitmap(decodedBitmap);
                    }

                    progressDialog.dismiss();
                } else {
                    Log.d(TAG, "No data found for UID: " + userId);
                    Toast.makeText(PlaceAdminProfileActivity.this, "No user data found", Toast.LENGTH_SHORT).show();
                    progressDialog.dismiss();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to fetch user data", error.toException());
                progressDialog.dismiss();
            }
        });
    }

    private void selectProfilePicture() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), 1);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            imageUri = data.getData();
            convertAndUploadProfilePicture();
        }
    }

    private void convertAndUploadProfilePicture() {
        try {
            progressDialog.setMessage("Uploading...");
            progressDialog.show();

            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);

            // Ensure image is circular
            bitmap = getCircularBitmap(bitmap);

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, byteArrayOutputStream);
            byte[] imageBytes = byteArrayOutputStream.toByteArray();
            String base64Image = Base64.encodeToString(imageBytes, Base64.DEFAULT);

            FirebaseUser user = mAuth.getCurrentUser();
            if (user == null) {
                progressDialog.dismiss();
                Toast.makeText(PlaceAdminProfileActivity.this, "User not logged in", Toast.LENGTH_SHORT).show();
                return;
            }

            String userId = user.getUid();
            Bitmap finalBitmap = bitmap;
            databaseReference.child(userId).child("profileImage").setValue(base64Image)
                    .addOnSuccessListener(aVoid -> {
                        progressDialog.dismiss();
                        Toast.makeText(PlaceAdminProfileActivity.this, "Profile Updated", Toast.LENGTH_SHORT).show();
                        profileImageView.setImageBitmap(finalBitmap);
                        saveNotification(userId, "PROFILE_UPDATED", "Profile image updated on: " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date()));
                    })
                    .addOnFailureListener(e -> {
                        progressDialog.dismiss();
                        Toast.makeText(PlaceAdminProfileActivity.this, "Failed to update profile: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Failed to update profile image: " + e.getMessage(), e);
                    });

        } catch (Exception e) {
            progressDialog.dismiss();
            Toast.makeText(this, "Error processing image", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Error processing image: " + e.getMessage(), e);
        }
    }

    private Bitmap getCircularBitmap(Bitmap bitmap) {
        int size = Math.min(bitmap.getWidth(), bitmap.getHeight());
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(output);
        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, size, size);

        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        canvas.drawCircle(size / 2, size / 2, size / 2, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);

        return output;
    }

    private void saveNotification(String userId, String type, String changeLog) {
        String notificationId = notificationsRef.child(userId).push().getKey();
        if (notificationId == null) return;
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("userId", userId);
        notificationData.put("type", type);
        notificationData.put("timestamp", System.currentTimeMillis());
        notificationData.put("status", "SUCCESS");
        notificationData.put("details", changeLog);

        notificationsRef.child(userId).child(notificationId).setValue(notificationData)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Notification saved: " + notificationId))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to save notification: " + e.getMessage()));
    }

    private void logoutUser() {
        if (isActivityDestroyed) return;
        isLoggingOut = true;

        // Remove Firebase listeners
        if (payoutsRef != null && payoutsListener != null) {
            payoutsRef.removeEventListener(payoutsListener);
            Log.d(TAG, "Removed payoutsListener");
        }
        if (qrHistoryRef != null && qrHistoryListener != null) {
            qrHistoryRef.removeEventListener(qrHistoryListener);
            Log.d(TAG, "Removed qrHistoryListener");
        }
        if (usersRef != null && userListener != null) {
            usersRef.removeEventListener(userListener);
            Log.d(TAG, "Removed userListener");
        }

        // Clear SharedPreferences
        clearAuthData();

        // Clear Firebase Auth
        mAuth.signOut();
        Log.d(TAG, "Firebase Auth signed out");

        // Redirect to LoginActivity
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
        Log.d(TAG, "Redirected to LoginActivity");
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
    }

    private void clearAuthData() {
        try {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.clear();
            editor.apply();
            Log.d(TAG, "SharedPreferences cleared");
        } catch (Exception e) {
            Log.e(TAG, "Failed to clear SharedPreferences: " + e.getMessage());
            SharedPreferences fallbackPrefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
            fallbackPrefs.edit().clear().apply();
            Log.d(TAG, "Cleared fallback SharedPreferences");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!isLoggingOut) {
            if (payoutsRef != null && payoutsListener != null) {
                payoutsRef.removeEventListener(payoutsListener);
            }
            if (qrHistoryRef != null && qrHistoryListener != null) {
                qrHistoryRef.removeEventListener(qrHistoryListener);
            }
            if (usersRef != null && userListener != null) {
                usersRef.removeEventListener(userListener);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isActivityDestroyed = true;
        progressDialog.dismiss();
    }
}
