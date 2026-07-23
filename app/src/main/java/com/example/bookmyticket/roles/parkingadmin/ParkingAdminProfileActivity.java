package com.example.bookmyticket.roles.parkingadmin;

import com.example.bookmyticket.R;
import com.example.bookmyticket.features.payment.BankActivity;

import android.app.ProgressDialog;
import android.content.Intent;
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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class ParkingAdminProfileActivity extends AppCompatActivity {
    private ImageView profileImageView, backButton, updateProfileBtn;
    private TextView userIdTextView, phoneNumberTextView, emailTextView;
    private LinearLayout linkBankAccountLayout;
    private ProgressDialog progressDialog;
    private DatabaseReference databaseReference;
    private FirebaseAuth mAuth;
    private Uri imageUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parkingprofile);

        // Initialize UI elements
        profileImageView = findViewById(R.id.profile_image);
        backButton = findViewById(R.id.back_button);
        userIdTextView = findViewById(R.id.user_id);
        phoneNumberTextView = findViewById(R.id.phone_number);
        emailTextView = findViewById(R.id.email_text_view);
        updateProfileBtn = findViewById(R.id.update_profile);
        linkBankAccountLayout = findViewById(R.id.link_bank_account);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        databaseReference = FirebaseDatabase.getInstance().getReference("users");

        // Setup progress dialog
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Loading profile...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        // Load user data
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            loadUserData(user.getUid());
        } else {
            progressDialog.dismiss();
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            finish();
        }
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        Window window = getWindow();
        window.setStatusBarColor(getColor(android.R.color.white));
        // Setup click listeners
        backButton.setOnClickListener(v -> finish());
        profileImageView.setOnClickListener(v -> selectProfilePicture());
        updateProfileBtn.setOnClickListener(v -> selectProfilePicture());
        linkBankAccountLayout.setOnClickListener(v -> {
            startActivity(new Intent(this, BankActivity.class));
        });

        // Set system UI colors
        setSystemColors();
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
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    private void loadUserData(String firebaseUid) {
        databaseReference.child(firebaseUid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                progressDialog.dismiss();

                if (snapshot.exists()) {
                    String email = snapshot.child("email").getValue(String.class);
                    String phone = snapshot.child("phone").getValue(String.class);
                    String role = snapshot.child("role").getValue(String.class);
                    String profileImage = snapshot.child("profileImage").getValue(String.class);
                    String customUid = snapshot.child("uid").getValue(String.class); // Get the custom UID

                    runOnUiThread(() -> {
                        // Display the custom UID instead of Firebase UID
                        userIdTextView.setText("User ID: " + customUid);
                        phoneNumberTextView.setText("Phone: " + phone);
                        emailTextView.setText("Email: " + email);

                        if (profileImage != null && !profileImage.isEmpty()) {
                            byte[] decodedBytes = Base64.decode(profileImage, Base64.DEFAULT);
                            Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                            profileImageView.setImageBitmap(bitmap);
                        }
                    });
                } else {
                    runOnUiThread(() ->
                            Toast.makeText(ParkingAdminProfileActivity.this,
                                    "User data not found", Toast.LENGTH_SHORT).show());
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressDialog.dismiss();
                runOnUiThread(() -> {
                    Toast.makeText(ParkingAdminProfileActivity.this,
                            "Failed to load profile", Toast.LENGTH_SHORT).show();
                    Log.e("ProfileActivity", "Database error: ", error.toException());
                });
            }
        });
    }

    private void selectProfilePicture() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, "Select Profile Picture"), 1);
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
            progressDialog.setMessage("Uploading profile picture...");
            progressDialog.show();

            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            final Bitmap bitmap = getCircularBitmap(BitmapFactory.decodeStream(inputStream));

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream);
            final String base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT);

            FirebaseUser user = mAuth.getCurrentUser();
            if (user == null) {
                progressDialog.dismiss();
                Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
                return;
            }

            databaseReference.child(user.getUid()).child("profileImage")
                    .setValue(base64Image)
                    .addOnSuccessListener(aVoid -> {
                        progressDialog.dismiss();
                        runOnUiThread(() -> {
                            profileImageView.setImageBitmap(bitmap);
                            Toast.makeText(this, "Profile picture updated", Toast.LENGTH_SHORT).show();
                        });
                    })
                    .addOnFailureListener(e -> {
                        progressDialog.dismiss();
                        runOnUiThread(() ->
                                Toast.makeText(this, "Failed to update: " + e.getMessage(),
                                        Toast.LENGTH_SHORT).show());
                    });

        } catch (Exception e) {
            progressDialog.dismiss();
            runOnUiThread(() ->
                    Toast.makeText(this, "Error processing image", Toast.LENGTH_SHORT).show());
            e.printStackTrace();
        }
    }

    private Bitmap getCircularBitmap(Bitmap bitmap) {
        int size = Math.min(bitmap.getWidth(), bitmap.getHeight());
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(output);
        Paint paint = new Paint();
        Rect rect = new Rect(0, 0, size, size);

        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);

        return output;
    }
}
