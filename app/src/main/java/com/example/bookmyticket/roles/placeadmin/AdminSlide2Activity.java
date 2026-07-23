package com.example.bookmyticket.roles.placeadmin;

import com.example.bookmyticket.R;
import com.example.bookmyticket.auth.LoginActivity;
import com.example.bookmyticket.features.payment.BankActivity;
import com.example.bookmyticket.model.Ticket;

import android.Manifest;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

public class AdminSlide2Activity extends AppCompatActivity {
    private static final String TAG = "QRGen";
    private static final String QR_HISTORY = "qrHistory";
    private static final String DT_FMT = "yyyy-MM-dd HH:mm";
    private static final String DATE_ONLY_FMT = "yyyy-MM-dd";
    private static final String DISPLAY_FMT = "h:mm a, d MMM yyyy";
    private static final String QR_DIR = "QRCodes";
    private static final int TKT_LEN = 6;
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final String NOTIFICATION_CHANNEL_ID = "QRDownloadChannel";
    private DatabaseReference notificationsRef; // Add this field
    private TextView txtPlace, txtQRGeneratedDate;
    private Button btnGenerateQR;
    private ImageView qrImg, backBtn;
    private TextView txtDwnQR;
    private View shimmerPlace, shimmerQRGeneratedDate;
    private String accHldr, accNum, ifsc, place, priceAdults, priceStudents, priceChildren, priceSenior, priceCamera, validFrom, validTo, helpline, instructions, emailId, bankName,phoneNumber;
    private double adultPrice, studentPrice, childPrice, seniorCitizenPrice, cameraPrice ;
    private DatabaseReference dbRef;
    private SimpleDateFormat fmt, dateOnlyFmt, displayFmt;
    private Random rnd;
    private FirebaseAuth auth;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private String qrId;
    private String generatedDate;
    private boolean hasGeneratedQR = false;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.admin_slide2);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        init();
        setSysUI();
        createNotificationChannel();
        getBankDetails();
    }

    private void init() {
        dbRef = FirebaseDatabase.getInstance().getReference();
        notificationsRef = FirebaseDatabase.getInstance().getReference("placeadmin_notifications");
        fmt = new SimpleDateFormat(DT_FMT, Locale.getDefault());
        dateOnlyFmt = new SimpleDateFormat(DATE_ONLY_FMT, Locale.getDefault());
        displayFmt = new SimpleDateFormat(DISPLAY_FMT, Locale.getDefault());
        rnd = new Random();
        auth = FirebaseAuth.getInstance();

        // Initialize EncryptedSharedPreferences for secure storage
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
            Log.e(TAG, "Failed to initialize EncryptedSharedPreferences: " + e.getMessage());
            sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        }

        backBtn = findViewById(R.id.backButton);
        btnGenerateQR = findViewById(R.id.btnGenerateQR);
        qrImg = findViewById(R.id.qrImageView);
        txtDwnQR = findViewById(R.id.txtDownloadQR);
        txtPlace = findViewById(R.id.txtPlace);
        txtQRGeneratedDate = findViewById(R.id.txtQRGeneratedDate);
        shimmerPlace = findViewById(R.id.shimmerPlace);
        shimmerQRGeneratedDate = findViewById(R.id.shimmerQRGeneratedDate);

        Intent intent = getIntent();
        place = intent.getStringExtra("place");
        priceAdults = getValidPrice(intent.getStringExtra("priceAdults"));
        priceStudents = getValidPrice(intent.getStringExtra("priceStudents"));
        priceChildren = getValidPrice(intent.getStringExtra("priceChildren"));
        priceSenior = getValidPrice(intent.getStringExtra("priceSeniorCitizens"));
        priceCamera = getValidPrice(intent.getStringExtra("priceCameraFee"));
        helpline = intent.getStringExtra("helpline");
        instructions = intent.getStringExtra("instructions");
        validFrom = intent.getStringExtra("validFrom");
        validTo = intent.getStringExtra("validTo");
        qrId = intent.getStringExtra("ticketId");

        adultPrice = parsePrice(priceAdults);
        studentPrice = parsePrice(priceStudents);
        childPrice = parsePrice(priceChildren);
        seniorCitizenPrice = parsePrice(priceSenior);
        cameraPrice = parsePrice(priceCamera);

        if (validFrom == null || validTo == null || validFrom.isEmpty() || validTo.isEmpty()) {
            showErrDlg("Invalid dates", "Please provide valid dates and try again.");
            redirectToLogin();
            return;
        }

        backBtn.setOnClickListener(v -> {
            Intent resultIntent = new Intent();
            resultIntent.putExtra("ticketId", qrId);
            setResult(RESULT_OK, resultIntent);
            finish();
        });

        btnGenerateQR.setOnClickListener(v -> {
            String buttonText = btnGenerateQR.getText().toString();
            if (buttonText.equals("Generate QR Code") || buttonText.equals("Update QR Code")) {
                if (validBank()) {
                    genQR();
                    btnGenerateQR.setText("Next");
                } else {
                    showErrDlg("Missing Bank Details", "Please add your bank details to proceed.");
                    Intent bankIntent = new Intent(AdminSlide2Activity.this, BankActivity.class);
                    bankIntent.putExtra("USER_UID", auth.getCurrentUser().getUid());
                    bankIntent.putExtra("phoneNumber", sharedPreferences.getString("phoneNumber", ""));
                    bankIntent.putExtra("email", sharedPreferences.getString("email", ""));
                    bankIntent.putExtra("isLoggedIn", true);
                    bankIntent.putExtra("isNewUser", true);
                    bankIntent.putExtra("adminType", "placeAdmin");
                    startActivity(bankIntent);
                }
            } else if (buttonText.equals("Next")) {
                FirebaseUser user = auth.getCurrentUser();
                if (user == null) {
                    showToast("Not authenticated. Please log in again.");
                    redirectToLogin();
                    return;
                }
                // Verify all required data is present
                String phoneNumber = sharedPreferences.getString("phoneNumber", null);
                String email = sharedPreferences.getString("email", "");
                String adminType = sharedPreferences.getString("adminType", null);
                if (phoneNumber == null || adminType == null) {
                    showErrDlg("Incomplete Data", "User data is incomplete. Please try again.");
                    redirectToLogin();
                    return;
                }
                Intent intentToDashboard = new Intent(AdminSlide2Activity.this, PlaceAdminDashboardActivity.class);
                intentToDashboard.putExtra("USER_UID", user.getUid());
                intentToDashboard.putExtra("phoneNumber", phoneNumber);
                intentToDashboard.putExtra("email", email);
                intentToDashboard.putExtra("isLoggedIn", true);
                intentToDashboard.putExtra("isNewUser", sharedPreferences.getBoolean("isNewUser", true));
                intentToDashboard.putExtra("adminType", "placeAdmin");
                intentToDashboard.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intentToDashboard);
                finish();
            }
        });

        txtDwnQR.setOnClickListener(v -> {
            if (qrImg.getDrawable() != null && qrId != null && generatedDate != null) {
                saveQRAsPDF(true, qrId, generatedDate);
            } else {
                showToast("Please generate a QR code first.");
            }
        });

        requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (!isGranted) Log.w(TAG, "Notification permission not granted");
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }

        if (qrId != null) {
            checkExistingQR();
        } else {
            btnGenerateQR.setText("Generate QR Code");
            btnGenerateQR.setEnabled(true);
        }
    }

    private void getBankDetails() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            showErrDlg("Authentication Error", "You are not logged in. Please log in again.");
            redirectToLogin();
            return;
        }

        String uid = user.getUid();
        // Check SharedPreferences first for cached data
        if (sharedPreferences.contains("phoneNumber") && sharedPreferences.contains("email") &&
                sharedPreferences.contains("accountHolder") && sharedPreferences.contains("accountNumber") &&
                sharedPreferences.contains("ifscCode") && sharedPreferences.contains("bankName")) {
            accHldr = sharedPreferences.getString("accountHolder", "");
            accNum = sharedPreferences.getString("accountNumber", "");
            ifsc = sharedPreferences.getString("ifscCode", "");
            bankName = sharedPreferences.getString("bankName", "");
            emailId = sharedPreferences.getString("email", "");
            phoneNumber = sharedPreferences.getString("phoneNumber", ""); // Initialize phoneNumber as String
            Log.d(TAG, "Using cached bank and user data from SharedPreferences");
            if (validBank() && !phoneNumber.isEmpty()) {
                return; // Use cached data if valid
            }
        }

        Task<DataSnapshot> placeAdminTask = dbRef.child("placeadmin").child(uid).get();
        Task<DataSnapshot> userTask = dbRef.child("users").child(uid).get();

        Tasks.whenAllSuccess(placeAdminTask, userTask).addOnSuccessListener(results -> {
            DataSnapshot placeAdminSnapshot = (DataSnapshot) results.get(0);
            DataSnapshot userSnapshot = (DataSnapshot) results.get(1);

            if (!placeAdminSnapshot.exists()) {
                showErrDlg("Bank Details Required", "Please add your bank details to continue.");
                Intent bankIntent = new Intent(AdminSlide2Activity.this, BankActivity.class);
                bankIntent.putExtra("USER_UID", uid);
                bankIntent.putExtra("phoneNumber", phoneNumber != null ? phoneNumber : "");
                bankIntent.putExtra("email", emailId != null ? emailId : "");
                bankIntent.putExtra("isLoggedIn", true);
                bankIntent.putExtra("isNewUser", true);
                bankIntent.putExtra("adminType", "placeAdmin");
                startActivity(bankIntent);
                return;
            }
            accHldr = placeAdminSnapshot.child("accountHolder").getValue(String.class);
            accNum = placeAdminSnapshot.child("accountNumber").getValue(String.class);
            ifsc = placeAdminSnapshot.child("ifscCode").getValue(String.class);
            bankName = placeAdminSnapshot.child("name").getValue(String.class);

            if (accHldr == null || accNum == null || ifsc == null || bankName == null) {
                showErrDlg("Incomplete Bank Details", "Please complete your bank details to proceed.");
                Intent bankIntent = new Intent(AdminSlide2Activity.this, BankActivity.class);
                bankIntent.putExtra("USER_UID", uid);
                bankIntent.putExtra("phoneNumber", phoneNumber != null ? phoneNumber : "");
                bankIntent.putExtra("email", emailId != null ? emailId : "");
                bankIntent.putExtra("isLoggedIn", true);
                bankIntent.putExtra("isNewUser", true);
                bankIntent.putExtra("adminType", "placeAdmin");
                startActivity(bankIntent);
                return;
            }

            if (!userSnapshot.exists()) {
                showToast("No user details found. Please contact support.");
                redirectToLogin();
                return;
            }
            emailId = userSnapshot.child("email").getValue(String.class) != null ? userSnapshot.child("email").getValue(String.class) : "";
            phoneNumber = userSnapshot.child("phone").getValue(String.class) != null ? userSnapshot.child("phone").getValue(String.class) : "";
            String role = userSnapshot.child("role").getValue(String.class);

            if (!"placeAdmin".equals(role)) {
                showErrDlg("Error", "Invalid user role. You do not have admin access.");
                redirectToLogin();
                return;
            }

            if (phoneNumber.isEmpty()) {
                showErrDlg("Error", "Phone number is missing. Please update your profile.");
                redirectToLogin();
                return;
            }

            // Save to SharedPreferences
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("userUid", uid);
            editor.putString("phoneNumber", phoneNumber);
            editor.putString("email", emailId);
            editor.putBoolean("isLoggedIn", true);
            editor.putBoolean("isNewUser", false); // Mark as not new after successful fetch
            editor.putString("adminType", "placeAdmin");
            editor.putString("userRole", "placeAdmin");
            editor.putString("accountHolder", accHldr);
            editor.putString("accountNumber", accNum);
            editor.putString("ifscCode", ifsc);
            editor.putString("bankName", bankName);
            editor.apply();
            Log.d(TAG, "Saved user data to SharedPreferences: uid=" + uid + ", phoneNumber=" + phoneNumber + ", email=" + emailId);
        }).addOnFailureListener(e -> {
            showErrDlg("Error", "Failed to fetch user or bank details. Please check your network and try again.");
            Log.e(TAG, "Error fetching data: " + e.getMessage());
            redirectToLogin();
        });
    }

    private void checkExistingQR() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            showToast("Not authenticated. Please log in again.");
            btnGenerateQR.setText("Generate QR Code");
            btnGenerateQR.setEnabled(true);
            redirectToLogin();
            return;
        }

        dbRef.child(QR_HISTORY).child(user.getUid()).child(qrId).get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                generatedDate = snapshot.child("timestamp").getValue(Long.class) != null ?
                        fmt.format(new Date(snapshot.child("timestamp").getValue(Long.class))) : fmt.format(new Date());
                genQRImg(qrId);
                txtPlace.setText(place);
                try {
                    txtQRGeneratedDate.setText(dateOnlyFmt.format(fmt.parse(generatedDate)));
                } catch (ParseException e) {
                    Log.e(TAG, "Error parsing QR generated date: " + e.getMessage());
                    txtQRGeneratedDate.setText("QR Generated Date: Unknown");
                }
                txtPlace.setVisibility(View.VISIBLE);
                txtQRGeneratedDate.setVisibility(View.VISIBLE);
                shimmerPlace.setVisibility(View.GONE);
                shimmerQRGeneratedDate.setVisibility(View.GONE);
                btnGenerateQR.setText("Update QR Code");
                btnGenerateQR.setEnabled(true);
            } else {
                btnGenerateQR.setText("Generate QR Code");
                btnGenerateQR.setEnabled(true);
            }
        }).addOnFailureListener(e -> {
            showToast("Failed to check existing QR. Please try again.");
            Log.e(TAG, "Error checking QR: " + e.getMessage());
            btnGenerateQR.setText("Generate QR Code");
            btnGenerateQR.setEnabled(true);
        });
    }
    private void genQR() {
        try {
            Date now = new Date();
            FirebaseUser user = auth.getCurrentUser();
            if (user == null) {
                showToast("Not authenticated. Please log in again.");
                redirectToLogin();
                return;
            }

            String userId = user.getUid();
            boolean isNewQR = qrId == null;

            if (isNewQR) {
                qrId = genTktId();
            }

            // Encode both ticketId and Firebase UID in QR code
            String qrContent = qrId + "|" + userId;

            // Check if QR code exists in database to confirm if it's new or an update
            dbRef.child(QR_HISTORY).child(userId).child(qrId).get().addOnSuccessListener(snapshot -> {
                String notificationType;
                String changeLog;

                if (!snapshot.exists()) {
                    // New QR code
                    notificationType = "QR_GENERATED";
                    changeLog = String.format("New QR code generated for place: %s, ticketId: %s, userId: %s, generated on: %s",
                            place != null ? place : "Unknown", qrId, userId, fmt.format(now));
                } else {
                    // Existing QR code (update)
                    notificationType = "QR_UPDATED";
                    StringBuilder changes = new StringBuilder("Updated QR code for place: " + (place != null ? place : "Unknown") + ", ticketId: " + qrId + ", userId: " + userId + ", updated on: " + fmt.format(now) + ". Changes: ");
                    DataSnapshot existingData = snapshot;

                    // Compare fields to identify changes
                    if (!Objects.equals(existingData.child("placeName").getValue(String.class), place)) {
                        changes.append("Place changed from ").append(existingData.child("placeName").getValue(String.class)).append(" to ").append(place).append("; ");
                    }
                    if (!Objects.equals(existingData.child("helpline").getValue(String.class), helpline)) {
                        changes.append("Helpline changed from ").append(existingData.child("helpline").getValue(String.class)).append(" to ").append(helpline).append("; ");
                    }
                    if (!Objects.equals(existingData.child("instructions").getValue(String.class), instructions)) {
                        changes.append("Instructions changed from ").append(existingData.child("instructions").getValue(String.class)).append(" to ").append(instructions).append("; ");
                    }
                    if (!Objects.equals(existingData.child("priceAdults").getValue(Double.class), adultPrice)) {
                        changes.append("Adult price changed from ").append(existingData.child("priceAdults").getValue(Double.class)).append(" to ").append(adultPrice).append("; ");
                    }
                    if (!Objects.equals(existingData.child("priceStudents").getValue(Double.class), studentPrice)) {
                        changes.append("Student price changed from ").append(existingData.child("priceStudents").getValue(Double.class)).append(" to ").append(studentPrice).append("; ");
                    }
                    if (!Objects.equals(existingData.child("priceChildren").getValue(Double.class), childPrice)) {
                        changes.append("Child price changed from ").append(existingData.child("priceChildren").getValue(Double.class)).append(" to ").append(childPrice).append("; ");
                    }
                    if (!Objects.equals(existingData.child("priceSenior").getValue(Double.class), seniorCitizenPrice)) {
                        changes.append("Senior price changed from ").append(existingData.child("priceSenior").getValue(Double.class)).append(" to ").append(seniorCitizenPrice).append("; ");
                    }
                    if (!Objects.equals(existingData.child("priceCamera").getValue(Double.class), cameraPrice)) {
                        changes.append("Camera price changed from ").append(existingData.child("priceCamera").getValue(Double.class)).append(" to ").append(cameraPrice).append("; ");
                    }
                    if (!Objects.equals(existingData.child("validFrom").getValue(String.class), validFrom)) {
                        changes.append("Valid from changed from ").append(existingData.child("validFrom").getValue(String.class)).append(" to ").append(validFrom).append("; ");
                    }
                    if (!Objects.equals(existingData.child("validTo").getValue(String.class), validTo)) {
                        changes.append("Valid to changed from ").append(existingData.child("validTo").getValue(String.class)).append(" to ").append(validTo).append("; ");
                    }

                    changeLog = changes.length() > changes.indexOf("Changes: ") + 8 ? changes.toString() : changes.append("No significant changes detected.").toString();
                }

                generatedDate = fmt.format(now);
                genQRImg(qrContent);

                txtPlace.setText(place != null ? place : "Unknown");
                txtQRGeneratedDate.setText(dateOnlyFmt.format(now));
                txtPlace.setVisibility(View.VISIBLE);
                txtQRGeneratedDate.setVisibility(View.VISIBLE);
                shimmerPlace.setVisibility(View.GONE);
                shimmerQRGeneratedDate.setVisibility(View.GONE);

                saveToQRHistory(userId, qrId, accHldr, instructions, helpline, generatedDate);
                saveNotification(userId, notificationType, changeLog);
                hasGeneratedQR = true;
            }).addOnFailureListener(e -> {
                // Fallback to generating a new QR code if database check fails, assuming it's new
                String notificationType = "QR_GENERATED";
                String changeLog = String.format("New QR code generated for place: %s, ticketId: %s, userId: %s, generated on: %s (database check failed, treated as new)",
                        place != null ? place : "Unknown", qrId, userId, fmt.format(now));

                generatedDate = fmt.format(now);
                genQRImg(qrContent);

                txtPlace.setText(place != null ? place : "Unknown");
                txtQRGeneratedDate.setText(dateOnlyFmt.format(now));
                txtPlace.setVisibility(View.VISIBLE);
                txtQRGeneratedDate.setVisibility(View.VISIBLE);
                shimmerPlace.setVisibility(View.GONE);
                shimmerQRGeneratedDate.setVisibility(View.GONE);

                saveToQRHistory(userId, qrId, accHldr, instructions, helpline, generatedDate);
                saveNotification(userId, notificationType, changeLog);
                hasGeneratedQR = true;

                Log.e(TAG, "Error checking QR code existence: " + e.getMessage(), e);
            });
        } catch (Exception e) {
            showErrDlg("Error", "Failed to generate QR code: " + e.getMessage());
            Log.e(TAG, "Error in genQR: " + e.getMessage(), e);
        }
    }

    private String saveQRAsPDF(boolean saveLocally, String ticketId, String generatedDate) {
        try {
            FirebaseUser user = auth.getCurrentUser();
            if (user == null) {
                showToast("Not authenticated. Please log in again.");
                return null;
            }
            String qrContent = ticketId + "|" + user.getUid();
            BitMatrix matrix = new QRCodeWriter().encode(qrContent, BarcodeFormat.QR_CODE, 500, 500);
            Bitmap qr = new BarcodeEncoder().createBitmap(matrix);

            PdfDocument doc = new PdfDocument();
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
            PdfDocument.Page page = doc.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            canvas.drawColor(Color.WHITE);

            Paint textPaint = new Paint();
            textPaint.setColor(Color.BLACK);
            textPaint.setTextSize(20f);
            textPaint.setAntiAlias(true);
            textPaint.setTextAlign(Paint.Align.CENTER);
            float pageWidth = pageInfo.getPageWidth();
            float textX = pageWidth / 2f;
            float textY = 50f;
            canvas.drawText("SCAN FOR A TICKET", textX, textY, textPaint);
            float lineHeight = textPaint.getTextSize() * 1.2f;

            float qrX = (pageWidth - 500f) / 2f;
            float qrY = textY + lineHeight + 20f;
            canvas.drawBitmap(qr, qrX, qrY, null);

            Paint borderPaint = new Paint();
            borderPaint.setColor(Color.BLACK);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(2f);
            canvas.drawRect(qrX, qrY, qrX + 500f, qrY + 500f, borderPaint);

            textY = qrY + 500f + 20f;
            canvas.drawText(place != null ? place : "Unknown Place", textX, textY, textPaint);
            textY += lineHeight;
            String displayDate = "Unknown Date";
            try {
                Date validFromDate = validFrom != null ? fmt.parse(validFrom) : new Date();
                displayDate = dateOnlyFmt.format(validFromDate);
            } catch (Exception e) {
                Log.e(TAG, "Error parsing validFrom date for PDF: " + e.getMessage());
            }
            canvas.drawText(displayDate, textX, textY, textPaint);

            doc.finishPage(page);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.writeTo(baos);
            byte[] pdfData = baos.toByteArray();
            String base64Pdf = Base64.encodeToString(pdfData, Base64.DEFAULT);

            if (saveLocally) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    showToast("Storage permission required to save PDF.");
                    return null;
                }

                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), QR_DIR);
                if (!dir.exists() && !dir.mkdirs()) {
                    showToast("Failed to create directory for PDF storage.");
                    Log.e(TAG, "Failed to create directory: " + dir.getAbsolutePath());
                    return null;
                }

                String safeLoc = place != null ? place.replaceAll("[^a-zA-Z0-9]", "_") : "QR";
                String qrDate = generatedDate != null ? generatedDate.replace(":", "_").replace("-", "_") : "unknown_date";
                File pdfFile = new File(dir, String.format("%s_%s.pdf", safeLoc, qrDate));

                try (FileOutputStream fos = new FileOutputStream(pdfFile)) {
                    doc.writeTo(fos);
                }

                MediaScannerConnection.scanFile(this, new String[]{pdfFile.getAbsolutePath()}, new String[]{"application/pdf"},
                        (path, uri) -> showDownloadNotification(pdfFile.getAbsolutePath()));
            }

            doc.close();
            return base64Pdf;
        } catch (Exception e) {
            showToast("Failed to save PDF.");
            Log.e(TAG, "Error saving PDF: " + e.getMessage(), e);
            return null;
        }
    }

    private void genQRImg(String qrContent) {
        try {
            QRCodeWriter w = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
            BitMatrix m = w.encode(qrContent, BarcodeFormat.QR_CODE, 600, 600, hints);
            qrImg.setImageBitmap(new BarcodeEncoder().createBitmap(m));
        } catch (WriterException e) {
            showErrDlg("Error", "Failed to generate QR code image.");
            Log.e(TAG, "Error generating QR image: " + e.getMessage(), e);
        }
    }

    private void saveToQRHistory(String adminUid, String ticketId, String adminName, String instructions, String helpline, String generatedDate) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null || !currentUser.getUid().equals(adminUid)) {
            Log.e(TAG, "User not authenticated or UID mismatch");
            showToast("Authentication error. Please log in again.");
            redirectToLogin();
            return;
        }

        String pdfData = saveQRAsPDF(false, ticketId, generatedDate);
        if (pdfData == null) {
            showToast("Failed to generate PDF for database.");
            return;
        }

        Map<String, Object> qrHistoryData = new HashMap<>();
        Intent intent = getIntent();
        qrHistoryData.put("placeName", place != null ? place : "Unknown");
        qrHistoryData.put("helpline", helpline != null ? helpline : "");
        qrHistoryData.put("instructions", instructions != null ? instructions : "");
        qrHistoryData.put("priceAdults", adultPrice);
        qrHistoryData.put("priceCamera", cameraPrice);
        qrHistoryData.put("priceChildren", childPrice);
        qrHistoryData.put("priceSenior", seniorCitizenPrice);
        qrHistoryData.put("priceStudents", studentPrice);
        qrHistoryData.put("ticketId", ticketId);
        qrHistoryData.put("timestamp", System.currentTimeMillis());
        qrHistoryData.put("totalAmount", intent.getStringExtra("TOTAL_AMOUNT") != null ? intent.getStringExtra("TOTAL_AMOUNT") : "0");
        qrHistoryData.put("baseAmount", intent.getStringExtra("BASE_AMOUNT") != null ? intent.getStringExtra("BASE_AMOUNT") : "0");
        qrHistoryData.put("adminName", adminName != null ? adminName : "");
        qrHistoryData.put("adults", intent.getIntExtra("ADULTS", 0));
        qrHistoryData.put("students", intent.getIntExtra("STUDENTS", 0));
        qrHistoryData.put("children", intent.getIntExtra("CHILDREN", 0));
        qrHistoryData.put("seniorCitizens", intent.getIntExtra("SENIOR_CITIZENS", 0));
        qrHistoryData.put("cameras", intent.getIntExtra("CAMERAS", 0));

        // Save only time with AM/PM for validFrom and validTo
        try {
            SimpleDateFormat timeOnlyFmt = new SimpleDateFormat("h:mm a", Locale.getDefault());
            Date validFromDate = validFrom != null ? fmt.parse(validFrom) : new Date();
            Date validToDate = validTo != null ? fmt.parse(validTo) : new Date();
            qrHistoryData.put("validFrom", timeOnlyFmt.format(validFromDate));
            qrHistoryData.put("validTo", timeOnlyFmt.format(validToDate));
        } catch (ParseException e) {
            Log.e(TAG, "Error parsing dates for QR history: " + e.getMessage());
            qrHistoryData.put("validFrom", validFrom != null ? validFrom : "");
            qrHistoryData.put("validTo", validTo != null ? validTo : "");
        }

        // Save generatedDate as a new variable
        qrHistoryData.put("generatedDate", generatedDate != null ? generatedDate : "");

        qrHistoryData.put("emailId", emailId != null ? emailId : "");
        qrHistoryData.put("bankAccountNumber", accNum != null ? accNum : "");
        qrHistoryData.put("bankName", bankName != null ? bankName : "");
        qrHistoryData.put("pdfData", pdfData);
        qrHistoryData.put("adminUid", adminUid);

        Log.d(TAG, "Saving QR data to Firebase: ticketId=" + ticketId + ", adminUid=" + adminUid);
        dbRef.child(QR_HISTORY).child(adminUid).child(ticketId).setValue(qrHistoryData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "QR History and PDF saved successfully for ticketId: " + ticketId);
                    showToast("QR data updated successfully");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save QR History for ticketId: " + ticketId + ", error: " + e.getMessage());
                    showToast("Failed to save QR data. Please try again.");
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
    private String getValidPrice(String price) {
        return price != null ? price : "0";
    }

    private boolean validBank() {
        if (accHldr == null || accNum == null || ifsc == null || emailId == null || bankName == null) {
            showToast("Missing bank details or email. Please update your profile.");
            return false;
        }
        return true;
    }

    private void setSysUI() {
        Window window = getWindow();
        window.setStatusBarColor(Color.WHITE);
        window.setNavigationBarColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.getInsetsController().setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
        } else {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID, "QR Code Downloads", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Notifications for QR code PDF downloads");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private void showDownloadNotification(String filePath) {
        File pdfFile = new File(filePath);
        if (!pdfFile.exists()) {
            showToast("PDF file not found.");
            Log.e(TAG, "PDF file not found at: " + filePath);
            return;
        }

        try {
            Uri contentUri = FileProvider.getUriForFile(this, "com.example.bookmyticket.fileprovider", pdfFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(contentUri, "application/pdf");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);

            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.initial)
                    .setContentTitle("QR Code PDF Downloaded")
                    .setContentText("QR Code PDF Downloaded Successfully")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                manager.notify((int) System.currentTimeMillis(), builder.build());
            } else {
                Log.w(TAG, "Notification permission not granted");
                showToast("PDF downloaded, but notification permission not granted.");
            }
        } catch (IllegalArgumentException e) {
            showToast("Failed to open PDF.");
            Log.e(TAG, "Error creating notification: " + e.getMessage(), e);
        }
    }



    private String genTktId() {
        StringBuilder sb = new StringBuilder(TKT_LEN);
        for (int i = 0; i < TKT_LEN; i++) {
            sb.append(CHARS.charAt(rnd.nextInt(CHARS.length())));
        }
        String ticketId = sb.toString();
        Log.d(TAG, "Generated ticketId: " + ticketId);
        return ticketId;
    }



    private double parsePrice(String p) {
        try {
            return Double.parseDouble(p);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void showErrDlg(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private void redirectToLogin() {
        Intent intent = new Intent(AdminSlide2Activity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}
