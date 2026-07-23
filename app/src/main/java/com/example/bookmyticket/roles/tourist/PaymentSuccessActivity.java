package com.example.bookmyticket.roles.tourist;

import com.example.bookmyticket.R;
import com.example.bookmyticket.model.Ticket;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class PaymentSuccessActivity extends AppCompatActivity {
    private static final String TAG = "PaymentSuccessActivity";
    private static final int QR_CODE_SIZE = 600;
    private static final String ZERO_VISITORS = "0 Visitors";
    private static final String ALPHA_NUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int TICKET_ID_LENGTH = 8;
    private static final int UID_LENGTH = 6;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_payment_success);

        setupSystemUI();
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
        findViewById(R.id.main).setVisibility(View.GONE);

        new Thread(this::processIntentData).start();
        setupBackNavigation();
        overridePendingTransition(R.anim.enter_fade_in, R.anim.exit_fade_out);
    }

    private void setupSystemUI() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.WHITE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ViewCompat.getWindowInsetsController(window.getDecorView())
                    .setAppearanceLightStatusBars(true);
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        }

        View mainView = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
            int statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            v.setPadding(0, statusBarHeight, 0, navBarHeight);
            return insets;
        });
        mainView.requestApplyInsets();
    }

    private void checkAndGenerateUniqueUid(String userId, OnUidGenerated callback) {
        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users").child(userId);
        usersRef.get().addOnCompleteListener(task -> {
            if (isFinishing()) return;
            if (task.isSuccessful() && task.getResult().exists()) {
                String existingUid = task.getResult().child("uid").getValue(String.class);
                if (existingUid != null && existingUid.length() == UID_LENGTH) {
                    Log.d(TAG, "Using existing UID: " + existingUid + " for userId: " + userId);
                    callback.onUidGenerated(existingUid);
                } else {
                    Log.e(TAG, "Existing UID is invalid (length: " + (existingUid != null ? existingUid.length() : "null") + ") for userId: " + userId);
                    String newUid = generateUid();
                    Log.d(TAG, "Generated new UID: " + newUid + " for userId: " + userId);
                    checkUidUniqueness(newUid, userId, callback);
                }
            } else {
                String newUid = generateUid();
                Log.d(TAG, "No user found, generated new UID: " + newUid + " for userId: " + userId);
                checkUidUniqueness(newUid, userId, callback);
            }
        });
    }

    private String generateUid() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(UID_LENGTH);
        for (int i = 0; i < UID_LENGTH; i++) {
            sb.append(ALPHA_NUMERIC.charAt(random.nextInt(ALPHA_NUMERIC.length())));
        }
        return sb.toString();
    }

    private void checkUidUniqueness(String uid, String userId, OnUidGenerated callback) {
        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users");
        usersRef.orderByChild("uid").equalTo(uid).get().addOnCompleteListener(task -> {
            if (isFinishing()) return;
            if (task.isSuccessful() && task.getResult().exists()) {
                Log.d(TAG, "UID " + uid + " already exists, generating a new one");
                checkUidUniqueness(generateUid(), userId, callback);
            } else {
                Log.d(TAG, "UID " + uid + " is unique for userId: " + userId);
                callback.onUidGenerated(uid);
            }
        });
    }

    private interface OnUidGenerated {
        void onUidGenerated(String uid);
    }

    private void processIntentData() {
        String ticketId = getIntent().getStringExtra("TICKET_ID");
        if (ticketId == null || ticketId.isEmpty() || !ticketId.matches("^[A-Z0-9]{" + TICKET_ID_LENGTH + "}$")) {
            runOnUiThread(() -> Toast.makeText(this, "Invalid ticket ID", Toast.LENGTH_SHORT).show());
            navigateBack();
            return;
        }

        String userId = getIntent().getStringExtra("USER_UID");
        if (userId == null || userId.isEmpty()) {
            runOnUiThread(() -> Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show());
            navigateBack();
            return;
        }

        displayTicketDetails(
                ticketId,
                getIntent().getStringExtra("PLACE"),
                getIntent().getStringExtra("ADMIN_NAME"),
                getIntent().getStringExtra("INSTRUCTIONS"),
                getIntent().getStringExtra("HELPLINE"),
                getIntent().getIntExtra("TOTAL_PERSONS", 0),
                getIntent().getStringExtra("TOTAL_AMOUNT"),
                userId
        );
    }

    private void displayTicketDetails(String ticketId, String place, String admin,
                                      String instructions, String helpline,
                                      int persons, String amount, String userId) {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
            SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
            Date currentDate = new Date();

            TextView txtPlace = findViewById(R.id.txtPlace);
            TextView txtTicketId = findViewById(R.id.txtTicketId);
            TextView txtInstructions = findViewById(R.id.txtInstructions);
            TextView txtHelpline = findViewById(R.id.txtHelpline);
            TextView txtTotalAmount = findViewById(R.id.txtTotalAmount);
            TextView txtDate = findViewById(R.id.txtDate);
            TextView txtTime = findViewById(R.id.txtTime);
            TextView txtCameraCount = findViewById(R.id.txtCameraCount);
            TextView txtVisitors = findViewById(R.id.txtVisitors);
            TextView txtVisitorDetails = findViewById(R.id.txtVisitorDetails);
            LinearLayout cameraContainer = findViewById(R.id.cameraContainer);

            Intent intent = getIntent();
            int cameras = intent.getIntExtra("CAMERAS", 0);
            int adults = intent.getIntExtra("ADULTS", 0);
            int students = intent.getIntExtra("STUDENTS", 0);
            int children = intent.getIntExtra("CHILDREN", 0);
            int seniors = intent.getIntExtra("SENIOR_CITIZENS", 0);
            String validFrom = intent.getStringExtra("VALID_FROM");
            String validTo = intent.getStringExtra("VALIDITY_TIME");

            runOnUiThread(() -> {
                findViewById(R.id.progressBar).setVisibility(View.GONE);
                findViewById(R.id.main).setVisibility(View.VISIBLE);

                // Display total visitors
                int totalVisitors = adults + students + children + seniors;
                txtVisitors.setText(totalVisitors + (totalVisitors == 1 ? " Visitor" : " Visitors"));

                // Display detailed visitor breakdown
                StringBuilder visitorDetails = new StringBuilder();
                if (adults > 0) {
                    visitorDetails.append(adults).append(adults > 1 ? " Adults" : " Adult");
                }
                if (students > 0) {
                    if (visitorDetails.length() > 0) visitorDetails.append(", ");
                    visitorDetails.append(students).append(students > 1 ? " Students" : " Student");
                }
                if (children > 0) {
                    if (visitorDetails.length() > 0) visitorDetails.append(", ");
                    visitorDetails.append(children).append(children > 1 ? " Children" : " Child");
                }
                if (seniors > 0) {
                    if (visitorDetails.length() > 0) visitorDetails.append(", ");
                    visitorDetails.append(seniors).append(seniors > 1 ? " Senior Citizens" : " Senior Citizen");
                }
                txtVisitorDetails.setText(visitorDetails.length() > 0 ? visitorDetails.toString() : "");

                cameraContainer.setVisibility(cameras > 0 ? View.VISIBLE : View.GONE);
                if (cameras > 0) {
                    txtCameraCount.setText(cameras + (cameras > 1 ? " Cameras" : " Camera"));
                }

                txtPlace.setText(place != null ? place : "");
                txtTicketId.setText(getString(R.string.ticket_id_format, ticketId));
                txtInstructions.setText(instructions != null ? instructions : "");
                txtHelpline.setText(helpline != null ? helpline : "");
                txtTotalAmount.setText(getString(R.string.amount_format, amount != null ? amount : "0"));
                txtDate.setText(dateFormat.format(currentDate));
                // Display validFrom and validTo in "Entry Time"
                String timeText = (validFrom != null && validTo != null) ?
                        String.format("%s to %s", validFrom, validTo) : "N/A";
                txtTime.setText(timeText);
            });

            String qrContent = createQRContent(userId, ticketId);
            generateQRCode(qrContent);
            saveTicketToDatabase(ticketId, place, admin, instructions, helpline, persons, amount,
                    dateFormat.format(currentDate), timeFormat.format(currentDate), userId);

        } catch (Exception e) {
            Log.e(TAG, "Error displaying ticket details", e);
            runOnUiThread(() -> Toast.makeText(this, "Error displaying ticket information", Toast.LENGTH_SHORT).show());
        }
    }

    private String createQRContent(String userId, String ticketId) {
        try {
            return "u:" + userId + "|t:" + ticketId;
        } catch (Exception e) {
            Log.e(TAG, "Error creating QR content for ticketId: " + ticketId + ", userId: " + userId, e);
            return ticketId; // Fallback to ticketId only to avoid breaking existing QR codes
        }
    }

    private void generateQRCode(String data) {
        if (data == null || data.isEmpty()) {
            runOnUiThread(() -> Toast.makeText(this, "Failed to generate QR code", Toast.LENGTH_SHORT).show());
            return;
        }

        new Thread(() -> {
            try {
                DisplayMetrics displayMetrics = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
                int screenWidth = displayMetrics.widthPixels;
                int qrCodeSize = Math.min(screenWidth - 64, 600); // Subtract padding/margins, max 600px

                Map<EncodeHintType, Object> hints = new HashMap<>();
                hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);
                hints.put(EncodeHintType.MARGIN, 1);
                hints.put(EncodeHintType.CHARACTER_SET, "ISO-8859-1");

                BitMatrix bitMatrix = new MultiFormatWriter().encode(
                        data, BarcodeFormat.QR_CODE, qrCodeSize, qrCodeSize, hints);

                int width = bitMatrix.getWidth();
                int[] pixels = new int[width * width];
                int qrColor = Color.parseColor("#1976D2");
                int backgroundColor = Color.WHITE;

                for (int y = 0; y < width; y++) {
                    for (int x = 0; x < width; x++) {
                        pixels[y * width + x] = bitMatrix.get(x, y) ? qrColor : backgroundColor;
                    }
                }

                Bitmap bitmap = Bitmap.createBitmap(width, width, Bitmap.Config.ARGB_8888);
                bitmap.setPixels(pixels, 0, width, 0, 0, width, width);

                runOnUiThread(() -> {
                    ImageView qrImageView = findViewById(R.id.imgQRCode);
                    qrImageView.setImageBitmap(bitmap);
                });

            } catch (Exception e) {
                Log.e(TAG, "QR generation failed", e);
                runOnUiThread(() -> Toast.makeText(this, "Failed to generate QR code", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private Bitmap generateQRCodeBitmap(String data) {
        if (data == null || data.isEmpty()) return null;

        try {
            DisplayMetrics displayMetrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            int screenWidth = displayMetrics.widthPixels;
            int qrCodeSize = Math.min(screenWidth - 64, 600); // Subtract padding/margins, max 600px

            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);
            hints.put(EncodeHintType.MARGIN, 1);
            hints.put(EncodeHintType.CHARACTER_SET, "ISO-8859-1");

            BitMatrix bitMatrix = new MultiFormatWriter().encode(
                    data, BarcodeFormat.QR_CODE, qrCodeSize, qrCodeSize, hints);

            int width = bitMatrix.getWidth();
            int[] pixels = new int[width * width];
            int qrColor = Color.parseColor("#1976D2");
            int backgroundColor = Color.WHITE;

            for (int y = 0; y < width; y++) {
                for (int x = 0; x < width; x++) {
                    pixels[y * width + x] = bitMatrix.get(x, y) ? qrColor : backgroundColor;
                }
            }

            Bitmap bitmap = Bitmap.createBitmap(width, width, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, width, 0, 0, width, width);
            return bitmap;

        } catch (Exception e) {
            Log.e(TAG, "QR generation failed", e);
            return null;
        }
    }

    private void saveTicketToDatabase(String ticketId, String place, String adminName,
                                      String instructions, String helpline,
                                      int totalPersons, String totalAmount,
                                      String date, String time, String userId) {
        try {
            String adminUid = getIntent().getStringExtra("ADMIN_UID");
            String phoneNumber = getIntent().getStringExtra("phoneNumber");
            boolean isLoggedIn = getIntent().getBooleanExtra("isLoggedIn", false);
            double baseAmount = Double.parseDouble(getIntent().getStringExtra("BASE_AMOUNT"));
            String validityTime = getIntent().getStringExtra("VALIDITY_TIME");
            String validFrom = getIntent().getStringExtra("VALID_FROM");

            if (userId == null || userId.isEmpty()) {
                Log.e(TAG, "User ID is null or empty");
                runOnUiThread(() -> Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show());
                return;
            }

            // Check and generate/retrieve unique 6-digit UID
            checkAndGenerateUniqueUid(userId, uid -> {
                if (uid == null || uid.length() != UID_LENGTH) {
                    Log.e(TAG, "Failed to generate or retrieve valid 6-character UID for userId: " + userId);
                    runOnUiThread(() -> Toast.makeText(this, "Failed to generate user ID", Toast.LENGTH_SHORT).show());
                    return;
                }

                // Save user data to 'users' node
                Map<String, Object> userData = new HashMap<>();
                userData.put("uid", uid);
                userData.put("role", "tourist");
                userData.put("phone", phoneNumber != null ? phoneNumber : "");

                DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users").child(userId);
                usersRef.setValue(userData)
                        .addOnSuccessListener(aVoid -> Log.d(TAG, "User data saved successfully for userId: " + userId + " with UID: " + uid))
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to save user data for userId: " + userId, e);
                            runOnUiThread(() -> Toast.makeText(this, "Failed to save user data", Toast.LENGTH_SHORT).show());
                        });

                // Save ticket data
                String qrContent = createQRContent(userId, ticketId);

                Bitmap qrBitmap = generateQRCodeBitmap(qrContent);
                String qrImageBase64 = "";
                if (qrBitmap != null) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    qrBitmap.compress(Bitmap.CompressFormat.PNG, 80, baos);
                    qrImageBase64 = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
                }

                SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
                long currentTimestamp = System.currentTimeMillis();
                String validFromTime = validFrom != null && !validFrom.isEmpty() ?
                        validFrom : sdf.format(new Date(currentTimestamp));
                String validToTime = validityTime != null && !validityTime.isEmpty() ?
                        validityTime : sdf.format(new Date(currentTimestamp + 3 * 60 * 60 * 1000));

                Map<String, Object> ticketData = new HashMap<>();
                ticketData.put("ticketId", ticketId);
                ticketData.put("place", place != null ? place : "");
                ticketData.put("adminName", adminName != null ? adminName : "");
                ticketData.put("instructions", instructions != null ? instructions : "");
                ticketData.put("helpline", helpline != null ? helpline : "");
                ticketData.put("totalPersons", totalPersons);
                ticketData.put("totalAmount", totalAmount != null ? totalAmount : "0");
                ticketData.put("timestamp", currentTimestamp);
                ticketData.put("date", date);
                ticketData.put("time", time);
                ticketData.put("adults", getIntent().getIntExtra("ADULTS", 0));
                ticketData.put("students", getIntent().getIntExtra("STUDENTS", 0));
                ticketData.put("children", getIntent().getIntExtra("CHILDREN", 0));
                ticketData.put("seniorCitizens", getIntent().getIntExtra("SENIOR_CITIZENS", 0));
                ticketData.put("cameras", getIntent().getIntExtra("CAMERAS", 0));
                ticketData.put("validityTime", validToTime);
                ticketData.put("validFrom", validFromTime);
                ticketData.put("validTo", validToTime);
                ticketData.put("qrContent", qrContent);
                ticketData.put("qrImage", qrImageBase64);
                ticketData.put("priceAdults", getIntent().getDoubleExtra("PRICE_ADULTS", 0.0));
                ticketData.put("priceStudents", getIntent().getDoubleExtra("PRICE_STUDENTS", 0.0));
                ticketData.put("priceChildren", getIntent().getDoubleExtra("PRICE_CHILDREN", 0.0));
                ticketData.put("priceSenior", getIntent().getDoubleExtra("PRICE_SENIOR", 0.0));
                ticketData.put("priceCamera", getIntent().getDoubleExtra("PRICE_CAMERA", 0.0));

                DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference("tickets");
                dbRef.child(userId).child(ticketId).setValue(ticketData)
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "Ticket data saved successfully for ticketId: " + ticketId + ", userId: " + userId);
                            if (adminUid != null && !adminUid.isEmpty()) {
                                savePayoutToAdmin(ticketId, adminUid, baseAmount, totalPersons);
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to save ticket for ticketId: " + ticketId + ", userId: " + userId, e);
                            runOnUiThread(() -> Toast.makeText(this, "Failed to save ticket", Toast.LENGTH_SHORT).show());
                        });
            });
        } catch (Exception e) {
            Log.e(TAG, "Error saving ticket for ticketId: " + ticketId + ", userId: " + userId, e);
            runOnUiThread(() -> Toast.makeText(this, "Error saving ticket", Toast.LENGTH_SHORT).show());
        }
    }

    private void savePayoutToAdmin(String ticketId, String adminUid, double baseAmount, int totalPersons) {
        try {
            DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference("payouts");
            Map<String, Object> payoutData = new HashMap<>();
            payoutData.put("ticketId", ticketId);
            payoutData.put("QrRefTicketId", getIntent().getStringExtra("TICKET_ID"));
            payoutData.put("baseAmount", baseAmount);
            payoutData.put("totalPersons", totalPersons);
            payoutData.put("timestamp", System.currentTimeMillis());
            payoutData.put("status", "");
            payoutData.put("fundAccountId", getIntent().getStringExtra("FUND_ACCOUNT_ID"));
            payoutData.put("email", getIntent().getStringExtra("EMAIL"));

            dbRef.child(adminUid).child(ticketId).setValue(payoutData)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Payout data saved successfully for ticketId: " + ticketId))
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to save payout record for ticketId: " + ticketId, e);
                        runOnUiThread(() -> Toast.makeText(this, "Failed to save payout record", Toast.LENGTH_SHORT).show());
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error saving payout data for ticketId: " + ticketId, e);
        }
    }

    private void setupBackNavigation() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> navigateBack());
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                navigateBack();
            }
        });
    }

    private void navigateBack() {
        Intent intent = new Intent(this, TouristPageActivity.class);
        Intent originalIntent = getIntent();
        intent.putExtra("USER_UID", originalIntent.getStringExtra("USER_UID"));
        intent.putExtra("phoneNumber", originalIntent.getStringExtra("phoneNumber"));
        intent.putExtra("isLoggedIn", originalIntent.getBooleanExtra("isLoggedIn", false));
        intent.putExtra("FROM_PAYMENT_SUCCESS", true);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        overridePendingTransition(R.anim.enter_slide_in_right, R.anim.exit_slide_out_left);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.enter_slide_in_right, R.anim.exit_slide_out_left);
    }
}
