package com.example.bookmyticket.roles.parkingadmin;

import com.example.bookmyticket.R;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Objects;

public class ParkingPriceDetailsActivity extends AppCompatActivity {

    private static final String TAG = "ParkingPriceDetails";
    private TextInputEditText parkingArea, carParkingPrice, bikeParkingPrice, busParkingPrice;
    private TextInputLayout parkingAreaLayout, carParkingPriceLayout, bikeParkingPriceLayout, busParkingPriceLayout;
    private MaterialButton submitButton;
    private ImageView backButton;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isActivityDestroyed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parkingdetails);

        setupUI();
        initializeViews();
        setupInputValidation();

        // Get intent data
        Intent intent = getIntent();
        String userUid = intent.getStringExtra("USER_UID");
        String phoneNumber = intent.getStringExtra("phoneNumber");
        String email = intent.getStringExtra("email");
        String adminType = intent.getStringExtra("adminType");
        boolean isLoggedIn = intent.getBooleanExtra("isLoggedIn", false);
        boolean isNewUser = intent.getBooleanExtra("isNewUser", false);

        if (userUid == null || phoneNumber == null || adminType == null || !adminType.equals("parkingAdmin") || !isLoggedIn) {
            showToast("Invalid access to ParkingPriceDetailsActivity", false);
            finish();
            return;
        }

        submitButton.setOnClickListener(v -> {
            if (validateInputs()) {
                navigateToParkingAdminSetup(
                        userUid, phoneNumber, email, adminType, isLoggedIn, isNewUser,
                        parkingArea.getText().toString().trim(),
                        carParkingPrice.getText().toString().trim(),
                        bikeParkingPrice.getText().toString().trim(),
                        busParkingPrice.getText().toString().trim()
                );
            }
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
        } else {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        backButton = findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> finish());
    }

    private void initializeViews() {
        parkingArea = findViewById(R.id.parking_area);
        carParkingPrice = findViewById(R.id.car_parking_price);
        bikeParkingPrice = findViewById(R.id.bike_parking_price);
        busParkingPrice = findViewById(R.id.bus_parking_price);
        parkingAreaLayout = findViewById(R.id.parking_area_layout);
        carParkingPriceLayout = findViewById(R.id.car_parking_price_layout);
        bikeParkingPriceLayout = findViewById(R.id.bike_parking_price_layout);
        busParkingPriceLayout = findViewById(R.id.bus_parking_price_layout);
        submitButton = findViewById(R.id.submit_button);
    }

    private void setupInputValidation() {
        parkingArea.addTextChangedListener(createTextWatcher(parkingAreaLayout, input ->
                input.isEmpty() ? "Parking area name is required" : null));

        carParkingPrice.addTextChangedListener(createTextWatcher(carParkingPriceLayout, input ->
                validatePrice(input, "Car parking price")));

        bikeParkingPrice.addTextChangedListener(createTextWatcher(bikeParkingPriceLayout, input ->
                validatePrice(input, "Bike parking price")));

        busParkingPrice.addTextChangedListener(createTextWatcher(busParkingPriceLayout, input ->
                validatePrice(input, "Bus parking price")));
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

    private String validatePrice(String input, String fieldName) {
        if (input.isEmpty()) {
            return fieldName + " is required";
        }
        try {
            double price = Double.parseDouble(input);
            if (price <= 0) {
                return "Price must be greater than 0";
            }
            return null;
        } catch (NumberFormatException e) {
            return "Invalid price format";
        }
    }

    private boolean validateInputs() {
        boolean isValid = true;

        isValid &= validateField(parkingArea, parkingAreaLayout, "Parking area name is required");
        isValid &= validatePriceField(carParkingPrice, carParkingPriceLayout, "Car parking price");
        isValid &= validatePriceField(bikeParkingPrice, bikeParkingPriceLayout, "Bike parking price");
        isValid &= validatePriceField(busParkingPrice, busParkingPriceLayout, "Bus parking price");

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

    private boolean validatePriceField(TextInputEditText field, TextInputLayout layout, String fieldName) {
        String text = field.getText().toString().trim();
        if (text.isEmpty()) {
            layout.setError(fieldName + " is required");
            return false;
        }
        try {
            double price = Double.parseDouble(text);
            if (price <= 0) {
                layout.setError("Price must be greater than 0");
                return false;
            }
            layout.setError(null);
            return true;
        } catch (NumberFormatException e) {
            layout.setError("Invalid price format");
            return false;
        }
    }

    private void navigateToParkingAdminSetup(String userUid, String phoneNumber, String email, String adminType,
                                             boolean isLoggedIn, boolean isNewUser, String parkingArea,
                                             String carPrice, String bikePrice, String busPrice) {
        Intent intent = new Intent(this, ParkingAdminSetupActivity.class);
        intent.putExtra("USER_UID", userUid);
        intent.putExtra("phoneNumber", phoneNumber);
        intent.putExtra("email", email);
        intent.putExtra("adminType", adminType);
        intent.putExtra("isLoggedIn", isLoggedIn);
        intent.putExtra("isNewUser", isNewUser);
        intent.putExtra("parkingArea", parkingArea);
        intent.putExtra("carParkingPrice", carPrice);
        intent.putExtra("bikeParkingPrice", bikePrice);
        intent.putExtra("busParkingPrice", busPrice);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        finish();
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
