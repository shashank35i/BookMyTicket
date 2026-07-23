package com.example.bookmyticket.features.payment;

import com.example.bookmyticket.R;
import com.example.bookmyticket.model.Ticket;
import com.example.bookmyticket.roles.placeadmin.AdminSlide2Activity;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class BankActivity extends AppCompatActivity {

    private static final String TAG = "BankActivity";
    private static final int MAX_UID_ATTEMPTS = 5;
    private static final int MAX_API_RETRIES = 2;
    private static final long DATABASE_TIMEOUT = 8000;
    private EditText editName, editEmail, editAccountHolder, editAccountNumber, editReEnterAccountNumber, editIFSC;
    private Button btnSaveBankDetails;
    private ImageView backButton;
    private LinearLayout inputFields;
    private SharedPreferences sharedPreferences;
    private String userUid, loginPhoneNumber, email;
    private boolean isLoggedIn, isNewUser;
    private String priceAdults, priceStudents, priceChildren, priceSeniorCitizens, priceCameraFee;
    private String place, validFrom, validTo, helpline, instructions, ticketId;
    private static final String RAZORPAY_API_URL_CONTACTS = "https://api.razorpay.com/v1/contacts";
    private static final String RAZORPAY_API_URL_FUND_ACCOUNTS = "https://api.razorpay.com/v1/fund_accounts";
    private static final String RAZORPAY_KEY_ID = "rzp_test_O9fu5gbYR1vL8i";
    private static final String RAZORPAY_KEY_SECRET = "lKszbUEo0fGGKf7stA4IWPTX";
    private String selectedAdminType;
    private final OkHttpClient client = new OkHttpClient();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean isActivityDestroyed = false;
    private FirebaseAuth.AuthStateListener authStateListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bank);

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
            Log.e(TAG, "Failed to init EncryptedSharedPreferences: " + e.getMessage());
            sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        }

        initializeViews();
        setupUI();

        Intent intent = getIntent();
        userUid = intent.getStringExtra("USER_UID") != null ? intent.getStringExtra("USER_UID") : sharedPreferences.getString("userUid", null);
        loginPhoneNumber = intent.getStringExtra("phoneNumber") != null ? intent.getStringExtra("phoneNumber") : sharedPreferences.getString("phoneNumber", null);
        email = intent.getStringExtra("email") != null ? intent.getStringExtra("email") : sharedPreferences.getString("email", "");
        isLoggedIn = intent.getBooleanExtra("isLoggedIn", sharedPreferences.getBoolean("isLoggedIn", false));
        isNewUser = intent.getBooleanExtra("isNewUser", sharedPreferences.getBoolean("isNewUser", false));
        selectedAdminType = intent.getStringExtra("adminType") != null ? intent.getStringExtra("adminType") : sharedPreferences.getString("adminType", null);
        priceAdults = intent.getStringExtra("priceAdults");
        priceStudents = intent.getStringExtra("priceStudents");
        priceChildren = intent.getStringExtra("priceChildren");
        priceSeniorCitizens = intent.getStringExtra("priceSeniorCitizens");
        priceCameraFee = intent.getStringExtra("priceCameraFee");
        place = intent.getStringExtra("place");
        validFrom = intent.getStringExtra("validFrom");
        validTo = intent.getStringExtra("validTo");
        helpline = intent.getStringExtra("helpline");
        instructions = intent.getStringExtra("instructions");
        ticketId = intent.getStringExtra("ticketId");

        Log.d(TAG, "Received Intent: USER_UID=" + userUid + ", phoneNumber=" + loginPhoneNumber + ", isLoggedIn=" + isLoggedIn +
                ", adminType=" + selectedAdminType + ", ticketId=" + ticketId + ", validFrom=" + validFrom + ", validTo=" + validTo);

        // Validate date formats
        if (!isValidDateFormat(validFrom) || !isValidDateFormat(validTo)) {
            Log.e(TAG, "Invalid date format: validFrom=" + validFrom + ", validTo=" + validTo);
            showToast("Invalid date format for ticket validity", false);
            finish();
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || userUid == null || !userUid.equals(user.getUid()) || loginPhoneNumber == null || !isLoggedIn || selectedAdminType == null || !selectedAdminType.equals("placeAdmin")) {
            Log.e(TAG, "Invalid data: userUid=" + userUid + ", firebaseUid=" + (user != null ? user.getUid() : "null") +
                    ", phoneNumber=" + loginPhoneNumber + ", isLoggedIn=" + isLoggedIn + ", adminType=" + selectedAdminType);
            showToast("Invalid access to BankActivity", false);
            finish();
            return;
        }
        selectedAdminType = "placeAdmin";
        userUid = user.getUid();
        Log.d(TAG, "Authenticated userUid=" + userUid);
        editEmail.setText(email);

        checkSavedBankDetails();
    }

    private boolean isValidDateFormat(String date) {
        if (date == null) return false;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
            sdf.setLenient(false);
            sdf.parse(date);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void initializeViews() {
        editName = findViewById(R.id.editName);
        editEmail = findViewById(R.id.editEmail);
        editAccountHolder = findViewById(R.id.editAccountHolder);
        editAccountNumber = findViewById(R.id.editAccountNumber);
        editReEnterAccountNumber = findViewById(R.id.editReEnterAccountNumber);
        editIFSC = findViewById(R.id.editIFSC);
        btnSaveBankDetails = findViewById(R.id.btnSaveBankDetails);
        backButton = findViewById(R.id.backButton);
        inputFields = findViewById(R.id.inputFields);
    }

    private void setupUI() {
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        Window window = getWindow();
        window.setStatusBarColor(Color.parseColor("#FFFFFF"));
        window.setNavigationBarColor(Color.parseColor("#FFFFFF"));
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Objects.requireNonNull(window.getInsetsController()).setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            );
        } else {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        backButton.setOnClickListener(v -> finish());
        btnSaveBankDetails.setOnClickListener(v -> {
            if (btnSaveBankDetails.getText().toString().equals("Save Bank Details")) {
                saveBankDetails();
            } else {
                updateBankDetails();
            }
        });
    }

    private boolean validateInputs(String name, String email, String phone, String accountHolder,
                                   String accountNumber, String reEnterAccountNumber, String ifscCode, boolean isUpdate) {
        if (name.isEmpty() || (!isUpdate && email.isEmpty()) || phone.isEmpty() || accountHolder.isEmpty() ||
                accountNumber.isEmpty() || reEnterAccountNumber.isEmpty() || ifscCode.isEmpty()) {
            showToast("Please fill all required fields", false);
            return false;
        }
        if (!accountNumber.equals(reEnterAccountNumber)) {
            showToast("Account numbers do not match", false);
            return false;
        }
        if (!ifscCode.matches("^[A-Z]{4}[A-Z0-9]{7}$")) {
            showToast("Invalid IFSC code (e.g., SBIN0001234)", false);
            return false;
        }
        if (!phone.matches("^[0-9]{10}$")) {
            showToast("Invalid phone number (10 digits required)", false);
            return false;
        }
        if (!isUpdate && !email.matches("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$")) {
            showToast("Invalid email format", false);
            return false;
        }
        return true;
    }

    private void saveBankDetails() {
        String name = editName.getText().toString().trim();
        String email = editEmail.getText().toString().trim();
        String accountHolder = editAccountHolder.getText().toString().trim();
        String accountNumber = editAccountNumber.getText().toString().trim();
        String reEnterAccountNumber = editReEnterAccountNumber.getText().toString().trim();
        String ifscCode = editIFSC.getText().toString().trim();

        if (!validateInputs(name, email, loginPhoneNumber, accountHolder, accountNumber, reEnterAccountNumber, ifscCode, false)) {
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || !user.getUid().equals(userUid)) {
            showToast("Authentication failed", false);
            finish();
            return;
        }

        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Validating bank account...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        validateBankDetails(accountHolder, accountNumber, ifscCode, user.getUid(), name, email, loginPhoneNumber, progressDialog, 0);
    }

    private void updateBankDetails() {
        String name = editName.getText().toString().trim();
        String email = editEmail.getText().toString().trim();
        String accountHolder = editAccountHolder.getText().toString().trim();
        String accountNumber = editAccountNumber.getText().toString().trim();
        String reEnterAccountNumber = editReEnterAccountNumber.getText().toString().trim();
        String ifscCode = editIFSC.getText().toString().trim();

        if (!validateInputs(name, email, loginPhoneNumber, accountHolder, accountNumber, reEnterAccountNumber, ifscCode, true)) {
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || !user.getUid().equals(userUid)) {
            showToast("Authentication failed", false);
            return;
        }

        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Validating bank account...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        validateBankDetails(accountHolder, accountNumber, ifscCode, user.getUid(), name, email, loginPhoneNumber, progressDialog, 0);
    }

    private void validateBankDetails(String accountHolder, String accountNumber, String ifscCode, String userId,
                                     String name, String email, String phone, ProgressDialog progressDialog, int retryCount) {
        if (retryCount >= MAX_API_RETRIES) {
            handler.post(() -> {
                progressDialog.dismiss();
                showErrorDialog("Failed to validate bank details. Please try again.");
            });
            return;
        }

        executor.execute(() -> {
            if (isActivityDestroyed) return;
            try {
                if (!accountNumber.matches("^[0-9]{9,18}$")) {
                    handler.post(() -> {
                        progressDialog.dismiss();
                        showErrorDialog("Invalid account number (9-18 digits required)");
                    });
                    return;
                }
                if (!ifscCode.matches("^[A-Z]{4}[A-Z0-9]{7}$")) {
                    handler.post(() -> {
                        progressDialog.dismiss();
                        showErrorDialog("Invalid IFSC code (e.g., SBIN0001234)");
                    });
                    return;
                }
                if (!accountHolder.matches("^[A-Za-z\\s]+$")) {
                    handler.post(() -> {
                        progressDialog.dismiss();
                        showErrorDialog("Invalid account holder name (letters and spaces only)");
                    });
                    return;
                }

                handler.post(() -> {
                    if (isActivityDestroyed) return;
                    progressDialog.setMessage("Linking bank account...");
                    createRazorpayContactAndFundAccount(name, email, phone, accountHolder, accountNumber, ifscCode, userId, progressDialog, retryCount);
                });
            } catch (Exception e) {
                handler.post(() -> {
                    if (isActivityDestroyed) return;
                    progressDialog.dismiss();
                    showErrorDialog("Error validating bank details");
                });
            }
        });
    }

    private void createRazorpayContactAndFundAccount(String name, String email, String phone, String accountHolder,
                                                     String accountNumber, String ifscCode, String userId,
                                                     ProgressDialog progressDialog, int retryCount) {
        executor.execute(() -> {
            if (isActivityDestroyed) return;
            try {
                String contactId = createRazorpayContact(name, email, phone, retryCount);
                if (contactId == null) {
                    handler.post(() -> {
                        if (isActivityDestroyed) return;
                        progressDialog.dismiss();
                        showErrorDialog("Unable to create contact");
                    });
                    return;
                }

                String fundAccountId = createRazorpayFundAccount(contactId, accountHolder, accountNumber, ifscCode, retryCount);
                if (fundAccountId == null) {
                    handler.post(() -> {
                        if (isActivityDestroyed) return;
                        progressDialog.dismiss();
                        showErrorDialog("Unable to link bank account");
                    });
                    return;
                }

                handler.post(() -> {
                    if (isActivityDestroyed) return;
                    progressDialog.dismiss();
                    saveToFirebase(userId, name, email, phone, accountHolder, accountNumber, ifscCode, fundAccountId);
                });
            } catch (Exception e) {
                handler.post(() -> {
                    if (isActivityDestroyed) return;
                    progressDialog.dismiss();
                    showErrorDialog("Error linking bank account");
                });
            }
        });
    }

    private String createRazorpayContact(String name, String email, String phone, int retryCount) throws Exception {
        if (retryCount >= MAX_API_RETRIES) return null;

        JSONObject contactRequestBody = new JSONObject()
                .put("name", name)
                .put("email", email)
                .put("contact", phone)
                .put("type", "vendor"); // Changed from "employee" to "vendor"

        RequestBody contactBody = RequestBody.create(MediaType.parse("application/json"), contactRequestBody.toString());
        Request contactRequest = new Request.Builder()
                .url(RAZORPAY_API_URL_CONTACTS)
                .post(contactBody)
                .addHeader("Authorization", getAuthHeader())
                .build();

        Response contactResponse = client.newCall(contactRequest).execute();
        String responseBody = contactResponse.body() != null ? contactResponse.body().string() : "";

        if (contactResponse.isSuccessful()) {
            return new JSONObject(responseBody).getString("id");
        } else if (contactResponse.code() >= 500 && retryCount < MAX_API_RETRIES - 1) {
            Thread.sleep(1000 * (retryCount + 1));
            return createRazorpayContact(name, email, phone, retryCount + 1);
        } else {
            handler.post(() -> handleApiError(contactResponse.code(), responseBody, new ProgressDialog(this)));
            return null;
        }
    }

    private String createRazorpayFundAccount(String contactId, String accountHolder, String accountNumber,
                                             String ifscCode, int retryCount) throws Exception {
        if (retryCount >= MAX_API_RETRIES) return null;

        JSONObject fundAccountRequestBody = new JSONObject()
                .put("contact_id", contactId)
                .put("account_type", "bank_account")
                .put("bank_account", new JSONObject()
                        .put("name", accountHolder)
                        .put("account_number", accountNumber)
                        .put("ifsc", ifscCode));

        RequestBody fundAccountBody = RequestBody.create(MediaType.parse("application/json"), fundAccountRequestBody.toString());
        Request fundAccountRequest = new Request.Builder()
                .url(RAZORPAY_API_URL_FUND_ACCOUNTS)
                .post(fundAccountBody)
                .addHeader("Authorization", getAuthHeader())
                .build();

        Response fundAccountResponse = client.newCall(fundAccountRequest).execute();
        String responseBody = fundAccountResponse.body() != null ? fundAccountResponse.body().string() : "";

        if (fundAccountResponse.isSuccessful()) {
            return new JSONObject(responseBody).getString("id");
        } else if (fundAccountResponse.code() >= 500 && retryCount < MAX_API_RETRIES - 1) {
            Thread.sleep(1000 * (retryCount + 1));
            return createRazorpayFundAccount(contactId, accountHolder, accountNumber, ifscCode, retryCount + 1);
        } else {
            handler.post(() -> handleApiError(fundAccountResponse.code(), responseBody, new ProgressDialog(this)));
            return null;
        }
    }

    private void saveToFirebase(String userId, String name, String email, String phone, String accountHolder,
                                String accountNumber, String ifscCode, String fundAccountId) {
        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users");
        DatabaseReference placeAdminsRef = FirebaseDatabase.getInstance().getReference("placeadmin").child(userId);

        generateUniqueSixDigitUid(usersRef, new GenerateUidCallback() {
            @Override
            public void onUidGenerated(String sixDigitUid) {
                if (isActivityDestroyed) return;

                HashMap<String, Object> userDetails = new HashMap<>();
                userDetails.put("uid", sixDigitUid);
                userDetails.put("phone", phone);
                userDetails.put("email", email);
                userDetails.put("role", selectedAdminType);
                userDetails.put("name", name);

                HashMap<String, Object> placeAdminDetails = new HashMap<>();
                placeAdminDetails.put("name", name);
                placeAdminDetails.put("email", email); // Added email to placeadmin node
                placeAdminDetails.put("accountHolder", accountHolder);
                placeAdminDetails.put("accountNumber", accountNumber);
                placeAdminDetails.put("ifscCode", ifscCode);
                placeAdminDetails.put("fundAccountId", fundAccountId);
                if (priceAdults != null) placeAdminDetails.put("priceAdults", priceAdults);
                if (priceStudents != null) placeAdminDetails.put("priceStudents", priceStudents);
                if (priceChildren != null) placeAdminDetails.put("priceChildren", priceChildren);
                if (priceSeniorCitizens != null) placeAdminDetails.put("priceSeniorCitizens", priceSeniorCitizens);
                if (priceCameraFee != null) placeAdminDetails.put("priceCameraFee", priceCameraFee);
                if (place != null) placeAdminDetails.put("place", place);
                if (validFrom != null) placeAdminDetails.put("validFrom", validFrom);
                if (validTo != null) placeAdminDetails.put("validTo", validTo);
                if (helpline != null) placeAdminDetails.put("helpline", helpline);
                if (instructions != null) placeAdminDetails.put("instructions", instructions);
                if (ticketId != null) placeAdminDetails.put("ticketId", ticketId);

                Runnable timeoutRunnable = () -> {
                    if (isActivityDestroyed) return;
                    showToast("Database operation timed out", false);
                    finish();
                };
                handler.postDelayed(timeoutRunnable, DATABASE_TIMEOUT);

                usersRef.child(userId).setValue(userDetails).addOnCompleteListener(userTask -> {
                    handler.removeCallbacks(timeoutRunnable);
                    if (isActivityDestroyed) return;
                    if (userTask.isSuccessful()) {
                        placeAdminsRef.setValue(placeAdminDetails).addOnCompleteListener(placeTask -> {
                            if (isActivityDestroyed) return;
                            handler.removeCallbacks(timeoutRunnable);
                            if (placeTask.isSuccessful()) {
                                SharedPreferences.Editor editor = sharedPreferences.edit();
                                editor.putBoolean("isUserNodeCreated", true);
                                editor.putString("userRole", selectedAdminType);
                                editor.putString("phoneNumber", phone);
                                editor.putString("userUid", userId);
                                editor.putString("email", email);
                                editor.putBoolean("isLoggedIn", true);
                                editor.putBoolean("hasLoggedOut", false);
                                editor.putBoolean("isNewUser", false);
                                editor.apply();
                                showToast("Bank account linked successfully!", true);
                                navigateToAdminSlide2(userId);
                            } else {
                                showToast("Failed to save bank details", false);
                            }
                        });
                    } else {
                        showToast("Failed to save user details", false);
                    }
                });
            }

            @Override
            public void onError(String error) {
                if (isActivityDestroyed) return;
                handler.post(() -> {
                    showToast(error, false);
                    finish();
                });
            }
        });
    }

    private void navigateToAdminSlide2(String userId) {
        if (isActivityDestroyed) return;
        authStateListener = auth -> {
            FirebaseUser currentUser = auth.getCurrentUser();
            if (currentUser == null) {
                Log.e(TAG, "No logged-in user found when navigating to AdminSlide2Activity");
                showToast("No logged-in user. Please log in again.", false);
                finish();
                return;
            }
            if (!userId.equals(currentUser.getUid())) {
                Log.e(TAG, "Mismatch: passed userId=" + userId + ", Firebase UID=" + currentUser.getUid());
            }
            Log.d(TAG, "Navigating to AdminSlide2Activity with userUid=" + userUid + ", ticketId=" + ticketId + ", place=" + place +
                    ", validFrom=" + validFrom + ", validTo=" + validTo);
            Intent intent = new Intent(this, AdminSlide2Activity.class);
            intent.putExtra("USER_UID", userUid);
            intent.putExtra("phoneNumber", loginPhoneNumber);
            intent.putExtra("isLoggedIn", isLoggedIn);
            intent.putExtra("isNewUser", isNewUser);
            intent.putExtra("adminType", selectedAdminType);
            intent.putExtra("place", place);
            intent.putExtra("priceAdults", priceAdults);
            intent.putExtra("priceStudents", priceStudents);
            intent.putExtra("priceChildren", priceChildren);
            intent.putExtra("priceSeniorCitizens", priceSeniorCitizens);
            intent.putExtra("priceCameraFee", priceCameraFee);
            intent.putExtra("validFrom", validFrom);
            intent.putExtra("validTo", validTo);
            intent.putExtra("helpline", helpline);
            intent.putExtra("instructions", instructions);
            intent.putExtra("ticketId", ticketId);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivityForResult(intent, 1001);
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        };
        FirebaseAuth.getInstance().addAuthStateListener(authStateListener);
    }

    private interface GenerateUidCallback {
        void onUidGenerated(String uid);
        void onError(String error);
    }

    private void generateUniqueSixDigitUid(DatabaseReference usersRef, GenerateUidCallback callback) {
        generateUniqueSixDigitUidRecursive(usersRef, 0, callback);
    }

    private void generateUniqueSixDigitUidRecursive(DatabaseReference usersRef, int attempt, GenerateUidCallback callback) {
        if (attempt >= MAX_UID_ATTEMPTS) {
            callback.onError("Unable to generate unique UID");
            return;
        }

        String sixDigitUid = generateSixDigitUid();
        usersRef.orderByChild("uid").equalTo(sixDigitUid).limitToFirst(1).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    generateUniqueSixDigitUidRecursive(usersRef, attempt + 1, callback);
                } else {
                    callback.onUidGenerated(sixDigitUid);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError("Failed to check UID uniqueness");
            }
        });
    }

    private String generateSixDigitUid() {
        String characters = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        Random random = new Random();
        StringBuilder uid = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            uid.append(characters.charAt(random.nextInt(characters.length())));
        }
        return uid.toString();
    }

    private void checkSavedBankDetails() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || !user.getUid().equals(userUid)) {
            showToast("Authentication failed", false);
            finish();
            return;
        }

        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users").child(user.getUid());
        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isActivityDestroyed) return;
                if (snapshot.exists()) {
                    String name = snapshot.child("name").getValue(String.class);
                    String savedEmail = snapshot.child("email").getValue(String.class);
                    String phone = snapshot.child("phone").getValue(String.class);
                    String role = snapshot.child("role").getValue(String.class);

                    if (savedEmail != null && !savedEmail.isEmpty()) {
                        email = savedEmail;
                        editEmail.setText(email);
                    }
                    if (name != null) editName.setText(name);
                    if (phone != null) loginPhoneNumber = phone;

                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString("email", email);
                    editor.putString("phoneNumber", phone);
                    editor.putString("userRole", role);
                    editor.putBoolean("isUserNodeCreated", true);
                    editor.apply();
                }

                DatabaseReference placeAdminRef = FirebaseDatabase.getInstance().getReference("placeadmin").child(user.getUid());
                placeAdminRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot placeSnapshot) {
                        if (isActivityDestroyed) return;
                        if (placeSnapshot.exists()) {
                            String accountHolder = placeSnapshot.child("accountHolder").getValue(String.class);
                            String accountNumber = placeSnapshot.child("accountNumber").getValue(String.class);
                            String ifscCode = placeSnapshot.child("ifscCode").getValue(String.class);
                            priceAdults = placeSnapshot.child("priceAdults").getValue(String.class);
                            priceStudents = placeSnapshot.child("priceStudents").getValue(String.class);
                            priceChildren = placeSnapshot.child("priceChildren").getValue(String.class);
                            priceSeniorCitizens = placeSnapshot.child("priceSeniorCitizens").getValue(String.class);
                            priceCameraFee = placeSnapshot.child("priceCameraFee").getValue(String.class);
                            place = placeSnapshot.child("place").getValue(String.class);
                            validFrom = placeSnapshot.child("validFrom").getValue(String.class);
                            validTo = placeSnapshot.child("validTo").getValue(String.class);
                            helpline = placeSnapshot.child("helpline").getValue(String.class);
                            instructions = placeSnapshot.child("instructions").getValue(String.class);
                            ticketId = placeSnapshot.child("ticketId").getValue(String.class);

                            editAccountHolder.setText(accountHolder != null ? accountHolder : "");
                            editAccountNumber.setText(accountNumber != null ? accountNumber : "");
                            editReEnterAccountNumber.setText(accountNumber != null ? accountNumber : "");
                            editIFSC.setText(ifscCode != null ? ifscCode : "");

                            btnSaveBankDetails.setText("Save Changes");
                            btnSaveBankDetails.setOnClickListener(v -> updateBankDetails());
                        } else {
                            btnSaveBankDetails.setText("Save Bank Details");
                            btnSaveBankDetails.setOnClickListener(v -> saveBankDetails());
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        if (isActivityDestroyed) return;
                        showToast("Failed to fetch bank details", false);
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (isActivityDestroyed) return;
                showToast("Failed to fetch user details", false);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            Intent resultIntent = new Intent();
            resultIntent.putExtra("ticketId", ticketId);
            setResult(RESULT_OK, resultIntent);
            finish();
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
        toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 32);

        Animation enterAnim = AnimationUtils.loadAnimation(this, R.anim.toast_enter);
        Animation exitAnim = AnimationUtils.loadAnimation(this, R.anim.toast_exit);
        toastView.startAnimation(enterAnim);
        handler.postDelayed(() -> {
            if (!isActivityDestroyed) toastView.startAnimation(exitAnim);
        }, 2000);

        toast.show();
    }

    private void showErrorDialog(String message) {
        if (isActivityDestroyed) return;
        new AlertDialog.Builder(this)
                .setTitle("Bank Details Error")
                .setMessage(message)
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .setCancelable(false)
                .show();
    }

    private String getAuthHeader() {
        String credentials = RAZORPAY_KEY_ID + ":" + RAZORPAY_KEY_SECRET;
        return "Basic " + android.util.Base64.encodeToString(credentials.getBytes(), android.util.Base64.NO_WRAP);
    }

    private void handleApiError(int statusCode, String responseBody, ProgressDialog progressDialog) {
        if (isActivityDestroyed) return;
        String errorMessage;
        try {
            JSONObject errorJson = new JSONObject(responseBody);
            String errorDescription = errorJson.optString("description", "Unknown error occurred");
            switch (statusCode) {
                case 400:
                    errorMessage = "Invalid bank details provided";
                    break;
                case 401:
                    errorMessage = "Authentication failed. Please contact support";
                    break;
                case 429:
                    errorMessage = "Too many requests. Try again later";
                    break;
                case 500:
                    errorMessage = "Server error. Try again later";
                    break;
                default:
                    errorMessage = errorDescription.isEmpty() ? "An error occurred" : errorDescription;
            }
        } catch (Exception e) {
            errorMessage = "An unexpected error occurred";
        }
        String finalErrorMessage = errorMessage;
        handler.post(() -> {
            if (isActivityDestroyed) return;
            progressDialog.dismiss();
            showErrorDialog(finalErrorMessage);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isActivityDestroyed = true;
        if (!executor.isShutdown()) executor.shutdownNow();
        handler.removeCallbacksAndMessages(null);
        if (authStateListener != null) {
            FirebaseAuth.getInstance().removeAuthStateListener(authStateListener);
        }
    }
}
