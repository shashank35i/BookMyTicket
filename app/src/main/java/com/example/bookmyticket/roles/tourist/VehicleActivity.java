package com.example.bookmyticket.roles.tourist;

import com.example.bookmyticket.R;
import com.example.bookmyticket.model.Vehicle;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VehicleActivity extends AppCompatActivity {

    private TextInputLayout tilVehicleNumber, tilVehicleType;
    private EditText etVehicleNumber;
    private Spinner spVehicleType;
    private Button btnSave, btnDelete;
    private ImageView btnBack;
    private TextView headerTitle, tvSelectVehicle;
    private String userId, vehicleId, phoneNumber;
    private boolean isEditMode = false;
    private final String[] VEHICLE_TYPES = {"Bike", "Car", "Bus"};
    private static final String VEHICLE_NUMBER_REGEX = "^[A-Z0-9]{4,10}$";
    private List<Vehicle> vehicleList = new ArrayList<>();
    private ArrayAdapter<String> vehicleTypeAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vehicle);

        // Initialize views
        tilVehicleNumber = findViewById(R.id.til_vehicle_number);
        etVehicleNumber = findViewById(R.id.et_vehicle_number);
        tilVehicleType = findViewById(R.id.til_vehicle_type);
        spVehicleType = findViewById(R.id.sp_vehicle_type);
        btnSave = findViewById(R.id.btn_save);
        btnDelete = findViewById(R.id.btn_delete);
        btnBack = findViewById(R.id.btn_back);
        headerTitle = findViewById(R.id.header_title);
        tvSelectVehicle = findViewById(R.id.tv_select_vehicle);

        // Get current user ID and phone number
        userId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;
        phoneNumber = getIntent().getStringExtra("phoneNumber");
        vehicleId = getIntent().getStringExtra("vehicleId");
        isEditMode = vehicleId != null;

        // Validate user ID and phone number
        if (userId == null || phoneNumber == null) {
            showToast("User not logged in or phone number missing");
            finish();
            return;
        }

        // Set appropriate title
        headerTitle.setText(isEditMode ? "Edit Vehicle" : "Add Vehicle");

        // Setup vehicle type spinner
        vehicleTypeAdapter = new ArrayAdapter<>(this, R.layout.spinner_item, VEHICLE_TYPES);
        vehicleTypeAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spVehicleType.setAdapter(vehicleTypeAdapter);
        // Ensure the spinner text is visible by forcing a refresh
        spVehicleType.setSelection(0); // Set default selection to "Bike"

        // Setup vehicle selection
        tvSelectVehicle.setOnClickListener(v -> showVehicleSelectionDialog());

        // Load vehicles for selection
        loadVehiclesForSelection();

        // Load existing data if in edit mode
        if (isEditMode) {
            btnDelete.setVisibility(View.VISIBLE);
            btnDelete.setOnClickListener(v -> confirmDeleteVehicle());
            loadVehicleData();
        } else {
            btnDelete.setVisibility(View.GONE);
        }

        // Back button click handler
        btnBack.setOnClickListener(v -> onBackPressed());

        // Save button click handler
        btnSave.setOnClickListener(v -> saveVehicle());

        // Remove action bar and set system colors
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        setSystemColors();
    }

    private void setSystemColors() {
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.white));
        getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.white));
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    private void loadVehiclesForSelection() {
        setButtonsEnabled(false);
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("vehicle_details");
        ref.orderByChild("userUid").equalTo(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                vehicleList.clear();
                vehicleList.add(new Vehicle(null, null, "Add New Vehicle"));
                for (DataSnapshot vehicleSnapshot : snapshot.getChildren()) {
                    String number = vehicleSnapshot.getKey();
                    String type = vehicleSnapshot.child("vehicleType").getValue(String.class);
                    vehicleList.add(new Vehicle(number, type, number + " (" + type + ")"));
                }
                if (isEditMode) {
                    selectVehicleById(vehicleId);
                }
                setButtonsEnabled(true);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showToast("Failed to load vehicles: " + error.getMessage());
                setButtonsEnabled(true);
            }
        });
    }

    private void selectVehicleById(String vehicleId) {
        for (Vehicle vehicle : vehicleList) {
            if (vehicleId != null && vehicleId.equals(vehicle.number)) {
                tvSelectVehicle.setText(vehicle.displayText);
                etVehicleNumber.setText(vehicle.number);
                for (int i = 0; i < VEHICLE_TYPES.length; i++) {
                    if (VEHICLE_TYPES[i].equals(vehicle.type)) {
                        spVehicleType.setSelection(i);
                        // Force spinner to refresh to ensure text visibility
                        spVehicleType.invalidate();
                        break;
                    }
                }
                isEditMode = true;
                this.vehicleId = vehicleId;
                btnDelete.setVisibility(View.VISIBLE);
                headerTitle.setText("Edit Vehicle");
                break;
            }
        }
    }

    private void showVehicleSelectionDialog() {
        String[] vehicleNames = vehicleList.stream().map(v -> v.displayText).toArray(String[]::new);
        new AlertDialog.Builder(this, R.style.AlertDialogTheme)
                .setTitle("Select Vehicle")
                .setItems(vehicleNames, (dialog, which) -> {
                    Vehicle selectedVehicle = vehicleList.get(which);
                    tvSelectVehicle.setText(selectedVehicle.displayText);
                    if (which == 0) { // Add New Vehicle
                        isEditMode = false;
                        vehicleId = null;
                        etVehicleNumber.setText("");
                        spVehicleType.setSelection(0);
                        // Force spinner to refresh
                        spVehicleType.invalidate();
                        btnDelete.setVisibility(View.GONE);
                        headerTitle.setText("Add Vehicle");
                    } else {
                        isEditMode = true;
                        vehicleId = selectedVehicle.number;
                        etVehicleNumber.setText(selectedVehicle.number);
                        for (int i = 0; i < VEHICLE_TYPES.length; i++) {
                            if (VEHICLE_TYPES[i].equals(selectedVehicle.type)) {
                                spVehicleType.setSelection(i);
                                // Force spinner to refresh
                                spVehicleType.invalidate();
                                break;
                            }
                        }
                        btnDelete.setVisibility(View.VISIBLE);
                        headerTitle.setText("Edit Vehicle");
                    }
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void loadVehicleData() {
        setButtonsEnabled(false);
        FirebaseDatabase.getInstance()
                .getReference("vehicle_details")
                .child(vehicleId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            String number = snapshot.getKey();
                            String type = snapshot.child("vehicleType").getValue(String.class);
                            String phone = snapshot.child("phoneNumber").getValue(String.class);
                            String user = snapshot.child("userUid").getValue(String.class);

                            if (!userId.equals(user)) {
                                showToast("Unauthorized access to vehicle");
                                finish();
                                return;
                            }

                            tvSelectVehicle.setText(number + " (" + type + ")");
                            etVehicleNumber.setText(number);
                            for (int i = 0; i < VEHICLE_TYPES.length; i++) {
                                if (VEHICLE_TYPES[i].equals(type)) {
                                    spVehicleType.setSelection(i);
                                    // Force spinner to refresh
                                    spVehicleType.invalidate();
                                    break;
                                }
                            }
                            phoneNumber = phone != null ? phone : phoneNumber;
                        } else {
                            showToast("Vehicle data not found");
                            finish();
                        }
                        setButtonsEnabled(true);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        showToast("Failed to load vehicle: " + error.getMessage());
                        setButtonsEnabled(true);
                        finish();
                    }
                });
    }

    private void saveVehicle() {
        String number = etVehicleNumber.getText().toString().trim().toUpperCase();
        String type = (String) spVehicleType.getSelectedItem();

        // Validate vehicle number
        if (number.isEmpty()) {
            tilVehicleNumber.setError("Please enter vehicle number");
            etVehicleNumber.requestFocus();
            return;
        }

        if (!number.matches(VEHICLE_NUMBER_REGEX)) {
            tilVehicleNumber.setError("Vehicle number must be 4-10 alphanumeric characters");
            etVehicleNumber.requestFocus();
            return;
        }

        setButtonsEnabled(false);
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("vehicle_details");

        // Check for vehicle number uniqueness
        ref.child(number).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && (!isEditMode || !number.equals(vehicleId))) {
                    tilVehicleNumber.setError("Vehicle number already exists");
                    etVehicleNumber.requestFocus();
                    setButtonsEnabled(true);
                    return;
                }

                Map<String, Object> updates = new HashMap<>();
                updates.put("phoneNumber", phoneNumber);
                updates.put("userUid", userId);
                updates.put("vehicleType", type);
                updates.put("timestamp", System.currentTimeMillis());

                if (isEditMode && !number.equals(vehicleId)) {
                    // If vehicle number changed, delete old entry
                    FirebaseDatabase.getInstance().getReference("vehicle_details")
                            .child(vehicleId)
                            .removeValue();
                }

                ref.child(number).setValue(updates)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                showToast(isEditMode ? "Vehicle updated successfully" : "Vehicle added successfully");
                                getSharedPreferences("UserPrefs", MODE_PRIVATE)
                                        .edit()
                                        .putString("vehicleNumber", number)
                                        .putString("vehicleType", type)
                                        .putString("phoneNumber", phoneNumber)
                                        .apply();
                                finish();
                            } else {
                                showToast("Operation failed: " + task.getException().getMessage());
                            }
                            setButtonsEnabled(true);
                        });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showToast("Error checking vehicle number: " + error.getMessage());
                setButtonsEnabled(true);
            }
        });
    }

    private void deleteVehicle() {
        setButtonsEnabled(false);
        FirebaseDatabase.getInstance()
                .getReference("vehicle_details")
                .child(vehicleId)
                .removeValue()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        showToast("Vehicle deleted");
                        getSharedPreferences("UserPrefs", MODE_PRIVATE)
                                .edit()
                                .remove("vehicleNumber")
                                .remove("vehicleType")
                                .remove("phoneNumber")
                                .apply();
                        finish();
                    } else {
                        showToast("Failed to delete vehicle: " + task.getException().getMessage());
                    }
                    setButtonsEnabled(true);
                });
    }

    private void confirmDeleteVehicle() {
        new AlertDialog.Builder(this, R.style.AlertDialogTheme)
                .setTitle("Delete Vehicle")
                .setMessage("Are you sure you want to delete this vehicle?")
                .setPositiveButton("Delete", (dialog, which) -> deleteVehicle())
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .setCancelable(true)
                .show();
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void setButtonsEnabled(boolean enabled) {
        btnSave.setEnabled(enabled);
        btnDelete.setEnabled(enabled);
        btnBack.setEnabled(enabled);
        btnSave.setAlpha(enabled ? 1f : 0.5f);
        btnDelete.setAlpha(enabled ? 1f : 0.5f);
        btnBack.setAlpha(enabled ? 1f : 0.5f);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    private static class Vehicle {
        String number;
        String type;
        String displayText;

        Vehicle(String number, String type, String displayText) {
            this.number = number;
            this.type = type;
            this.displayText = displayText;
        }
    }
}
