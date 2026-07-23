package com.example.bookmyticket.roles.placeadmin;

import com.example.bookmyticket.R;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class PlaceBankDetailsActivity extends AppCompatActivity {

    private static final String TAG = "ManageBankDetailsActivity";
    private static final int MAX_API_RETRIES = 2;
    private static final long DATABASE_TIMEOUT = 5000;
    private EditText editAccountHolder, editAccountNumber, editReEnterAccountNumber, editIFSC;
    private Button btnSaveBankDetails;
    private ImageView backButton;
    private SharedPreferences sharedPreferences;
    private String userUid, adminType, place, ticketId;
    private static final String RAZORPAY_API_URL_CONTACTS = "https://api.razorpay.com/v1/contacts";
    private static final String RAZORPAY_API_URL_FUND_ACCOUNTS = "https://api.razorpay.com/v1/fund_accounts";
    private static final String RAZORPAY_KEY_ID = "rzp_test_O9fu5gbYR1vL8i";
    private static final String RAZORPAY_KEY_SECRET = "lKszbUEo0fGGKf7stA4IWPTX";
    private final OkHttpClient client = new OkHttpClient();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newFixedThreadPool(1);
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final DatabaseReference placeAdminRef = FirebaseDatabase.getInstance().getReference("placeadmin");
    private final DatabaseReference notificationsRef = FirebaseDatabase.getInstance().getReference("placeadmin_notifications");
    private boolean isActivityDestroyed = false;
    private String existingAccountHolder, existingAccountNumber, existingIfscCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_bank_details);

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
            Log.e(TAG, "Failed to init SharedPreferences: " + e.getMessage());
            sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        }

        // Initialize views
        editAccountHolder = findViewById(R.id.editAccountHolder);
        editAccountNumber = findViewById(R.id.editAccountNumber);
        editReEnterAccountNumber = findViewById(R.id.editReEnterAccountNumber);
        editIFSC = findViewById(R.id.editIFSC);
        btnSaveBankDetails = findViewById(R.id.btnSaveBankDetails);
        backButton = findViewById(R.id.backButton);

        // Disable copy-paste for account number fields
        disableCopyPaste(editAccountNumber);
        disableCopyPaste(editReEnterAccountNumber);

        // Setup UI
        setupUI();

        // Process intent
        Intent intent = getIntent();
        userUid = intent.getStringExtra("USER_UID") != null ? intent.getStringExtra("USER_UID") : sharedPreferences.getString("userUid", null);
        adminType = intent.getStringExtra("adminType") != null ? intent.getStringExtra("adminType") : sharedPreferences.getString("adminType", null);
        place = intent.getStringExtra("place") != null ? intent.getStringExtra("place") : sharedPreferences.getString("place", "Unknown");
        ticketId = intent.getStringExtra("ticketId") != null ? intent.getStringExtra("ticketId") : sharedPreferences.getString("ticketId", "N/A");

        // Validate authentication
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || userUid == null || !userUid.equals(user.getUid()) || adminType == null || !adminType.equals("placeAdmin")) {
            Log.e(TAG, "Invalid data: userUid=" + userUid + ", firebaseUid=" + (user != null ? user.getUid() : "null") +
                    ", adminType=" + adminType);
            showToast("Invalid access", false);
            finish();
            return;
        }
        userUid = user.getUid();

        // Load existing bank details
        checkSavedBankDetails();

        // Set listeners
        btnSaveBankDetails.setOnClickListener(v -> updateBankDetails());
        backButton.setOnClickListener(v -> finish());
    }

    private void disableCopyPaste(EditText editText) {
        editText.setTextIsSelectable(false);
        editText.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                return false;
            }
            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }
            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                return false;
            }
            @Override
            public void onDestroyActionMode(ActionMode mode) {}
        });
        editText.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                editText.requestFocus();
            }
            return false;
        });
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
        }
    }

    private void checkSavedBankDetails() {
        placeAdminRef.child(userUid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isActivityDestroyed) return;
                if (snapshot.exists()) {
                    existingAccountHolder = snapshot.child("accountHolder").getValue(String.class);
                    existingAccountNumber = snapshot.child("accountNumber").getValue(String.class);
                    existingIfscCode = snapshot.child("ifscCode").getValue(String.class);

                    editAccountHolder.setText(existingAccountHolder != null ? existingAccountHolder : "");
                    // Mask account number to show only last 4 digits

                        editAccountNumber.setText(existingAccountNumber);
                        editReEnterAccountNumber.setText(existingAccountNumber);

                    editIFSC.setText(existingIfscCode != null ? existingIfscCode : "");
                    btnSaveBankDetails.setText("Update Bank Details");
                } else {
                    btnSaveBankDetails.setText("Save Bank Details");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (isActivityDestroyed) return;
                showToast("Failed to fetch bank details", false);
            }
        });
    }

    private boolean validateInputs(String accountHolder, String accountNumber, String reEnterAccountNumber, String ifscCode) {
        if (accountHolder.isEmpty() || accountNumber.isEmpty() || reEnterAccountNumber.isEmpty() || ifscCode.isEmpty()) {
            showToast("Please fill all fields", false);
            return false;
        }
        // Skip account number matching if it's masked
        if (!accountNumber.startsWith("****") && !accountNumber.equals(reEnterAccountNumber)) {
            showToast("Account numbers do not match", false);
            return false;
        }
        if (!ifscCode.matches("^[A-Z]{4}[A-Z0-9]{7}$")) {
            showToast("Invalid IFSC code (e.g., SBIN0001234)", false);
            return false;
        }
        return true;
    }

    private void updateBankDetails() {
        String accountHolder = editAccountHolder.getText().toString().trim();
        String accountNumber = editAccountNumber.getText().toString().trim();
        String reEnterAccountNumber = editReEnterAccountNumber.getText().toString().trim();
        String ifscCode = editIFSC.getText().toString().trim();

        // If account number is masked, use existing account number
        if (accountNumber.startsWith("****")) {
            accountNumber = existingAccountNumber != null ? existingAccountNumber : "";
            reEnterAccountNumber = existingAccountNumber != null ? existingAccountNumber : "";
        }

        if (!validateInputs(accountHolder, accountNumber, reEnterAccountNumber, ifscCode)) {
            return;
        }

        FirebaseUser user = auth.getCurrentUser();
        if (user == null || !user.getUid().equals(userUid)) {
            showToast("Authentication failed", false);
            return;
        }

        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Validating bank account...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        String name = "Default Name";
        String email = "default@example.com";
        String phone = sharedPreferences.getString("phoneNumber", "0000000000");

        // Create change log
        StringBuilder changes = new StringBuilder();
        if (!accountHolder.equals(existingAccountHolder)) {
            changes.append("Account Holder changed from ")
                    .append(existingAccountHolder != null ? existingAccountHolder : "N/A")
                    .append(" to ")
                    .append(accountHolder)
                    .append("; ");
        }
        if (!accountNumber.equals(existingAccountNumber) && !accountNumber.startsWith("****")) {
            changes.append("Account Number changed from ")
                    .append(existingAccountNumber != null ?
                            "****" + existingAccountNumber.substring(existingAccountNumber.length() - 4) : "N/A")
                    .append(" to ")
                    .append("****").append(accountNumber.substring(accountNumber.length() - 4))
                    .append("; ");
        }
        if (!ifscCode.equals(existingIfscCode)) {
            changes.append("IFSC Code changed from ")
                    .append(existingIfscCode != null ? existingIfscCode : "N/A")
                    .append(" to ")
                    .append(ifscCode)
                    .append("; ");
        }

        String changeLog = changes.length() > 0 ? changes.toString() : "No changes made";

        validateBankDetails(accountHolder, accountNumber, ifscCode, user.getUid(), name, email, phone, progressDialog, 0, changeLog);
    }

    private void validateBankDetails(String accountHolder, String accountNumber, String ifscCode, String userId,
                                     String name, String email, String phone, ProgressDialog progressDialog,
                                     int retryCount, String changeLog) {
        if (retryCount >= MAX_API_RETRIES) {
            handler.post(() -> {
                if (!isActivityDestroyed) {
                    progressDialog.dismiss();
                    showErrorDialog("Failed to validate bank details.");
                }
            });
            return;
        }

        executor.execute(() -> {
            if (isActivityDestroyed) return;
            try {
                if (!accountNumber.matches("^[0-9]{9,18}$")) {
                    handler.post(() -> {
                        if (!isActivityDestroyed) {
                            progressDialog.dismiss();
                            showErrorDialog("Invalid account number (9-18 digits)");
                        }
                    });
                    return;
                }
                if (!ifscCode.matches("^[A-Z]{4}[A-Z0-9]{7}$")) {
                    handler.post(() -> {
                        if (!isActivityDestroyed) {
                            progressDialog.dismiss();
                            showErrorDialog("Invalid IFSC code");
                        }
                    });
                    return;
                }
                if (!accountHolder.matches("^[A-Za-z\\s]+$")) {
                    handler.post(() -> {
                        if (!isActivityDestroyed) {
                            progressDialog.dismiss();
                            showErrorDialog("Invalid account holder name");
                        }
                    });
                    return;
                }

                handler.post(() -> {
                    if (isActivityDestroyed) return;
                    progressDialog.setMessage("Linking bank account...");
                    createRazorpayContactAndFundAccount(name, email, phone, accountHolder, accountNumber, ifscCode, userId, progressDialog, retryCount, changeLog);
                });
            } catch (Exception e) {
                handler.post(() -> {
                    if (!isActivityDestroyed) {
                        progressDialog.dismiss();
                        showErrorDialog("Error validating bank details");
                    }
                });
            }
        });
    }

    private void createRazorpayContactAndFundAccount(String name, String email, String phone, String accountHolder,
                                                     String accountNumber, String ifscCode, String userId,
                                                     ProgressDialog progressDialog, int retryCount, String changeLog) {
        executor.execute(() -> {
            if (isActivityDestroyed) return;
            try {
                String contactId = createRazorpayContact(name, email, phone, retryCount);
                if (contactId == null) {
                    handler.post(() -> {
                        if (!isActivityDestroyed) {
                            progressDialog.dismiss();
                            showErrorDialog("Unable to create contact");
                        }
                    });
                    return;
                }

                String fundAccountId = createRazorpayFundAccount(contactId, accountHolder, accountNumber, ifscCode, retryCount);
                if (fundAccountId == null) {
                    handler.post(() -> {
                        if (!isActivityDestroyed) {
                            progressDialog.dismiss();
                            showErrorDialog("Unable to link bank account");
                        }
                    });
                    return;
                }

                handler.post(() -> {
                    if (isActivityDestroyed) return;
                    saveToFirebase(userId, accountHolder, accountNumber, ifscCode, fundAccountId, progressDialog, changeLog);
                });
            } catch (Exception e) {
                handler.post(() -> {
                    if (!isActivityDestroyed) {
                        progressDialog.dismiss();
                        showErrorDialog("Error linking bank account");
                    }
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
                .put("type", "employee");

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
            Thread.sleep(500 * (retryCount + 1));
            return createRazorpayContact(name, email, phone, retryCount + 1);
        } else {
            handler.post(() -> handleApiError(contactResponse.code(), responseBody));
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
            Thread.sleep(500 * (retryCount + 1));
            return createRazorpayFundAccount(contactId, accountHolder, accountNumber, ifscCode, retryCount + 1);
        } else {
            handler.post(() -> handleApiError(fundAccountResponse.code(), responseBody));
            return null;
        }
    }

    private void saveToFirebase(String userId, String accountHolder, String accountNumber, String ifscCode,
                                String fundAccountId, ProgressDialog progressDialog, String changeLog) {
        HashMap<String, Object> bankDetails = new HashMap<>();
        bankDetails.put("accountHolder", accountHolder);
        bankDetails.put("accountNumber", accountNumber);
        bankDetails.put("ifscCode", ifscCode);
        bankDetails.put("fundAccountId", fundAccountId);
        bankDetails.put("lastUpdated", System.currentTimeMillis());

        Runnable timeoutRunnable = () -> {
            if (isActivityDestroyed) return;
            progressDialog.dismiss();
            showToast("Database operation timed out", false);
            finish();
        };
        handler.postDelayed(timeoutRunnable, DATABASE_TIMEOUT);

        placeAdminRef.child(userId).updateChildren(bankDetails).addOnCompleteListener(task -> {
            handler.removeCallbacks(timeoutRunnable);
            if (isActivityDestroyed) return;
            progressDialog.dismiss();
            if (task.isSuccessful()) {
                saveNotification(userId, "BANK_DETAILS_UPDATED", changeLog);
                showToast("Bank details updated successfully", true);
                finish();
            } else {
                showToast("Failed to update bank details", false);
            }
        });
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

    private void showToast(String message, boolean isSuccess) {
        if (isActivityDestroyed) return;
        View toastView = getLayoutInflater().inflate(R.layout.custom_toast1, null);
        TextView toastText = toastView.findViewById(R.id.toast_message);
        ImageView toastIcon = toastView.findViewById(R.id.toast_icon);
        toastText.setText(message);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            toastText.setTypeface(getResources().getFont(R.font.poppins_regular));
        }
        toastView.setBackgroundColor(isSuccess ? getResources().getColor(R.color.success_green) : getResources().getColor(R.color.error_color));
        toastIcon.setImageResource(isSuccess ? R.drawable.ic_success : R.drawable.ic_error);

        Toast toast = new Toast(this);
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setView(toastView);
        toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 32);

        Animation enterAnim = AnimationUtils.loadAnimation(this, R.anim.toast_enter);
        toastView.startAnimation(enterAnim);
        toast.show();
    }

    private void showErrorDialog(String message) {
        if (isActivityDestroyed) return;
        new AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage(message)
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .setCancelable(false)
                .show();
    }

    private String getAuthHeader() {
        String credentials = RAZORPAY_KEY_ID + ":" + RAZORPAY_KEY_SECRET;
        return "Basic " + android.util.Base64.encodeToString(credentials.getBytes(), android.util.Base64.NO_WRAP);
    }

    private void handleApiError(int statusCode, String responseBody) {
        if (isActivityDestroyed) return;
        String errorMessage;
        try {
            JSONObject errorJson = new JSONObject(responseBody);
            String errorDescription = errorJson.optString("description", "Unknown error");
            switch (statusCode) {
                case 400: errorMessage = "Invalid bank details: " + errorDescription; break;
                case 401: errorMessage = "Authentication failed"; break;
                case 429: errorMessage = "Too many requests. Please try again later."; break;
                case 500: errorMessage = "Server error. Please try again later."; break;
                default: errorMessage = errorDescription.isEmpty() ? "An error occurred" : errorDescription;
            }
        } catch (Exception e) {
            errorMessage = "Unexpected error occurred";
        }
        showErrorDialog(errorMessage);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isActivityDestroyed = true;
        executor.shutdownNow();
        handler.removeCallbacksAndMessages(null);
    }
}
