package com.example.bookmyticket.roles.parkingadmin;

import com.example.bookmyticket.R;
import com.example.bookmyticket.auth.LoginActivity;
import com.example.bookmyticket.features.reports.ReportsActivity;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.example.bookmyticket.databinding.ActivityParkingAdminDashboardBinding;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ParkingAdminDashboardActivity extends AppCompatActivity {
    private static final String TAG = "ParkingAdminDashboard";
    private ActivityParkingAdminDashboardBinding binding;
    private FirebaseAuth mAuth;
    private SharedPreferences sharedPreferences;
    private Handler handler;
    private Vibrator vibrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        binding = ActivityParkingAdminDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Set status and navigation bar colors
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.WHITE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.getInsetsController().setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            );
            window.setDecorFitsSystemWindows(false);
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            );
        }

        // Hide ActionBar
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Initialize FirebaseAuth, SharedPreferences, and Vibrator
        mAuth = FirebaseAuth.getInstance();
        handler = new Handler(Looper.getMainLooper());
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        try {
            MasterKey masterKey = new MasterKey.Builder(this)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            sharedPreferences = EncryptedSharedPreferences.create(
                    this, "UserPrefs", masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize EncryptedSharedPreferences: " + e.getMessage());
            sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        }

        // Check if user is logged in
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Log.w(TAG, "No authenticated user found, redirecting to LoginActivity");
            redirectToLogin();
            return;
        }
        Log.d(TAG, "Authenticated user: UID=" + user.getUid());

        // Start shimmers
        binding.welcomeTextShimmer.startShimmer();
        binding.parkingAreaShimmer.startShimmer();
        binding.quickStatsShimmer.startShimmer();
        binding.activityCardShimmer.startShimmer();

        // Load cached data immediately
        loadCachedData();

        // Set click listeners
        setClickListeners();

        // Setup scroll listener for compact header bar
        setupScrollListener();

        // Apply enter animation
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);

        // Load Firebase data asynchronously
        new LoadDataTask().execute(user.getUid());
    }

    private void loadCachedData() {
        String cachedName = sharedPreferences.getString("userName", "User");
        String cachedParkingArea = sharedPreferences.getString("parkingArea", "Not Available");
        long cachedVehicles = sharedPreferences.getLong("vehicles", 0);
        long cachedRevenue = sharedPreferences.getLong("parkingRevenue", 0);
        long cachedBookings = sharedPreferences.getLong("activeBookings", 0);

        binding.welcomeText.setText(cachedName);
        binding.parkingArea.setText("Parking Area: " + cachedParkingArea);
        binding.vehicleCount.setText(String.valueOf(cachedVehicles));
        binding.parkingRevenueCount.setText("₹" + cachedRevenue);
        binding.activeBookings.setText(String.valueOf(cachedBookings));

        // Keep shimmers visible until Firebase data is loaded
        binding.welcomeTextShimmer.setVisibility(View.VISIBLE);
        binding.parkingAreaShimmer.setVisibility(View.VISIBLE);
        binding.quickStatsShimmer.setVisibility(View.VISIBLE);
        binding.activityCardShimmer.setVisibility(View.VISIBLE);
        binding.welcomeText.setVisibility(View.GONE);
        binding.parkingArea.setVisibility(View.GONE);
        binding.quickStats.setVisibility(View.GONE);
        binding.activityCard.setVisibility(View.GONE);

        Log.d(TAG, "Loaded cached data");
    }

    private void setClickListeners() {
        binding.profileIcon.setOnClickListener(v -> {
            animateButtonClick(v);
            openProfilePage();
        });

        binding.settingsIcon.setOnClickListener(v -> {
            animateButtonClick(v);
            startActivity(new Intent(this, ParkingSettingsActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        binding.compactProfileIcon.setOnClickListener(v -> {
            animateButtonClick(v);
            openProfilePage();
        });

        binding.compactSettingsIcon.setOnClickListener(v -> {
            animateButtonClick(v);
            startActivity(new Intent(this, ParkingSettingsActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        binding.home.setOnClickListener(v -> {
            animateButtonClick(v);
            Intent intent = new Intent(this, ParkingAdminDashboardActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });

        binding.reports.setOnClickListener(v -> {
            animateButtonClick(v);
            startActivity(new Intent(this, ReportsActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        binding.navSettings.setOnClickListener(v -> {
            animateButtonClick(v);
            startActivity(new Intent(this, ParkingSettingsActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        binding.logout.setOnClickListener(v -> {
            animateButtonClick(v);
            logout();
        });

        binding.addBankAccountButton.setOnClickListener(v -> {
            animateButtonClick(v);
            Intent setupIntent = new Intent(this, ParkingAdminSetupActivity.class);
            setupIntent.putExtra("adminType", "parkingAdmin");
            setupIntent.putExtra("phoneNumber", getIntent().getStringExtra("phoneNumber"));
            setupIntent.putExtra("USER_UID", getIntent().getStringExtra("USER_UID"));
            startActivity(setupIntent);
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        binding.scanCard.setOnClickListener(v -> {
            animateButtonClick(v);
            if (binding.addBankAccountCard.getVisibility() == View.VISIBLE) {
                if (vibrator != null && vibrator.hasVibrator()) {
                    vibrator.vibrate(200);
                }
                Toast.makeText(this, "Add bank account first to unlock scanning", Toast.LENGTH_SHORT).show();
                animateShineEffect();
            } else {
                startActivity(new Intent(this, ScanVehicleActivity.class));
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            }
        });

        binding.scanVehicleButton.setOnClickListener(v -> {
            if (binding.addBankAccountCard.getVisibility() == View.VISIBLE) {
                if (vibrator != null && vibrator.hasVibrator()) {
                    vibrator.vibrate(200);
                }
                Toast.makeText(this, "Add bank account first to unlock scanning", Toast.LENGTH_SHORT).show();
                animateShineEffect();
            } else if (binding.scanVehicleButton.isAnimating()) {
                binding.scanVehicleButton.pauseAnimation();
            } else {
                binding.scanVehicleButton.playAnimation();
            }
        });

        binding.manualEntry.setOnClickListener(v -> {
            animateButtonClick(v);
            startActivity(new Intent(this, ManualEntryActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        binding.viewAllButton.setOnClickListener(v -> {
            animateButtonClick(v);
            startActivity(new Intent(this, ReportsActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });
    }

    private void checkUserAndBankAccountStatus(String userId) {
        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users").child(userId);
        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    runOnUiThread(() -> {
                        binding.addBankAccountCard.setVisibility(View.VISIBLE);
                        Log.d(TAG, "No user node found, showing bank account placeholder");
                    });
                } else {
                    DatabaseReference parkingAdminsRef = FirebaseDatabase.getInstance().getReference("parkingadmins").child(userId);
                    parkingAdminsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            runOnUiThread(() -> {
                                if (snapshot.exists() && snapshot.hasChild("fundAccountId")) {
                                    binding.addBankAccountCard.setVisibility(View.GONE);
                                    Log.d(TAG, "Bank account exists, hiding placeholder");
                                } else {
                                    binding.addBankAccountCard.setVisibility(View.VISIBLE);
                                    Log.d(TAG, "No bank account found, showing placeholder");
                                }
                            });
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Log.e(TAG, "Failed to check bank account status: " + error.getMessage());
                            runOnUiThread(() -> binding.addBankAccountCard.setVisibility(View.VISIBLE));
                        }
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to check user node: " + error.getMessage());
                runOnUiThread(() -> binding.addBankAccountCard.setVisibility(View.VISIBLE));
            }
        });
    }

    private void setupScrollListener() {
        binding.compactHeaderBar.setAlpha(0f);
        binding.compactHeaderBar.setTranslationY(-10f);
        binding.compactHeaderBar.setVisibility(View.VISIBLE);

        final int scrollThreshold = 60;
        final int headerColor = Color.parseColor("#1C333C");
        final int transparentColor = Color.TRANSPARENT;

        binding.scrollView.setOnScrollChangeListener(new NestedScrollView.OnScrollChangeListener() {
            @Override
            public void onScrollChange(NestedScrollView v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                float progress = Math.min(scrollY / (float) scrollThreshold, 1f);
                binding.compactHeaderBar.setAlpha(progress);
                binding.compactHeaderBar.setTranslationY(-10f * (1f - progress));
                int interpolatedColor = interpolateColor(headerColor, progress);
                getWindow().setStatusBarColor(interpolatedColor);
            }

            private int interpolateColor(int endColor, float fraction) {
                int startA = Color.alpha(transparentColor);
                int startR = Color.red(transparentColor);
                int startG = Color.green(transparentColor);
                int startB = Color.blue(transparentColor);

                int endA = Color.alpha(endColor);
                int endR = Color.red(endColor);
                int endG = Color.green(endColor);
                int endB = Color.blue(endColor);

                int a = Math.round(startA + (endA - startA) * fraction);
                int r = Math.round(startR + (endR - startR) * fraction);
                int g = Math.round(startG + (endG - startG) * fraction);
                int b = Math.round(startB + (endB - startB) * fraction);

                return Color.argb(a, r, g, b);
            }
        });
    }

    private void redirectToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
        Log.d(TAG, "Redirected to LoginActivity");
    }

    private void animateShineEffect() {
        ObjectAnimator shine = ObjectAnimator.ofFloat(binding.addBankAccountCard, "alpha", 1f, 0.5f, 1f);
        shine.setDuration(1000);
        shine.start();
    }

    private void logout() {
        try {
            MasterKey masterKey = new MasterKey.Builder(this)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            SharedPreferences sharedPreferences = EncryptedSharedPreferences.create(
                    this, "UserPrefs", masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            sharedPreferences.edit().clear().apply();
            Log.d(TAG, "SharedPreferences cleared");
        } catch (Exception e) {
            Log.e(TAG, "Failed to clear SharedPreferences: " + e.getMessage());
        }

        mAuth.signOut();
        Log.d(TAG, "Firebase Auth signed out");
        redirectToLogin();
    }

    private void animateButtonClick(View view) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1.0f, 0.95f, 1.0f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1.0f, 0.95f, 1.0f);
        scaleX.setDuration(200);
        scaleY.setDuration(200);
        scaleX.start();
        scaleY.start();
    }

    private void animateQuickStats() {
        for (int i = 0; i < binding.quickStats.getChildCount(); i++) {
            View card = binding.quickStats.getChildAt(i);
            card.setScaleX(0.8f);
            card.setScaleY(0.8f);
            card.setAlpha(0f);
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(card, "scaleX", 0.8f, 1.0f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(card, "scaleY", 0.8f, 1.0f);
            ObjectAnimator fadeIn = ObjectAnimator.ofFloat(card, "alpha", 0f, 1f);
            scaleX.setDuration(300);
            scaleY.setDuration(300);
            fadeIn.setDuration(300);
            scaleX.setStartDelay(i * 100L);
            scaleY.setStartDelay(i * 100L);
            fadeIn.setStartDelay(i * 100L);
            scaleX.start();
            scaleY.start();
            fadeIn.start();
        }
    }

    private void openProfilePage() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            Intent intent = new Intent(this, ParkingAdminProfileActivity.class);
            intent.putExtra("uid", currentUser.getUid());
            intent.putExtra("phoneNumber", sharedPreferences.getString("phoneNumber", "Not Available"));
            startActivity(intent);
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        } else {
            Log.w(TAG, "No authenticated user for profile page, redirecting to LoginActivity");
            redirectToLogin();
        }
    }

    private void loadUserData(String userId) {
        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users").child(userId);
        DatabaseReference parkingAdminsRef = FirebaseDatabase.getInstance().getReference("parkingadmins").child(userId);

        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String name = snapshot.child("name").getValue(String.class);
                    runOnUiThread(() -> {
                        if (name != null) {
                            binding.welcomeText.setText(name);
                            sharedPreferences.edit().putString("userName", name).apply();
                            Log.d(TAG, "User name loaded: " + name);
                        } else {
                            binding.welcomeText.setText("User");
                            Log.w(TAG, "No name found for user: " + userId);
                        }
                        binding.welcomeTextShimmer.stopShimmer();
                        binding.welcomeTextShimmer.setVisibility(View.GONE);
                        binding.welcomeText.setVisibility(View.VISIBLE);
                    });
                } else {
                    runOnUiThread(() -> {
                        binding.welcomeText.setText("User");
                        binding.welcomeTextShimmer.stopShimmer();
                        binding.welcomeTextShimmer.setVisibility(View.GONE);
                        binding.welcomeText.setVisibility(View.VISIBLE);
                        Log.w(TAG, "No user data found for UID: " + userId);
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to fetch user data: " + error.getMessage());
                runOnUiThread(() -> {
                    binding.welcomeText.setText("User");
                    binding.welcomeTextShimmer.stopShimmer();
                    binding.welcomeTextShimmer.setVisibility(View.GONE);
                    binding.welcomeText.setVisibility(View.VISIBLE);
                });
            }
        });

        parkingAdminsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String area = snapshot.child("parkingArea").getValue(String.class);
                    runOnUiThread(() -> {
                        if (area != null) {
                            binding.parkingArea.setText("Parking Area: " + area);
                            sharedPreferences.edit().putString("parkingArea", area).apply();
                            Log.d(TAG, "Parking area loaded: " + area);
                        } else {
                            binding.parkingArea.setText("Parking Area: Not Available");
                            Log.w(TAG, "No parking area found for admin: " + userId);
                        }
                        binding.parkingAreaShimmer.stopShimmer();
                        binding.parkingAreaShimmer.setVisibility(View.GONE);
                        binding.parkingArea.setVisibility(View.VISIBLE);
                    });
                } else {
                    runOnUiThread(() -> {
                        binding.parkingArea.setText("Parking Area: Not Available");
                        binding.parkingAreaShimmer.stopShimmer();
                        binding.parkingAreaShimmer.setVisibility(View.GONE);
                        binding.parkingArea.setVisibility(View.VISIBLE);
                        Log.w(TAG, "No parking admin data found for UID: " + userId);
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to fetch parking admin data: " + error.getMessage());
                runOnUiThread(() -> {
                    binding.parkingArea.setText("Parking Area: Not Available");
                    binding.parkingAreaShimmer.stopShimmer();
                    binding.parkingAreaShimmer.setVisibility(View.GONE);
                    binding.parkingArea.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void updateRealTimeData() {
        String currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        DatabaseReference realTimeRef = FirebaseDatabase.getInstance().getReference("realTimeData").child(currentDate);
        realTimeRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Long vehicles = snapshot.child("vehicles").getValue(Long.class);
                    Long parkingRevenue = snapshot.child("parkingRevenue").getValue(Long.class);
                    Long activeBookingsValue = snapshot.child("activeBookings").getValue(Long.class);

                    runOnUiThread(() -> {
                        binding.vehicleCount.setText(String.valueOf(vehicles != null ? vehicles : 0));
                        binding.parkingRevenueCount.setText("₹" + (parkingRevenue != null ? parkingRevenue : 0));
                        binding.activeBookings.setText(String.valueOf(activeBookingsValue != null ? activeBookingsValue : 0));

                        sharedPreferences.edit()
                                .putLong("vehicles", vehicles != null ? vehicles : 0)
                                .putLong("parkingRevenue", parkingRevenue != null ? parkingRevenue : 0)
                                .putLong("activeBookings", activeBookingsValue != null ? activeBookingsValue : 0)
                                .apply();

                        Log.d(TAG, "Real-time data updated for " + currentDate + ": vehicles=" + vehicles + ", revenue=" + parkingRevenue + ", bookings=" + activeBookingsValue);

                        binding.quickStatsShimmer.stopShimmer();
                        binding.activityCardShimmer.stopShimmer();
                        binding.quickStatsShimmer.setVisibility(View.GONE);
                        binding.activityCardShimmer.setVisibility(View.GONE);
                        binding.quickStats.setVisibility(View.VISIBLE);
                        binding.activityCard.setVisibility(View.VISIBLE);

                        animateQuickStats();
                    });
                } else {
                    runOnUiThread(() -> {
                        binding.vehicleCount.setText("0");
                        binding.parkingRevenueCount.setText("₹0");
                        binding.activeBookings.setText("0");
                        sharedPreferences.edit()
                                .putLong("vehicles", 0)
                                .putLong("parkingRevenue", 0)
                                .putLong("activeBookings", 0)
                                .apply();
                        binding.quickStatsShimmer.stopShimmer();
                        binding.activityCardShimmer.stopShimmer();
                        binding.quickStatsShimmer.setVisibility(View.GONE);
                        binding.activityCardShimmer.setVisibility(View.GONE);
                        binding.quickStats.setVisibility(View.VISIBLE);
                        binding.activityCard.setVisibility(View.VISIBLE);
                        Log.w(TAG, "No real-time data found for " + currentDate + ", initialized with zeros");
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to fetch real-time data: " + error.getMessage());
                runOnUiThread(() -> {
                    binding.quickStatsShimmer.stopShimmer();
                    binding.activityCardShimmer.setVisibility(View.GONE);
                    binding.quickStatsShimmer.setVisibility(View.GONE);
                    binding.activityCardShimmer.setVisibility(View.GONE);
                    binding.quickStats.setVisibility(View.VISIBLE);
                    binding.activityCard.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private class LoadDataTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... params) {
            String userId = params[0];
            checkUserAndBankAccountStatus(userId);
            loadUserData(userId);
            updateRealTimeData();
            return null;
        }
    }
}
