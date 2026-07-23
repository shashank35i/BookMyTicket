package com.example.bookmyticket.roles.parkingadmin;

import com.example.bookmyticket.R;
import com.example.bookmyticket.model.Vehicle;

import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import android.animation.ObjectAnimator;
import android.animation.AnimatorSet;

public class ManualEntryActivity extends AppCompatActivity {
    private TextInputLayout tilVehicleNumber;
    private TextInputEditText vehicleNumberInput;
    private ImageView btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manual_entry);
        overridePendingTransition(R.anim.fade_in_fast, R.anim.fade_out_fast);
if(getSupportActionBar()!=null){
    getSupportActionBar().hide();
}
        tilVehicleNumber = findViewById(R.id.til_vehicle_number);
        vehicleNumberInput = findViewById(R.id.vehicle_number_input);
        Button confirmButton = findViewById(R.id.confirm_button);
        Button cancelButton = findViewById(R.id.cancel_button);
        btnBack = findViewById(R.id.back_button);

        setupButtonAnimations();

        confirmButton.setOnClickListener(v -> {
            String number = vehicleNumberInput.getText().toString().trim().toUpperCase();
            if (number.matches("^[A-Z]{2}[0-9]{1,2}[A-Z]{0,2}[0-9]{4}$")) {
                Intent result = new Intent();
                result.putExtra("vehicle_number", number);
                setResult(RESULT_OK, result);
                finish();
                overridePendingTransition(R.anim.fade_in_fast, R.anim.fade_out_fast);
            } else {
                tilVehicleNumber.setError("Enter valid vehicle number (e.g., MH12AB1234)");
            }
        });

        cancelButton.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
            overridePendingTransition(R.anim.fade_in_fast, R.anim.fade_out_fast);
        });

        btnBack.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
            overridePendingTransition(R.anim.fade_in_fast, R.anim.fade_out_fast);
        });
    }

    private void setupButtonAnimations() {
        for (View button : new View[]{findViewById(R.id.confirm_button), findViewById(R.id.cancel_button), btnBack}) {
            button.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        ObjectAnimator scaleDownX = ObjectAnimator.ofFloat(v, "scaleX", 0.95f);
                        ObjectAnimator scaleDownY = ObjectAnimator.ofFloat(v, "scaleY", 0.95f);
                        scaleDownX.setDuration(100);
                        scaleDownY.setDuration(100);
                        AnimatorSet scaleDown = new AnimatorSet();
                        scaleDown.playTogether(scaleDownX, scaleDownY);
                        scaleDown.start();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        ObjectAnimator scaleUpX = ObjectAnimator.ofFloat(v, "scaleX", 1.0f);
                        ObjectAnimator scaleUpY = ObjectAnimator.ofFloat(v, "scaleY", 1.0f);
                        scaleUpX.setDuration(100);
                        scaleUpY.setDuration(100);
                        AnimatorSet scaleUp = new AnimatorSet();
                        scaleUp.playTogether(scaleUpX, scaleUpY);
                        scaleUp.start();
                        break;
                }
                return false;
            });
        }
    }

    @Override
    public void onBackPressed() {
        setResult(RESULT_CANCELED);
        super.onBackPressed();
        overridePendingTransition(R.anim.fade_in_fast, R.anim.fade_out_fast);
    }
}
