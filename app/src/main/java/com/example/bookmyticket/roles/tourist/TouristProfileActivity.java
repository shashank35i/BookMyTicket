package com.example.bookmyticket.roles.tourist;

import com.example.bookmyticket.R;
import com.example.bookmyticket.auth.LoginActivity;

import static android.content.ContentValues.TAG;

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

import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.facebook.shimmer.ShimmerFrameLayout;

import java.io.ByteArrayOutputStream;

public class TouristProfileActivity extends AppCompatActivity {
    private ImageView profileImageView, backButton, updateProfileBtn;
    private TextView userIdTextView, phoneNumberTextView;
    private LinearLayout favoriteDestinationsLayout, manageVehicleLayout, activityHistoryLayout, aboutLayout, logoutLayout;
    private ShimmerFrameLayout profileShimmer, detailsShimmer;
    private com.google.firebase.database.DatabaseReference databaseReference;
    private com.google.firebase.auth.FirebaseAuth mAuth;
    private Uri imageUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Set PhonePe-style animations
        overridePendingTransition(R.anim.phonepe_enter, R.anim.phonepe_stay);
        setContentView(R.layout.activity_touristprofile);

        // Initialize UI elements
        profileImageView = findViewById(R.id.profile_image);
        backButton = findViewById(R.id.back_button);
        userIdTextView = findViewById(R.id.user_id);
        phoneNumberTextView = findViewById(R.id.phone_number);
        updateProfileBtn = findViewById(R.id.update_profile);
        favoriteDestinationsLayout = findViewById(R.id.favorite_destinations_layout);
        manageVehicleLayout = findViewById(R.id.manage_vehicle_layout);
        activityHistoryLayout = findViewById(R.id.activity_history_layout);
        aboutLayout = findViewById(R.id.about_layout);
        logoutLayout = findViewById(R.id.logout_layout);
        profileShimmer = findViewById(R.id.profile_shimmer);
        detailsShimmer = findViewById(R.id.details_shimmer);

        mAuth = com.google.firebase.auth.FirebaseAuth.getInstance();
        databaseReference = com.google.firebase.database.FirebaseDatabase.getInstance().getReference("users");

        // Apply opening animation to main layout
        View mainLayout = findViewById(R.id.main_layout);
        Animation enterAnimation = AnimationUtils.loadAnimation(this, R.anim.phonepe_enter);
        mainLayout.startAnimation(enterAnimation);

        // Start shimmer effect
        profileShimmer.startShimmer();
        detailsShimmer.startShimmer();
        updateProfileBtn.setVisibility(View.GONE); // Hide edit button during loading

        com.google.firebase.auth.FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            loadUserData(user.getUid());
        } else {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            stopShimmer();
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Handle Back Button Click
        backButton.setOnClickListener(v -> {
            finish();
            overridePendingTransition(R.anim.phonepe_stay, R.anim.phonepe_exit);
        });

        // Handle Favorite Destinations Click


        // Handle Activity History Click
        activityHistoryLayout.setOnClickListener(v -> {
            Intent intent = new Intent(TouristProfileActivity.this, TicketHistoryTouristActivity.class);
            startActivity(intent);
            overridePendingTransition(R.anim.phonepe_enter, R.anim.phonepe_exit);
        });

        // Handle About Click


        // Handle Logout Click
        logoutLayout.setOnClickListener(v -> {
            try {
                MasterKey masterKey = new MasterKey.Builder(this)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build();
                SharedPreferences sharedPreferences = EncryptedSharedPreferences.create(
                        this, "UserPrefs", masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                );
                sharedPreferences.edit().clear().commit(); // Use commit() for synchronous clearing
                Log.d(TAG, "EncryptedSharedPreferences cleared");
            } catch (Exception e) {
                Log.e(TAG, "Failed to clear EncryptedSharedPreferences: " + e.getMessage());
            }



            // Step 3: Redirect to LoginActivity
            redirectToLogin();
        });

        // Handle Profile Image Update
        profileImageView.setOnClickListener(v -> selectProfilePicture());
        updateProfileBtn.setOnClickListener(v -> selectProfilePicture());

        setSystemColors();
    }
    private void redirectToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
        Toast.makeText(this, "Logout Successful", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Redirected to LoginActivity with cleared task");
    }
    private void setSystemColors() {
        Window window = getWindow();
        window.setStatusBarColor(Color.parseColor("#FFFFFF"));
        window.setNavigationBarColor(Color.parseColor("#FFFFFF"));

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

    private void loadUserData(String firebaseUid) {
        databaseReference.child(firebaseUid).addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
            @Override
            public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String phoneNumber = snapshot.child("phone").getValue(String.class);
                    String customUid = snapshot.child("uid").getValue(String.class);
                    String profileImageBase64 = snapshot.child("profileImage").getValue(String.class);

                    userIdTextView.setText("User ID: " + customUid);
                    phoneNumberTextView.setText("Phone Number: " + phoneNumber);

                    if (profileImageBase64 != null && !profileImageBase64.isEmpty()) {
                        byte[] decodedString = Base64.decode(profileImageBase64, Base64.DEFAULT);
                        Bitmap decodedBitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                        profileImageView.setImageBitmap(decodedBitmap);
                    }

                    stopShimmer();
                    updateProfileBtn.setVisibility(View.VISIBLE);
                } else {
                    Toast.makeText(TouristProfileActivity.this, "No user data found", Toast.LENGTH_SHORT).show();
                    stopShimmer();
                    updateProfileBtn.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {
                Log.e("TouristProfileActivity", "Failed to fetch user data", error.toException());
                Toast.makeText(TouristProfileActivity.this, "Failed to load data", Toast.LENGTH_SHORT).show();
                stopShimmer();
                updateProfileBtn.setVisibility(View.VISIBLE);
            }
        });
    }

    private void stopShimmer() {
        profileShimmer.stopShimmer();
        profileShimmer.setShimmer(null);
        detailsShimmer.stopShimmer();
        detailsShimmer.setShimmer(null);
    }

    private void selectProfilePicture() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), 1);
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
            profileShimmer.startShimmer(); // Start shimmer during upload
            updateProfileBtn.setVisibility(View.GONE);

            java.io.InputStream inputStream = getContentResolver().openInputStream(imageUri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);

            bitmap = getCircularBitmap(bitmap);

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, byteArrayOutputStream);
            byte[] imageBytes = byteArrayOutputStream.toByteArray();
            String base64Image = Base64.encodeToString(imageBytes, Base64.DEFAULT);

            com.google.firebase.auth.FirebaseUser user = mAuth.getCurrentUser();
            if (user == null) {
                stopShimmer();
                Toast.makeText(TouristProfileActivity.this, "User not logged in", Toast.LENGTH_SHORT).show();
                updateProfileBtn.setVisibility(View.VISIBLE);
                return;
            }

            String uid = user.getUid();
            Bitmap finalBitmap = bitmap;
            databaseReference.child(uid).child("profileImage").setValue(base64Image)
                    .addOnSuccessListener(aVoid -> {
                        stopShimmer();
                        Toast.makeText(TouristProfileActivity.this, "Profile Updated", Toast.LENGTH_SHORT).show();
                        profileImageView.setImageBitmap(finalBitmap);
                        updateProfileBtn.setVisibility(View.VISIBLE);
                    })
                    .addOnFailureListener(e -> {
                        stopShimmer();
                        Toast.makeText(TouristProfileActivity.this, "Failed to update profile: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        updateProfileBtn.setVisibility(View.VISIBLE);
                    });

        } catch (Exception e) {
            stopShimmer();
            Toast.makeText(this, "Error processing image", Toast.LENGTH_SHORT).show();
            updateProfileBtn.setVisibility(View.VISIBLE);
            e.printStackTrace();
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

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.phonepe_stay, R.anim.phonepe_exit);
    }
}
