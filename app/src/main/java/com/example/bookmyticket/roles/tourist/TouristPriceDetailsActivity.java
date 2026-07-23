package com.example.bookmyticket.roles.tourist;

import com.example.bookmyticket.R;
import com.example.bookmyticket.auth.LoginActivity;
import com.example.bookmyticket.features.payment.BankActivity;
import com.example.bookmyticket.roles.placeadmin.AdminSlide2Activity;

import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

public class TouristPriceDetailsActivity extends AppCompatActivity {
    private static final String TAG = "TouristPriceDetails";
    private static final String PREFS_NAME = "UserPrefs";
    private static final String TICKET_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int TICKET_LENGTH = 6;
    private static final int REQUEST_CODE_BANK = 1001;

    private EditText editPlace, editTextAmount, editTextStudents, editTextChildren, editTextSeniorCitizens, editTextCameraFee, instructions, editHelpline;
    private TextView editValidFrom, editValidTo;
    private Button btnNext;
    private ImageView backButton;
    private SharedPreferences sharedPreferences;
    private String userUid, phoneNumber, adminType, ticketId, email;
    private boolean isLoggedIn, isNewUser;
    private String validFromFull, validToFull;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
    private final SimpleDateFormat fullDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
    private final Random random = new Random();
    private boolean isNavigating;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tourist_price_details);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Initialize SharedPreferences
        initSharedPreferences();

        // Set system UI colors
        setSystemColors();

        // Initialize views
        initViews();

        // Handle intent and state restoration
        isNavigating = false;
        if (savedInstanceState != null) {
            restoreInstanceState(savedInstanceState);
        } else {
            processIntent(getIntent());
        }

        // Validate authentication
        if (!validateAuth()) {
            navigateToLoginActivity();
            return;
        }

        // Ensure ticketId
        if (ticketId == null) {
            ticketId = generateTicketId();
            sharedPreferences.edit().putString("ticketId", ticketId).apply();
        }

        // Set listeners
        setupListeners();

        // Initial button state
        updateNextButtonState();
    }

    private void initSharedPreferences() {
        try {
            MasterKey masterKey = new MasterKey.Builder(this)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            sharedPreferences = EncryptedSharedPreferences.create(
                    this, PREFS_NAME, masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            Log.e(TAG, "EncryptedSharedPreferences failed: " + e.getMessage());
            sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        }
    }

    private void setSystemColors() {
        Window window = getWindow();
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.primary_blue));
        window.setNavigationBarColor(ContextCompat.getColor(this, R.color.white));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.getInsetsController().setSystemBarsAppearance(
                    0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            );
        }
    }

    private void initViews() {
        editPlace = findViewById(R.id.editPlace);
        editTextAmount = findViewById(R.id.editTextAmount);
        editTextStudents = findViewById(R.id.editTextStudents);
        editTextChildren = findViewById(R.id.editTextChildren);
        editTextSeniorCitizens = findViewById(R.id.editTextSeniorCitizens);
        editTextCameraFee = findViewById(R.id.editTextCameraFee);
        instructions = findViewById(R.id.instructions);
        editHelpline = findViewById(R.id.editHelpline);
        editValidFrom = findViewById(R.id.editValidFrom);
        editValidTo = findViewById(R.id.editValidTo);
        btnNext = findViewById(R.id.btnNext);
        backButton = findViewById(R.id.back_button);
    }

    private void processIntent(Intent intent) {
        userUid = intent.getStringExtra("USER_UID");
        phoneNumber = intent.getStringExtra("phoneNumber");
        isLoggedIn = intent.getBooleanExtra("isLoggedIn", false);
        isNewUser = intent.getBooleanExtra("isNewUser", false);
        adminType = intent.getStringExtra("adminType");
        ticketId = intent.getStringExtra("ticketId");
        email = intent.getStringExtra("email");

        editPlace.setText(intent.getStringExtra("place"));
        editTextAmount.setText(intent.getStringExtra("priceAdults"));
        editTextStudents.setText(intent.getStringExtra("priceStudents"));
        editTextChildren.setText(intent.getStringExtra("priceChildren"));
        editTextSeniorCitizens.setText(intent.getStringExtra("priceSeniorCitizens"));
        editTextCameraFee.setText(intent.getStringExtra("priceCameraFee"));
        instructions.setText(intent.getStringExtra("instructions"));
        editHelpline.setText(intent.getStringExtra("helpline"));

        setDefaultValidTimes();
        setTimeFromIntent(intent.getStringExtra("validFrom"), editValidFrom, true);
        setTimeFromIntent(intent.getStringExtra("validTo"), editValidTo, false);
    }

    private void setTimeFromIntent(String time, TextView textView, boolean isFrom) {
        if (time != null && isValidTime(time)) {
            try {
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(fullDateFormat.parse(time));
                if (isFrom) {
                    validFromFull = fullDateFormat.format(calendar.getTime());
                } else {
                    validToFull = fullDateFormat.format(calendar.getTime());
                }
                textView.setText(timeFormat.format(calendar.getTime()));
            } catch (ParseException e) {
                Log.e(TAG, "Error parsing time: " + e.getMessage());
            }
        }
    }

    private boolean validateAuth() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null && userUid != null && userUid.equals(user.getUid()) &&
                phoneNumber != null && isLoggedIn && adminType != null && adminType.equals("placeAdmin");
    }

    private String generateTicketId() {
        StringBuilder sb = new StringBuilder(TICKET_LENGTH);
        for (int i = 0; i < TICKET_LENGTH; i++) {
            sb.append(TICKET_CHARS.charAt(random.nextInt(TICKET_CHARS.length())));
        }
        return sb.toString();
    }

    private void setDefaultValidTimes() {
        Calendar calendar = Calendar.getInstance();
        validFromFull = fullDateFormat.format(calendar.getTime());
        editValidFrom.setText(timeFormat.format(calendar.getTime()));

        calendar.add(Calendar.HOUR_OF_DAY, 3);
        validToFull = fullDateFormat.format(calendar.getTime());
        editValidTo.setText(timeFormat.format(calendar.getTime()));
    }

    private void setupListeners() {
        TextWatcher validationWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                updateNextButtonState();
            }
        };

        for (EditText editText : new EditText[]{editPlace, editTextAmount, editTextStudents, editTextChildren,
                editTextSeniorCitizens, editTextCameraFee, instructions, editHelpline}) {
            editText.addTextChangedListener(validationWatcher);
        }
        editValidFrom.addTextChangedListener(validationWatcher);
        editValidTo.addTextChangedListener(validationWatcher);

        editValidFrom.setOnClickListener(v -> showTimePickerDialog(editValidFrom, true));
        editValidTo.setOnClickListener(v -> showTimePickerDialog(editValidTo, false));
        backButton.setOnClickListener(v -> onBackPressed());
        btnNext.setOnClickListener(v -> {
            if (isNavigating) {
                Log.d(TAG, "Navigation blocked: isNavigating is true");
                return;
            }
            String error = validateInputs();
            if (error != null) {
                new AlertDialog.Builder(this)
                        .setTitle("Invalid Input")
                        .setMessage(error)
                        .setPositiveButton("OK", null)
                        .show();
                Log.d(TAG, "Input validation failed: " + error);
                return;
            }
            isNavigating = true;
            Log.d(TAG, "Inputs validated, proceeding to check bank details");
            checkBankDetailsAndNavigate();
        });
    }

    private void showTimePickerDialog(TextView textView, boolean isFromTime) {
        Calendar calendar = Calendar.getInstance();
        try {
            String fullDateTime = isFromTime ? validFromFull : validToFull;
            if (fullDateTime != null) {
                calendar.setTime(fullDateFormat.parse(fullDateTime));
            }
        } catch (Exception e) {
            calendar.setTimeInMillis(System.currentTimeMillis());
        }

        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);

        TimePickerDialog timePickerDialog = new TimePickerDialog(
                this,
                (view, selectedHour, selectedMinute) -> {
                    calendar.set(Calendar.HOUR_OF_DAY, selectedHour);
                    calendar.set(Calendar.MINUTE, selectedMinute);
                    textView.setText(timeFormat.format(calendar.getTime()));
                    if (isFromTime) {
                        validFromFull = fullDateFormat.format(calendar.getTime());
                    } else {
                        validToFull = fullDateFormat.format(calendar.getTime());
                    }
                    updateNextButtonState();
                },
                hour,
                minute,
                false // 12-hour format with AM/PM
        );
        timePickerDialog.show();
    }

    private void updateNextButtonState() {
        boolean isValid = areAllFieldsFilled();
        btnNext.setEnabled(isValid);
        btnNext.setBackgroundTintList(ContextCompat.getColorStateList(
                this, isValid ? R.color.button_enabled : R.color.button_disabled));
    }

    private boolean areAllFieldsFilled() {
        for (EditText editText : new EditText[]{editPlace, editTextAmount, editTextStudents, editTextChildren,
                editTextSeniorCitizens, editTextCameraFee, instructions, editHelpline}) {
            if (editText.getText().toString().trim().isEmpty()) return false;
        }
        return !editValidFrom.getText().toString().trim().isEmpty() &&
                !editValidTo.getText().toString().trim().isEmpty();
    }

    private String validateInputs() {
        String place = editPlace.getText().toString().trim();
        String amount = editTextAmount.getText().toString().trim();
        String students = editTextStudents.getText().toString().trim();
        String children = editTextChildren.getText().toString().trim();
        String seniorCitizens = editTextSeniorCitizens.getText().toString().trim();
        String cameraFee = editTextCameraFee.getText().toString().trim();
        String helpline = editHelpline.getText().toString().trim();
        String instructionsText = instructions.getText().toString().trim();
        String validFrom = editValidFrom.getText().toString().trim();
        String validTo = editValidTo.getText().toString().trim();

        if (place.isEmpty()) return "Place is required.";
        if (!isValidNumber(amount)) return "Adult price must be a non-negative number.";
        if (!isValidNumber(students)) return "Student price must be a non-negative number.";
        if (!isValidNumber(children)) return "Child price must be a non-negative number.";
        if (!isValidNumber(seniorCitizens)) return "Senior citizen price must be a non-negative number.";
        if (!isValidNumber(cameraFee)) return "Camera fee must be a non-negative number.";
        if (helpline.isEmpty()) return "Helpline is required.";
        if (instructionsText.isEmpty()) return "Instructions are required.";
        if (validFrom.isEmpty() || !isValidTime(validFrom)) return "Valid From time must be in hh:mm a format.";
        if (validTo.isEmpty() || !isValidTime(validTo)) return "Valid To time must be in hh:mm a format.";

        try {
            Date fromDateTime = fullDateFormat.parse(validFromFull);
            Date toDateTime = fullDateFormat.parse(validToFull);
            if (fromDateTime != null && toDateTime != null && !toDateTime.after(fromDateTime)) {
                return "Valid To time must be after Valid From time.";
            }
        } catch (ParseException e) {
            return "Invalid date-time format.";
        }
        return null;
    }

    private boolean isValidNumber(String input) {
        try {
            return !input.isEmpty() && Double.parseDouble(input) >= 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isValidTime(String time) {
        try {
            timeFormat.setLenient(false);
            timeFormat.parse(time);
            return true;
        } catch (ParseException e) {
            return false;
        }
    }

    private void checkBankDetailsAndNavigate() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.e(TAG, "No authenticated user found, navigating to LoginActivity");
            isNavigating = false;
            navigateToLoginActivity();
            return;
        }

        Log.d(TAG, "Checking bank details for user UID: " + user.getUid());
        DatabaseReference placeAdminRef = FirebaseDatabase.getInstance().getReference("placeadmin").child(user.getUid());
        placeAdminRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.d(TAG, "Database snapshot received for user UID: " + user.getUid());
                if (snapshot.exists()) {
                    String accountHolder = snapshot.child("accountHolder").getValue(String.class);
                    String accountNumber = snapshot.child("accountNumber").getValue(String.class);
                    String ifscCode = snapshot.child("ifscCode").getValue(String.class);
                    String savedTicketId = snapshot.child("ticketId").getValue(String.class);
                    Log.d(TAG, "Snapshot data - accountHolder: " + accountHolder + ", accountNumber: " + accountNumber +
                            ", ifscCode: " + ifscCode + ", savedTicketId: " + savedTicketId);

                    if (accountHolder != null && accountNumber != null && ifscCode != null) {
                        Log.d(TAG, "Bank details found, navigating to AdminSlide2Activity with ticketId: " +
                                (savedTicketId != null ? savedTicketId : ticketId));
                        navigateToAdminSlide2(savedTicketId != null ? savedTicketId : ticketId);
                    } else {
                        Log.d(TAG, "Bank details missing, navigating to BankActivity");
                        navigateToBankActivity(true);
                    }
                } else {
                    Log.d(TAG, "No snapshot exists for user, navigating to BankActivity");
                    navigateToBankActivity(true);
                }
                isNavigating = false;
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Database query cancelled: " + error.getMessage());
                Toast.makeText(TouristPriceDetailsActivity.this, "Failed to check bank details: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                navigateToBankActivity(true);
                isNavigating = false;
            }
        });
    }

    private void navigateToBankActivity(boolean includePriceDetails) {
        if (isFinishing() || isDestroyed()) {
            Log.w(TAG, "Activity is finishing or destroyed, cannot navigate to BankActivity");
            isNavigating = false;
            return;
        }
        Log.d(TAG, "Starting BankActivity, includePriceDetails: " + includePriceDetails);
        Intent intent = new Intent(this, BankActivity.class);
        putCommonExtras(intent);
        if (includePriceDetails) {
            putPriceDetailsExtras(intent);
        }
        try {
            startActivityForResult(intent, REQUEST_CODE_BANK);
            Log.d(TAG, "BankActivity started successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start BankActivity: " + e.getMessage());
            Toast.makeText(this, "Error starting BankActivity", Toast.LENGTH_SHORT).show();
        }
        isNavigating = false;
    }

    private void navigateToAdminSlide2(String ticketIdToUse) {
        Log.d(TAG, "Starting AdminSlide2Activity with ticketId: " + ticketIdToUse);
        Intent intent = new Intent(this, AdminSlide2Activity.class);
        putCommonExtras(intent);
        intent.putExtra("ticketId", ticketIdToUse);
        putPriceDetailsExtras(intent);
        try {
            startActivityForResult(intent, REQUEST_CODE_BANK);
            Log.d(TAG, "AdminSlide2Activity started successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start AdminSlide2Activity: " + e.getMessage());
            Toast.makeText(this, "Error starting AdminSlide2Activity", Toast.LENGTH_SHORT).show();
        }
        isNavigating = false;
    }

    private void navigateToPlaceAdminDashboard() {
        if (isNavigating) {
            Log.d(TAG, "Navigation blocked: isNavigating is true");
            return;
        }
        isNavigating = true;
        Log.d(TAG, "Navigating to LoginActivity with role selection state");
        Intent intent = new Intent(this, LoginActivity.class);
        intent.putExtra("USER_UID", userUid);
        intent.putExtra("phoneNumber", phoneNumber);
        intent.putExtra("isLoggedIn", isLoggedIn);
        intent.putExtra("isNewUser", isNewUser);
        intent.putExtra("adminType", adminType);
        intent.putExtra("email", email);
        intent.putExtra("showRoleSelection", true);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        isNavigating = false;
    }

    private void navigateToLoginActivity() {
        if (isNavigating) {
            Log.d(TAG, "Navigation blocked: isNavigating is true");
            return;
        }
        isNavigating = true;
        Log.d(TAG, "Navigating to LoginActivity with role selection state");
        Intent intent = new Intent(this, LoginActivity.class);
        intent.putExtra("showRoleSelection", true);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        isNavigating = false;
    }

    private void putCommonExtras(Intent intent) {
        intent.putExtra("USER_UID", userUid);
        intent.putExtra("phoneNumber", phoneNumber);
        intent.putExtra("isLoggedIn", isLoggedIn);
        intent.putExtra("isNewUser", isNewUser);
        intent.putExtra("adminType", adminType);
        intent.putExtra("email", email);
    }

    private void putPriceDetailsExtras(Intent intent) {
        intent.putExtra("place", editPlace.getText().toString().trim());
        intent.putExtra("priceAdults", editTextAmount.getText().toString().trim());
        intent.putExtra("priceStudents", editTextStudents.getText().toString().trim());
        intent.putExtra("priceChildren", editTextChildren.getText().toString().trim());
        intent.putExtra("priceSeniorCitizens", editTextSeniorCitizens.getText().toString().trim());
        intent.putExtra("priceCameraFee", editTextCameraFee.getText().toString().trim());
        intent.putExtra("instructions", instructions.getText().toString().trim());
        intent.putExtra("helpline", editHelpline.getText().toString().trim());
        intent.putExtra("validFrom", validFromFull);
        intent.putExtra("validTo", validToFull);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("place", editPlace.getText().toString().trim());
        outState.putString("priceAdults", editTextAmount.getText().toString().trim());
        outState.putString("priceStudents", editTextStudents.getText().toString().trim());
        outState.putString("priceChildren", editTextChildren.getText().toString().trim());
        outState.putString("priceSeniorCitizens", editTextSeniorCitizens.getText().toString().trim());
        outState.putString("priceCameraFee", editTextCameraFee.getText().toString().trim());
        outState.putString("instructions", instructions.getText().toString().trim());
        outState.putString("helpline", editHelpline.getText().toString().trim());
        outState.putString("validFrom", validFromFull);
        outState.putString("validTo", validToFull);
        outState.putString("ticketId", ticketId);
        outState.putString("USER_UID", userUid);
        outState.putString("phoneNumber", phoneNumber);
        outState.putString("adminType", adminType);
        outState.putString("email", email);
        outState.putBoolean("isLoggedIn", isLoggedIn);
        outState.putBoolean("isNewUser", isNewUser);
    }

    private void restoreInstanceState(Bundle savedInstanceState) {
        editPlace.setText(savedInstanceState.getString("place", ""));
        editTextAmount.setText(savedInstanceState.getString("priceAdults", ""));
        editTextStudents.setText(savedInstanceState.getString("priceStudents", ""));
        editTextChildren.setText(savedInstanceState.getString("priceChildren", ""));
        editTextSeniorCitizens.setText(savedInstanceState.getString("priceSeniorCitizens", ""));
        editTextCameraFee.setText(savedInstanceState.getString("priceCameraFee", ""));
        instructions.setText(savedInstanceState.getString("instructions", ""));
        editHelpline.setText(savedInstanceState.getString("helpline", ""));
        ticketId = savedInstanceState.getString("ticketId");
        userUid = savedInstanceState.getString("USER_UID");
        phoneNumber = savedInstanceState.getString("phoneNumber");
        adminType = savedInstanceState.getString("adminType");
        email = savedInstanceState.getString("email");
        isLoggedIn = savedInstanceState.getBoolean("isLoggedIn", false);
        isNewUser = savedInstanceState.getBoolean("isNewUser", false);

        setTimeFromIntent(savedInstanceState.getString("validFrom"), editValidFrom, true);
        setTimeFromIntent(savedInstanceState.getString("validTo"), editValidTo, false);
        if (validFromFull == null || validToFull == null) {
            setDefaultValidTimes();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_BANK && resultCode == RESULT_OK && data != null) {
            String newTicketId = data.getStringExtra("ticketId");
            if (newTicketId != null) {
                ticketId = newTicketId;
                sharedPreferences.edit().putString("ticketId", ticketId).apply();
                Log.d(TAG, "Updated ticketId from BankActivity: " + ticketId);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isNavigating = false;
    }

    @Override
    public void onBackPressed() {
        if (isNavigating) {
            Log.d(TAG, "Back pressed blocked: isNavigating is true");
            return;
        }
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Log.d(TAG, "No authenticated user, navigating to LoginActivity");
            navigateToLoginActivity();
        } else {
            Log.d(TAG, "Authenticated user found, navigating to role selection");
            navigateToPlaceAdminDashboard();
        }
    }
}
