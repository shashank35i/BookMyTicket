package com.example.bookmyticket.auth;

import com.example.bookmyticket.R;
import com.example.bookmyticket.roles.parkingadmin.ParkingAdminDashboardActivity;
import com.example.bookmyticket.roles.parkingadmin.ParkingPriceDetailsActivity;
import com.example.bookmyticket.roles.placeadmin.PlaceAdminDashboardActivity;
import com.example.bookmyticket.roles.tourist.TouristPageActivity;
import com.example.bookmyticket.roles.tourist.TouristPriceDetailsActivity;
import com.example.bookmyticket.security.AppSignatureHelper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.auth.api.phone.SmsRetrieverClient;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.FirebaseException;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LoginActivity extends AppCompatActivity {
    private static final String TAG = "LoginActivity";
    private static final long OTP_REQUEST_INTERVAL = 30000;
    private static final String PREFS_NAME = "UserPrefs";
    private static final String PREF_LOGGED_IN = "isLoggedIn";
    private static final String PREF_ROLE = "userRole";
    private static final String PREF_PHONE = "phoneNumber";
    private static final String PREF_UID = "userUid";
    private static final long DATABASE_TIMEOUT = 8000;
    private static final int MAX_RETRY_COUNT = 2;
    private static final String SMS_CONSENT_ACTION = "com.google.android.gms.auth.api.phone.SMS_RETRIEVED";

    // UI Components
    private ImageView backButton;
    private EditText phoneNumber, otpInput;
    private MaterialButton sendOtpButton, verifyOtpButton, touristButton, placeAdminButton, parkingAdminButton, adminButton;
    private TextView resendOtpText, notThisNumberText, timerText, titleText, roleSelectionTitle;
    private View phoneNumberLayout, otpInputLayout, roleSelectionLayout, adminOptionsContainer;
    private ProgressBar phoneProgressBar, otpProgressBar;

    // Firebase
    private FirebaseAuth mAuth;
    private DatabaseReference usersRef;

    // SMS Retriever
    private SmsRetrieverClient smsRetrieverClient;
    private SmsBroadcastReceiver smsBroadcastReceiver;

    // Other variables
    private String verificationId;
    private String phoneWithCode;
    private long lastOtpRequestTime = 0;
    private CountDownTimer countDownTimer;
    private InputMethodManager inputMethodManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private SharedPreferences sharedPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        optimizeWindowSettings();
        initializeFirebase();
        initializeSharedPreferences();
        initializeSmsRetriever();
        initializeViews();
        setupClickListeners();
        checkAuthenticatedUser();
    }

    private void initializeSharedPreferences() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                MasterKey masterKey = new MasterKey.Builder(this)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build();
                sharedPrefs = EncryptedSharedPreferences.create(
                        this, PREFS_NAME, masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
                Log.d(TAG, "EncryptedSharedPreferences initialized successfully");
            } else {
                sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                Log.d(TAG, "Using standard SharedPreferences for Android version < M");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize EncryptedSharedPreferences: " + e.getMessage(), e);
            // Fallback to standard SharedPreferences if encryption fails
            sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            Log.w(TAG, "Falling back to standard SharedPreferences due to encryption failure");
            // Clear any corrupted preferences to prevent further issues
            sharedPrefs.edit().clear().apply();
        }
    }

    private void optimizeWindowSettings() {
        Window window = getWindow();

        // Set status bar background color (dark color)
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.black)); // Or use 0xFF121212

        // For API 23+ (Marshmallow), set light status bar icons
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Clear any existing flags that might interfere
            int flags = window.getDecorView().getSystemUiVisibility();
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR; // Clear light status bar flag if set

            // For API 30+ (Android 11), use WindowInsetsController
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, window.getDecorView());
                controller.setAppearanceLightStatusBars(false); // false = white icons
            } else {
                // For API 23-29, use system UI visibility flags
                window.getDecorView().setSystemUiVisibility(flags);
            }
        }

        // Additional window flags
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);

        // Hide keyboard initially
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
    }
    private void initializeFirebase() {
        mAuth = FirebaseAuth.getInstance();
        usersRef = FirebaseDatabase.getInstance().getReference("users");

    }

    private void initializeSmsRetriever() {
        smsRetrieverClient = SmsRetriever.getClient(this);
        smsBroadcastReceiver = new SmsBroadcastReceiver();
        IntentFilter intentFilter = new IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(smsBroadcastReceiver, intentFilter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(smsBroadcastReceiver, intentFilter);
        }
        startSmsRetriever();
    }

    private void startSmsRetriever() {
        Task<Void> smsRetrieverTask = smsRetrieverClient.startSmsRetriever();
        smsRetrieverTask.addOnSuccessListener(aVoid -> Log.d(TAG, "SMS Retriever started successfully"))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to start SMS Retriever: " + e.getMessage());
                    Toast.makeText(this, "Failed to initialize SMS retriever, please enter OTP manually", Toast.LENGTH_LONG).show();
                });
    }

    private void initializeViews() {
        backButton = findViewById(R.id.backButton);
        phoneNumber = findViewById(R.id.phoneNumber);
        otpInput = findViewById(R.id.otpInput);
        sendOtpButton = findViewById(R.id.sendOtpButton);
        verifyOtpButton = findViewById(R.id.verifyOtpButton);
        touristButton = findViewById(R.id.touristButton);
        placeAdminButton = findViewById(R.id.placeAdminButton);
        parkingAdminButton = findViewById(R.id.parkingAdminButton);
        adminButton = findViewById(R.id.adminButton);
        resendOtpText = findViewById(R.id.resendOtpText);
        notThisNumberText = findViewById(R.id.notThisNumberText);
        timerText = findViewById(R.id.timerText);
        phoneNumberLayout = findViewById(R.id.phoneNumberLayout);
        otpInputLayout = findViewById(R.id.otpInputLayout);
        roleSelectionLayout = findViewById(R.id.roleSelectionLayout);
        adminOptionsContainer = findViewById(R.id.adminOptionsContainer);
        phoneProgressBar = findViewById(R.id.phoneProgressBar);
        otpProgressBar = findViewById(R.id.otpProgressBar);
        titleText = findViewById(R.id.titleText);
        roleSelectionTitle = findViewById(R.id.roleSelectionTitle);

        phoneNumber.setInputType(InputType.TYPE_CLASS_PHONE);
        otpInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        phoneProgressBar.setVisibility(View.GONE);
        otpProgressBar.setVisibility(View.GONE);
    }

    private void setupClickListeners() {
        sendOtpButton.setOnClickListener(v -> handleSendOtp());
        verifyOtpButton.setOnClickListener(v -> handleVerifyOtp());
        resendOtpText.setOnClickListener(v -> handleResendOtp());
        notThisNumberText.setOnClickListener(v -> resetToPhoneInput());
        backButton.setOnClickListener(v -> handleBackButton());

        touristButton.setOnClickListener(v -> handleRoleSelection("tourist"));
        placeAdminButton.setOnClickListener(v -> handleRoleSelection("placeAdmin"));
        parkingAdminButton.setOnClickListener(v -> handleRoleSelection("parkingAdmin"));
        adminButton.setOnClickListener(v -> toggleAdminOptions());
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    private void checkAuthenticatedUser() {
        try {
            if (sharedPrefs == null) {
                Log.e(TAG, "SharedPreferences is null, initializing standard SharedPreferences");
                sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                sharedPrefs.edit().clear().apply();
                showInitialView();
                return;
            }
            boolean isLoggedIn = sharedPrefs.getBoolean(PREF_LOGGED_IN, false);
            String userRole = sharedPrefs.getString(PREF_ROLE, "");
            String userUid = sharedPrefs.getString(PREF_UID, "");
            boolean hasLoggedOut = sharedPrefs.getBoolean("hasLoggedOut", false);

            if (isLoggedIn && !hasLoggedOut && isValidRole(userRole) && !userUid.isEmpty()) {
                FirebaseUser currentUser = mAuth.getCurrentUser();
                if (currentUser != null && currentUser.getUid().equals(userUid)) {
                    checkUserInDatabase(userUid, false, 0);
                } else {
                    sharedPrefs.edit().clear().apply();
                    showInitialView();
                }
            } else {
                sharedPrefs.edit().clear().apply();
                showInitialView();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking authenticated user: " + e.getMessage(), e);
            // Clear preferences and fallback to standard SharedPreferences if encryption issue persists
            sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            sharedPrefs.edit().clear().apply();
            showInitialView();
        }
    }

    private boolean isValidRole(String role) {
        return "placeAdmin".equals(role) || "parkingAdmin".equals(role) || "tourist".equals(role);
    }

    private void checkUserInDatabase(String firebaseUid, boolean fromVerification, int retryCount) {
        if (retryCount >= MAX_RETRY_COUNT) {
            phoneProgressBar.setVisibility(View.GONE);
            Toast.makeText(this, "Failed to connect to database, please try again later", Toast.LENGTH_LONG).show();
            if (!fromVerification) {
                showInitialView();
            } else {
                resetToPhoneInput();
            }
            return;
        }

        phoneProgressBar.setVisibility(View.VISIBLE);
        Runnable timeoutRunnable = () -> {
            phoneProgressBar.setVisibility(View.GONE);
            checkUserInDatabase(firebaseUid, fromVerification, retryCount + 1);
        };
        mainHandler.postDelayed(timeoutRunnable, DATABASE_TIMEOUT);

        usersRef.child(firebaseUid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                mainHandler.removeCallbacks(timeoutRunnable);
                phoneProgressBar.setVisibility(View.GONE);
                if (snapshot.exists()) {
                    String phone = snapshot.child("phone").getValue(String.class);
                    String role = snapshot.child("role").getValue(String.class);
                    String email = snapshot.child("email").getValue(String.class);
                    if (role != null && phone != null && isValidRole(role)) {
                        saveUserSession(phone, role, firebaseUid, email);
                        navigateToDashboard(role, false, firebaseUid, phone, email);
                    } else {
                        if (!fromVerification) {
                            showInitialView();
                        } else {
                            showRoleSelection();
                        }
                    }
                } else {
                    if (!fromVerification) {
                        showInitialView();
                    } else {
                        showRoleSelection();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                mainHandler.removeCallbacks(timeoutRunnable);
                phoneProgressBar.setVisibility(View.GONE);
                Log.e(TAG, "Database error: " + error.getMessage());
                checkUserInDatabase(firebaseUid, fromVerification, retryCount + 1);
            }
        });
    }

    private void showInitialView() {
        phoneNumberLayout.setVisibility(View.VISIBLE);
        otpInputLayout.setVisibility(View.GONE);
        roleSelectionLayout.setVisibility(View.GONE);
        adminOptionsContainer.setVisibility(View.GONE);
        backButton.setVisibility(View.GONE);
        titleText.setText("Verify Your Phone Number");
        phoneProgressBar.setVisibility(View.GONE);
        phoneNumber.setEnabled(true);
        sendOtpButton.setEnabled(true);
        phoneNumber.setText("");
        phoneNumber.requestFocus();
        showKeyboard(phoneNumber);
    }

    private void handleSendOtp() {
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "No internet connection, please check your network", Toast.LENGTH_LONG).show();
            return;
        }

        String phone = phoneNumber.getText().toString().trim();
        if (!isValidPhoneNumber(phone)) {
            phoneNumber.setError("Enter valid 10-digit number");
            phoneNumber.requestFocus();
            return;
        }

        if (System.currentTimeMillis() - lastOtpRequestTime < OTP_REQUEST_INTERVAL) {
            Toast.makeText(this, "Please try again after 30 seconds", Toast.LENGTH_SHORT).show();
            return;
        }

        phoneWithCode = "+91" + phone;
        lastOtpRequestTime = System.currentTimeMillis();
        sendOtpButton.setEnabled(false);
        phoneNumber.setEnabled(false);
        phoneProgressBar.setVisibility(View.VISIBLE);
        hideKeyboard();

        String appSignatureHash = getAppSignatureHash();
        startSmsRetriever();

        PhoneAuthOptions.Builder builder = PhoneAuthOptions.newBuilder(mAuth)
                .setPhoneNumber(phoneWithCode)
                .setTimeout(30L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                        phoneProgressBar.setVisibility(View.GONE);
                        phoneNumber.setEnabled(true);
                        sendOtpButton.setEnabled(true);
                        signInWithCredential(credential);
                    }

                    @Override
                    public void onVerificationFailed(@NonNull FirebaseException e) {
                        phoneProgressBar.setVisibility(View.GONE);
                        sendOtpButton.setEnabled(true);
                        phoneNumber.setEnabled(true);
                        resetToPhoneInput();
                        String errorMessage = "Failed to send OTP, please try again";
                        if (e instanceof FirebaseTooManyRequestsException) {
                            errorMessage = "Too many OTP requests, please try again later";
                        } else if (e instanceof FirebaseNetworkException) {
                            errorMessage = "Network error, please check your connection";
                        } else if (e instanceof FirebaseAuthInvalidCredentialsException) {
                            errorMessage = "Invalid phone number, please check and try again";
                        } else if (e.getMessage() != null && e.getMessage().contains("SMS quota exceeded")) {
                            errorMessage = "Firebase SMS quota exceeded, please try again later";
                        }
                        Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                        Log.e(TAG, "OTP verification failed: " + e.getMessage(), e);
                    }

                    @Override
                    public void onCodeSent(@NonNull String newVerificationId, @NonNull PhoneAuthProvider.ForceResendingToken token) {
                        phoneProgressBar.setVisibility(View.GONE);
                        sendOtpButton.setEnabled(true);
                        phoneNumber.setEnabled(true);
                        verificationId = newVerificationId;
                        showOtpInputView();
                        startCountdownTimer();
                    }
                });

        if (appSignatureHash != null) {
            builder.setForceResendingToken(null);
        }

        PhoneAuthProvider.verifyPhoneNumber(builder.build());
    }

    private String getAppSignatureHash() {
        try {
            String packageName = getPackageName();
            android.content.pm.PackageInfo info = getPackageManager().getPackageInfo(packageName, android.content.pm.PackageManager.GET_SIGNATURES);
            for (android.content.pm.Signature signature : info.signatures) {
                MessageDigest md = MessageDigest.getInstance("SHA");
                md.update(signature.toByteArray());
                String hash = android.util.Base64.encodeToString(md.digest(), android.util.Base64.NO_WRAP);
                Log.d(TAG, "App Signature Hash: " + hash);
                return hash;
            }
        } catch (android.content.pm.PackageManager.NameNotFoundException | NoSuchAlgorithmException e) {
            Log.e(TAG, "Failed to get app signature hash: " + e.getMessage());
        }
        return null;
    }

    private void showOtpInputView() {
        phoneNumberLayout.setVisibility(View.GONE);
        otpInputLayout.setVisibility(View.VISIBLE);
        backButton.setVisibility(View.VISIBLE);
        verifyOtpButton.setVisibility(View.VISIBLE);
        titleText.setText("Enter OTP");
        notThisNumberText.setVisibility(View.VISIBLE);
        otpInput.setText("");
        otpInput.requestFocus();
        showKeyboard(otpInput);
    }

    private boolean isValidPhoneNumber(String phone) {
        return phone.length() == 10 && TextUtils.isDigitsOnly(phone);
    }

    private void handleVerifyOtp() {
        String otp = otpInput.getText().toString().trim();
        if (!isValidOtp(otp)) {
            otpInput.setError("Enter valid 6-digit OTP");
            otpInput.requestFocus();
            return;
        }

        if (verificationId == null) {
            Toast.makeText(this, "No verification in progress, please request a new OTP", Toast.LENGTH_LONG).show();
            resetToPhoneInput();
            return;
        }

        verifyOtpButton.setEnabled(false);
        otpProgressBar.setVisibility(View.VISIBLE);
        hideKeyboard();

        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, otp);
        signInWithCredential(credential);
    }

    private boolean isValidOtp(String otp) {
        return otp.length() == 6 && TextUtils.isDigitsOnly(otp);
    }

    private void signInWithCredential(PhoneAuthCredential credential) {
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    otpProgressBar.setVisibility(View.GONE);
                    verifyOtpButton.setEnabled(true);
                    if (task.isSuccessful()) {
                        if (countDownTimer != null) countDownTimer.cancel();
                        FirebaseUser user = task.getResult().getUser();
                        if (user != null) {
                            checkUserInDatabase(user.getUid(), true, 0);
                        } else {
                            Toast.makeText(this, "Authentication error, please try again", Toast.LENGTH_LONG).show();
                            resetToPhoneInput();
                        }
                    } else {
                        String errorMessage = "Invalid OTP, please try again";
                        if (task.getException() instanceof FirebaseAuthInvalidCredentialsException) {
                            errorMessage = "Invalid OTP entered";
                        } else if (task.getException() instanceof FirebaseTooManyRequestsException) {
                            errorMessage = "Too many attempts, please try again later";
                        } else if (task.getException() instanceof FirebaseNetworkException) {
                            errorMessage = "Network error, please check your connection";
                        }
                        otpInput.setError(errorMessage);
                        otpInput.requestFocus();
                        showKeyboard(otpInput);
                        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
                        Log.e(TAG, "Sign-in failed: " + task.getException().getMessage());
                    }
                });
    }

    private void showRoleSelection() {
        otpInputLayout.setVisibility(View.GONE);
        roleSelectionLayout.setVisibility(View.VISIBLE);
        backButton.setVisibility(View.VISIBLE);
        adminOptionsContainer.setVisibility(View.GONE);
        touristButton.setVisibility(View.VISIBLE);
        adminButton.setVisibility(View.VISIBLE);
        roleSelectionTitle.setText("Select your role:\n• Tourist - Explore and book tickets\n• Admin - Manage venues and bookings");
        titleText.setText("Select Your Role for " + (phoneWithCode != null ? phoneWithCode : "New User"));
    }

    private void handleRoleSelection(String role) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Session expired, please try again", Toast.LENGTH_LONG).show();
            resetToPhoneInput();
            return;
        }

        String phoneWithoutCode = phoneWithCode != null ? phoneWithCode.substring(3) : "";
        if (phoneWithoutCode.isEmpty() || !phoneWithoutCode.matches("^[0-9]{10}$")) {
            Toast.makeText(this, "Invalid phone number, please try again", Toast.LENGTH_LONG).show();
            resetToPhoneInput();
            return;
        }

        saveUserSession(phoneWithoutCode, role, currentUser.getUid(), null);
        if (!role.equals("tourist")) {
            usersRef.child(currentUser.getUid()).child("role").setValue(role);
            usersRef.child(currentUser.getUid()).child("phone").setValue(phoneWithoutCode);
        }

        navigateToDashboard(role, true, currentUser.getUid(), phoneWithoutCode, null);
    }

    private void saveUserSession(String phone, String role, String userId, String email) {
        if (phone == null || !phone.matches("^[0-9]{10}$")) {
            Log.e(TAG, "Invalid phone number for session: " + phone);
            Toast.makeText(this, "Invalid phone number, please try again", Toast.LENGTH_LONG).show();
            resetToPhoneInput();
            return;
        }
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putBoolean(PREF_LOGGED_IN, true);
        editor.putString(PREF_ROLE, role);
        editor.putString(PREF_PHONE, phone);
        editor.putString(PREF_UID, userId);
        if (email != null) {
            editor.putString("email", email);
        }
        editor.putBoolean("hasLoggedOut", false);
        editor.apply();
    }

    private void navigateToDashboard(String role, boolean isNewUser, String userUid, String phoneNumber, String email) {
        if (userUid == null || userUid.isEmpty() || phoneNumber == null || phoneNumber.isEmpty()) {
            Log.e(TAG, "Invalid navigation data: userUid=" + userUid + ", phoneNumber=" + phoneNumber);
            Toast.makeText(this, "Authentication error, please try again", Toast.LENGTH_LONG).show();
            resetToPhoneInput();
            return;
        }

        Intent intent;
        switch (role) {
            case "placeAdmin":
                intent = new Intent(LoginActivity.this, isNewUser ? TouristPriceDetailsActivity.class : PlaceAdminDashboardActivity.class);
                intent.putExtra("adminType", "placeAdmin");
                break;
            case "parkingAdmin":
                intent = new Intent(LoginActivity.this, isNewUser ? ParkingPriceDetailsActivity.class : ParkingAdminDashboardActivity.class);
                intent.putExtra("adminType", "parkingAdmin");
                break;
            default:
                intent = new Intent(LoginActivity.this, TouristPageActivity.class);
                intent.putExtra("adminType", "tourist");
                break;
        }

        intent.putExtra("USER_UID", userUid);
        intent.putExtra("phoneNumber", phoneNumber);
        intent.putExtra("isLoggedIn", true);
        intent.putExtra("isNewUser", isNewUser);
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

    private void handleResendOtp() {
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "No internet connection, please check your network", Toast.LENGTH_LONG).show();
            return;
        }

        if (System.currentTimeMillis() - lastOtpRequestTime < OTP_REQUEST_INTERVAL) {
            Toast.makeText(this, "Please wait before requesting a new OTP", Toast.LENGTH_SHORT).show();
            return;
        }

        lastOtpRequestTime = System.currentTimeMillis();
        verificationId = null;
        resendOtpText.setVisibility(View.GONE);
        phoneProgressBar.setVisibility(View.VISIBLE);

        startSmsRetriever();

        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(mAuth)
                .setPhoneNumber(phoneWithCode)
                .setTimeout(30L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                        phoneProgressBar.setVisibility(View.GONE);
                        signInWithCredential(credential);
                    }

                    @Override
                    public void onVerificationFailed(@NonNull FirebaseException e) {
                        phoneProgressBar.setVisibility(View.GONE);
                        String errorMessage = "Failed to resend OTP, please try again";
                        if (e instanceof FirebaseTooManyRequestsException) {
                            errorMessage = "Too many OTP requests, please try again later";
                        } else if (e instanceof FirebaseNetworkException) {
                            errorMessage = "Network error, please check your connection";
                        } else if (e instanceof FirebaseAuthInvalidCredentialsException) {
                            errorMessage = "Invalid phone number, please check and try again";
                        } else if (e.getMessage() != null && e.getMessage().contains("SMS quota exceeded")) {
                            errorMessage = "Firebase SMS quota exceeded, please try again later";
                        }
                        Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                        Log.e(TAG, "Resend OTP failed: " + e.getMessage(), e);
                    }

                    @Override
                    public void onCodeSent(@NonNull String newVerificationId, @NonNull PhoneAuthProvider.ForceResendingToken token) {
                        phoneProgressBar.setVisibility(View.GONE);
                        verificationId = newVerificationId;
                        startCountdownTimer();
                    }
                }).build();
        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    private void startCountdownTimer() {
        if (countDownTimer != null) countDownTimer.cancel();
        resendOtpText.setVisibility(View.GONE);
        timerText.setVisibility(View.VISIBLE);
        notThisNumberText.setVisibility(View.VISIBLE);

        countDownTimer = new CountDownTimer(30000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timerText.setText(String.format("00:%02d", millisUntilFinished / 1000));
            }

            @Override
            public void onFinish() {
                timerText.setVisibility(View.GONE);
                resendOtpText.setVisibility(View.VISIBLE);
            }
        }.start();
    }

    private void resetToPhoneInput() {
        otpInputLayout.setVisibility(View.GONE);
        roleSelectionLayout.setVisibility(View.GONE);
        phoneNumber.setText("");
        otpInput.setText("");
        verificationId = null;
        phoneWithCode = null;
        phoneProgressBar.setVisibility(View.GONE);
        otpProgressBar.setVisibility(View.GONE);
        if (countDownTimer != null) countDownTimer.cancel();
        verifyOtpButton.setVisibility(View.GONE);
        resendOtpText.setVisibility(View.GONE);
        notThisNumberText.setVisibility(View.GONE);
        timerText.setVisibility(View.GONE);
        backButton.setVisibility(View.GONE);
        titleText.setText("Verify Your Phone Number");
        phoneNumberLayout.setVisibility(View.VISIBLE);
        phoneNumber.setEnabled(true);
        sendOtpButton.setEnabled(true);
        phoneNumber.requestFocus();
        showKeyboard(phoneNumber);
    }

    private void handleBackButton() {
        if (otpInputLayout.getVisibility() == View.VISIBLE) {
            resetToPhoneInput();
        } else if (roleSelectionLayout.getVisibility() == View.VISIBLE) {
            if (adminOptionsContainer.getVisibility() == View.VISIBLE) {
                toggleAdminOptions();
            } else {
                otpInputLayout.setVisibility(View.VISIBLE);
                verifyOtpButton.setVisibility(View.VISIBLE);
                backButton.setVisibility(View.VISIBLE);
                titleText.setText("Enter OTP");
                roleSelectionLayout.setVisibility(View.GONE);
                notThisNumberText.setVisibility(View.VISIBLE);
                otpInput.requestFocus();
                showKeyboard(otpInput);
            }
        } else {
            finish();
        }
    }

    private void toggleAdminOptions() {
        if (adminOptionsContainer.getVisibility() == View.VISIBLE) {
            adminOptionsContainer.setVisibility(View.GONE);
            touristButton.setVisibility(View.VISIBLE);
            adminButton.setVisibility(View.VISIBLE);
            roleSelectionTitle.setText("Select your role:\n• Tourist - Explore and book tickets\n• Admin - Manage venues and bookings");
            titleText.setText("Select Your Role");
        } else {
            touristButton.setVisibility(View.GONE);
            adminButton.setVisibility(View.GONE);
            roleSelectionTitle.setText("Select admin type:\n• Place Admin - Manage place venues\n• Parking Admin - Manage parking facilities");
            titleText.setText("Choose Admin Type");
            adminOptionsContainer.setVisibility(View.VISIBLE);
        }
    }

    private void showKeyboard(EditText editText) {
        mainHandler.post(() -> inputMethodManager.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT));
    }

    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view != null) {
            mainHandler.post(() -> inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0));
        }
    }

    private class SmsBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (SMS_CONSENT_ACTION.equals(intent.getAction())) {
                Bundle extras = intent.getExtras();
                if (extras == null) {
                    Log.e(TAG, "SMS Broadcast received with null extras");
                    return;
                }

                com.google.android.gms.common.api.Status status =
                        (com.google.android.gms.common.api.Status) extras.get(SmsRetriever.EXTRA_STATUS);
                if (status == null) {
                    Log.e(TAG, "SMS Broadcast received with null status");
                    return;
                }

                switch (status.getStatusCode()) {
                    case com.google.android.gms.common.api.CommonStatusCodes.SUCCESS:
                        String message = (String) extras.get(SmsRetriever.EXTRA_SMS_MESSAGE);
                        if (message != null) {
                            String otp = extractOtpFromMessage(message);
                            if (otp != null && isValidOtp(otp)) {
                                otpInput.setText(otp);
                                mainHandler.post(() -> handleVerifyOtp());
                            } else {
                                Log.w(TAG, "Invalid or no OTP found in message: " + message);
                                otpInput.requestFocus();
                                showKeyboard(otpInput);
                                Toast.makeText(LoginActivity.this, "Could not auto-detect OTP, please enter manually", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Log.w(TAG, "SMS message is null");
                            otpInput.requestFocus();
                            showKeyboard(otpInput);
                        }
                        break;
                    case com.google.android.gms.common.api.CommonStatusCodes.TIMEOUT:
                        Log.w(TAG, "SMS Retriever timed out");
                        otpInput.requestFocus();
                        showKeyboard(otpInput);
                        Toast.makeText(LoginActivity.this, "SMS retrieval timed out, please enter OTP manually", Toast.LENGTH_LONG).show();
                        break;
                    default:
                        Log.w(TAG, "SMS Retriever unknown status: " + status.getStatusCode());
                        break;
                }
            }
        }

        private String extractOtpFromMessage(String message) {
            String[] patterns = {
                    "\\b\\d{6}\\b",
                    "code:\\s*(\\d{6})",
                    "OTPNn:\\s*(\\d{6})",
                    "is\\s*(\\d{6})\\s*for",
                    "(\\d{6})\\s*is your verification code"
            };
            for (String pattern : patterns) {
                Pattern p = Pattern.compile(pattern);
                Matcher matcher = p.matcher(message);
                if (matcher.find()) {
                    String otp = matcher.group(1);
                    Log.d(TAG, "Extracted OTP: " + otp);
                    return otp;
                }
            }
            Log.w(TAG, "No OTP found in message: " + message);
            return null;
        }
    }

    @Override
    public void onBackPressed() {
        handleBackButton();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
        if (smsBroadcastReceiver != null) {
            try {
                unregisterReceiver(smsBroadcastReceiver);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Receiver not registered: " + e.getMessage());
            }
            smsBroadcastReceiver = null;
        }
        mainHandler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("phoneWithCode", phoneWithCode);
        outState.putString("verificationId", verificationId);
        outState.putInt("currentView",
                roleSelectionLayout.getVisibility() == View.VISIBLE ? 3 :
                        otpInputLayout.getVisibility() == View.VISIBLE ? 2 : 1);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        phoneWithCode = savedInstanceState.getString("phoneWithCode");
        verificationId = savedInstanceState.getString("verificationId");
        int currentView = savedInstanceState.getInt("currentView");
        if (currentView == 3) {
            showRoleSelection();
        } else if (currentView == 2) {
            showOtpInputView();
        } else {
            showInitialView();
        }
    }
}
