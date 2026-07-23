package com.example.bookmyticket.core;

import com.example.bookmyticket.R;
import com.example.bookmyticket.auth.LoginActivity;
import com.example.bookmyticket.roles.parkingadmin.ParkingAdminDashboardActivity;
import com.example.bookmyticket.roles.parkingadmin.ParkingPriceDetailsActivity;
import com.example.bookmyticket.roles.placeadmin.PlaceAdminDashboardActivity;
import com.example.bookmyticket.roles.tourist.TouristPageActivity;
import com.example.bookmyticket.roles.tourist.TouristPriceDetailsActivity;

import android.Manifest;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int SPLASH_MIN_DURATION = 300;
    private static final String KEY_SAVED_STATE = "saved_state";
    private static final int PERMISSION_REQUEST_CODE = 100;

    private FirebaseAuth mAuth;
    private DatabaseReference usersRef;
    private SharedPreferences sharedPrefs;
    private ExecutorService executorService;
    private Handler mainHandler;
    private boolean isActivityDestroyed;
    private boolean hasCheckedUserState;
    private FrameLayout splashContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.SplashTheme);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SplashScreen.installSplashScreen(this).setKeepOnScreenCondition(() -> !hasCheckedUserState);
        }
        super.onCreate(savedInstanceState);

        long startTime = System.nanoTime();
        setContentView(R.layout.activity_splash);
        Log.d(TAG, "setContentView activity_splash duration: " + (System.nanoTime() - startTime) / 1_000_000 + "ms");

        splashContainer = findViewById(R.id.splash_container);
        mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.postDelayed(this::optimizeWindowSettings, 50);

        executorService = Executors.newSingleThreadExecutor();
        isActivityDestroyed = false;
        hasCheckedUserState = savedInstanceState != null && savedInstanceState.getBoolean(KEY_SAVED_STATE, false);

        executorService.execute(this::initializeFirebaseAndPrefs);
        if (!hasCheckedUserState) {
            mainHandler.postDelayed(this::checkUserState, SPLASH_MIN_DURATION);
        }
    }

    private void initializeFirebaseAndPrefs() {
        try {
            KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !keyguardManager.isDeviceSecure()) {
                Log.w(TAG, "No secure lock screen, using unencrypted SharedPreferences");
                sharedPrefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);

            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                MasterKey masterKey = new MasterKey.Builder(this, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build();
                sharedPrefs = EncryptedSharedPreferences.create(
                        this,
                        "UserPrefs",
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                );
                Log.d(TAG, "EncryptedSharedPreferences initialized successfully");
            } else {
                sharedPrefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
                Log.w(TAG, "Using unencrypted SharedPreferences due to API < 23");
            }
            mAuth = FirebaseAuth.getInstance();
            usersRef = FirebaseDatabase.getInstance().getReference("users");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize SharedPrefs: ", e);
            sharedPrefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
            mainHandler.post(() -> Toast.makeText(this, "Storage initialization failed", Toast.LENGTH_SHORT).show());
        }
    }

    private void checkUserState() {
        if (isActivityDestroyed || executorService.isShutdown() || hasCheckedUserState) return;
        hasCheckedUserState = true;
        executorService.execute(() -> {
            try {
                if (!isNetworkAvailable()) {
                    mainHandler.post(this::showNoInternetDialog);
                    return;
                }

                String userRole, userUid, phoneNumber, email;
                boolean isLoggedIn, hasLoggedOut;
                synchronized (sharedPrefs) {
                    userRole = sharedPrefs.getString("userRole", "");
                    userUid = sharedPrefs.getString("userUid", "");
                    phoneNumber = sharedPrefs.getString("phoneNumber", "");
                    email = sharedPrefs.getString("email", null);
                    isLoggedIn = sharedPrefs.getBoolean("isLoggedIn", false);
                    hasLoggedOut = sharedPrefs.getBoolean("hasLoggedOut", false);
                    Log.d(TAG, "Reading SharedPreferences: userRole=" + userRole + ", userUid=" + userUid + ", isLoggedIn=" + isLoggedIn);
                }

                FirebaseUser currentUser = mAuth.getCurrentUser();
                if (!isLoggedIn || hasLoggedOut || currentUser == null || userUid.isEmpty() || !isValidRole(userRole)) {
                    try {
                        synchronized (sharedPrefs) {
                            sharedPrefs.edit().clear().apply();
                        }
                    } catch (SecurityException e) {
                        Log.e(TAG, "Failed to clear preferences, reinitializing: ", e);
                        reinitializeSharedPrefs();
                    }
                    mainHandler.post(this::transitionToMainContent);
                    return;
                }

                verifyUserInDatabase(currentUser, userRole, phoneNumber, email);
            } catch (SecurityException e) {
                Log.e(TAG, "Security error checking user state: ", e);
                reinitializeSharedPrefs();
                mainHandler.post(() -> {
                    Toast.makeText(this, "Session error, please log in again", Toast.LENGTH_SHORT).show();
                    transitionToMainContent();
                });
            } catch (Exception e) {
                Log.e(TAG, "Error checking user state: ", e);
                mainHandler.post(() -> {
                    Toast.makeText(this, "Session error, please log in again", Toast.LENGTH_SHORT).show();
                    transitionToMainContent();
                });
            }
        });
    }

    private void reinitializeSharedPrefs() {
        try {
            getSharedPreferences("UserPrefs", Context.MODE_PRIVATE).edit().clear().apply();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                MasterKey masterKey = new MasterKey.Builder(this, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build();
                sharedPrefs = EncryptedSharedPreferences.create(
                        this,
                        "UserPrefs",
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                );
                Log.d(TAG, "Reinitialized EncryptedSharedPreferences");
            } else {
                sharedPrefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to reinitialize SharedPrefs: ", e);
            sharedPrefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        }
    }

    private boolean isNetworkAvailable() {
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        android.net.NetworkInfo ni = cm.getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }

    private void showNoInternetDialog() {
        if (isActivityDestroyed) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_no_internet, null);
        builder.setView(dialogView).setCancelable(false);
        android.widget.TextView title = dialogView.findViewById(R.id.dialog_title);
        android.widget.TextView message = dialogView.findViewById(R.id.dialog_message);
        android.widget.Button turnOnButton = dialogView.findViewById(R.id.turn_on_button);
        title.setText("No Internet");
        message.setText("Enable Wi-Fi or data.");
        turnOnButton.setText("Turn On");
        AlertDialog dialog = builder.create();
        turnOnButton.setOnClickListener(v -> {
            dialog.dismiss();
            startActivityForResult(new Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS), 1);
        });
        dialog.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1) {
            if (isNetworkAvailable()) {
                hasCheckedUserState = false;
                checkUserState();
            } else {
                showNoInternetDialog();
            }
        }
    }

    private void verifyUserInDatabase(FirebaseUser user, String expectedRole, String phoneNumber, String email) {
        if (isActivityDestroyed) return;
        usersRef.child(user.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isActivityDestroyed) return;
                boolean isNewUser = !snapshot.exists();
                String phone = snapshot.child("phone").getValue(String.class);
                String role = snapshot.child("role").getValue(String.class);
                String dbEmail = snapshot.child("email").getValue(String.class);

                if (snapshot.exists() && role != null && phone != null && isValidRole(role)) {
                    synchronized (sharedPrefs) {
                        SharedPreferences.Editor editor = sharedPrefs.edit();
                        editor.putString("userRole", role)
                                .putString("userUid", user.getUid())
                                .putString("phoneNumber", phone)
                                .putBoolean("isLoggedIn", true)
                                .putBoolean("hasLoggedOut", false);
                        if (dbEmail != null) {
                            editor.putString("email", dbEmail);
                        }
                        editor.apply();
                    }
                    redirectToDashboard(role, user.getUid(), phone, dbEmail, isNewUser);
                } else if (!expectedRole.isEmpty() && isValidRole(expectedRole)) {
                    redirectToDashboard(expectedRole, user.getUid(), phoneNumber, email, true);
                } else {
                    synchronized (sharedPrefs) {
                        sharedPrefs.edit().clear().apply();
                    }
                    mainHandler.post(() -> transitionToMainContent());
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (isActivityDestroyed) return;
                Log.e(TAG, "Database error: " + error.getMessage());
                mainHandler.post(() -> {
                    Toast.makeText(MainActivity.this, "Database error, please try again", Toast.LENGTH_SHORT).show();
                    transitionToMainContent();
                });
            }
        });
    }

    private boolean isValidRole(String role) {
        return "placeAdmin".equals(role) || "parkingAdmin".equals(role) || "tourist".equals(role);
    }

    private void transitionToMainContent() {
        if (isActivityDestroyed) return;
        long startTime = System.nanoTime();
        setContentView(R.layout.activity_main);
        Log.d(TAG, "setContentView activity_main duration: " + (System.nanoTime() - startTime) / 1_000_000 + "ms");
        MaterialButton continueButton = findViewById(R.id.continueButton);
        if (continueButton != null) {
            continueButton.setOnClickListener(v -> {
                v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(50).withEndAction(() ->
                        v.animate().scaleX(1f).scaleY(1f).setDuration(50).withEndAction(() -> {
                            if (!isActivityDestroyed) requestAllPermissions();
                        }).start()).start();
            });
        }
        if (splashContainer != null) splashContainer.setVisibility(View.GONE);
    }

    private void requestAllPermissions() {
        ArrayList<String> permissionsNeeded = new ArrayList<>();

        // Camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.CAMERA);
        }

        // Media permissions (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_VIDEO);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_AUDIO);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            // Storage permissions (Android 12 and below)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsNeeded.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE);
        } else {
            redirectToLogin();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            // Proceed to LoginActivity regardless of permission status
            redirectToLogin();
        }
    }

    private void redirectToLogin() {
        if (isActivityDestroyed) return;
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void redirectToDashboard(String role, String userUid, String phoneNumber, String email, boolean isNewUser) {
        if (isActivityDestroyed || userUid == null || userUid.isEmpty() || phoneNumber == null || phoneNumber.isEmpty()) {
            Log.e(TAG, "Invalid navigation data: userUid=" + userUid + ", phoneNumber=" + phoneNumber);
            mainHandler.post(() -> transitionToMainContent());
            return;
        }

        Intent intent = new Intent(this, getDashboardClass(role, isNewUser));
        intent.putExtra("USER_UID", userUid);
        intent.putExtra("phoneNumber", phoneNumber);
        intent.putExtra("isLoggedIn", true);
        intent.putExtra("isNewUser", isNewUser);
        intent.putExtra("adminType", role);
        if (email != null) {
            intent.putExtra("email", email);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        Log.d(TAG, "Navigating to: " + intent.getComponent().getClassName() +
                ", role=" + role +
                ", userUid=" + userUid +
                ", phoneNumber=" + phoneNumber +
                ", isLoggedIn=true" +
                ", isNewUser=" + isNewUser +
                ", email=" + email);
        startActivity(intent);
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private Class<?> getDashboardClass(String role, boolean isNewUser) {
        if (role == null || !isValidRole(role)) return LoginActivity.class;
        if (isNewUser) {
            switch (role) {
                case "placeAdmin": return TouristPriceDetailsActivity.class;
                case "parkingAdmin": return ParkingPriceDetailsActivity.class;
                case "tourist": return TouristPageActivity.class;
                default: return LoginActivity.class;
            }
        } else {
            switch (role) {
                case "placeAdmin": return PlaceAdminDashboardActivity.class;
                case "parkingAdmin": return ParkingAdminDashboardActivity.class;
                case "tourist": return TouristPageActivity.class;
                default: return LoginActivity.class;
            }
        }
    }

    private void optimizeWindowSettings() {
        if (isActivityDestroyed) return;
        Window window = getWindow();
        window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        window.setStatusBarColor(Color.parseColor("#161B22"));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_SAVED_STATE, hasCheckedUserState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!hasCheckedUserState) {
            checkUserState();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isActivityDestroyed = true;
        mainHandler.removeCallbacksAndMessages(null);
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}
