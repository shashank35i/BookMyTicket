package com.example.bookmyticket.roles.parkingadmin;

import com.example.bookmyticket.R;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.json.JSONException;
import org.json.JSONObject;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ParkingAdminSetupActivity extends AppCompatActivity {

    private TextInputEditText name, email, accountPayeeName, accountNumber, reEnterAccountNumber, ifscCode;
    private TextInputLayout nameLayout, emailLayout, accountPayeeNameLayout, accountNumberLayout,
            reEnterAccountNumberLayout, ifscCodeLayout;
    private MaterialButton submitButton;
    private ProgressBar loadingIndicator;
    private Toolbar toolbar;
    private String selectedAdminType, intentPhoneNumber, intentUserUid, parkingArea, carParkingPrice, bikeParkingPrice, busParkingPrice;
    private FirebaseAuth mAuth;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final OkHttpClient client = new OkHttpClient.Builder().build();
    private SharedPreferences sharedPreferences;
    private boolean isActivityDestroyed = false;

    private static final String RAZORPAY_API_URL_CONTACTS = "https://api.razorpay.com/v1/contacts";
    private static final String RAZORPAY_API_URL_FUND_ACCOUNTS = "https://api.razorpay.com/v1/fund_accounts";
    private static final String RAZORPAY_KEY_ID = "rzp_test_O9fu5gbYR1vL8i"; // Replace with production key
    private static final String RAZORPAY_KEY_SECRET = "lKszbUEo0fGGKf7stA4IWPTX"; // Replace with production secret
    private static final String IFSC_PATTERN = "^[A-Z]{4}0[A-Z0-9]{6}$";
    private static final String PHONE_PATTERN = "^[6-9][0-9]{9}$";
    private static final String ALPHA_NUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 6;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parking_admin_setup);

        // Initialize FirebaseAuth
        mAuth = FirebaseAuth.getInstance();

        // Initialize SharedPreferences
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
            Log.e("ParkingAdminSetup", "Failed to init EncryptedSharedPreferences: " + e.getMessage());
            sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        }

        // Get Intent Data
        Intent intent = getIntent();
        selectedAdminType = intent.getStringExtra("adminType");
        intentPhoneNumber = intent.getStringExtra("phoneNumber");
        intentUserUid = intent.getStringExtra("USER_UID");
        String emailText = intent.getStringExtra("email");
        boolean isLoggedIn = intent.getBooleanExtra("isLoggedIn", false);
        parkingArea = intent.getStringExtra("parkingArea");
        carParkingPrice = intent.getStringExtra("carParkingPrice");
        bikeParkingPrice = intent.getStringExtra("bikeParkingPrice");
        busParkingPrice = intent.getStringExtra("busParkingPrice");

        // Validate intent data
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null || intentUserUid == null || !intentUserUid.equals(user.getUid()) ||
                intentPhoneNumber == null || !intentPhoneNumber.matches(PHONE_PATTERN) ||
                !isLoggedIn || selectedAdminType == null || !selectedAdminType.equals("parkingAdmin") ||
                parkingArea == null || carParkingPrice == null || bikeParkingPrice == null || busParkingPrice == null) {
            Log.e("ParkingAdminSetup", "Invalid data: userUid=" + intentUserUid + ", firebaseUid=" + (user != null ? user.getUid() : "null") +
                    ", phoneNumber=" + intentPhoneNumber + ", isLoggedIn=" + isLoggedIn +
                    ", adminType=" + selectedAdminType + ", parking details=" + parkingArea);
            showToast("Invalid access to ParkingAdminSetupActivity", false);
            finish();
            return;
        }

        // Initialize Views
        initializeViews();

        // Setup Toolbar
        setupToolbar();

        // Pre-fill email
        email.setText(emailText != null ? emailText : "");

        // Setup TextWatcher
        handler.post(this::setupInputValidation);

        // Submit Button Click
        submitButton.setOnClickListener(v -> {
            if (validateInputs()) {
                saveParkingAdminDetails();
            }
        });

        // Apply enter animation
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    private void setupToolbar() {
        toolbar = findViewById(R.id.toolbar);
        try {
            setSupportActionBar(toolbar);
            if (toolbar.getNavigationIcon() != null) {
                toolbar.getNavigationIcon().setColorFilter(getResources().getColor(android.R.color.black), PorterDuff.Mode.SRC_IN);
            }
            toolbar.setNavigationOnClickListener(v -> onBackPressed());
        } catch (IllegalStateException e) {
            Log.e("ParkingAdminSetup", "Toolbar setup failed", e);
            toolbar.setVisibility(View.GONE);
        }
    }

    private void initializeViews() {
        name = findViewById(R.id.name);
        email = findViewById(R.id.email);
        accountPayeeName = findViewById(R.id.account_payee_name);
        accountNumber = findViewById(R.id.account_number);
        reEnterAccountNumber = findViewById(R.id.re_enter_account_number);
        ifscCode = findViewById(R.id.ifsc_code);
        nameLayout = findViewById(R.id.name_layout);
        emailLayout = findViewById(R.id.email_layout);
        accountPayeeNameLayout = findViewById(R.id.account_payee_name_layout);
        accountNumberLayout = findViewById(R.id.account_number_layout);
        reEnterAccountNumberLayout = findViewById(R.id.re_enter_account_number_layout);
        ifscCodeLayout = findViewById(R.id.ifsc_code_layout);
        submitButton = findViewById(R.id.submit_button);
        loadingIndicator = findViewById(R.id.loading_indicator);
    }

    private void setupInputValidation() {
        email.addTextChangedListener(createTextWatcher(emailLayout, input ->
                input.isEmpty() ? "Email is required" :
                        !Patterns.EMAIL_ADDRESS.matcher(input).matches() ? "Invalid email format" : null));

        ifscCode.addTextChangedListener(createTextWatcher(ifscCodeLayout, input ->
                input.isEmpty() ? "IFSC code is required" :
                        !input.matches(IFSC_PATTERN) ? "Invalid IFSC code format" : null));

        reEnterAccountNumber.addTextChangedListener(createTextWatcher(reEnterAccountNumberLayout, input -> {
            String original = accountNumber.getText().toString().trim();
            return input.isEmpty() ? "Re-enter account number" :
                    !input.equals(original) ? "Account numbers do not match" : null;
        }));

        name.addTextChangedListener(createTextWatcher(nameLayout, input ->
                input.isEmpty() ? "Name is required" : null));

        accountPayeeName.addTextChangedListener(createTextWatcher(accountPayeeNameLayout, input ->
                input.isEmpty() ? "Account holder’s name is required" : null));

        accountNumber.addTextChangedListener(createTextWatcher(accountNumberLayout, input ->
                input.isEmpty() ? "Account number is required" : null));
    }

    private TextWatcher createTextWatcher(TextInputLayout layout, ErrorProvider errorProvider) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                layout.setError(errorProvider.getError(s.toString().trim()));
            }
        };
    }

    private interface ErrorProvider {
        String getError(String input);
    }

    private boolean validateInputs() {
        boolean isValid = true;

        isValid &= validateField(name, nameLayout, "Name is required");
        isValid &= validateEmail(email, emailLayout);
        isValid &= validateField(accountPayeeName, accountPayeeNameLayout, "Account holder’s name is required");
        isValid &= validateField(accountNumber, accountNumberLayout, "Account number is required");
        isValid &= validateReEnterAccountNumber(reEnterAccountNumber, reEnterAccountNumberLayout, accountNumber);
        isValid &= validateIFSC(ifscCode, ifscCodeLayout);

        return isValid;
    }

    private boolean validateField(TextInputEditText field, TextInputLayout layout, String errorMessage) {
        String text = field.getText().toString().trim();
        if (text.isEmpty()) {
            layout.setError(errorMessage);
            return false;
        }
        layout.setError(null);
        return true;
    }

    private boolean validateEmail(TextInputEditText field, TextInputLayout layout) {
        String text = field.getText().toString().trim();
        if (text.isEmpty()) {
            layout.setError("Email is required");
            return false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(text).matches()) {
            layout.setError("Invalid email format");
            return false;
        }
        layout.setError(null);
        return true;
    }

    private boolean validateReEnterAccountNumber(TextInputEditText field, TextInputLayout layout, TextInputEditText originalField) {
        String text = field.getText().toString().trim();
        String original = originalField.getText().toString().trim();
        if (text.isEmpty()) {
            layout.setError("Re-enter account number");
            return false;
        } else if (!text.equals(original)) {
            layout.setError("Account numbers do not match");
            return false;
        }
        layout.setError(null);
        return true;
    }

    private boolean validateIFSC(TextInputEditText field, TextInputLayout layout) {
        String text = field.getText().toString().trim();
        if (text.isEmpty()) {
            layout.setError("IFSC code is required");
            return false;
        } else if (!text.matches(IFSC_PATTERN)) {
            layout.setError("Invalid IFSC code format");
            return false;
        }
        layout.setError(null);
        return true;
    }

    private String generateUniqueCode() {
        SecureRandom random = new SecureRandom();
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(ALPHA_NUMERIC.charAt(random.nextInt(ALPHA_NUMERIC.length())));
        }
        return code.toString();
    }

    private void saveUserDataToFirebase(FirebaseUser user, String name, String email, String phone, Runnable onSuccess, int retryCount) {
        if (retryCount >= 5) {
            showErrorDialog("Unable to generate unique UID. Please try again later.");
            toggleLoading(false);
            return;
        }

        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users");
        String newUid = generateUniqueCode();

        usersRef.orderByChild("uid").equalTo(newUid).get().addOnCompleteListener(task -> {
            if (isActivityDestroyed) return;
            if (task.isSuccessful()) {
                DataSnapshot snapshot = task.getResult();
                if (snapshot.exists()) {
                    saveUserDataToFirebase(user, name, email, phone, onSuccess, retryCount + 1);
                } else {
                    saveUserData(user, name, email, phone, newUid, onSuccess);
                }
            } else {
                Exception e = task.getException();
                Log.e("ParkingAdminSetup", "UID check failed", e);
                if (e != null && (e.getMessage().contains("Failed to connect") || e.getMessage().contains("Permission denied"))) {
                    showErrorDialog(e.getMessage().contains("Failed to connect") ?
                            "Network error. Please check your connection and retry." :
                            "Permission denied. Contact support.");
                    toggleLoading(false);
                } else {
                    saveUserData(user, name, email, phone, newUid, onSuccess);
                }
            }
        });
    }

    private void saveUserData(FirebaseUser user, String name, String email, String phone, String newUid, Runnable onSuccess) {
        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users");

        if (!phone.matches(PHONE_PATTERN)) {
            showErrorDialog("Invalid phone number");
            toggleLoading(false);
            return;
        }

        Map<String, Object> userData = new HashMap<>();
        userData.put("email", email);
        userData.put("phone", phone);
        userData.put("role", selectedAdminType);
        userData.put("name", name);
        userData.put("uid", newUid);

        usersRef.child(user.getUid()).setValue(userData)
                .addOnCompleteListener(task -> {
                    if (isActivityDestroyed) return;
                    if (task.isSuccessful()) {
                        Log.d("Firebase", "User data saved successfully for UID=<redacted>");
                        onSuccess.run();
                    } else {
                        Log.e("Firebase", "Failed to save user data: " + task.getException().getMessage());
                        showErrorDialog("Failed to save user data. Please try again.");
                        toggleLoading(false);
                    }
                });
    }

    private void saveUserDataToFirebase(FirebaseUser user, String name, String email, String phone, Runnable onSuccess) {
        saveUserDataToFirebase(user, name, email, phone, onSuccess, 0);
    }

    private void saveParkingAdminDetails() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            showToast("Please log in to continue", false);
            return;
        }

        toggleLoading(true);

        String nameText = name.getText().toString().trim();
        String emailText = email.getText().toString().trim();
        String phoneText = intentPhoneNumber;
        String accountHolder = accountPayeeName.getText().toString().trim();
        String accNumber = accountNumber.getText().toString().trim();
        String ifsc = ifscCode.getText().toString().trim();

        saveUserDataToFirebase(user, nameText, emailText, phoneText, () -> {
            createRazorpayContactAndFundAccount(
                    nameText, emailText, phoneText, accountHolder, accNumber, ifsc,
                    parkingArea, carParkingPrice, bikeParkingPrice, busParkingPrice,
                    user.getUid()
            );
        });
    }

    private void createRazorpayContactAndFundAccount(String name, String email, String phone,
                                                     String accountHolder, String accountNumber,
                                                     String ifsc, String parkingArea, String carPrice,
                                                     String bikePrice, String busPrice,
                                                     String firebaseAuthUid) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            if (isActivityDestroyed) return;
            try {
                String auth = Credentials.basic(RAZORPAY_KEY_ID, RAZORPAY_KEY_SECRET);

                JSONObject contactRequest = new JSONObject()
                        .put("name", name)
                        .put("email", email)
                        .put("contact", phone)
                        .put("type", "employee");

                Request contactReq = new Request.Builder()
                        .url(RAZORPAY_API_URL_CONTACTS)
                        .post(RequestBody.create(MediaType.parse("application/json"), contactRequest.toString()))
                        .addHeader("Authorization", auth)
                        .build();

                Response contactResponse = client.newCall(contactReq).execute();
                if (contactResponse.isSuccessful()) {
                    JSONObject contactJson = new JSONObject(contactResponse.body().string());
                    String contactId = contactJson.getString("id");

                    JSONObject fundAccountRequest = new JSONObject()
                            .put("contact_id", contactId)
                            .put("account_type", "bank_account")
                            .put("bank_account", new JSONObject()
                                    .put("name", accountHolder)
                                    .put("account_number", accountNumber)
                                    .put("ifsc", ifsc));

                    Request fundReq = new Request.Builder()
                            .url(RAZORPAY_API_URL_FUND_ACCOUNTS)
                            .post(RequestBody.create(MediaType.parse("application/json"), fundAccountRequest.toString()))
                            .addHeader("Authorization", auth)
                            .build();

                    Response fundResponse = client.newCall(fundReq).execute();
                    if (fundResponse.isSuccessful()) {
                        JSONObject fundJson = new JSONObject(fundResponse.body().string());
                        String fundAccountId = fundJson.getString("id");

                        handler.post(() -> saveParkingAdminData(firebaseAuthUid, fundAccountId, parkingArea,
                                carPrice, bikePrice, busPrice));
                    } else {
                        handleRazorpayError(fundResponse, "fund account creation");
                    }
                } else {
                    handleRazorpayError(contactResponse, "contact creation");
                }
            } catch (Exception e) {
                Log.e("Razorpay", "Error: " + e.getMessage(), e);
                handler.post(() -> {
                    showErrorDialog("An unexpected error occurred while linking the bank account. Please try again.");
                    toggleLoading(false);
                });
            } finally {
                executor.shutdown();
            }
        });
    }

    private void handleRazorpayError(Response response, String operation) throws JSONException {
        if (isActivityDestroyed) return;
        int statusCode = response.code();
        String errorResponse = "";
        try {
            errorResponse = response.body() != null ? response.body().string() : "{}";
        } catch (Exception e) {
            Log.e("Razorpay", "Error reading response body", e);
        }
        Log.e("Razorpay", operation + " failed. Status: " + statusCode + ", Response: " + errorResponse);

        String userMessage;
        switch (statusCode) {
            case 400:
                JSONObject errorJson = new JSONObject(errorResponse);
                String errorDescription = errorJson.optJSONObject("error") != null
                        ? errorJson.getJSONObject("error").optString("description", "Invalid input provided.")
                        : "Invalid input provided.";
                userMessage = "Failed to link bank account: " + errorDescription + " Please check your details.";
                break;
            case 401:
            case 403:
                userMessage = "Authentication error. Please contact support.";
                break;
            case 429:
                userMessage = "Too many requests. Please try again later.";
                break;
            case 500:
                userMessage = "Server error. Please try again later.";
                break;
            default:
                userMessage = "Failed to link bank account. Please try again.";
                break;
        }
        handler.post(() -> {
            showErrorDialog(userMessage);
            toggleLoading(false);
        });
    }

    private void saveParkingAdminData(String firebaseAuthUid, String fundAccountId, String parkingArea,
                                      String carPrice, String bikePrice, String busPrice) {
        DatabaseReference parkingAdminsRef = FirebaseDatabase.getInstance().getReference("parkingadmins");

        Map<String, Object> parkingAdminData = new HashMap<>();
        parkingAdminData.put("name", name.getText().toString().trim());
        parkingAdminData.put("accountHolder", accountPayeeName.getText().toString().trim());
        parkingAdminData.put("accountNumber", accountNumber.getText().toString().trim());
        parkingAdminData.put("ifscCode", ifscCode.getText().toString().trim());
        parkingAdminData.put("fundAccountId", fundAccountId);
        parkingAdminData.put("parkingArea", parkingArea);
        parkingAdminData.put("carParkingPrice", carPrice);
        parkingAdminData.put("bikeParkingPrice", bikePrice);
        parkingAdminData.put("busParkingPrice", busPrice);

        parkingAdminsRef.child(firebaseAuthUid).setValue(parkingAdminData)
                .addOnCompleteListener(task -> {
                    toggleLoading(false);
                    if (isActivityDestroyed) return;
                    if (task.isSuccessful()) {
                        saveLoginState(intentPhoneNumber, firebaseAuthUid);
                        showToast("Your account has been successfully created!", true);
                        navigateToDashboard();
                    } else {
                        showErrorDialog("Failed to save parking admin data. Please try again.");
                    }
                });
    }

    private void saveLoginState(String phoneNumber, String firebaseAuthUid) {
        try {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean("isLoggedIn", true);
            editor.putString("phoneNumber", phoneNumber);
            editor.putString("firebaseAuthUid", firebaseAuthUid);
            editor.putString("role", selectedAdminType);
            editor.putString("email", email.getText().toString().trim());
            editor.apply();
            Log.d("ParkingAdminSetup", "Login state saved: phoneNumber=" + phoneNumber + ", uid=" + firebaseAuthUid + ", role=" + selectedAdminType);
        } catch (Exception e) {
            Log.e("ParkingAdminSetup", "Failed to save login state: " + e.getMessage());
            showToast("Failed to save login state", false);
        }
    }

    private void navigateToDashboard() {
        if (isActivityDestroyed) return;
        Intent intent = new Intent(this, ParkingAdminDashboardActivity.class);
        intent.putExtra("adminType", selectedAdminType);
        intent.putExtra("USER_UID", intentUserUid);
        intent.putExtra("phoneNumber", intentPhoneNumber);
        intent.putExtra("isLoggedIn", true);
        intent.putExtra("isNewUser", false);
        intent.putExtra("email", email.getText().toString().trim());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        finish();
    }

    private void toggleLoading(boolean isLoading) {
        if (loadingIndicator != null) {
            loadingIndicator.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }
        submitButton.setEnabled(!isLoading);
    }

    private void showToast(String message, boolean isSuccess) {
        if (isActivityDestroyed) return;
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void showErrorDialog(String message) {
        if (isActivityDestroyed) return;
        new AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage(message)
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .setCancelable(true)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isActivityDestroyed = true;
        handler.removeCallbacksAndMessages(null);
    }
}
