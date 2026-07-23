package com.example.bookmyticket.roles.parkingadmin;

import com.example.bookmyticket.R;
import com.example.bookmyticket.auth.LoginActivity;
import com.example.bookmyticket.model.Vehicle;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.util.Size;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.constraintlayout.motion.widget.MotionLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScanVehicleActivity extends AppCompatActivity {
    private static final String TAG = "ScanVehicleActivity";
    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final long SCAN_DEBOUNCE_DELAY = 100; // Ultra-fast processing
    private static final long ANIMATION_DURATION = 150; // Snappier animations
    private static final int TARGET_RESOLUTION_WIDTH = 640; // Minimal resolution for speed
    private static final int TARGET_RESOLUTION_HEIGHT = 480;
    private static final long[] DEFAULT_VIBRATION_PATTERN = {0, 40, 15, 25};

    // UI Components
    private MotionLayout motionLayout;
    private PreviewView cameraPreview;
    private ImageView scanLaser;
    private ImageView scanSuccess;
    private ImageView scannerBorder;
    private FrameLayout scannerFrame;
    private TextView txtRecognizedNumber;
    private MaterialButton btnScan;
    private MaterialButton btnConfirm;
    private MaterialButton btnManualEntry;
    private MaterialButton btnEdit;
    private ImageView btnBack;
    private ImageView btnFlash;

    // CameraX
    private Camera camera;
    private ProcessCameraProvider cameraProvider;
    private ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ScaleGestureDetector scaleGestureDetector;
    private float currentZoomRatio = 1.0f;
    private float maxZoomRatio = 30.0f;
    private float minZoomRatio = 1.0f;

    // ML Kit
    private TextRecognizer textRecognizer;
    private long lastScanTime = 0;
    private boolean isScanning = false;
    private boolean isAnimationPlaying = false;
    private String recognizedVehicleNumber = null;

    // Firebase
    private DatabaseReference vehicleDetailsReference;
    private DatabaseReference parkingAdminsReference;
    private DatabaseReference touristNotificationsReference;
    private DatabaseReference paymentsReference;
    private DatabaseReference adminNotificationsReference;
    private String adminUid;

    // Vibration
    private Vibrator vibrator;

    // Handlers
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Parking Prices
    private String carParkingPrice = "0";
    private String bikeParkingPrice = "0";
    private String busParkingPrice = "0";

    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_vehicle);
        overridePendingTransition(R.anim.fade_in_fast, R.anim.fade_out_fast);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        initializeViews();
        initializeFirebase();
        initializeTextRecognizer();
        setSystemColors();
        setupPinchToZoom();
        setupVibration();
        requestCameraPermission();
    }

    private void initializeViews() {
        motionLayout = findViewById(R.id.motion_layout);
        cameraPreview = findViewById(R.id.preview_view);
        scanLaser = findViewById(R.id.scan_laser);
        scanSuccess = findViewById(R.id.scan_success);
        scannerBorder = findViewById(R.id.scanner_border);
        scannerFrame = findViewById(R.id.scanner_frame);
        txtRecognizedNumber = findViewById(R.id.recognized_number);
        btnScan = findViewById(R.id.btn_scan);
        btnConfirm = findViewById(R.id.btn_confirm);
        btnManualEntry = findViewById(R.id.btn_manual_entry);
        btnEdit = findViewById(R.id.btn_edit);
        btnBack = findViewById(R.id.back_button);
        btnFlash = findViewById(R.id.flash_button);

        txtRecognizedNumber.setText("Align number plate within the frame");
        btnScan.setEnabled(true);
        btnConfirm.setEnabled(false);
        btnManualEntry.setEnabled(true);
        btnEdit.setEnabled(false);
        btnEdit.setVisibility(View.GONE);
        btnConfirm.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.disabled_grey_color));
        scanSuccess.setVisibility(View.GONE);
        scanLaser.setVisibility(View.VISIBLE);
        cameraPreview.setEnabled(true);

        setupButtonAnimations();

        btnBack.setOnClickListener(v -> {
            vibrate(DEFAULT_VIBRATION_PATTERN);
            onBackPressed();
        });

        btnFlash.setOnClickListener(v -> {
            vibrate(DEFAULT_VIBRATION_PATTERN);
            if (camera != null && camera.getCameraInfo().hasFlashUnit()) {
                boolean isFlashOn = camera.getCameraInfo().getTorchState().getValue() != null &&
                        camera.getCameraInfo().getTorchState().getValue() == 1;
                camera.getCameraControl().enableTorch(!isFlashOn);
                camera.getCameraInfo().getTorchState().observe(this, state -> {
                    boolean torchOn = state != null && state == 1;
                    btnFlash.setImageResource(torchOn ? R.drawable.ic_torch_on : R.drawable.ic_torch_off);
                    Toast.makeText(this, "Flash " + (torchOn ? "ON" : "OFF"), Toast.LENGTH_SHORT).show();
                });
            } else {
                Toast.makeText(this, "Flash not available", Toast.LENGTH_SHORT).show();
            }
        });

        btnScan.setOnClickListener(v -> {
            vibrate(DEFAULT_VIBRATION_PATTERN);
            if (!isScanning) {
                isScanning = true;
                txtRecognizedNumber.setText("Scanning vehicle number...");
                scanLaser.setVisibility(View.GONE);
                startScanning();
            } else {
                restartScanning();
            }
        });

        btnConfirm.setOnClickListener(v -> {
            vibrate(DEFAULT_VIBRATION_PATTERN);
            if (recognizedVehicleNumber != null && !recognizedVehicleNumber.isEmpty()) {
                showPaymentDialog(recognizedVehicleNumber);
            } else {
                restartScanning();
            }
        });

        btnManualEntry.setOnClickListener(v -> {
            vibrate(DEFAULT_VIBRATION_PATTERN);
            Intent intent = new Intent(this, ManualEntryActivity.class);
            startActivityForResult(intent, 101);
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        btnEdit.setOnClickListener(v -> {
            vibrate(DEFAULT_VIBRATION_PATTERN);
            Intent intent = new Intent(this, ManualEntryActivity.class);
            intent.putExtra("vehicle_number", recognizedVehicleNumber);
            startActivityForResult(intent, 101);
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });
    }

    private void setupButtonAnimations() {
        for (View button : new View[]{btnScan, btnConfirm, btnManualEntry, btnEdit, btnBack, btnFlash}) {
            button.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        ObjectAnimator scaleDownX = ObjectAnimator.ofFloat(v, "scaleX", 0.95f);
                        ObjectAnimator scaleDownY = ObjectAnimator.ofFloat(v, "scaleY", 0.95f);
                        scaleDownX.setDuration(80);
                        scaleDownY.setDuration(80);
                        AnimatorSet scaleDown = new AnimatorSet();
                        scaleDown.playTogether(scaleDownX, scaleDownY);
                        scaleDown.start();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        ObjectAnimator scaleUpX = ObjectAnimator.ofFloat(v, "scaleX", 1.0f);
                        ObjectAnimator scaleUpY = ObjectAnimator.ofFloat(v, "scaleY", 1.0f);
                        scaleUpX.setDuration(80);
                        scaleUpY.setDuration(80);
                        AnimatorSet scaleUp = new AnimatorSet();
                        scaleUp.playTogether(scaleUpX, scaleUpY);
                        scaleUp.start();
                        break;
                }
                return false;
            });
        }
    }

    private void initializeFirebase() {
        vehicleDetailsReference = FirebaseDatabase.getInstance().getReference("vehicle_details");
        parkingAdminsReference = FirebaseDatabase.getInstance().getReference("parkingadmins");
        touristNotificationsReference = FirebaseDatabase.getInstance().getReference("tourist_notifications");
        paymentsReference = FirebaseDatabase.getInstance().getReference("payments");
        adminNotificationsReference = FirebaseDatabase.getInstance().getReference("admin_notifications");

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "No authenticated user found");
            Toast.makeText(this, "Please log in to continue", Toast.LENGTH_LONG).show();
            redirectToLogin();
            return;
        }
        adminUid = currentUser.getUid();
        Log.d(TAG, "Authenticated Admin UID: " + adminUid);
        loadParkingPrices();
    }

    private void initializeTextRecognizer() {
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    }

    private void setupVibration() {
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    }

    private void setSystemColors() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.WHITE);
        window.setNavigationBarColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    private void loadParkingPrices() {
        if (adminUid == null || adminUid.isEmpty()) {
            Log.e(TAG, "loadParkingPrices: adminUid is null or empty");
            Toast.makeText(this, "Cannot load pricing: Invalid admin", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        parkingAdminsReference.child(adminUid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    carParkingPrice = snapshot.child("carParkingPrice").getValue(String.class);
                    bikeParkingPrice = snapshot.child("bikeParkingPrice").getValue(String.class);
                    busParkingPrice = snapshot.child("busParkingPrice").getValue(String.class);
                    Log.d(TAG, "Loaded prices: Car=" + carParkingPrice + ", Bike=" + bikeParkingPrice + ", Bus=" + busParkingPrice);
                } else {
                    Log.w(TAG, "No pricing data found for adminUid: " + adminUid);
                    Toast.makeText(ScanVehicleActivity.this, "Pricing data not available", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load parking prices: " + error.getMessage());
                Toast.makeText(ScanVehicleActivity.this, "Failed to load pricing: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        } else {
            startCamera();
        }
    }

    private void startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCamera();
            } catch (Exception e) {
                Log.e(TAG, "Camera initialization failed", e);
                runOnUiThread(() -> Toast.makeText(this, "Camera unavailable. Please try again.", Toast.LENGTH_LONG).show());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void bindCamera() {
        Preview preview = new Preview.Builder()
                .setTargetResolution(new Size(TARGET_RESOLUTION_WIDTH, TARGET_RESOLUTION_HEIGHT))
                .build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(TARGET_RESOLUTION_WIDTH, TARGET_RESOLUTION_HEIGHT))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, image -> {
            if (!isScanning || isAnimationPlaying) {
                image.close();
                return;
            }

            long currentTime = System.currentTimeMillis();
            if (currentTime - lastScanTime < SCAN_DEBOUNCE_DELAY) {
                image.close();
                return;
            }
            lastScanTime = currentTime;

            cameraPreview.post(() -> {
                try {
                    int[] frameLocation = new int[2];
                    scannerFrame.getLocationOnScreen(frameLocation);
                    int frameLeft = frameLocation[0];
                    int frameTop = frameLocation[1];
                    int frameWidth = scannerFrame.getWidth();
                    int frameHeight = scannerFrame.getHeight();

                    int[] previewLocation = new int[2];
                    cameraPreview.getLocationOnScreen(previewLocation);
                    int previewLeft = previewLocation[0];
                    int previewTop = previewLocation[1];

                    int cropLeft = Math.max(0, frameLeft - previewLeft);
                    int cropTop = Math.max(0, frameTop - previewTop);
                    int cropRight = Math.min(cropLeft + frameWidth, cameraPreview.getWidth());
                    int cropBottom = Math.min(cropTop + frameHeight, cameraPreview.getHeight());

                    if (cropRight <= cropLeft || cropBottom <= cropTop) {
                        Log.w(TAG, "Invalid crop region");
                        image.close();
                        return;
                    }

                    Bitmap fullBitmap = cameraPreview.getBitmap();
                    if (fullBitmap == null) {
                        Log.w(TAG, "Failed to get bitmap from PreviewView");
                        image.close();
                        return;
                    }

                    float scaleX = (float) fullBitmap.getWidth() / cameraPreview.getWidth();
                    float scaleY = (float) fullBitmap.getHeight() / cameraPreview.getHeight();
                    int scaledLeft = Math.round(cropLeft * scaleX);
                    int scaledTop = Math.round(cropTop * scaleY);
                    int scaledWidth = Math.round((cropRight - cropLeft) * scaleX);
                    int scaledHeight = Math.round((cropBottom - cropTop) * scaleY);

                    scaledLeft = Math.max(0, scaledLeft);
                    scaledTop = Math.max(0, scaledTop);
                    scaledWidth = Math.min(scaledWidth, fullBitmap.getWidth() - scaledLeft);
                    scaledHeight = Math.min(scaledHeight, fullBitmap.getHeight() - scaledTop);

                    if (scaledWidth <= 0 || scaledHeight <= 0) {
                        Log.w(TAG, "Invalid scaled crop dimensions: width=" + scaledWidth + ", height=" + scaledHeight);
                        fullBitmap.recycle();
                        image.close();
                        return;
                    }

                    Bitmap croppedBitmap = Bitmap.createBitmap(
                            fullBitmap,
                            scaledLeft,
                            scaledTop,
                            scaledWidth,
                            scaledHeight
                    );
                    fullBitmap.recycle();

                    Matrix matrix = new Matrix();
                    matrix.postRotate(image.getImageInfo().getRotationDegrees());
                    Bitmap rotatedBitmap = Bitmap.createBitmap(
                            croppedBitmap,
                            0,
                            0,
                            croppedBitmap.getWidth(),
                            croppedBitmap.getHeight(),
                            matrix,
                            true
                    );
                    croppedBitmap.recycle();

                    InputImage inputImage = InputImage.fromBitmap(rotatedBitmap, 0);
                    textRecognizer.process(inputImage)
                            .addOnSuccessListener(visionText -> {
                                String recognizedText = visionText.getText();
                                Log.d(TAG, "OCR raw text: [" + recognizedText + "]");
                                String vehicleNumber = extractVehicleNumber(recognizedText);
                                if (!vehicleNumber.equals("No vehicle number detected") && !vehicleNumber.equals(recognizedVehicleNumber)) {
                                    recognizedVehicleNumber = vehicleNumber;
                                    runOnUiThread(() -> {
                                        isScanning = false;
                                        scanLaser.setVisibility(View.VISIBLE);
                                        playScanAnimation();
                                        txtRecognizedNumber.setText("Recognized: " + vehicleNumber);
                                        btnConfirm.setEnabled(true);
                                        btnConfirm.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.primary_teal));
                                        btnEdit.setEnabled(true);
                                        btnEdit.setVisibility(View.VISIBLE);
                                        btnScan.setText("Rescan");
                                        motionLayout.transitionToEnd();
                                    });
                                }
                                rotatedBitmap.recycle();
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Text recognition failed: " + e.getMessage());
                                rotatedBitmap.recycle();
                            })
                            .addOnCompleteListener(task -> image.close());
                } catch (Exception e) {
                    Log.e(TAG, "Error in image processing pipeline: " + e.getMessage(), e);
                    image.close();
                }
            });
        });

        preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());
        cameraProvider.unbindAll();
        camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
        cameraPreview.setEnabled(true);
        maxZoomRatio = Math.min(camera.getCameraInfo().getZoomState().getValue().getMaxZoomRatio(), 30.0f);
        minZoomRatio = camera.getCameraInfo().getZoomState().getValue().getMinZoomRatio();
        currentZoomRatio = minZoomRatio;
        camera.getCameraControl().setZoomRatio(currentZoomRatio);
        setupContinuousAutoFocus();
        runOnUiThread(this::startBorderAnimation);
    }

    private void startScanning() {
        if (camera == null) {
            Toast.makeText(this, "Camera not initialized", Toast.LENGTH_SHORT).show();
            isScanning = false;
            txtRecognizedNumber.setText("Align number plate within the frame");
            btnScan.setText("Scan");
            scanLaser.setVisibility(View.VISIBLE);
            return;
        }
        isScanning = true;
        txtRecognizedNumber.setText("Scanning vehicle number...");
        scanLaser.setVisibility(View.GONE);
    }

    private void setupContinuousAutoFocus() {
        if (camera == null) return;
        MeteringPointFactory factory = cameraPreview.getMeteringPointFactory();
        float centerX = cameraPreview.getWidth() / 2f;
        float centerY = cameraPreview.getHeight() / 2f;
        MeteringPoint point = factory.createPoint(centerX, centerY, 0.25f);
        FocusMeteringAction action = new FocusMeteringAction.Builder(point)
                .setAutoCancelDuration(100, TimeUnit.MILLISECONDS) // Ultra-fast autofocus
                .build();
        camera.getCameraControl().startFocusAndMetering(action);
        mainHandler.postDelayed(this::setupContinuousAutoFocus, 100);
    }

    private String extractVehicleNumber(String text) {
        if (text == null || text.isEmpty()) return "No vehicle number detected";
        text = text.replaceAll("[\\s\\-.,:;]+", "").toUpperCase();
        Log.d(TAG, "Normalized OCR text: " + text);
        String regex = "^[A-Z]{2}[0-9]{2}[A-Z]{0,2}[0-9]{4}$";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            String detectedNumber = matcher.group(0);
            Log.d(TAG, "Extracted vehicle number: " + detectedNumber);
            return detectedNumber;
        }
        Log.w(TAG, "No vehicle number matched in text: " + text);
        return "No vehicle number detected";
    }

    private void setupPinchToZoom() {
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (camera != null) {
                    float scaleFactor = detector.getScaleFactor();
                    currentZoomRatio = Math.max(minZoomRatio, Math.min(currentZoomRatio * scaleFactor, maxZoomRatio));
                    camera.getCameraControl().setZoomRatio(currentZoomRatio);
                    return true;
                }
                return false;
            }
        });

        cameraPreview.setOnTouchListener((v, event) -> scaleGestureDetector.onTouchEvent(event));
    }

    private void startBorderAnimation() {
        scannerBorder.setVisibility(View.VISIBLE);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(scannerBorder, "scaleX", 0.98f, 1.02f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(scannerBorder, "scaleY", 0.98f, 1.02f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(scannerBorder, "alpha", 0.7f, 1.0f);
        scaleX.setDuration(300); // Fast but smooth
        scaleY.setDuration(300);
        alpha.setDuration(300);
        scaleX.setRepeatCount(ObjectAnimator.INFINITE);
        scaleY.setRepeatCount(ObjectAnimator.INFINITE);
        alpha.setRepeatCount(ObjectAnimator.INFINITE);
        scaleX.setRepeatMode(ObjectAnimator.REVERSE);
        scaleY.setRepeatMode(ObjectAnimator.REVERSE);
        alpha.setRepeatMode(ObjectAnimator.REVERSE);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(scaleX, scaleY, alpha);
        animatorSet.start();
        scannerBorder.setTag(animatorSet);
    }

    private void stopBorderAnimation() {
        if (scannerBorder.getTag() instanceof AnimatorSet) {
            ((AnimatorSet) scannerBorder.getTag()).cancel();
            scannerBorder.setTag(null);
        }
        stopLaserAnimation();
    }

    private void startLaserAnimation(float startY, float endY) {
        scanLaser.setVisibility(View.VISIBLE);
        scanLaser.setTranslationY(startY);
        ObjectAnimator laserAnimator = ObjectAnimator.ofFloat(scanLaser, "translationY", startY, endY);
        ObjectAnimator laserAlpha = ObjectAnimator.ofFloat(scanLaser, "alpha", 0.8f, 1.0f, 0.8f);
        laserAnimator.setDuration(ANIMATION_DURATION);
        laserAlpha.setDuration(ANIMATION_DURATION);
        AnimatorSet laserSet = new AnimatorSet();
        laserSet.playTogether(laserAnimator, laserAlpha);
        laserSet.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationEnd(Animator animation) {
                scanLaser.setVisibility(View.GONE);
            }
            @Override
            public void onAnimationStart(Animator animation) {}
            @Override
            public void onAnimationCancel(Animator animation) {
                scanLaser.setVisibility(View.GONE);
            }
            @Override
            public void onAnimationRepeat(Animator animation) {}
        });
        laserSet.start();
        scanLaser.setTag(laserSet);
    }

    private void stopLaserAnimation() {
        if (scanLaser.getTag() instanceof AnimatorSet) {
            ((AnimatorSet) scanLaser.getTag()).cancel();
            scanLaser.setTag(null);
        }
        scanLaser.setVisibility(View.GONE);
    }

    private void playScanAnimation() {
        runOnUiThread(() -> {
            if (isAnimationPlaying) return;
            isAnimationPlaying = true;
            stopBorderAnimation();
            scanSuccess.setVisibility(View.GONE);
            scannerBorder.setVisibility(View.VISIBLE);
            performScanVibration();

            float borderHeight = scannerBorder.getHeight() > 0 ? scannerBorder.getHeight() : 180 * getResources().getDisplayMetrics().density;
            float startY = -borderHeight / 2;
            float endY = borderHeight / 2;
            startLaserAnimation(startY, endY);

            scanSuccess.setVisibility(View.VISIBLE);
            scanSuccess.setScaleX(0f);
            scanSuccess.setScaleY(0f);
            AnimatorSet successSet = new AnimatorSet();
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(scanSuccess, "scaleX", 0f, 1.3f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(scanSuccess, "scaleY", 0f, 1.3f);
            ObjectAnimator alpha = ObjectAnimator.ofFloat(scanSuccess, "alpha", 0f, 1f);
            scaleX.setDuration(150);
            scaleY.setDuration(150);
            alpha.setDuration(150);
            successSet.playTogether(scaleX, scaleY, alpha);
            successSet.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    scanSuccess.animate().scaleX(1f).scaleY(1f).setDuration(80).withEndAction(() -> {
                        performSuccessVibration();
                        isAnimationPlaying = false;
                    }).start();
                }
                @Override
                public void onAnimationStart(Animator animation) {}
                @Override
                public void onAnimationCancel(Animator animation) {
                    isAnimationPlaying = false;
                }
                @Override
                public void onAnimationRepeat(Animator animation) {}
            });
            successSet.start();
        });
    }

    private void restartScanning() {
        runOnUiThread(() -> {
            isScanning = false;
            isAnimationPlaying = false;
            recognizedVehicleNumber = null;
            txtRecognizedNumber.setText("Align number plate within the frame");
            btnScan.setEnabled(true);
            btnScan.setText("Scan");
            btnManualEntry.setEnabled(true);
            btnEdit.setEnabled(false);
            btnEdit.setVisibility(View.GONE);
            btnConfirm.setEnabled(false);
            btnConfirm.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.disabled_grey_color));
            scanSuccess.setVisibility(View.GONE); // Explicitly hide success animation
            scanLaser.setVisibility(View.VISIBLE);
            currentZoomRatio = minZoomRatio;
            if (camera != null) {
                camera.getCameraControl().setZoomRatio(minZoomRatio);
            }
            motionLayout.transitionToStart();
            startBorderAnimation();
        });
    }

    private void fetchVehicleOwnerDetails(String vehicleNumber, AlertDialog dialog, TextView txtVehicleNumber, TextView txtOwnerUid, TextView txtVehicleType, TextView txtOwnerPhone, TextView txtPaymentAmount, TextView txtErrorMessage, MaterialButton btnConfirm) {
        Log.d(TAG, "Fetching details for vehicle number: " + vehicleNumber);
        vehicleDetailsReference.child(vehicleNumber).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                runOnUiThread(() -> {
                    txtErrorMessage.setVisibility(View.GONE);
                    btnConfirm.setEnabled(false);
                    btnConfirm.setBackgroundTintList(ContextCompat.getColorStateList(ScanVehicleActivity.this, R.color.disabled_grey_color));

                    if (snapshot.exists()) {
                        String userUid = snapshot.child("userUid").getValue(String.class);
                        String vehicleType = snapshot.child("vehicleType").getValue(String.class);
                        String phone = snapshot.child("phoneNumber").getValue(String.class);
                        Log.d(TAG, "Data retrieved - UID: " + userUid + ", Type: " + vehicleType + ", Phone: " + phone);

                        if (userUid != null && !userUid.isEmpty() && vehicleType != null && !vehicleType.isEmpty() && phone != null && !phone.isEmpty()) {
                            String amount = getVehiclePrice(vehicleType);
                            txtVehicleNumber.setText("Vehicle: " + vehicleNumber);
                            txtOwnerUid.setText("UID: " + userUid);
                            txtVehicleType.setText("Vehicle Type: " + vehicleType);
                            txtOwnerPhone.setText("Phone: " + phone);
                            txtPaymentAmount.setText("Amount: ₹" + amount);
                            btnConfirm.setEnabled(true);
                            btnConfirm.setBackgroundTintList(ContextCompat.getColorStateList(ScanVehicleActivity.this, R.color.primary_teal));
                            Log.d(TAG, "Vehicle details updated successfully for " + vehicleNumber);
                        } else {
                            txtErrorMessage.setVisibility(View.VISIBLE);
                            txtErrorMessage.setText("Incomplete vehicle details for " + vehicleNumber);
                            Log.w(TAG, "Incomplete data: UID=" + userUid + ", Type=" + vehicleType + ", Phone=" + phone);
                        }
                    } else {
                        txtErrorMessage.setVisibility(View.VISIBLE);
                        txtErrorMessage.setText("Vehicle " + vehicleNumber + " not found");
                        Log.w(TAG, "Vehicle " + vehicleNumber + " not found in database");
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                runOnUiThread(() -> {
                    txtErrorMessage.setVisibility(View.VISIBLE);
                    txtErrorMessage.setText("Database error: " + error.getMessage());
                    btnConfirm.setEnabled(false);
                    btnConfirm.setBackgroundTintList(ContextCompat.getColorStateList(ScanVehicleActivity.this, R.color.disabled_grey_color));
                });
            }
        });
    }

    private String getVehiclePrice(String vehicleType) {
        if (vehicleType == null) return "0";
        switch (vehicleType.toLowerCase()) {
            case "car":
                return carParkingPrice != null ? carParkingPrice : "0";
            case "bike":
                return bikeParkingPrice != null ? bikeParkingPrice : "0";
            case "bus":
                return busParkingPrice != null ? busParkingPrice : "0";
            default:
                Log.w(TAG, "Unknown vehicle type: " + vehicleType);
                return "0";
        }
    }

    private void showPaymentDialog(String vehicleNumber) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.FullScreenDialogTheme);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_payment_confirmation, null);
        builder.setView(dialogView);

        TextView txtTitle = dialogView.findViewById(R.id.txt_title);
        TextView txtVehicleNumber = dialogView.findViewById(R.id.txt_vehicle_number);
        TextView txtOwnerUid = dialogView.findViewById(R.id.txt_owner_uid);
        TextView txtVehicleType = dialogView.findViewById(R.id.txt_vehicle_type);
        TextView txtOwnerPhone = dialogView.findViewById(R.id.txt_owner_phone);
        TextView txtPaymentAmount = dialogView.findViewById(R.id.txt_payment_amount);
        TextView txtErrorMessage = dialogView.findViewById(R.id.txt_error_message);
        TextInputLayout tilManualEntry = dialogView.findViewById(R.id.til_manual_entry);
        TextInputEditText etManualEntry = dialogView.findViewById(R.id.et_manual_entry);
        MaterialButton btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_cancel);
        MaterialButton btnEditManual = dialogView.findViewById(R.id.btn_edit_manual);
        MaterialButton btnCancelEdit = dialogView.findViewById(R.id.btn_cancel_edit);
        MaterialButton btnSaveEdit = dialogView.findViewById(R.id.btn_save_edit);

        txtTitle.setText("Confirm Payment");
        txtVehicleNumber.setText("Vehicle: " + vehicleNumber);
        txtOwnerUid.setText("UID: Fetching...");
        txtVehicleType.setText("Vehicle Type: Fetching...");
        txtOwnerPhone.setText("Phone: Fetching...");
        txtPaymentAmount.setText("Amount: Fetching...");
        txtErrorMessage.setVisibility(View.GONE);
        tilManualEntry.setVisibility(View.GONE);
        btnCancelEdit.setVisibility(View.GONE);
        btnSaveEdit.setVisibility(View.GONE);
        btnConfirm.setEnabled(false);
        btnConfirm.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.disabled_grey_color));

        AlertDialog dialog = builder.create();
        String originalVehicleNumber = vehicleNumber;

        fetchVehicleOwnerDetails(vehicleNumber, dialog, txtVehicleNumber, txtOwnerUid, txtVehicleType, txtOwnerPhone, txtPaymentAmount, txtErrorMessage, btnConfirm);

        btnConfirm.setOnClickListener(v -> {
            vibrate(DEFAULT_VIBRATION_PATTERN);
            String currentVehicleNumber = txtVehicleNumber.getText().toString().replace("Vehicle: ", "");
            Log.d(TAG, "Confirming payment for vehicle: " + currentVehicleNumber);
            vehicleDetailsReference.child(currentVehicleNumber).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        String userUid = snapshot.child("userUid").getValue(String.class);
                        String vehicleType = snapshot.child("vehicleType").getValue(String.class);
                        String phone = snapshot.child("phoneNumber").getValue(String.class);
                        if (userUid != null && !userUid.isEmpty() && vehicleType != null && !vehicleType.isEmpty() && phone != null && !phone.isEmpty()) {
                            String amount = getVehiclePrice(vehicleType);
                            processPayment(currentVehicleNumber, userUid, vehicleType, amount);
                            dialog.dismiss();
                        } else {
                            txtErrorMessage.setVisibility(View.VISIBLE);
                            txtErrorMessage.setText("Incomplete vehicle details");
                            btnConfirm.setEnabled(false);
                            btnConfirm.setBackgroundTintList(ContextCompat.getColorStateList(ScanVehicleActivity.this, R.color.disabled_grey_color));
                        }
                    } else {
                        txtErrorMessage.setVisibility(View.VISIBLE);
                        txtErrorMessage.setText("Vehicle " + currentVehicleNumber + " not found");
                        btnConfirm.setEnabled(false);
                        btnConfirm.setBackgroundTintList(ContextCompat.getColorStateList(ScanVehicleActivity.this, R.color.disabled_grey_color));
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e(TAG, "Failed to fetch vehicle details: " + error.getMessage());
                    txtErrorMessage.setVisibility(View.VISIBLE);
                    txtErrorMessage.setText("Database error: " + error.getMessage());
                    btnConfirm.setEnabled(false);
                    btnConfirm.setBackgroundTintList(ContextCompat.getColorStateList(ScanVehicleActivity.this, R.color.disabled_grey_color));
                }
            });
        });

        btnCancel.setOnClickListener(v -> {
            vibrate(DEFAULT_VIBRATION_PATTERN);
            dialog.dismiss();
            restartScanning();
        });

        btnEditManual.setOnClickListener(v -> {
            vibrate(DEFAULT_VIBRATION_PATTERN);
            tilManualEntry.setVisibility(View.VISIBLE);
            etManualEntry.setText(recognizedVehicleNumber);
            btnEditManual.setVisibility(View.GONE);
            btnCancelEdit.setVisibility(View.VISIBLE);
            btnSaveEdit.setVisibility(View.VISIBLE);
            btnConfirm.setVisibility(View.GONE);
            btnCancel.setVisibility(View.GONE);
            txtErrorMessage.setVisibility(View.GONE);
        });

        btnCancelEdit.setOnClickListener(v -> {
            vibrate(DEFAULT_VIBRATION_PATTERN);
            tilManualEntry.setVisibility(View.GONE);
            tilManualEntry.setError(null);
            txtVehicleNumber.setText("Vehicle: " + originalVehicleNumber);
            recognizedVehicleNumber = originalVehicleNumber;
            btnEditManual.setVisibility(View.VISIBLE);
            btnCancelEdit.setVisibility(View.GONE);
            btnSaveEdit.setVisibility(View.GONE);
            btnConfirm.setVisibility(View.VISIBLE);
            btnCancel.setVisibility(View.VISIBLE);
            txtErrorMessage.setVisibility(View.GONE);
            txtOwnerUid.setText("UID: Fetching...");
            txtVehicleType.setText("Vehicle Type: Fetching...");
            txtOwnerPhone.setText("Phone: Fetching...");
            txtPaymentAmount.setText("Amount: Fetching...");
            fetchVehicleOwnerDetails(originalVehicleNumber, dialog, txtVehicleNumber, txtOwnerUid, txtVehicleType, txtOwnerPhone, txtPaymentAmount, txtErrorMessage, btnConfirm);
        });

        btnSaveEdit.setOnClickListener(v -> {
            vibrate(DEFAULT_VIBRATION_PATTERN);
            String manualNumber = etManualEntry.getText().toString().trim().toUpperCase();
            Log.d(TAG, "Saving edited vehicle number: " + manualNumber);
            if (manualNumber.matches("^[A-Z]{2}[0-9]{2}[A-Z]{0,2}[0-9]{4}$")) {
                tilManualEntry.setError(null);
                recognizedVehicleNumber = manualNumber;
                txtVehicleNumber.setText("Vehicle: " + manualNumber);
                txtErrorMessage.setVisibility(View.GONE);
                tilManualEntry.setVisibility(View.GONE);
                btnEditManual.setVisibility(View.VISIBLE);
                btnCancelEdit.setVisibility(View.GONE);
                btnSaveEdit.setVisibility(View.GONE);
                btnConfirm.setVisibility(View.VISIBLE);
                btnCancel.setVisibility(View.VISIBLE);
                txtOwnerUid.setText("UID: Fetching...");
                txtVehicleType.setText("Vehicle Type: Fetching...");
                txtOwnerPhone.setText("Phone: Fetching...");
                txtPaymentAmount.setText("Amount: Fetching...");
                btnConfirm.setEnabled(false);
                btnConfirm.setBackgroundTintList(ContextCompat.getColorStateList(ScanVehicleActivity.this, R.color.disabled_grey_color));
                fetchVehicleOwnerDetails(manualNumber, dialog, txtVehicleNumber, txtOwnerUid, txtVehicleType, txtOwnerPhone, txtPaymentAmount, txtErrorMessage, btnConfirm);
            } else {
                tilManualEntry.setError("Enter valid vehicle number (e.g., MH12AB1234)");
                txtErrorMessage.setVisibility(View.VISIBLE);
                txtErrorMessage.setText("Invalid vehicle number format");
                Log.w(TAG, "Invalid vehicle number format: " + manualNumber);
            }
        });

        dialog.setOnShowListener(d -> {
            dialogView.setTranslationY(1000);
            dialogView.animate()
                    .translationY(0)
                    .setDuration(300)
                    .setInterpolator(new OvershootInterpolator())
                    .start();
        });
        dialog.setOnDismissListener(d -> restartScanning());
        dialog.show();
    }

    private void processPayment(String vehicleNumber, String userUid, String vehicleType, String amount) {
        String paymentId = paymentsReference.child(userUid).push().getKey();
        Map<String, Object> paymentData = new HashMap<>();
        paymentData.put("vehicleNumber", vehicleNumber);
        paymentData.put("amount", amount);
        paymentData.put("status", "pending");
        paymentData.put("timestamp", System.currentTimeMillis());
        paymentData.put("adminUid", adminUid);
        paymentData.put("vehicleType", vehicleType);

        paymentsReference.child(userUid).child(paymentId).setValue(paymentData)
                .addOnSuccessListener(aVoid -> {
                    sendNotificationToTourist(userUid, paymentId, vehicleNumber, amount, vehicleType);
                    saveAdminNotification(paymentId, vehicleNumber, amount, userUid);
                    Toast.makeText(this, "Payment request sent for " + vehicleNumber, Toast.LENGTH_LONG).show();
                    Log.d(TAG, "Processing payment for: " + vehicleNumber);
                    listenForPaymentCompletion(userUid, paymentId, amount);
                    restartScanning();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to create payment request: " + e.getMessage());
                    Toast.makeText(this, "Failed to send payment request: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    restartScanning();
                });
    }

    private void sendNotificationToTourist(String userUid, String paymentId, String vehicleNumber, String amount, String vehicleType) {
        String notificationId = touristNotificationsReference.child(userUid).push().getKey();
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("type", "payment_request");
        notificationData.put("paymentId", paymentId);
        notificationData.put("vehicleNumber", vehicleNumber);
        notificationData.put("amount", amount);
        notificationData.put("vehicleType", vehicleType);
        notificationData.put("timestamp", System.currentTimeMillis());
        touristNotificationsReference.child(userUid).child(notificationId).setValue(notificationData)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Tourist notification sent: " + notificationId))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to send tourist notification: " + e.getMessage()));
    }

    private void saveAdminNotification(String paymentId, String vehicleNumber, String amount, String userUid) {
        String notificationId = adminNotificationsReference.child(adminUid).push().getKey();
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("type", "payment_request_sent");
        notificationData.put("paymentId", paymentId);
        notificationData.put("vehicleNumber", vehicleNumber);
        notificationData.put("amount", amount);
        notificationData.put("userUid", userUid);
        notificationData.put("timestamp", System.currentTimeMillis());
        adminNotificationsReference.child(adminUid).child(notificationId).setValue(notificationData)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Admin notification stored: " + notificationId))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to store admin notification: " + e.getMessage()));
    }

    private void listenForPaymentCompletion(String userUid, String paymentId, String amount) {
        paymentsReference.child(userUid).child(paymentId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && "completed".equals(snapshot.child("status").getValue(String.class))) {
                    updateAdminDashboard(amount);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Payment status listener cancelled: " + error.getMessage());
            }
        });
    }

    private void updateAdminDashboard(String amount) {
        parkingAdminsReference.child(adminUid).runTransaction(new com.google.firebase.database.Transaction.Handler() {
            @NonNull
            @Override
            public com.google.firebase.database.Transaction.Result doTransaction(@NonNull com.google.firebase.database.MutableData mutableData) {
                Long activeBookings = mutableData.child("activebookings").getValue(Long.class);
                Double todaysRevenue = mutableData.child("todays_parking_revenue").getValue(Double.class);
                Long vehiclesToday = mutableData.child("vehicles_today").getValue(Long.class);

                mutableData.child("activebookings").setValue((activeBookings == null ? 0 : activeBookings) + 1);
                mutableData.child("todays_parking_revenue").setValue((todaysRevenue == null ? 0.0 : todaysRevenue) + Double.parseDouble(amount));
                mutableData.child("vehicles_today").setValue((vehiclesToday == null ? 0 : vehiclesToday) + 1);

                return com.google.firebase.database.Transaction.success(mutableData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
                if (error != null) {
                    Log.e(TAG, "Failed to update dashboard: " + error.getMessage());
                } else {
                    Log.d(TAG, "Dashboard updated successfully");
                }
            }
        });
    }

    private void performScanVibration() {
        vibrate(new long[]{0, 10, 20, 10});
    }

    private void performSuccessVibration() {
        vibrate(new long[]{0, 20, 80, 20});
    }

    private void vibrate(long[] pattern) {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                vibrator.vibrate(pattern, -1);
            }
        }
    }

    private void redirectToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 101 && resultCode == RESULT_OK && data != null) {
            String manualNumber = data.getStringExtra("vehicle_number");
            if (manualNumber != null && manualNumber.matches("^[A-Z]{2}[0-9]{2}[A-Z]{0,2}[0-9]{4}$")) {
                recognizedVehicleNumber = manualNumber;
                txtRecognizedNumber.setText("Recognized: " + manualNumber);
                btnConfirm.setEnabled(true);
                btnConfirm.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.primary_teal));
                motionLayout.transitionToEnd();
                showPaymentDialog(manualNumber);
            } else {
                Toast.makeText(this, "Invalid vehicle number format", Toast.LENGTH_SHORT).show();
                restartScanning();
            }
        } else {
            restartScanning();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isScanning && !isAnimationPlaying) {
            restartScanning();
            startCamera();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mainHandler.removeCallbacksAndMessages(null);
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        try {
            if (!cameraExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                cameraExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cameraExecutor.shutdownNow();
        }
        if (textRecognizer != null) {
            textRecognizer.close();
        }
    }

    @Override
    public void onBackPressed() {
        overridePendingTransition(R.anim.fade_in_fast, R.anim.fade_out_fast);
        super.onBackPressed();
    }
}
