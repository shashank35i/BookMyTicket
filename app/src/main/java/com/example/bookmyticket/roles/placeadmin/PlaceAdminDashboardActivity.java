package com.example.bookmyticket.roles.placeadmin;

import com.example.bookmyticket.R;
import com.example.bookmyticket.auth.LoginActivity;
import com.example.bookmyticket.features.payment.BankActivity;
import com.example.bookmyticket.features.reports.ReportsActivity;
import com.example.bookmyticket.features.settings.SettingsActivity;
import com.example.bookmyticket.features.tickets.TicketHistoryActivity;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.Manifest;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.io.FileOutputStream;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PlaceAdminDashboardActivity extends AppCompatActivity {

    private static final String TAG = "PlaceAdminDashboard";
    private static final long DATABASE_TIMEOUT = 8000;
    private TextView todayVisitorsCount, todayRevenueCount;
    private CardView scanQrCard, generateQrCard, ticketHistoryCard, reportsCard, addBankAccountCard;
    private RecyclerView activeQrList;
    private LinearLayout navLogout, emptyStateContainer;
    private ImageView emptyStateImage, closeBankAccountCard;
    private TextView emptyStateText, addBankAccountText;
    private View loadingIndicator;
    private FirebaseAuth mAuth;
    private SharedPreferences sharedPreferences;
    private DatabaseReference payoutsRef, qrHistoryRef, usersRef;
    private ValueEventListener payoutsListener, qrHistoryListener, userListener;
    private boolean isLoggingOut = false;
    private boolean isActivityDestroyed = false;
    private ShimmerFrameLayout shimmerLayout;
    private LinearLayout placeholderLayout;
    private ScrollView mainContent;
    private String userUid, phoneNumber, email;
    private boolean isLoggedIn, isNewUser;
    private Handler handler;
    private LinearLayout settingsIcon;
    private MaterialButton addBankAccountButton;
    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
    private SimpleDateFormat displayFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
    private SimpleDateFormat fileDateFormat = new SimpleDateFormat("dd_MM_yyyy", Locale.getDefault());
    private final DatabaseReference notificationsRef = FirebaseDatabase.getInstance().getReference("placeadmin_notifications");
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_adminpage);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        // Set status bar color
        setupPremiumUI();

        // Initialize handler
        handler = new Handler(Looper.getMainLooper());

        // Initialize Firebase and SharedPreferences
        mAuth = FirebaseAuth.getInstance();
        try {
            MasterKey masterKey = new MasterKey.Builder(this)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            sharedPreferences = EncryptedSharedPreferences.create(
                    this, "UserPrefs", masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            Log.d(TAG, "Initialized EncryptedSharedPreferences");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize EncryptedSharedPreferences: " + e.getMessage());
            sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);
            Log.d(TAG, "Fallback to regular SharedPreferences");
        }

        // Retrieve intent extras or fallback to SharedPreferences
        Intent intent = getIntent();
        userUid = intent.getStringExtra("USER_UID");
        phoneNumber = intent.getStringExtra("phoneNumber");
        email = intent.getStringExtra("email");
        isLoggedIn = intent.getBooleanExtra("isLoggedIn", sharedPreferences.getBoolean("isLoggedIn", false));
        isNewUser = intent.getBooleanExtra("isNewUser", sharedPreferences.getBoolean("isNewUser", false));
        String adminType = intent.getStringExtra("adminType") != null ? intent.getStringExtra("adminType") : sharedPreferences.getString("adminType", null);

        // Fallback to SharedPreferences if intent extras are missing
        if (userUid == null) userUid = sharedPreferences.getString("userUid", null);
        if (phoneNumber == null) phoneNumber = sharedPreferences.getString("phoneNumber", null);
        if (adminType == null) adminType = sharedPreferences.getString("adminType", null);

        // Validate intent extras
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null || userUid == null || phoneNumber == null || !isLoggedIn || adminType == null) {
            Log.e(TAG, "Invalid intent/SharedPreferences extras: userUid=" + userUid + ", phoneNumber=" + phoneNumber +
                    ", isLoggedIn=" + isLoggedIn + ", adminType=" + adminType);
            showToast("Invalid login data", false);
            logoutUser();
            return;
        }

        // Validate adminType
        if (!"placeAdmin".equals(adminType)) {
            Log.w(TAG, "Invalid adminType: " + adminType);
            showToast("Invalid user role", false);
            logoutUser();
            return;
        }

        // Fallback to Firebase UID if intent UID is invalid
        if (!userUid.equals(currentUser.getUid())) {
            Log.w(TAG, "UID mismatch: intentUid=" + userUid + ", firebaseUid=" + currentUser.getUid() + "; using Firebase UID");
            userUid = currentUser.getUid();
        }

        // Save intent extras to SharedPreferences
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("userUid", userUid);
        editor.putString("phoneNumber", phoneNumber);
        editor.putString("email", email != null ? email : "");
        editor.putBoolean("isLoggedIn", isLoggedIn);
        editor.putBoolean("isNewUser", isNewUser);
        editor.putString("adminType", adminType);
        editor.putString("userRole", "placeAdmin");
        editor.putBoolean("hasLoggedOut", false);
        editor.apply();
        Log.d(TAG, "Saved intent extras to SharedPreferences");

        // Initialize Firebase references
        usersRef = FirebaseDatabase.getInstance().getReference("users").child(userUid);

        // Initialize views
        initViews();

        // Log mainContent initialization
        Log.d(TAG, "mainContent initialized: " + (mainContent != null ? "not null" : "null"));

        // Set up click listeners immediately
        setupClickListeners();

        // Control Add Bank Account placeholder visibility
        boolean hasDismissedBankPrompt = sharedPreferences.getBoolean("hasDismissedBankPrompt", false);
        boolean isUserNodeCreated = sharedPreferences.getBoolean("isUserNodeCreated", false);
        if (isNewUser && !hasDismissedBankPrompt && !isUserNodeCreated) {
            addBankAccountCard.setVisibility(View.VISIBLE);
            Log.d(TAG, "Showing Add Bank Account placeholder for new user");
        } else {
            addBankAccountCard.setVisibility(View.GONE);
            Log.d(TAG, "Hiding Add Bank Account placeholder");
        }

        // Set up close button for Add Bank Account placeholder
        closeBankAccountCard.setOnClickListener(v -> {
            addBankAccountCard.setVisibility(View.GONE);
            SharedPreferences.Editor editor1 = sharedPreferences.edit();
            editor1.putBoolean("hasDismissedBankPrompt", true);
            editor1.apply();
            Log.d(TAG, "Add Bank Account placeholder closed and dismissal saved");
        });

        // Set up Add button for Add Bank Account placeholder
        String finalAdminType = adminType;
        addBankAccountButton.setOnClickListener(v -> {
            Intent bankIntent = new Intent(this, BankActivity.class);
            bankIntent.putExtra("USER_UID", userUid);
            bankIntent.putExtra("phoneNumber", phoneNumber);
            bankIntent.putExtra("email", email);
            bankIntent.putExtra("isLoggedIn", isLoggedIn);
            bankIntent.putExtra("isNewUser", isNewUser);
            bankIntent.putExtra("adminType", finalAdminType);
            startActivity(bankIntent);
            Log.d(TAG, "Navigating to BankActivity with intent extras");
        });

        // Start shimmer effect
        shimmerLayout.startShimmer();

        // Simulate loading delay
        handler.postDelayed(() -> {
            if (isActivityDestroyed || isFinishing()) {
                Log.w(TAG, "Activity is destroyed or finishing; skipping delayed task");
                return;
            }

            // Stop shimmer and show content
            shimmerLayout.stopShimmer();
            shimmerLayout.setVisibility(View.GONE);

            if (mainContent != null) {
                mainContent.setVisibility(View.VISIBLE);
                Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
                mainContent.startAnimation(fadeIn);
                mainContent.invalidate();
                mainContent.requestLayout();
            } else {
                Log.e(TAG, "mainContent is null; cannot set visibility or animate");
                showToast("Error loading dashboard", false);
                logoutUser();
                return;
            }

            // Proceed with Firebase-related logic
            verifyUserRole();
        }, 500);
    }



    private void initViews() {
        todayVisitorsCount = findViewById(R.id.today_visitors_count);
        todayRevenueCount = findViewById(R.id.today_revenue_count);
        scanQrCard = findViewById(R.id.scan_qr_card);
        generateQrCard = findViewById(R.id.generate_qr_card);
        ticketHistoryCard = findViewById(R.id.ticket_history_card);
        reportsCard = findViewById(R.id.reports_card);
        addBankAccountCard = findViewById(R.id.add_bank_account_card);
        activeQrList = findViewById(R.id.recent_activity_list);
        emptyStateContainer = findViewById(R.id.empty_state_container);
        emptyStateImage = findViewById(R.id.empty_state_image);
        emptyStateText = findViewById(R.id.empty_state_text);
        loadingIndicator = findViewById(R.id.loading_indicator);
        shimmerLayout = findViewById(R.id.shimmer_layout);
        placeholderLayout = findViewById(R.id.placeholder_layout);
        closeBankAccountCard = findViewById(R.id.close_bank_account_card);
        addBankAccountText = findViewById(R.id.add_bank_account_text);
        mainContent = findViewById(R.id.main_content);
        addBankAccountButton = findViewById(R.id.add_bank_account_button);
        settingsIcon = findViewById(R.id.settings_icon); // Add this line

        // Optimize RecyclerView
        activeQrList.setLayoutManager(new LinearLayoutManager(this));
        activeQrList.setHasFixedSize(true);
    }

    private void verifyUserRole() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "No authenticated user; firebaseUid=null");
            showToast("User authentication failed", false);
            logoutUser();
            return;
        }

        if (!userUid.equals(currentUser.getUid())) {
            Log.w(TAG, "UID mismatch: intentUid=" + userUid + ", firebaseUid=" + currentUser.getUid() + "; proceeding with Firebase UID");
            userUid = currentUser.getUid();
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("userUid", userUid);
            editor.apply();
        }

        String cachedRole = sharedPreferences.getString("userRole", "");
        if ("placeAdmin".equals(cachedRole)) {
            // Proceed if cached role is valid
            Log.d(TAG, "Cached role verified: placeAdmin");
            checkBankDetails();
            return;
        }

        // Timeout for Firebase query
        Runnable timeoutRunnable = () -> {
            if (isActivityDestroyed) return;
            showToast("Database operation timed out", false);
            addBankAccountCard.setVisibility(View.VISIBLE);
            setupClickListeners();
            setupFirebase();
            loadDashboardStats();
            loadActiveQRCodes();
        };
        handler.postDelayed(timeoutRunnable, DATABASE_TIMEOUT);

        userListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isActivityDestroyed) return;
                handler.removeCallbacks(timeoutRunnable);
                if (snapshot.exists()) {
                    String actualRole = snapshot.child("role").getValue(String.class);
                    String dbPhone = snapshot.child("phone").getValue(String.class);
                    String dbEmail = snapshot.child("email").getValue(String.class);
                    if (!"placeAdmin".equals(actualRole)) {
                        Log.w(TAG, "Role mismatch: Firebase role=" + actualRole + ", expected=placeAdmin");
                        showToast("Invalid user role detected", false);
                        clearAuthData();
                        logoutUser();
                        return;
                    }
                    if (dbPhone != null && !dbPhone.equals(phoneNumber)) {
                        Log.w(TAG, "Phone mismatch: Firebase phone=" + dbPhone + ", expected=" + phoneNumber);
                        phoneNumber = dbPhone; // Update phoneNumber to avoid future mismatches
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString("phoneNumber", dbPhone);
                        editor.apply();
                    }
                    Log.d(TAG, "Role verified: placeAdmin");
                    // Update SharedPreferences with Firebase data
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString("phoneNumber", dbPhone);
                    editor.putString("email", dbEmail != null ? dbEmail : "");
                    editor.putBoolean("isUserNodeCreated", true);
                    editor.putBoolean("isNewUser", false);
                    editor.putString("userRole", "placeAdmin");
                    editor.apply();
                    checkBankDetails();
                } else {
                    Log.d(TAG, "No user data found for UID=" + userUid + ", isNewUser=" + isNewUser);
                    showToast("Please add user details to continue", false);
                    addBankAccountCard.setVisibility(View.VISIBLE);
                    if (isNewUser) {
                        showNewUserSetupPrompt();
                    }
                    setupFirebase();
                    loadDashboardStats();
                    loadActiveQRCodes();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (isActivityDestroyed) return;
                handler.removeCallbacks(timeoutRunnable);
                Log.e(TAG, "Failed to verify role: " + error.getMessage());
                showToast("Error accessing user data: " + error.getMessage(), false);
                addBankAccountCard.setVisibility(View.VISIBLE);
                setupClickListeners();
                setupFirebase();
                loadDashboardStats();
                loadActiveQRCodes();
            }
        };
        usersRef.addListenerForSingleValueEvent(userListener);
    }

    private void checkBankDetails() {
        DatabaseReference placeAdminsRef = FirebaseDatabase.getInstance().getReference("placeadmin").child(userUid);
        Runnable timeoutRunnable = () -> {
            if (isActivityDestroyed) return;
            showToast("Failed to fetch bank details", false);
            addBankAccountCard.setVisibility(View.VISIBLE);
            setupClickListeners();
            setupFirebase();
            loadDashboardStats();
            loadActiveQRCodes();
        };
        handler.postDelayed(timeoutRunnable, DATABASE_TIMEOUT);

        placeAdminsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isActivityDestroyed) return;
                handler.removeCallbacks(timeoutRunnable);
                if (snapshot.exists()) {
                    addBankAccountCard.setVisibility(View.GONE);
                    Log.d(TAG, "Bank details found for UID=" + userUid);
                    setupFirebase();
                    loadDashboardStats();
                    loadActiveQRCodes();
                } else {
                    Log.d(TAG, "No bank details found for UID=" + userUid);
                    showNewUserSetupPrompt();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (isActivityDestroyed) return;
                handler.removeCallbacks(timeoutRunnable);
                Log.e(TAG, "Failed to fetch bank details: " + error.getMessage());
                showToast("Failed to fetch bank details", false);
                showNewUserSetupPrompt();
            }
        });
    }

    private void showNewUserSetupPrompt() {
        if (isActivityDestroyed) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Complete Your Profile");
        builder.setMessage("Welcome! Please add your bank details to continue.");
        builder.setPositiveButton("Set Up Now", (dialog, which) -> {
            Intent intent = new Intent(this, BankActivity.class);
            intent.putExtra("USER_UID", userUid);
            intent.putExtra("phoneNumber", phoneNumber);
            intent.putExtra("email", email);
            intent.putExtra("isLoggedIn", isLoggedIn);
            intent.putExtra("isNewUser", isNewUser);
            intent.putExtra("adminType", "placeAdmin");
            startActivity(intent);
        });
        builder.setNegativeButton("Later", (dialog, which) -> {
            setupFirebase();
            loadDashboardStats();
            loadActiveQRCodes();
        });
        builder.setCancelable(false);
        builder.show();
    }

    private void setupFirebase() {
        String placeId = userUid;
        payoutsRef = FirebaseDatabase.getInstance().getReference("payouts").child(placeId);
        qrHistoryRef = FirebaseDatabase.getInstance().getReference("qrHistory").child(placeId);
    }

    private void setupClickListeners() {
        View profileIcon = findViewById(R.id.profile_icon);
        if (profileIcon == null) {
            Log.e(TAG, "profile_icon view not found in layout");
        } else {
            profileIcon.setOnClickListener(v -> {
                Log.d(TAG, "Profile icon clicked");
                startActivity(new Intent(this, PlaceAdminProfileActivity.class));
            });
        }

        View notificationIcon = findViewById(R.id.notification_icon);
        if (notificationIcon == null) {
            Log.e(TAG, "notification_icon view not found in layout");
        } else {
            notificationIcon.setOnClickListener(v -> {
                Log.d(TAG, "Notification icon clicked");
                try {
                    startActivity(new Intent(this, PlaceAdminNotificationsActivity.class));
                } catch (Exception e) {
                    Log.e(TAG, "Failed to start PlaceAdminNotificationsActivity", e);
                    showToast("Failed to open notifications: " + e.getMessage(), false);
                }
            });
        }

        if (settingsIcon == null) {
            Log.e(TAG, "settings_icon view not found in layout");
        } else {
            settingsIcon.setOnClickListener(v -> {
                Log.d(TAG, "Settings icon clicked");
                Intent intent = new Intent(this, SettingsActivity.class);
                intent.putExtra("USER_UID", userUid);
                intent.putExtra("phoneNumber", phoneNumber);
                intent.putExtra("email", email);
                intent.putExtra("isLoggedIn", isLoggedIn);
                intent.putExtra("isNewUser", isNewUser);
                intent.putExtra("adminType", "placeAdmin");
                startActivity(intent);
            });
        }

        if (scanQrCard == null) {
            Log.e(TAG, "scan_qr_card view not found in layout");
        } else {
            scanQrCard.setOnClickListener(v -> startActivity(new Intent(this, ScanQRActivity.class)));
        }

        if (generateQrCard == null) {
            Log.e(TAG, "generate_qr_card view not found in layout");
        } else {
            generateQrCard.setOnClickListener(v -> startActivity(new Intent(this, AdminSlide1.class)));
        }

        if (ticketHistoryCard == null) {
            Log.e(TAG, "ticket_history_card view not found in layout");
        } else {
            ticketHistoryCard.setOnClickListener(v -> startActivity(new Intent(this, TicketHistoryActivity.class)));
        }

        if (reportsCard == null) {
            Log.e(TAG, "reports_card view not found in layout");
        } else {
            reportsCard.setOnClickListener(v -> startActivity(new Intent(this, ReportsActivity.class)));
        }

        if (navLogout == null) {
            Log.e(TAG, "nav_logout view not found in layout");
        } else {
            navLogout.setOnClickListener(v -> logoutUser());
        }
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
        showToast("Logged out successfully", true);
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

    private void loadDashboardStats() {
        if (payoutsRef == null) {
            Log.e(TAG, "payoutsRef is null, cannot load dashboard stats");
            return;
        }
        long startOfDay = getStartOfDayTimestamp();

        payoutsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isActivityDestroyed) return;
                int todayVisitors = 0;
                double totalRevenue = 0.0;

                for (DataSnapshot payout : snapshot.getChildren()) {
                    Integer persons = payout.child("totalPersons").getValue(Integer.class);
                    Double baseAmount = payout.child("baseAmount").getValue(Double.class);

                    if (persons != null) {
                        todayVisitors += persons;
                    }
                    if (baseAmount != null) {
                        totalRevenue += baseAmount;
                    }
                }

                // Set visitors count and adjust text size
                String visitorsText = String.valueOf(todayVisitors);
                todayVisitorsCount.setText(visitorsText);
                int visitorDigits = visitorsText.length();
                if (visitorDigits > 6) {
                    todayVisitorsCount.setTextSize(18);
                } else if (visitorDigits > 4) {
                    todayVisitorsCount.setTextSize(20);
                } else {
                    todayVisitorsCount.setTextSize(24);
                }

                // Format revenue and adjust text size
                String formattedRevenue = formatCurrency(totalRevenue);
                todayRevenueCount.setText(formattedRevenue);
                // Count digits in revenue (excluding currency symbol, decimal point, and commas)
                String numericPart = formattedRevenue.replaceAll("[^0-9]", "");
                int revenueDigits = numericPart.length();
                if (revenueDigits > 6) {
                    todayRevenueCount.setTextSize(18);
                } else if (revenueDigits > 4) {
                    todayRevenueCount.setTextSize(20);
                } else {
                    todayRevenueCount.setTextSize(24);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (isActivityDestroyed || isLoggingOut) return;
                showToast("Failed to load dashboard stats: " + error.getMessage(), false);
                Log.e(TAG, "Failed to load dashboard stats: " + error.getMessage());
            }
        };

        payoutsRef.orderByChild("timestamp").startAt(startOfDay).addValueEventListener(payoutsListener);
    }

    private void loadActiveQRCodes() {
        if (qrHistoryRef == null) {
            Log.e(TAG, "qrHistoryRef is null, cannot load active QR codes");
            return;
        }

        activeQrList.setVisibility(View.GONE);
        emptyStateContainer.setVisibility(View.GONE);
        loadingIndicator.setVisibility(View.VISIBLE);

        qrHistoryListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isActivityDestroyed) return;
                List<ActiveQRCode> activeQRCodes = new ArrayList<>();
                Log.d(TAG, "Total QR snapshots received: " + snapshot.getChildrenCount());

                for (DataSnapshot qrSnapshot : snapshot.getChildren()) {
                    ActiveQRCode qrCode = new ActiveQRCode();
                    qrCode.setId(qrSnapshot.getKey());
                    qrCode.setValidFrom(qrSnapshot.child("validFrom").getValue(String.class));
                    qrCode.setValidTo(qrSnapshot.child("validTo").getValue(String.class));
                    qrCode.setInstructions(qrSnapshot.child("instructions").getValue(String.class));
                    qrCode.setHelpline(qrSnapshot.child("helpline").getValue(String.class));
                    qrCode.setPriceAdults(getStringValue(qrSnapshot.child("priceAdults")));
                    qrCode.setPriceStudents(getStringValue(qrSnapshot.child("priceStudents")));
                    qrCode.setPriceChildren(getStringValue(qrSnapshot.child("priceChildren")));
                    qrCode.setPriceSenior(getStringValue(qrSnapshot.child("priceSenior")));
                    qrCode.setPriceCamera(getStringValue(qrSnapshot.child("priceCamera")));
                    qrCode.setBankName(qrSnapshot.child("bankName").getValue(String.class));
                    qrCode.setEmailId(qrSnapshot.child("emailId").getValue(String.class));
                    qrCode.setTotalAmount(qrSnapshot.child("totalAmount").getValue(String.class));
                    qrCode.setPlaceName(qrSnapshot.child("placeName").getValue(String.class));
                    qrCode.setBankAccountNumber(qrSnapshot.child("bankAccountNumber").getValue(String.class));
                    Long timestamp = qrSnapshot.child("timestamp").getValue(Long.class);
                    qrCode.setTimestamp(timestamp);
                    qrCode.setPdfData(qrSnapshot.child("pdfData").getValue(String.class));
                    // Retrieve generatedDate
                    qrCode.setGeneratedDate(qrSnapshot.child("generatedDate").getValue(String.class));

                    // Log each QR code's timestamp and generatedDate for debugging
                    Log.d(TAG, "QR ID: " + qrCode.getId() + ", Timestamp: " + timestamp + ", GeneratedDate: " + qrCode.getGeneratedDate());

                    // Only add valid QR codes (optional: add additional validation if needed)
                    if (timestamp != null) {
                        activeQRCodes.add(qrCode);
                    } else {
                        Log.w(TAG, "Skipping QR ID: " + qrCode.getId() + " due to null timestamp");
                    }
                }

                loadingIndicator.setVisibility(View.GONE);
                if (activeQRCodes.isEmpty()) {
                    activeQrList.setVisibility(View.GONE);
                    emptyStateContainer.setVisibility(View.VISIBLE);
                    emptyStateText.setText("No active QR codes");
                    emptyStateImage.setImageResource(R.drawable.ic_empty_tickets);
                    Log.d(TAG, "No valid QR codes found");
                } else {
                    emptyStateContainer.setVisibility(View.GONE);
                    activeQrList.setVisibility(View.VISIBLE);

                    // Sort in descending order based on timestamp
                    Collections.sort(activeQRCodes, (qr1, qr2) -> {
                        Long timestamp1 = qr1.getTimestamp() != null ? qr1.getTimestamp() : 0L;
                        Long timestamp2 = qr2.getTimestamp() != null ? qr2.getTimestamp() : 0L;
                        return timestamp2.compareTo(timestamp1); // Newest first
                    });

                    // Log sorted QR codes for debugging
                    Log.d(TAG, "Sorted QR codes:");
                    for (ActiveQRCode qr : activeQRCodes) {
                        Log.d(TAG, "QR ID: " + qr.getId() + ", Timestamp: " + qr.getTimestamp() + ", GeneratedDate: " + qr.getGeneratedDate());
                    }

                    // Set adapter and notify data changes
                    ActiveQRAdapter adapter = new ActiveQRAdapter(activeQRCodes);
                    activeQrList.setAdapter(adapter);
                    adapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (isActivityDestroyed || isLoggingOut) return;
                loadingIndicator.setVisibility(View.GONE);
                activeQrList.setVisibility(View.GONE);
                emptyStateContainer.setVisibility(View.VISIBLE);
                emptyStateText.setText("Error loading QR codes");
                emptyStateImage.setImageResource(R.drawable.info);
                showToast("Failed to load active QR codes", false);
                Log.e(TAG, "Failed to load active QR codes: " + error.getMessage());
            }
        };

        // Optional: Sort by timestamp on the server side for efficiency
        qrHistoryRef.orderByChild("timestamp").addValueEventListener(qrHistoryListener);
    }

    private void deleteQRCode(String qrId) {
        if (qrHistoryRef == null) {
            Log.e(TAG, "qrHistoryRef is null, cannot delete QR code");
            showToast("Error: Database reference not initialized", false);
            return;
        }

        qrHistoryRef.child(qrId).removeValue((error, ref) -> {
            if (isActivityDestroyed) return;
            if (error == null) {
                showToast("QR code deleted successfully", true);
                Log.d(TAG, "Deleted QR code with ID: " + qrId);
                // Save QR_DELETED notification
                saveNotification(userUid, "QR_DELETED", "QR code with ID " + qrId + " has been deleted");
            } else {
                showToast("Failed to delete QR code: " + error.getMessage(), false);
                Log.e(TAG, "Failed to delete QR code with ID: " + qrId + ", error: " + error.getMessage());
            }
        });
    }

    private void showCustomDeleteDialog(String qrId) {
        if (isActivityDestroyed) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_delete_qr, null);
        builder.setView(dialogView);

        // Initialize dialog views
        TextView title = dialogView.findViewById(R.id.delete_dialog_title);
        TextView message = dialogView.findViewById(R.id.delete_dialog_message);
        MaterialButton cancelButton = dialogView.findViewById(R.id.cancel_button);
        MaterialButton deleteButton = dialogView.findViewById(R.id.delete_button);

        // Create and show the dialog
        AlertDialog dialog = builder.create();

        // Adjust dialog appearance
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
            params.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.85);
            dialog.getWindow().setAttributes(params);
        }

        // Set button click listeners
        cancelButton.setOnClickListener(v -> dialog.dismiss());
        deleteButton.setOnClickListener(v -> {
            deleteQRCode(qrId);
            dialog.dismiss();
        });

        dialog.show();
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

    private void showPremiumQRDetailsDialog(ActiveQRCode qrCode) {
        if (isActivityDestroyed) return;

        // Pre-calculate entry time text to avoid delays during view binding
        String entryTimeText = formatEntryTime(qrCode.getValidFrom(), qrCode.getValidTo());

        // Inflate dialog view (optimized with ViewHolder pattern if reused)
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_qr_details, null);

        // Bind views efficiently
        TextView placeName = dialogView.findViewById(R.id.place_name);
        TextView entryTime = dialogView.findViewById(R.id.entry_time);
        TextView instructions = dialogView.findViewById(R.id.instructions);
        TextView helpline = dialogView.findViewById(R.id.helpline);
        TextView adultsPrice = dialogView.findViewById(R.id.adults_price);
        TextView studentsPrice = dialogView.findViewById(R.id.students_price);
        TextView childrenPrice = dialogView.findViewById(R.id.children_price);
        TextView seniorPrice = dialogView.findViewById(R.id.senior_price);
        TextView cameraPrice = dialogView.findViewById(R.id.camera_price);
        TextView bankName = dialogView.findViewById(R.id.bank_name);
        TextView emailId = dialogView.findViewById(R.id.email_id);
        TextView bankAccountNumber = dialogView.findViewById(R.id.bank_account_number);
        MaterialButton closeButton = dialogView.findViewById(R.id.close_button);
        FrameLayout downloadButton = dialogView.findViewById(R.id.download_button);

        // Set view data with minimal checks
        placeName.setText(qrCode.getPlaceName() != null ? qrCode.getPlaceName() : "Not specified");
        entryTime.setText(entryTimeText);
        instructions.setText(qrCode.getInstructions() != null ? qrCode.getInstructions() : "No special instructions");
        helpline.setText(qrCode.getHelpline() != null ? qrCode.getHelpline() : "Not provided");
        adultsPrice.setText(formatPrice(qrCode.getPriceAdults()));
        studentsPrice.setText(formatPrice(qrCode.getPriceStudents()));
        childrenPrice.setText(formatPrice(qrCode.getPriceChildren()));
        seniorPrice.setText(formatPrice(qrCode.getPriceSenior()));
        cameraPrice.setText(formatPrice(qrCode.getPriceCamera()));
        bankName.setText(qrCode.getBankName() != null ? qrCode.getBankName() : "Not provided");
        emailId.setText(qrCode.getEmailId() != null ? qrCode.getEmailId() : "Not provided");
        bankAccountNumber.setText(qrCode.getBankAccountNumber() != null ? qrCode.getBankAccountNumber() : "Not provided");

        // Set download button listener
        downloadButton.setOnClickListener(v -> {
            if (qrCode.getPdfData() != null && qrCode.getPlaceName() != null && qrCode.getTimestamp() != null) {
                downloadQRCodePDF(qrCode.getPdfData(), qrCode.getPlaceName(), qrCode.getTimestamp());
            } else {
                showToast("PDF data not available", false);
                Log.e(TAG, "Missing PDF data, placeName, or timestamp for QR ID: " + qrCode.getId());
            }
        });

        // Create and configure dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        // Set window attributes before showing
        Window window = dialog.getWindow();
        if (window != null) {
            WindowManager.LayoutParams params = new WindowManager.LayoutParams();
            params.copyFrom(window.getAttributes());
            DisplayMetrics displayMetrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            params.width = (int) (displayMetrics.widthPixels * 0.90);
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            params.gravity = Gravity.CENTER;
            window.setAttributes(params);
            window.setBackgroundDrawableResource(R.drawable.dialog_rounded_background);
        }

        // Apply fade-in animation
        dialogView.setAlpha(0f);
        dialogView.animate().alpha(1f).setDuration(150).start(); // Reduced duration for faster feel

        // Show dialog
        dialog.show();

        // Close button listener with fade-out
        closeButton.setOnClickListener(v -> {
            dialogView.animate().alpha(0f).setDuration(150).withEndAction(dialog::dismiss).start();
        });
    }

    // Helper method to pre-format entry time
    private String formatEntryTime(String validFrom, String validTo) {
        SimpleDateFormat inputTimeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
        SimpleDateFormat outputTimeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
        try {
            Date from = validFrom != null ? inputTimeFormat.parse(validFrom) : null;
            Date to = validTo != null ? inputTimeFormat.parse(validTo) : null;
            if (from != null && to != null) {
                return String.format("%s - %s", outputTimeFormat.format(from), outputTimeFormat.format(to));
            } else if (from != null) {
                return outputTimeFormat.format(from);
            } else if (to != null) {
                return outputTimeFormat.format(to);
            }
        } catch (ParseException e) {
            Log.e(TAG, "Error parsing validFrom or validTo time: " + e.getMessage(), e);
        }
        return "Not available";
    }



    private void downloadQRCodePDF(String base64Pdf, String placeName, Long timestamp) {
        if (isActivityDestroyed) return;
        try {
            // Decode Base64 PDF data
            byte[] pdfData = Base64.decode(base64Pdf, Base64.DEFAULT);

            // Create directory for QR codes
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "QRCodes");
            if (!dir.exists() && !dir.mkdirs()) {
                showToast("Failed to create directory", false);
                Log.e(TAG, "Failed to create directory: " + dir.getAbsolutePath());
                return;
            }

            // Format filename: placeName_QRGeneratedDate.pdf
            String safePlaceName = placeName.replaceAll("[^a-zA-Z0-9]", "_");
            String qrDate = fileDateFormat.format(new Date(timestamp));
            File pdfFile = new File(dir, String.format("%s_%s.pdf", safePlaceName, qrDate));

            // Write PDF to file
            try (FileOutputStream fos = new FileOutputStream(pdfFile)) {
                fos.write(pdfData);
            }

            // Trigger media scanner and show notification
            MediaScannerConnection.scanFile(this, new String[]{pdfFile.getAbsolutePath()}, new String[]{"application/pdf"},
                    (path, uri) -> showDownloadNotification(pdfFile.getAbsolutePath()));
        } catch (Exception e) {
            showToast("Failed to download PDF", false);
            Log.e(TAG, "Error downloading PDF: " + e.getMessage(), e);
        }
    }

    private void showDownloadNotification(String filePath) {
        if (isActivityDestroyed) return;
        File pdfFile = new File(filePath);
        if (!pdfFile.exists()) {
            showToast("PDF file not found", false);
            Log.e(TAG, "PDF file not found at: " + filePath);
            return;
        }

        try {
            Uri contentUri = FileProvider.getUriForFile(this, "com.example.bookmyticket.fileprovider", pdfFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(contentUri, "application/pdf");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);

            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "QRDownloadChannel")
                    .setSmallIcon(R.drawable.initial)
                    .setContentTitle("QR Code PDF Downloaded")
                    .setContentText("QR Code PDF Downloaded Successfully")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                manager.notify((int) System.currentTimeMillis(), builder.build());
            } else {
                Log.w(TAG, "Notification permission not granted");
                showToast("PDF downloaded, but notification permission not granted", false);
            }
        } catch (IllegalArgumentException e) {
            showToast("Failed to open PDF", false);
            Log.e(TAG, "Error creating notification: " + e.getMessage(), e);
        }
    }

    private void showToast(String message, boolean isSuccess) {
        if (isActivityDestroyed) return;
        View toastView = getLayoutInflater().inflate(R.layout.custom_toast, findViewById(R.id.custom_toast_layout));
        TextView toastText = toastView.findViewById(R.id.toast_message);
        toastText.setText(message);
        toastView.setBackgroundResource(isSuccess ? R.drawable.toast_success_bg : R.drawable.toast_error_bg);

        Toast toast = new Toast(this);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setView(toastView);
        toast.setGravity(Gravity.BOTTOM | Gravity.END, 32, 32);
        toast.show();
        Log.d(TAG, "showToast: Displayed toast with message=" + message);
    }

    private void setupPremiumUI() {
        Window window = getWindow();
        window.setStatusBarColor(Color.parseColor("#3E2A47"));
        window.setNavigationBarColor(ContextCompat.getColor(this, R.color.premium_white));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.getInsetsController().setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            );
        }
    }

    private long getStartOfDayTimestamp() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private String formatCurrency(double amount) {
        return NumberFormat.getCurrencyInstance(new Locale("en", "IN")).format(amount);
    }

    private String getStringValue(DataSnapshot snapshot) {
        Object value = snapshot.getValue();
        return value != null ? value.toString() : "0";
    }



    private String formatPrice(String price) {
        try {
            double amount = Double.parseDouble(price);
            return String.format(Locale.getDefault(), "₹%.2f", amount);
        } catch (NumberFormatException e) {
            return "₹0.00";
        }
    }

    private static class ActiveQRCode {
        private String id, validFrom, validTo, instructions, helpline, priceAdults, priceStudents, priceChildren, priceSenior, priceCamera, bankName, emailId, totalAmount, placeName, bankAccountNumber, pdfData, generatedDate;
        private Long timestamp;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getValidFrom() { return validFrom; }
        public void setValidFrom(String validFrom) { this.validFrom = validFrom; }
        public String getValidTo() { return validTo; }
        public void setValidTo(String validTo) { this.validTo = validTo; }
        public String getInstructions() { return instructions; }
        public void setInstructions(String instructions) { this.instructions = instructions; }
        public String getHelpline() { return helpline; }
        public void setHelpline(String helpline) { this.helpline = helpline; }
        public String getPriceAdults() { return priceAdults; }
        public void setPriceAdults(String priceAdults) { this.priceAdults = priceAdults; }
        public String getPriceStudents() { return priceStudents; }
        public void setPriceStudents(String priceStudents) { this.priceStudents = priceStudents; }
        public String getPriceChildren() { return priceChildren; }
        public void setPriceChildren(String priceChildren) { this.priceChildren = priceChildren; }
        public String getPriceSenior() { return priceSenior; }
        public void setPriceSenior(String priceSenior) { this.priceSenior = priceSenior; }
        public String getPriceCamera() { return priceCamera; }
        public void setPriceCamera(String priceCamera) { this.priceCamera = priceCamera; }
        public String getBankName() { return bankName; }
        public void setBankName(String bankName) { this.bankName = bankName; }
        public String getEmailId() { return emailId; }
        public void setEmailId(String emailId) { this.emailId = emailId; }
        public String getTotalAmount() { return totalAmount; }
        public void setTotalAmount(String totalAmount) { this.totalAmount = totalAmount; }
        public String getPlaceName() { return placeName; }
        public void setPlaceName(String placeName) { this.placeName = placeName; }
        public String getBankAccountNumber() { return bankAccountNumber; }
        public void setBankAccountNumber(String bankAccountNumber) { this.bankAccountNumber = bankAccountNumber; }
        public Long getTimestamp() { return timestamp; }
        public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
        public String getPdfData() { return pdfData; }
        public void setPdfData(String pdfData) { this.pdfData = pdfData; }
        public String getGeneratedDate() { return generatedDate; }
        public void setGeneratedDate(String generatedDate) { this.generatedDate = generatedDate; }
    }

    private class ActiveQRAdapter extends RecyclerView.Adapter<ActiveQRAdapter.ViewHolder> {
        private final List<ActiveQRCode> activeQRCodes;

        public ActiveQRAdapter(List<ActiveQRCode> activeQRCodes) {
            this.activeQRCodes = activeQRCodes;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_active_qr_premium, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ActiveQRCode qrCode = activeQRCodes.get(position);

            // Apply animation
            Animation animation = AnimationUtils.loadAnimation(holder.itemView.getContext(), R.anim.fade_in_up);
            holder.itemView.startAnimation(animation);

            // Set static title
            if (holder.activityTitle != null) {
                String name = qrCode.getPlaceName();
                holder.activityTitle.setText(name != null ? String.format("%s", name) : "NULL");
            } else {
                Log.e(TAG, "activityTitle is null");
            }

            // Set validity based on generatedDate
            if (holder.validityText != null) {
                String generatedDate = qrCode.getGeneratedDate();
                if (generatedDate != null && !generatedDate.isEmpty()) {
                    try {
                        SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                        SimpleDateFormat outputFormat = new SimpleDateFormat("d MMM yyyy", Locale.getDefault());
                        Date date = inputFormat.parse(generatedDate);
                        if (date != null) {
                            holder.validityText.setText(String.format("Generated on %s", outputFormat.format(date)));
                        } else {
                            holder.validityText.setText("Generated on N/A");
                        }
                    } catch (ParseException e) {
                        holder.validityText.setText("Generated on N/A");
                        Log.e(TAG, "Error parsing generatedDate: " + e.getMessage(), e);
                    }
                } else {
                    holder.validityText.setText("Generated on N/A");
                }
            } else {
                Log.e(TAG, "validityText is null");
            }

            // Set active badge
            if (holder.activeBadge != null) {
                holder.activeBadge.setText("ACTIVE");
            } else {
                Log.e(TAG, "activeBadge is null");
            }

            // Handle item click for details
            holder.itemView.setOnClickListener(v -> {
                if (!isActivityDestroyed) {
                    showPremiumQRDetailsDialog(qrCode);
                }
            });

            // Handle delete button click
            if (holder.deleteButton != null) {
                holder.deleteButton.setOnClickListener(v -> {
                    if (!isActivityDestroyed) {
                        showCustomDeleteDialog(qrCode.getId());
                    }
                });
            } else {
                Log.e(TAG, "deleteButton is null");
            }
        }

        @Override
        public int getItemCount() {
            return activeQRCodes.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            TextView activityTitle, validityText, activeBadge;
            ImageView deleteButton;

            public ViewHolder(View view) {
                super(view);
                activityTitle = view.findViewById(R.id.activity_title);
                validityText = view.findViewById(R.id.validity_text);
                activeBadge = view.findViewById(R.id.active_badge);
                deleteButton = view.findViewById(R.id.delete_button);

                // Log if views are null
                if (activityTitle == null) Log.e(TAG, "Failed to find activity_title in item_active_qr_premium");
                if (validityText == null) Log.e(TAG, "Failed to find validity_text in item_active_qr_premium");
                if (activeBadge == null) Log.e(TAG, "Failed to find active_badge in item_active_qr_premium");
                if (deleteButton == null) Log.e(TAG, "Failed to find delete_button in item_active_qr_premium");
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isActivityDestroyed = true;
        // Clean up Firebase listeners
        if (payoutsRef != null && payoutsListener != null) {
            payoutsRef.removeEventListener(payoutsListener);
            Log.d(TAG, "Removed payoutsListener in onDestroy");
        }
        if (qrHistoryRef != null && qrHistoryListener != null) {
            qrHistoryRef.removeEventListener(qrHistoryListener);
            Log.d(TAG, "Removed qrHistoryListener in onDestroy");
        }
        if (usersRef != null && userListener != null) {
            usersRef.removeEventListener(userListener);
            Log.d(TAG, "Removed userListener in onDestroy");
        }
        // Stop shimmer if active
        if (shimmerLayout != null) {
            shimmerLayout.stopShimmer();
        }
        // Clean up handler
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
            Log.d(TAG, "Removed handler callbacks in onDestroy");
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.enter_anim, R.anim.exit_anim);
    }
}
