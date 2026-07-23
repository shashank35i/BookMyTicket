package com.example.bookmyticket.features.scanner;

import com.example.bookmyticket.R;
import com.example.bookmyticket.model.QRData;
import com.example.bookmyticket.roles.tourist.PaymentSuccessActivity;
import com.example.bookmyticket.roles.tourist.TouristPageActivity;

import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.InputType;
import android.util.Log;
import android.util.Size;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.core.SurfaceOrientedMeteringPointFactory;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import com.google.zxing.common.HybridBinarizer;
import com.razorpay.Checkout;
import com.razorpay.PaymentResultListener;
import org.json.JSONObject;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.google.common.util.concurrent.ListenableFuture;
import android.view.animation.AccelerateDecelerateInterpolator;

public class QRScannerActivity extends AppCompatActivity implements PaymentResultListener {
    private static final String TAG = "QRScanner";
    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final long SCAN_DEBOUNCE_DELAY = 50;
    private static final long ANIMATION_DURATION = 100;
    private static final String PREFS_NAME = "QRScannerPrefs";
    private static final String KEY_PAYMENT_PENDING = "paymentPending";
    private static final int TARGET_RESOLUTION_WIDTH = 640; // Reduced from 1280
    private static final int TARGET_RESOLUTION_HEIGHT = 480;
    private static final int AUTO_FOCUS_INTERVAL_MS = 1500;

    private PreviewView cameraPreview;
    private MaterialButton btnContinue;
    private MaterialCardView paymentContainer, numberOfPersonsSection;
    private ImageView scannerBorder, btnBackPayment, btnBackPersons, imgDownArrow, scanLaser, scanSuccess;
    private FrameLayout swipeContainer;
    private MaterialCardView swipeThumb;
    private TextView swipeText, txtTicketPrice, txtPlatformFee, txtGST, txtTotalAmount, txtConvenienceFees;
    private TextView txtAdults, txtStudents, txtChildren, txtSeniorCitizens, txtCameras;
    private LinearLayout feeDetails;
    private ProgressBar swipeProgress;
    private MaterialToolbar header;
    private boolean isSwiping = false;
    private boolean isDropdownVisible = false;
    private float initialX = 0f;

    private String ticketId, scannedData, totalAmount, place, validFrom, validTo, adminName, instructions, helpline;
    private double adultPrice, studentPrice, childPrice, seniorCitizenPrice, cameraPrice;
    private String adminUid, phoneNumber, firebaseUid;
    private boolean isLoggedIn;

    private DatabaseReference historyReference;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isScanning = false;
    private boolean isPaymentCompleted = false;
    private long lastScanTime = 0;
    private boolean isProcessingFrame = false;
    private static final String ALPHA_NUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int TICKET_ID_LENGTH = 8;
    private Vibrator vibrator;
    private ScaleGestureDetector scaleGestureDetector;
    private Camera camera;
    private float currentZoomRatio = 1.0f;
    private float maxZoomRatio = 10.0f;
    private float minZoomRatio = 1.0f;

    private final ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();
    private final MultiFormatReader qrReader = new MultiFormatReader();
    private final Map<String, Map<String, Object>> qrCache = new HashMap<>();
    private DatabaseReference touristNotificationsReference = FirebaseDatabase.getInstance().getReference("tourist_notifications");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Window window = getWindow();
        window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        WindowCompat.setDecorFitsSystemWindows(window, false);
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            controller.setAppearanceLightNavigationBars(false);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qrscanner);

        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true);
        } catch (Exception e) {
            Log.w(TAG, "Firebase persistence already enabled or failed: " + e.getMessage());
        }

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        initializeViews();
        if (cameraPreview == null || scannerBorder == null) {
            Log.e(TAG, "UI initialization failed: cameraPreview or scannerBorder is null");
            Toast.makeText(this, "UI initialization failed", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Intent intent = getIntent();
        firebaseUid = intent.getStringExtra("USER_UID");
        phoneNumber = intent.getStringExtra("phoneNumber");
        isLoggedIn = intent.getBooleanExtra("isLoggedIn", false);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        if (firebaseUid != null && phoneNumber != null && phoneNumber.matches("^[0-9]{10}$")) {
            Log.d(TAG, "Authenticated user: userUid=" + firebaseUid + ", phoneNumber=" + phoneNumber);
            editor.putString("userUid", firebaseUid)
                    .putString("phoneNumber", phoneNumber)
                    .putBoolean("isLoggedIn", isLoggedIn)
                    .apply();
        } else {
            Log.d(TAG, "Proceeding as unauthenticated user, signing in anonymously");
            FirebaseAuth.getInstance().signInAnonymously().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    firebaseUid = task.getResult().getUser().getUid();
                    editor.putString("userUid", firebaseUid)
                            .putBoolean("isLoggedIn", false)
                            .apply();
                    Log.d(TAG, "Anonymous auth successful: userUid=" + firebaseUid);
                } else {
                    Log.e(TAG, "Anonymous auth failed", task.getException());
                    Toast.makeText(this, "Authentication failed, please try again", Toast.LENGTH_SHORT).show();
                }
            });
            phoneNumber = null;
            isLoggedIn = false;
        }

        if (prefs.getBoolean(KEY_PAYMENT_PENDING, false) && !isPaymentCompleted) {
            editor.remove(KEY_PAYMENT_PENDING).apply();
            startActivity(new Intent(this, TouristPageActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
            return;
        }

        historyReference = FirebaseDatabase.getInstance().getReference("qrHistory");
        historyReference.keepSynced(true);
        Checkout.preload(getApplicationContext());
        setupButtonListeners();
        setupSwipeButton();
        setupPinchToZoom();
        requestCameraPermission();
    }

    private void initializeViews() {
        cameraPreview = findViewById(R.id.camera_preview);
        scannerBorder = findViewById(R.id.scanner_border);
        scanLaser = findViewById(R.id.scan_laser);
        scanSuccess = findViewById(R.id.scan_success);
        header = findViewById(R.id.header);
        paymentContainer = findViewById(R.id.payment_container);
        txtTicketPrice = findViewById(R.id.txtTicketPrice);
        txtPlatformFee = findViewById(R.id.txtPlatformFee);
        txtGST = findViewById(R.id.txtGST);
        txtTotalAmount = findViewById(R.id.txtTotalAmount);
        txtConvenienceFees = findViewById(R.id.txtConvenienceFees);
        imgDownArrow = findViewById(R.id.imgDownArrow);
        feeDetails = findViewById(R.id.feeDetails);
        btnBackPayment = findViewById(R.id.btn_back_payment);
        numberOfPersonsSection = findViewById(R.id.number_of_persons_section);
        btnContinue = findViewById(R.id.btnContinue);
        btnBackPersons = findViewById(R.id.btn_back_persons);
        txtAdults = findViewById(R.id.txtAdults);
        txtStudents = findViewById(R.id.txtStudents);
        txtChildren = findViewById(R.id.txtChildren);
        txtSeniorCitizens = findViewById(R.id.txtSeniorCitizens);
        txtCameras = findViewById(R.id.txtCameras);
        swipeContainer = findViewById(R.id.swipe_container);
        swipeThumb = findViewById(R.id.swipe_thumb);
        swipeText = findViewById(R.id.swipe_text);
        swipeProgress = findViewById(R.id.swipe_progress);
        resetUIState();
    }

    private void startQRScanner() {
        isScanning = false;
        scannedData = null;
        scanLaser.setVisibility(View.GONE);
        scanSuccess.setVisibility(View.GONE);
        runOnUiThread(() -> findViewById(R.id.scan_instructions).setVisibility(View.VISIBLE));

        Map<DecodeHintType, Object> hints = new HashMap<>();
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.QR_CODE));
        hints.put(DecodeHintType.TRY_HARDER, Boolean.FALSE); // Disable TRY_HARDER for speed
        qrReader.setHints(hints);

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Camera permission not granted, requesting permission");
            requestCameraPermission();
            return;
        }

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                attemptCameraBinding(cameraProvider, 0);
            } catch (Exception e) {
                Log.e(TAG, "Failed to get camera provider", e);
                handleCameraInitializationFailure(e, 0);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void attemptCameraBinding(ProcessCameraProvider cameraProvider, int attemptCount) {
        final int MAX_RETRIES = 3;
        try {
            cameraProvider.unbindAll();

            Preview.Builder previewBuilder = new Preview.Builder()
                    .setTargetRotation(cameraPreview.getDisplay().getRotation());
            try {
                previewBuilder.setTargetResolution(new Size(TARGET_RESOLUTION_WIDTH, TARGET_RESOLUTION_HEIGHT));
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Target resolution not supported, falling back to default");
            }
            Preview preview = previewBuilder.build();

            CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

            ImageAnalysis.Builder analysisBuilder = new ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888);
            try {
                analysisBuilder.setTargetResolution(new Size(TARGET_RESOLUTION_WIDTH, TARGET_RESOLUTION_HEIGHT));
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Analysis resolution not supported, falling back to default");
            }
            ImageAnalysis imageAnalysis = analysisBuilder.build();

            imageAnalysis.setAnalyzer(cameraExecutor, image -> {
                if (isProcessingFrame || isScanning || System.currentTimeMillis() - lastScanTime < SCAN_DEBOUNCE_DELAY) {
                    image.close();
                    return;
                }
                isProcessingFrame = true;
                lastScanTime = System.currentTimeMillis();

                try {
                    int[] rgbPixels = convertYUVToRGB(image);
                    if (rgbPixels == null) {
                        Log.w(TAG, "RGB conversion failed, skipping frame");
                        image.close();
                        return;
                    }

                    LuminanceSource source = new RGBLuminanceSource(image.getWidth(), image.getHeight(), rgbPixels);
                    BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
                    Result result = qrReader.decodeWithState(bitmap);

                    if (result != null && result.getText() != null) {
                        String newData = result.getText().trim();
                        Log.d(TAG, "Raw QR data scanned: " + newData + ", length: " + newData.length());
                        if (isQRCodeFullyVisible(result, image) && isValidQR(newData) && !newData.equals(scannedData)) {
                            Log.d(TAG, "Valid QR Scanned: " + newData);
                            runOnUiThread(() -> {
                                scannedData = newData;
                                isScanning = true;
                                scanLaser.setVisibility(View.GONE);
                                scanSuccess.setVisibility(View.VISIBLE);
                                findViewById(R.id.scan_instructions).setVisibility(View.GONE);
                                cameraProvider.unbindAll();
                                vibrate();
                                processScannedData(newData, 0);
                            });
                        } else {
                            Log.w(TAG, "QR scan failed: fullyVisible=" + isQRCodeFullyVisible(result, image) +
                                    ", validQR=" + isValidQR(newData) + ", isDuplicate=" + newData.equals(scannedData));
                        }
                    }
                } catch (com.google.zxing.NotFoundException e) {
                    // Silent catch for no QR code found
                } catch (Exception e) {
                    Log.w(TAG, "QR decode failed: " + e.getMessage());
                } finally {
                    image.close();
                    isProcessingFrame = false;
                }
            });

            preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            maxZoomRatio = Math.min(camera.getCameraInfo().getZoomState().getValue().getMaxZoomRatio(), 10.0f);
            minZoomRatio = camera.getCameraInfo().getZoomState().getValue().getMinZoomRatio();
            currentZoomRatio = minZoomRatio;
            camera.getCameraControl().setZoomRatio(currentZoomRatio);
            camera.getCameraControl().setLinearZoom(0f);
            camera.getCameraControl().enableTorch(false);

            runOnUiThread(() -> scanLaser.setVisibility(View.GONE));
            Log.d(TAG, "Camera bound successfully on attempt " + (attemptCount + 1));
        } catch (Exception e) {
            Log.e(TAG, "Camera binding attempt " + (attemptCount + 1) + " failed", e);
            if (attemptCount < MAX_RETRIES - 1) {
                mainHandler.postDelayed(() -> {
                    if (!isFinishing()) {
                        attemptCameraBinding(cameraProvider, attemptCount + 1);
                    }
                }, 300);
            } else {
                handleCameraInitializationFailure(e, attemptCount);
            }
        }
    }

    private boolean isQRCodeFullyVisible(Result result, ImageProxy image) {
        ResultPoint[] points = result.getResultPoints();
        if (points == null || points.length < 3) {
            Log.w(TAG, "QR code has insufficient finder patterns: " + (points == null ? "null" : points.length));
            return false;
        }

        float imageWidth = image.getWidth();
        float imageHeight = image.getHeight();
        float margin = 0.05f * Math.min(imageWidth, imageHeight); // Relaxed margin for better detection

        for (ResultPoint point : points) {
            float x = point.getX();
            float y = point.getY();
            if (x < margin || x > imageWidth - margin || y < margin || y > imageHeight - margin) {
                Log.w(TAG, "QR code finder pattern outside bounds: x=" + x + ", y=" + y);
                return false;
            }
        }

        float minX = Float.MAX_VALUE, maxX = Float.MIN_VALUE, minY = Float.MAX_VALUE, maxY = Float.MIN_VALUE;
        for (ResultPoint point : points) {
            minX = Math.min(minX, point.getX());
            maxX = Math.max(maxX, point.getX());
            minY = Math.min(minY, point.getY());
            maxY = Math.max(maxY, point.getY());
        }

        float qrWidth = maxX - minX;
        float qrHeight = maxY - minY;
        float areaRatio = (qrWidth * qrHeight) / (imageWidth * imageHeight);
        if (areaRatio < 0.01f) { // Relaxed from 0.02 to 0.01
            Log.w(TAG, "QR code too small, area ratio: " + areaRatio);
            return false;
        }

        Log.d(TAG, "QR code fully visible: areaRatio=" + areaRatio);
        return true;
    }

    private void handleCameraInitializationFailure(Exception e, int attemptCount) {
        Log.e(TAG, "Camera initialization failed after " + (attemptCount + 1) + " attempts", e);
        runOnUiThread(() -> {
            if (isFinishing()) return;
            Toast.makeText(this, "Unable to start camera. Please try again.", Toast.LENGTH_LONG).show();
            new AlertDialog.Builder(this)
                    .setTitle("Camera Error")
                    .setMessage("Failed to initialize camera. Would you like to retry or continue without scanning?")
                    .setPositiveButton("Retry", (dialog, which) -> {
                        vibrate();
                        startQRScanner();
                    })

                    .setCancelable(false)
                    .show();
        });
    }

    private int[] convertYUVToRGB(ImageProxy image) {
        try {
            ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
            ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
            ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();
            int yRowStride = image.getPlanes()[0].getRowStride();
            int uvRowStride = image.getPlanes()[1].getRowStride();
            int uvPixelStride = image.getPlanes()[1].getPixelStride();
            int width = image.getWidth();
            int height = image.getHeight();

            byte[] yData = new byte[yBuffer.remaining()];
            byte[] uData = new byte[uBuffer.remaining()];
            byte[] vData = new byte[vBuffer.remaining()];
            yBuffer.get(yData);
            uBuffer.get(uData);
            vBuffer.get(vData);

            int[] rgbPixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int yIndex = y * yRowStride + x;
                    int uvIndex = (y / 2) * uvRowStride + (x / 2) * uvPixelStride;

                    int Y = yData[yIndex] & 0xFF;
                    int U = uData[uvIndex] & 0xFF;
                    int V = vData[uvIndex] & 0xFF;

                    int R = Y + ((360 * (V - 128)) >> 8);
                    int G = Y - ((88 * (U - 128) + 183 * (V - 128)) >> 8);
                    int B = Y + ((454 * (U - 128)) >> 8);

                    R = Math.max(0, Math.min(255, R));
                    G = Math.max(0, Math.min(255, G));
                    B = Math.max(0, Math.min(255, B));

                    rgbPixels[y * width + x] = 0xFF000000 | (R << 16) | (G << 8) | B;
                }
            }
            return rgbPixels;
        } catch (Exception e) {
            Log.e(TAG, "YUV conversion failed: " + e.getMessage());
            return null;
        }
    }

    private boolean isValidQR(String data) {
        if (data == null || data.isEmpty()) {
            Log.w(TAG, "QR Validation: Data is null or empty");
            return false;
        }
        // Expecting format: ticketId|firebaseUid
        boolean isValid = data.matches("^[a-zA-Z0-9]{6,}\\|[a-zA-Z0-9-]+$");
        if (!isValid) {
            Log.w(TAG, "QR Validation: Data does not match expected format. Data=" + data + ", length=" + data.length());
            runOnUiThread(() -> {
                Toast.makeText(this, "Invalid QR code format: " + data, Toast.LENGTH_LONG).show();
            });
        }
        return isValid;
    }

    private void processScannedData(String qrContent, int retryCount) {
        final int MAX_RETRIES = 2;
        String[] parts = qrContent.trim().split("\\|");
        if (parts.length != 2 || parts[0].length() != 6 || parts[1].length() != 28) {
            Log.w(TAG, "Invalid QR content format: " + qrContent);
            handleQRNotFound(qrContent, retryCount);
            return;
        }
        String ticketId = parts[0].toUpperCase();
        String adminUid = parts[1];
        Log.d(TAG, "Processing QR: ticketId=" + ticketId + ", adminUid=" + adminUid);

        String cacheKey = ticketId + "|" + adminUid;
        if (qrCache.containsKey(cacheKey)) {
            Log.d(TAG, "Using cached QR data for ticketId: " + ticketId + ", adminUid: " + adminUid);
            applyQRData(qrCache.get(cacheKey));
            return;
        }

        FirebaseDatabase.getInstance().getReference(".info/connected").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean connected = snapshot.getValue(Boolean.class) != null && snapshot.getValue(Boolean.class);
                if (!connected && retryCount < MAX_RETRIES - 1) {
                    Log.w(TAG, "Firebase offline, retrying (" + (retryCount + 1) + "/" + MAX_RETRIES + ")");
                    mainHandler.postDelayed(() -> processScannedData(qrContent, retryCount + 1), 500);
                    return;
                }

                // Query specific adminUid node for the ticketId in qrHistory
                DatabaseReference qrHistoryRef = historyReference.child(adminUid).child(ticketId);
                DatabaseReference placeAdminRef = FirebaseDatabase.getInstance().getReference("placeadmin").child(adminUid);

                qrHistoryRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot ticketSnapshot) {
                        if (!ticketSnapshot.exists()) {
                            Log.w(TAG, "TicketId not found for adminUid: " + adminUid + ", ticketId: " + ticketId);
                            handleQRNotFound(qrContent, retryCount);
                            return;
                        }

                        // Query placeadmin node for fundAccountId and email
                        placeAdminRef.addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot adminSnapshot) {
                                Map<String, Object> qrData = new HashMap<>();
                                qrData.put("adminUid", ticketSnapshot.child("adminUid").getValue(String.class));
                                qrData.put("ticketId", ticketSnapshot.child("ticketId").getValue(String.class));
                                qrData.put("placeName", ticketSnapshot.child("placeName").getValue(String.class));
                                qrData.put("validFrom", ticketSnapshot.child("validFrom").getValue(String.class));
                                qrData.put("validTo", ticketSnapshot.child("validTo").getValue(String.class));
                                qrData.put("adminName", ticketSnapshot.child("adminName").getValue(String.class));
                                qrData.put("instructions", ticketSnapshot.child("instructions").getValue(String.class));
                                qrData.put("helpline", ticketSnapshot.child("helpline").getValue(String.class));
                                qrData.put("priceAdults", ticketSnapshot.child("priceAdults").getValue(Double.class));
                                qrData.put("priceStudents", ticketSnapshot.child("priceStudents").getValue(Double.class));
                                qrData.put("priceChildren", ticketSnapshot.child("priceChildren").getValue(Double.class));
                                qrData.put("priceSenior", ticketSnapshot.child("priceSenior").getValue(Double.class));
                                qrData.put("priceCamera", ticketSnapshot.child("priceCamera").getValue(Double.class));

                                // Add fundAccountId and email from placeadmin
                                qrData.put("fundAccountId", adminSnapshot.child("fundAccountId").getValue(String.class));
                                qrData.put("email", adminSnapshot.child("email").getValue(String.class));

                                Log.d(TAG, "QR data retrieved: ticketId=" + ticketId + ", adminUid=" + adminUid +
                                        ", fundAccountId=" + qrData.get("fundAccountId") + ", email=" + qrData.get("email"));

                                cameraExecutor.execute(() -> qrCache.put(cacheKey, qrData));
                                applyQRData(qrData);
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                                Log.w(TAG, "Placeadmin query cancelled: " + error.getMessage());
                                handleQRNotFound(qrContent, retryCount);
                            }
                        });
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.w(TAG, "QRHistory query cancelled: " + error.getMessage());
                        handleQRNotFound(qrContent, retryCount);
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "Connectivity check failed: " + error.getMessage());
                handleQRNotFound(qrContent, retryCount);
            }
        });
    }

    private void handleQRNotFound(String qrContent, int retryCount) {
        final int MAX_RETRIES = 2;
        if (retryCount < MAX_RETRIES - 1) {
            Log.d(TAG, "Retrying query (" + (retryCount + 1) + "/" + MAX_RETRIES + ")");
            mainHandler.postDelayed(() -> processScannedData(qrContent, retryCount + 1), 500);
        } else {
            runOnUiThread(() -> {
                new AlertDialog.Builder(QRScannerActivity.this)
                        .setTitle("Invalid QR Code")
                        .setMessage("The scanned QR code is invalid or not found. Please try again.")
                        .setPositiveButton("Retry", (dialog, which) -> {
                            vibrate();
                            restartScanning();
                        })
                        .setCancelable(false)
                        .show();
            });
        }
    }


    private void showNumberOfPersonsSection() {
        if (numberOfPersonsSection.getVisibility() == View.VISIBLE) return;
        mainHandler.removeCallbacksAndMessages(null);
        numberOfPersonsSection.setVisibility(View.VISIBLE);
        numberOfPersonsSection.setTranslationY(0);
        Log.d(TAG, "Number of persons section shown instantly for ticketId: " + ticketId);
    }

    private void hideNumberOfPersonsSection() {
        numberOfPersonsSection.animate()
                .translationY(getResources().getDisplayMetrics().heightPixels)
                .setDuration(ANIMATION_DURATION)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    numberOfPersonsSection.setVisibility(View.GONE);
                    restartScanning();
                })
                .start();
    }

    private void showPaymentContainer() {
        numberOfPersonsSection.setVisibility(View.GONE);
        paymentContainer.setVisibility(View.VISIBLE);
        swipeContainer.setVisibility(View.VISIBLE);
        feeDetails.setVisibility(View.GONE);
        isDropdownVisible = false;
        imgDownArrow.setImageResource(R.drawable.ic_expand_more);
        resetSwipeButton();
        swipeText.setText("Swipe to pay ₹" + (totalAmount != null ? totalAmount : "0.00"));
        cameraPreview.setVisibility(View.GONE);
        scannerBorder.setVisibility(View.GONE);
        scanLaser.setVisibility(View.GONE);
        scanSuccess.setVisibility(View.GONE);
        paymentContainer.setTranslationY(getResources().getDisplayMetrics().heightPixels);
        paymentContainer.animate()
                .translationY(0)
                .setDuration(ANIMATION_DURATION)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
    }

    private void hidePaymentContainerWithAnimation() {
        paymentContainer.animate()
                .translationY(getResources().getDisplayMetrics().heightPixels)
                .setDuration(ANIMATION_DURATION)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withStartAction(() -> {
                    swipeContainer.setVisibility(View.GONE);
                    feeDetails.setVisibility(View.GONE);
                    isDropdownVisible = false;
                    imgDownArrow.setImageResource(R.drawable.ic_expand_more);
                })
                .withEndAction(() -> {
                    paymentContainer.setVisibility(View.GONE);
                    numberOfPersonsSection.setVisibility(View.VISIBLE);
                    numberOfPersonsSection.setTranslationY(0);
                    calculateTotalAmount();
                    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                    prefs.edit().remove(KEY_PAYMENT_PENDING).apply();
                    btnContinue.setEnabled(true);
                    btnContinue.setAlpha(1f);
                })
                .start();
    }

    private void resetUIState() {
        paymentContainer.setVisibility(View.GONE);
        numberOfPersonsSection.setVisibility(View.GONE);
        scannerBorder.setVisibility(View.VISIBLE);
        cameraPreview.setVisibility(View.VISIBLE);
        scanLaser.setVisibility(View.GONE);
        scanSuccess.setVisibility(View.GONE);
        swipeContainer.setVisibility(View.GONE);
        feeDetails.setVisibility(View.GONE);
        isDropdownVisible = false;
        imgDownArrow.setImageResource(R.drawable.ic_expand_more);
        currentZoomRatio = minZoomRatio;
        mainHandler.removeCallbacksAndMessages(null);
    }

    private void requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.CAMERA)) {
                new AlertDialog.Builder(this)
                        .setTitle("Camera Permission Required")
                        .setMessage("This app needs camera access to scan QR codes. Please grant the permission.")
                        .setPositiveButton("Grant", (dialog, which) -> {
                            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
                        })

                        .setCancelable(false)
                        .show();
            } else {
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
            }
        } else {
            startQRScanner();
        }
    }

    private void setupButtonListeners() {
        ImageView btnBack = header.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> {
            vibrate();
            onBackPressed();
        });

        btnContinue.setOnClickListener(v -> {
            vibrate();
            calculateTotalAmount();
            if (totalAmount != null && !totalAmount.equals("0.00")) {
                showPaymentContainer();
            } else {
                Toast.makeText(this, "Please select at least one ticket", Toast.LENGTH_SHORT).show();
            }
        });

        btnBackPayment.setOnClickListener(v -> {
            vibrate();
            hidePaymentContainerWithAnimation();
        });

        btnBackPersons.setOnClickListener(v -> {
            vibrate();
            hideNumberOfPersonsSection();
        });

        txtConvenienceFees.setOnClickListener(v -> {
            vibrate();
            toggleConvenienceFeeDropdown();
        });

        imgDownArrow.setOnClickListener(v -> {
            vibrate();
            toggleConvenienceFeeDropdown();
        });

        setupNumberPicker(R.id.btnIncreaseAdults, R.id.btnDecreaseAdults, txtAdults);
        setupNumberPicker(R.id.btnIncreaseStudents, R.id.btnDecreaseStudents, txtStudents);
        setupNumberPicker(R.id.btnIncreaseChildren, R.id.btnDecreaseChildren, txtChildren);
        setupNumberPicker(R.id.btnIncreaseSeniorCitizens, R.id.btnDecreaseSeniorCitizens, txtSeniorCitizens);
        setupNumberPicker(R.id.btnIncreaseCameras, R.id.btnDecreaseCameras, txtCameras);
    }

    private void setupNumberPicker(int increaseId, int decreaseId, TextView txtValue) {
        ImageView btnIncrease = findViewById(increaseId);
        ImageView btnDecrease = findViewById(decreaseId);
        btnIncrease.setOnClickListener(v -> {
            vibrate();
            int currentValue = Integer.parseInt(txtValue.getText().toString());
            if (currentValue < 99) {
                txtValue.setText(String.valueOf(currentValue + 1));
                calculateTotalAmount();
            }
        });
        btnDecrease.setOnClickListener(v -> {
            vibrate();
            int currentValue = Integer.parseInt(txtValue.getText().toString());
            if (currentValue > 0) {
                txtValue.setText(String.valueOf(currentValue - 1));
                calculateTotalAmount();
            }
        });
    }

    private void toggleConvenienceFeeDropdown() {
        isDropdownVisible = !isDropdownVisible;
        feeDetails.setVisibility(isDropdownVisible ? View.VISIBLE : View.GONE);
        imgDownArrow.setImageResource(isDropdownVisible ? R.drawable.ic_expand_less : R.drawable.ic_expand_more);
    }

    private void calculateTotalAmount() {
        int adults = Integer.parseInt(txtAdults.getText().toString());
        int students = Integer.parseInt(txtStudents.getText().toString());
        int children = Integer.parseInt(txtChildren.getText().toString());
        int seniorCitizens = Integer.parseInt(txtSeniorCitizens.getText().toString());
        int cameras = Integer.parseInt(txtCameras.getText().toString());

        double baseAmount = (adults * adultPrice) + (students * studentPrice) +
                (children * childPrice) + (seniorCitizens * seniorCitizenPrice) +
                (cameras * cameraPrice);

        double platformFee = baseAmount * 0.20;
        double gst = platformFee * 0.18;
        double totalAmountDouble = baseAmount + platformFee + gst;

        this.totalAmount = String.format(Locale.getDefault(), "%.2f", totalAmountDouble);

        runOnUiThread(() -> {
            LinearLayout adultsContainer = findViewById(R.id.adultsContainer);
            LinearLayout studentsContainer = findViewById(R.id.studentsContainer);
            LinearLayout childrenContainer = findViewById(R.id.childrenContainer);
            LinearLayout seniorCitizensContainer = findViewById(R.id.seniorCitizensContainer);
            LinearLayout camerasContainer = findViewById(R.id.camerasContainer);
            TextView txtAdultsSummary = findViewById(R.id.txtAdultsSummary);
            TextView txtStudentsSummary = findViewById(R.id.txtStudentsSummary);
            TextView txtChildrenSummary = findViewById(R.id.txtChildrenSummary);
            TextView txtSeniorCitizensSummary = findViewById(R.id.txtSeniorCitizensSummary);
            TextView txtCamerasSummary = findViewById(R.id.txtCamerasSummary);

            adultsContainer.setVisibility(adults > 0 ? View.VISIBLE : View.GONE);
            studentsContainer.setVisibility(students > 0 ? View.VISIBLE : View.GONE);
            childrenContainer.setVisibility(children > 0 ? View.VISIBLE : View.GONE);
            seniorCitizensContainer.setVisibility(seniorCitizens > 0 ? View.VISIBLE : View.GONE);
            camerasContainer.setVisibility(cameras > 0 ? View.VISIBLE : View.GONE);

            if (adults > 0) txtAdultsSummary.setText(String.format(Locale.getDefault(), "%d × ₹%.0f", adults, adultPrice));
            if (students > 0) txtStudentsSummary.setText(String.format(Locale.getDefault(), "%d × ₹%.0f", students, studentPrice));
            if (children > 0) txtChildrenSummary.setText(String.format(Locale.getDefault(), "%d × ₹%.0f", children, childPrice));
            if (seniorCitizens > 0) txtSeniorCitizensSummary.setText(String.format(Locale.getDefault(), "%d × ₹%.0f", seniorCitizens, seniorCitizenPrice));
            if (cameras > 0) txtCamerasSummary.setText(String.format(Locale.getDefault(), "%d × ₹%.0f", cameras, cameraPrice));

            txtTicketPrice.setText(String.format(Locale.getDefault(), "₹%.2f", baseAmount));
            txtPlatformFee.setText(String.format(Locale.getDefault(), "₹%.2f", platformFee));
            txtGST.setText(String.format(Locale.getDefault(), "₹%.2f", gst));
            txtTotalAmount.setText(String.format(Locale.getDefault(), "₹%.2f", totalAmountDouble));
            txtConvenienceFees.setText(String.format(Locale.getDefault(), "Convenience fees ₹%.2f", platformFee + gst));
        });
    }

    private void startPayment() {
        if (isPaymentCompleted || totalAmount == null || totalAmount.equals("0.00")) {
            resetSwipeButton();
            return;
        }

        vibrate();
        try {
            int amountInPaise = (int) (Double.parseDouble(totalAmount) * 100);
            Checkout checkout = new Checkout();
            checkout.setKeyID("rzp_test_O9fu5gbYR1vL8i");
            JSONObject options = new JSONObject();
            options.put("name", "Book My Ticket");
            options.put("description", "Payment for QR Code: " + ticketId);
            options.put("currency", "INR");
            options.put("amount", amountInPaise);
            if (phoneNumber != null && !phoneNumber.isEmpty()) {
                options.put("prefill.contact", phoneNumber);
            }
            checkout.open(this, options);
        } catch (Exception e) {
            Log.e(TAG, "Payment initiation failed", e);
            Toast.makeText(this, "Payment initiation failed", Toast.LENGTH_SHORT).show();
            resetSwipeButton();
        }
    }

    private void saveNotification(String userUid, String type, String paymentId, String amount) {
        String notificationId = touristNotificationsReference.child(userUid).push().getKey();
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("type", type);
        notificationData.put("paymentId", paymentId != null ? paymentId : "N/A");
        notificationData.put("ticketId", ticketId != null ? ticketId : "N/A");
        notificationData.put("amount", amount != null ? amount : "0.00");
        notificationData.put("place", place != null ? place : "Unknown");
        notificationData.put("timestamp", System.currentTimeMillis());
        touristNotificationsReference.child(userUid).child(notificationId).setValue(notificationData)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Notification saved: " + notificationId + ", Type: " + type))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to save notification: " + e.getMessage()));
    }

    @Override
    public void onPaymentSuccess(String razorpayPaymentID) {
        isPaymentCompleted = true;
        vibrate();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().remove(KEY_PAYMENT_PENDING).apply();
        swipeProgress.setVisibility(View.INVISIBLE);
        swipeText.setText("Payment successful!");
        swipeText.setTextColor(Color.WHITE);
        saveNotification(firebaseUid, "payment_success", razorpayPaymentID, totalAmount);
        proceedToPaymentSuccess();
    }

    @Override
    public void onPaymentError(int code, String response) {
        if (isFinishing()) return;
        resetSwipeButton();
        swipeText.setText("Payment cancelled");
        swipeText.setTextColor(Color.parseColor("#FFCDD2"));
        vibrate();
        saveNotification(firebaseUid, "payment_failed", null, totalAmount);
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_PAYMENT_PENDING, false).apply();
        mainHandler.postDelayed(() -> {
            Intent intent = new Intent(this, TouristPageActivity.class);
            intent.putExtra("USER_UID", firebaseUid);
            intent.putExtra("phoneNumber", phoneNumber);
            intent.putExtra("isLoggedIn", isLoggedIn);
            intent.putExtra("FROM_PAYMENT_SUCCESS", false);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }, 1000);
    }

    private String generateTicketId() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(TICKET_ID_LENGTH);
        for (int i = 0; i < TICKET_ID_LENGTH; i++) {
            sb.append(ALPHA_NUMERIC.charAt(random.nextInt(ALPHA_NUMERIC.length())));
        }
        return sb.toString();
    }

    private void checkAndGenerateUniqueTicketId(OnTicketIdGenerated callback) {
        DatabaseReference ticketsRef = FirebaseDatabase.getInstance().getReference("tickets");
        String userId = firebaseUid;
        String ticketId = generateTicketId();

        ticketsRef.child(userId).child(ticketId).get().addOnCompleteListener(task -> {
            if (isFinishing()) return;
            if (task.isSuccessful() && task.getResult().exists()) {
                checkAndGenerateUniqueTicketId(callback);
            } else {
                callback.onTicketIdGenerated(ticketId);
            }
        });
    }

    private interface OnTicketIdGenerated {
        void onTicketIdGenerated(String ticketId);
    }

    private void applyQRData(Map<String, Object> qrData) {
        adminUid = (String) qrData.get("adminUid");
        place = (String) qrData.get("placeName");
        validFrom = (String) qrData.get("validFrom");
        validTo = (String) qrData.get("validTo");
        adminName = (String) qrData.get("adminName");
        instructions = (String) qrData.get("instructions");
        helpline = (String) qrData.get("helpline");
        adultPrice = qrData.get("priceAdults") != null ? (Double) qrData.get("priceAdults") : 0.0;
        studentPrice = qrData.get("priceStudents") != null ? (Double) qrData.get("priceStudents") : 0.0;
        childPrice = qrData.get("priceChildren") != null ? (Double) qrData.get("priceChildren") : 0.0;
        seniorCitizenPrice = qrData.get("priceSenior") != null ? (Double) qrData.get("priceSenior") : 0.0;
        cameraPrice = qrData.get("priceCamera") != null ? (Double) qrData.get("priceCamera") : 0.0;

        // Fallback for validFrom and validTo if null
        SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
        long currentTimestamp = System.currentTimeMillis();
        if (validFrom == null || validFrom.isEmpty()) {
            validFrom = sdf.format(new Date(currentTimestamp));
            Log.w(TAG, "validFrom was null or empty, using fallback: " + validFrom);
        }
        if (validTo == null || validTo.isEmpty()) {
            validTo = sdf.format(new Date(currentTimestamp + 3 * 60 * 60 * 1000)); // Default to 3 hours later
            Log.w(TAG, "validTo was null or empty, using fallback: " + validTo);
        }

        checkAndGenerateUniqueTicketId(newTicketId -> {
            this.ticketId = newTicketId;
            runOnUiThread(() -> {
                txtAdults.setText("0");
                txtStudents.setText("0");
                txtChildren.setText("0");
                txtSeniorCitizens.setText("0");
                txtCameras.setText("0");
                txtTotalAmount.setText("₹0.00");
                showNumberOfPersonsSection();
                Log.d(TAG, "Number of persons section shown for new ticketId: " + newTicketId +
                        ", validFrom: " + validFrom + ", validTo: " + validTo);
            });
        });
    }

    private void proceedToPaymentSuccess() {
        int adults = Integer.parseInt(txtAdults.getText().toString());
        int students = Integer.parseInt(txtStudents.getText().toString());
        int children = Integer.parseInt(txtChildren.getText().toString());
        int seniorCitizens = Integer.parseInt(txtSeniorCitizens.getText().toString());
        int cameras = Integer.parseInt(txtCameras.getText().toString());
        double baseAmount = (adults * adultPrice) + (students * studentPrice) +
                (children * childPrice) + (seniorCitizens * seniorCitizenPrice) +
                (cameras * cameraPrice);

        Intent intent = new Intent(this, PaymentSuccessActivity.class);
        intent.putExtra("FROM_PAYMENT_SUCCESS", true);
        intent.putExtra("USER_UID", firebaseUid);
        intent.putExtra("phoneNumber", phoneNumber);
        intent.putExtra("isLoggedIn", isLoggedIn);
        intent.putExtra("ADMIN_UID", adminUid);
        intent.putExtra("TICKET_ID", ticketId);
        intent.putExtra("PLACE", place);
        intent.putExtra("ADMIN_NAME", adminName);
        intent.putExtra("INSTRUCTIONS", instructions);
        intent.putExtra("HELPLINE", helpline);
        intent.putExtra("TOTAL_PERSONS", adults + students + children + seniorCitizens);
        intent.putExtra("TOTAL_AMOUNT", totalAmount);
        intent.putExtra("BASE_AMOUNT", String.format(Locale.getDefault(), "%.2f", baseAmount));
        intent.putExtra("ADULTS", adults);
        intent.putExtra("STUDENTS", students);
        intent.putExtra("CHILDREN", children);
        intent.putExtra("SENIOR_CITIZENS", seniorCitizens);
        intent.putExtra("CAMERAS", cameras);
        intent.putExtra("DATE", new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(System.currentTimeMillis()));
        intent.putExtra("TIME", new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(System.currentTimeMillis()));
        intent.putExtra("VALID_FROM", validFrom);
        intent.putExtra("VALIDITY_TIME", validTo);
        intent.putExtra("PRICE_ADULTS", adultPrice);
        intent.putExtra("PRICE_STUDENTS", studentPrice);
        intent.putExtra("PRICE_CHILDREN", childPrice);
        intent.putExtra("PRICE_SENIOR", seniorCitizenPrice);
        intent.putExtra("PRICE_CAMERA", cameraPrice);
        intent.putExtra("FUND_ACCOUNT_ID", (String) Objects.requireNonNull(qrCache.get(scannedData)).get("fundAccountId"));
        intent.putExtra("EMAIL", (String) Objects.requireNonNull(qrCache.get(scannedData)).get("email"));

        startActivity(intent);
        Toast.makeText(this, "Payment Successful", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void setupPinchToZoom() {
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (camera != null) {
                    currentZoomRatio = Math.max(minZoomRatio, Math.min(currentZoomRatio * detector.getScaleFactor(), maxZoomRatio));
                    camera.getCameraControl().setZoomRatio(currentZoomRatio);
                    return true;
                }
                return false;
            }
        });

        cameraPreview.setOnTouchListener((v, event) -> scaleGestureDetector.onTouchEvent(event));
    }

    private void setupSwipeButton() {
        swipeThumb.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (!isSwiping) {
                        initialX = event.getRawX();
                        isSwiping = true;
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (isSwiping) {
                        float distance = event.getRawX() - initialX;
                        int maxDistance = swipeContainer.getWidth() - swipeThumb.getWidth() - 8;
                        float newPosition = Math.min(Math.max(distance, 0), maxDistance);
                        swipeThumb.setTranslationX(newPosition);
                        float progress = newPosition / maxDistance;
                        swipeText.setAlpha(1f - (progress * 0.7f));
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (isSwiping) {
                        isSwiping = false;
                        float threshold = swipeContainer.getWidth() * 0.6f;
                        if (swipeThumb.getTranslationX() >= threshold - swipeThumb.getWidth()) {
                            completeSwipeAnimation();
                        } else {
                            resetSwipeAnimation();
                        }
                    }
                    return true;
            }
            return false;
        });
    }

    private void completeSwipeAnimation() {
        vibrate();
        swipeThumb.animate()
                .translationX(swipeContainer.getWidth() - swipeThumb.getWidth() - 8)
                .setDuration(ANIMATION_DURATION)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withStartAction(() -> {
                    swipeProgress.setVisibility(View.VISIBLE);
                    swipeText.setText("Processing...");
                    swipeText.setTextColor(Color.WHITE);
                })
                .withEndAction(() -> {
                    swipeThumb.setEnabled(false);
                    startPayment();
                })
                .start();
    }

    private void resetSwipeAnimation() {
        swipeThumb.animate()
                .translationX(0f)
                .setDuration(ANIMATION_DURATION)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withStartAction(() -> {
                    swipeText.setAlpha(1f);
                    swipeProgress.setVisibility(View.INVISIBLE);
                    swipeText.setText("Swipe to pay ₹" + (totalAmount != null ? totalAmount : "0.00"));
                    swipeText.setTextColor(Color.WHITE);
                })
                .withEndAction(() -> swipeThumb.setEnabled(true))
                .start();
    }

    private void resetSwipeButton() {
        swipeThumb.setTranslationX(0f);
        swipeText.setAlpha(1f);
        swipeProgress.setVisibility(View.INVISIBLE);
        swipeText.setText("Swipe to pay ₹" + (totalAmount != null ? totalAmount : "0.00"));
        swipeText.setTextColor(Color.WHITE);
        swipeThumb.setEnabled(true);
        isSwiping = false;
    }

    private void vibrate() {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(30);
            }
        }
    }

    private void restartScanning() {
        resetUIState();
        mainHandler.post(this::startQRScanner);
    }

    @Override
    public void onBackPressed() {
        if (swipeContainer.getVisibility() == View.VISIBLE || isSwiping) {
            saveNotification(firebaseUid, "payment_pending", null, totalAmount);
            new AlertDialog.Builder(this)
                    .setTitle("Warning")
                    .setMessage("Are you sure you want to exit? Your payment is in progress.")
                    .setPositiveButton("Confirm", (d, which) -> {
                        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                        prefs.edit().putBoolean(KEY_PAYMENT_PENDING, true).apply();
                        startActivity(new Intent(this, TouristPageActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
                        finish();
                    })
                    .setNegativeButton("Cancel", null)
                    .setCancelable(false)
                    .show();
        } else if (paymentContainer.getVisibility() == View.VISIBLE) {
            hidePaymentContainerWithAnimation();
        } else if (numberOfPersonsSection.getVisibility() == View.VISIBLE) {
            hideNumberOfPersonsSection();
        } else {
            if (scannedData != null && !isPaymentCompleted) {
                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                prefs.edit().putBoolean(KEY_PAYMENT_PENDING, true).apply();
                saveNotification(firebaseUid, "payment_pending", null, totalAmount);
            }
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        try {
            cameraExecutor.awaitTermination(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            cameraExecutor.shutdownNow();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isFinishing()) {
            mainHandler.postDelayed(this::requestCameraPermission, 0);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE && grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Camera permission granted");
                startQRScanner();
            } else {
                Log.e(TAG, "Camera permission denied");
                Toast.makeText(this, "Camera permission is required to scan QR codes", Toast.LENGTH_LONG).show();
                new AlertDialog.Builder(this)
                        .setTitle("Permission Denied")
                        .setMessage("Camera access is required for QR scanning. Retry or continue without scanning?")
                        .setPositiveButton("Retry", (dialog, which) -> requestCameraPermission())

                        .setCancelable(false)
                        .show();
            }
        }
    }
}
