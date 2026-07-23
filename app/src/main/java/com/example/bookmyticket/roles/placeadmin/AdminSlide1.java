package com.example.bookmyticket.roles.placeadmin;

import com.example.bookmyticket.R;

import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Color;
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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Random;

public class AdminSlide1 extends AppCompatActivity {
    private EditText editPlace, editTextAmount, editTextStudents, editTextChildren, editTextSeniorCitizens, editTextCameraFee, instructions;
    private EditText editHelpline;
    private TextView editValidFrom, editValidTo;
    private Button btnNext;
    private ImageView backButton;
    private final SimpleDateFormat fullDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
    private String validFromFull;
    private String validToFull;
    private String ticketId;
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int TKT_LEN = 6;
    private Random rnd = new Random();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.admin_slide1);
        setSystemColors();

        initializeViews();
        setButtonListeners();
        setDefaultValidTimes();
        setupTextWatchers();
        updateNextButtonState();

        if (ticketId == null) {
            ticketId = generateTicketId();
        }
    }

    private String generateTicketId() {
        StringBuilder sb = new StringBuilder(TKT_LEN);
        for (int i = 0; i < TKT_LEN; i++) {
            sb.append(CHARS.charAt(rnd.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    private void setDefaultValidTimes() {
        Calendar calendar = Calendar.getInstance();
        validFromFull = fullDateFormat.format(calendar.getTime());
        editValidFrom.setText(timeFormat.format(calendar.getTime()));

        calendar.add(Calendar.HOUR, 3);
        validToFull = fullDateFormat.format(calendar.getTime());
        editValidTo.setText(timeFormat.format(calendar.getTime()));
    }

    private void setSystemColors() {
        Window window = getWindow();
        window.setStatusBarColor(Color.parseColor("#FFFFFF"));
        window.setNavigationBarColor(Color.parseColor("#FFFFFF"));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                );
            }
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            );
        }
    }

    private void initializeViews() {
        editPlace = findViewById(R.id.editPlace);
        editTextAmount = findViewById(R.id.editTextAmount);
        editTextStudents = findViewById(R.id.editTextStudents);
        editTextChildren = findViewById(R.id.editTextChildren);
        editTextSeniorCitizens = findViewById(R.id.editTextSeniorCitizens);
        editTextCameraFee = findViewById(R.id.editTextCameraFee);
        instructions = findViewById(R.id.instructions);
        editValidFrom = findViewById(R.id.editValidFrom);
        editValidTo = findViewById(R.id.editValidTo);
        editHelpline = findViewById(R.id.editHelpline);
        btnNext = findViewById(R.id.btnNext);
        backButton = findViewById(R.id.backButton);

        // Log null views for debugging
        if (btnNext == null) {
            Log.e("AdminSlide1", "btnNext is null. Check if R.id.btnNext exists in layout.");
        }
        if (backButton == null) {
            Log.e("AdminSlide1", "backButton is null. Check if R.id.backButton exists in layout.");
        }
    }

    private void setButtonListeners() {
        if (editValidFrom == null || editValidTo == null || backButton == null || btnNext == null) {
            Log.e("AdminSlide1", "Cannot set listeners: One or more views are null.");
            return;
        }

        editValidFrom.setOnClickListener(v -> showTimePickerDialog(editValidFrom, true));
        editValidTo.setOnClickListener(v -> showTimePickerDialog(editValidTo, false));
        backButton.setOnClickListener(v -> finish());
        btnNext.setOnClickListener(v -> {
            if (!areAllFieldsFilled()) {
                new AlertDialog.Builder(this)
                        .setTitle("Incomplete Form")
                        .setMessage("Please fill in all fields before proceeding.")
                        .setPositiveButton("OK", null)
                        .show();
                return;
            }

            Intent intent = new Intent(AdminSlide1.this, AdminSlide2Activity.class);
            intent.putExtra("place", editPlace.getText().toString().trim());
            intent.putExtra("priceAdults", editTextAmount.getText().toString().trim());
            intent.putExtra("priceStudents", editTextStudents.getText().toString().trim());
            intent.putExtra("priceChildren", editTextChildren.getText().toString().trim());
            intent.putExtra("priceSeniorCitizens", editTextSeniorCitizens.getText().toString().trim());
            intent.putExtra("priceCameraFee", editTextCameraFee.getText().toString().trim());
            intent.putExtra("validFrom", validFromFull);
            intent.putExtra("validTo", validToFull);
            intent.putExtra("helpline", editHelpline.getText().toString().trim());
            intent.putExtra("instructions", instructions.getText().toString().trim());
            intent.putExtra("ticketId", ticketId);
            startActivityForResult(intent, 1001);
        });
    }

    private void setupTextWatchers() {
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                updateNextButtonState();
            }
        };

        editPlace.addTextChangedListener(watcher);
        editTextAmount.addTextChangedListener(watcher);
        editTextStudents.addTextChangedListener(watcher);
        editTextChildren.addTextChangedListener(watcher);
        editTextSeniorCitizens.addTextChangedListener(watcher);
        editTextCameraFee.addTextChangedListener(watcher);
        instructions.addTextChangedListener(watcher);
        editHelpline.addTextChangedListener(watcher);
        editValidFrom.addTextChangedListener(watcher);
        editValidTo.addTextChangedListener(watcher);
    }

    private boolean areAllFieldsFilled() {
        return !editPlace.getText().toString().trim().isEmpty() &&
                !editTextAmount.getText().toString().trim().isEmpty() &&
                !editTextStudents.getText().toString().trim().isEmpty() &&
                !editTextChildren.getText().toString().trim().isEmpty() &&
                !editTextSeniorCitizens.getText().toString().trim().isEmpty() &&
                !editTextCameraFee.getText().toString().trim().isEmpty() &&
                !instructions.getText().toString().trim().isEmpty() &&
                !editHelpline.getText().toString().trim().isEmpty() &&
                !editValidFrom.getText().toString().trim().isEmpty() &&
                !editValidTo.getText().toString().trim().isEmpty();
    }

    private void updateNextButtonState() {
        boolean allFilled = areAllFieldsFilled();
        btnNext.setEnabled(allFilled);
        btnNext.setBackgroundTintList(getResources().getColorStateList(
                allFilled ? R.color.button_enabled : R.color.button_disabled));
    }



    private void showTimePickerDialog(TextView textView, boolean isFromTime) {
        Calendar calendar = Calendar.getInstance();
        try {
            String fullDateTime = isFromTime ? validFromFull : validToFull;
            if (fullDateTime != null) {
                calendar.setTime(fullDateFormat.parse(fullDateTime));
            }
        } catch (Exception e) {
            // Fallback to current time if parsing fails
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
                false // Use 12-hour format with AM/PM
        );
        timePickerDialog.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            ticketId = data.getStringExtra("ticketId");
        }
    }
}
