package com.example.bookmyticket.roles.placeadmin;

import com.example.bookmyticket.R;
import com.example.bookmyticket.model.Ticket;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScanQRActivity extends AppCompatActivity {
    private PreviewView previewView;
    private ExecutorService cameraExecutor;
    private Camera camera;
    private boolean isTorchOn = false;
    private ImageView btnTorch, btnGallery, btnBack;
    private static final int CAMERA_REQUEST_CODE = 100;
    private static final int GALLERY_REQUEST_CODE = 200;
    private static final String TAG = "ScanQRActivity";
    private static final int TICKET_ID_LENGTH = 8;
    private boolean isProcessing = false;
    private ScaleGestureDetector scaleGestureDetector;
    private float zoomRatio = 1.0f;
    private static final float MAX_ZOOM = 4.0f;
    private ProcessCameraProvider cameraProvider;
    private final BarcodeScanner barcodeScanner = BarcodeScanning.getClient(
            new com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build()
    );
    private Animation toastAnimation;
    private Vibrator vibrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        setContentView(R.layout.activity_scan_qr);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        // Enable Firebase offline persistence
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true);
        } catch (Exception e) {
            Log.e(TAG, "Failed to enable Firebase persistence", e);
        }

        initializeViews();
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                Log.d(TAG, "Camera provider initialized successfully");
            } catch (Exception e) {
                Log.e(TAG, "Failed to preload camera provider", e);
                showCustomToastWithVibration("Error initializing camera provider");
            }
        }, ContextCompat.getMainExecutor(this));

        setupCamera();
        setupWindowColors();
        setupClickListeners();
        setupPinchToZoom();
        applyEnterAnimations();
        toastAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in_up);
    }

    private void initializeViews() {
        previewView = findViewById(R.id.camera_preview);
        btnTorch = findViewById(R.id.btn_torch);
        btnGallery = findViewById(R.id.btn_gallery);
        btnBack = findViewById(R.id.btn_back);
        cameraExecutor = Executors.newFixedThreadPool(2);
        Log.d(TAG, "Views initialized");
    }

    private void startCamera() {
        if (cameraProvider == null) {
            ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
            cameraProviderFuture.addListener(() -> {
                try {
                    cameraProvider = cameraProviderFuture.get();
                    bindCameraUseCases();
                } catch (Exception e) {
                    Log.e(TAG, "Camera init failed", e);
                    showCustomToastWithVibration("Error initializing camera");
                }
            }, ContextCompat.getMainExecutor(this));
        } else {
            bindCameraUseCases();
        }
    }

    private void bindCameraUseCases() {
        try {
            CameraSelector cameraSelector = new CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build();

            Preview preview = new Preview.Builder()
                    .setTargetResolution(new android.util.Size(1280, 720))
                    .build();
            preview.setSurfaceProvider(previewView.getSurfaceProvider());

            ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                    .setTargetResolution(new android.util.Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build();

            imageAnalysis.setAnalyzer(cameraExecutor, this::processImage);

            cameraProvider.unbindAll();
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
            camera.getCameraControl().setZoomRatio(zoomRatio);
            if (isTorchOn) {
                camera.getCameraControl().enableTorch(true);
            }
            Log.d(TAG, "Camera bound successfully");
        } catch (Exception e) {
            Log.e(TAG, "Camera bind failed", e);
            showCustomToastWithVibration("Error starting camera");
        }
    }

    private void setupCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST_CODE);
            Log.d(TAG, "Requesting camera permission");
        }
    }

    private void setupWindowColors() {
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.parseColor("#000000"));
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> {
            applyClickAnimation(v);
            finish();
        });
        btnTorch.setOnClickListener(v -> {
            applyClickAnimation(v);
            toggleTorch();
        });
        btnGallery.setOnClickListener(v -> {
            applyClickAnimation(v);
            openGallery();
        });
    }

    private void applyClickAnimation(View view) {
        Animation scale = AnimationUtils.loadAnimation(this, R.anim.button_scale);
        view.startAnimation(scale);
    }

    private void setupPinchToZoom() {
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (camera != null) {
                    float scale = detector.getScaleFactor();
                    zoomRatio = Math.min(Math.max(zoomRatio * scale, 1.0f), MAX_ZOOM);
                    camera.getCameraControl().setZoomRatio(zoomRatio);
                    Log.d(TAG, "Zoom adjusted to: " + zoomRatio);
                }
                return true;
            }
        });

        previewView.setOnTouchListener((v, event) -> {
            scaleGestureDetector.onTouchEvent(event);
            return true;
        });
    }

    private void applyEnterAnimations() {
        Animation headerAnim = AnimationUtils.loadAnimation(this, R.anim.fade_in_down);
        Animation footerAnim = AnimationUtils.loadAnimation(this, R.anim.fade_in_up);
        findViewById(R.id.header).startAnimation(headerAnim);
        findViewById(R.id.footer).startAnimation(footerAnim);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void processImage(ImageProxy image) {
        if (isProcessing || image.getImage() == null) {
            image.close();
            Log.d(TAG, "Skipping image processing: isProcessing=" + isProcessing + ", image=" + (image.getImage() == null ? "null" : "valid"));
            return;
        }

        isProcessing = true;
        InputImage inputImage = InputImage.fromMediaImage(image.getImage(), image.getImageInfo().getRotationDegrees());

        barcodeScanner.process(inputImage)
                .addOnSuccessListener(barcodes -> {
                    if (!barcodes.isEmpty()) {
                        String qrContent = barcodes.get(0).getRawValue();
                        if (qrContent == null || qrContent.isEmpty()) {
                            showCustomToastWithVibration("Invalid QR code");
                            Log.e(TAG, "Invalid QR code: qrContent is null or empty");
                            image.close();
                            isProcessing = false;
                            return;
                        }
                        try {
                            // Parse compact string format u:userId|t:ticketId
                            String userId = null;
                            String ticketId = null;
                            String[] parts = qrContent.split("\\|");
                            for (String part : parts) {
                                if (part.startsWith("u:")) {
                                    userId = part.substring(2);
                                } else if (part.startsWith("t:")) {
                                    ticketId = part.substring(2);
                                }
                            }
                            if (ticketId == null || ticketId.isEmpty() || ticketId.length() != TICKET_ID_LENGTH || userId == null || userId.isEmpty()) {
                                showCustomToastWithVibration("Invalid QR code data");
                                Log.e(TAG, "Invalid QR code data: ticketId=" + ticketId + ", userId=" + userId);
                                image.close();
                                isProcessing = false;
                                return;
                            }
                            Log.d(TAG, "Scanned QR code: userId=" + userId + ", ticketId=" + ticketId);
                            fetchTicketData(userId, ticketId);
                        } catch (Exception e) {
                            showCustomToastWithVibration("Invalid QR code format");
                            Log.e(TAG, "Failed to parse QR code content: " + qrContent, e);
                            image.close();
                            isProcessing = false;
                        }
                    } else {
                        Log.d(TAG, "No QR codes detected in image");
                        image.close();
                        isProcessing = false;
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to scan QR code", e);
                    showCustomToastWithVibration("Scan failed: " + e.getMessage());
                    image.close();
                    isProcessing = false;
                });
    }

    private void fetchTicketData(String userId, String ticketId) {
        // Validate ticket ID and user ID format to prevent injection
        if (!ticketId.matches("^[A-Z0-9]{" + TICKET_ID_LENGTH + "}$") || !userId.matches("^[A-Za-z0-9-_]+$")) {
            showCustomToastWithVibration("Invalid ticket ID or user ID format");
            Log.e(TAG, "Invalid format: ticketId=" + ticketId + ", userId=" + userId);
            isProcessing = false;
            return;
        }

        // Check authentication
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            showCustomToastWithVibration("Please sign in to scan tickets");
            Log.e(TAG, "User not authenticated");
            isProcessing = false;
            return;
        }

        Log.d(TAG, "Querying Firebase for userId: " + userId + ", ticketId: " + ticketId);
        DatabaseReference ticketRef = FirebaseDatabase.getInstance().getReference("tickets").child(userId).child(ticketId);
        ticketRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot ticketSnapshot) {
                Log.d(TAG, "Ticket snapshot for userId: " + userId + ", ticketId: " + ticketId + ", exists=" + ticketSnapshot.exists());
                if (ticketSnapshot.exists()) {
                    try {
                        // Validate required fields
                        String place = ticketSnapshot.child("place").getValue(String.class);
                        Integer adults = ticketSnapshot.child("adults").getValue(Integer.class);
                        Integer students = ticketSnapshot.child("students").getValue(Integer.class);
                        Integer children = ticketSnapshot.child("children").getValue(Integer.class);
                        Integer seniors = ticketSnapshot.child("seniorCitizens").getValue(Integer.class);
                        Integer cameras = ticketSnapshot.child("cameras").getValue(Integer.class);
                        String date = ticketSnapshot.child("date").getValue(String.class);
                        String time = ticketSnapshot.child("time").getValue(String.class);
                        String validTo = ticketSnapshot.child("validTo").getValue(String.class);
                        String totalAmount = ticketSnapshot.child("totalAmount").getValue(String.class);
                        String instructions = ticketSnapshot.child("instructions").getValue(String.class);
                        String helpline = ticketSnapshot.child("helpline").getValue(String.class);

                        if (place == null || adults == null || students == null || children == null ||
                                seniors == null || cameras == null || date == null || time == null ||
                                validTo == null || totalAmount == null || instructions == null || helpline == null) {
                            Log.e(TAG, "Missing or null ticket data for ticketId: " + ticketId + ", snapshot: " + ticketSnapshot.toString());
                            showCustomToastWithVibration("Invalid ticket data");
                            isProcessing = false;
                            return;
                        }

                        Intent intent = new Intent(ScanQRActivity.this, ValidationSuccessActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

                        intent.putExtra("PLACE", place);
                        intent.putExtra("ADULTS", adults);
                        intent.putExtra("STUDENTS", students);
                        intent.putExtra("CHILDREN", children);
                        intent.putExtra("SENIORS", seniors);
                        intent.putExtra("CAMERAS", cameras);
                        intent.putExtra("DATE", date);
                        intent.putExtra("TIME", time);
                        intent.putExtra("VALIDITY", validTo);
                        intent.putExtra("AMOUNT", totalAmount);
                        intent.putExtra("INSTRUCTIONS", instructions);
                        intent.putExtra("HELPLINE", helpline);
                        intent.putExtra("TICKET_ID", ticketId);

                        Log.d(TAG, "Launching ValidationSuccessActivity with ticketId: " + ticketId);
                        startActivity(intent);
                        finish();
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing ticket data for ticketId: " + ticketId, e);
                        showCustomToastWithVibration("Error processing ticket data");
                        isProcessing = false;
                    }
                } else {
                    Log.e(TAG, "Ticket not found for userId: " + userId + ", ticketId: " + ticketId);
                    showCustomToastWithVibration("Ticket not found");
                    isProcessing = false;
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Database error for userId: " + userId + ", ticketId: " + ticketId + ", error: " + databaseError.getMessage());
                showCustomToastWithVibration("Failed to fetch ticket data: " + databaseError.getMessage());
                isProcessing = false;
            }
        });
    }

    private void toggleTorch() {
        if (camera != null) {
            isTorchOn = !isTorchOn;
            camera.getCameraControl().enableTorch(isTorchOn);
            btnTorch.setImageResource(isTorchOn ? R.drawable.ic_torch_on : R.drawable.ic_torch_off);
            Log.d(TAG, "Torch toggled: " + (isTorchOn ? "ON" : "OFF"));
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, GALLERY_REQUEST_CODE);
        Log.d(TAG, "Opening gallery for QR code selection");
    }

    private void showCustomToastWithVibration(String message) {
        try {
            if (vibrator.hasVibrator()) {
                vibrator.vibrate(50);
            }

            LayoutInflater inflater = getLayoutInflater();
            View layout = inflater.inflate(R.layout.custom_toast, null);
            TextView text = layout.findViewById(R.id.toast_text);
            text.setText(message);
            layout.setBackgroundResource(R.drawable.toast_background);

            Toast toast = new Toast(this);
            toast.setDuration(Toast.LENGTH_SHORT);
            toast.setView(layout);
            layout.startAnimation(toastAnimation);
            toast.show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing custom toast: " + message, e);
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == GALLERY_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            try {
                if (isProcessing) {
                    Log.d(TAG, "Skipping gallery processing: already processing");
                    return;
                }
                isProcessing = true;

                Uri imageUri = data.getData();
                InputImage image = InputImage.fromFilePath(this, imageUri);

                barcodeScanner.process(image)
                        .addOnSuccessListener(barcodes -> {
                            if (!barcodes.isEmpty()) {
                                String qrContent = barcodes.get(0).getRawValue();
                                if (qrContent == null || qrContent.isEmpty()) {
                                    showCustomToastWithVibration("Invalid QR code");
                                    Log.e(TAG, "Invalid QR code from gallery: qrContent is null or empty");
                                    isProcessing = false;
                                    return;
                                }
                                try {
                                    // Parse compact string format u:userId|t:ticketId
                                    String userId = null;
                                    String ticketId = null;
                                    String[] parts = qrContent.split("\\|");
                                    for (String part : parts) {
                                        if (part.startsWith("u:")) {
                                            userId = part.substring(2);
                                        } else if (part.startsWith("t:")) {
                                            ticketId = part.substring(2);
                                        }
                                    }
                                    if (ticketId == null || ticketId.isEmpty() || ticketId.length() != TICKET_ID_LENGTH || userId == null || userId.isEmpty()) {
                                        showCustomToastWithVibration("Invalid QR code data");
                                        Log.e(TAG, "Invalid QR code data from gallery: ticketId=" + ticketId + ", userId=" + userId);
                                        isProcessing = false;
                                        return;
                                    }
                                    Log.d(TAG, "Scanned QR code from gallery: userId=" + userId + ", ticketId=" + ticketId);
                                    fetchTicketData(userId, ticketId);
                                } catch (Exception e) {
                                    showCustomToastWithVibration("Invalid QR code format");
                                    Log.e(TAG, "Failed to parse QR code content from gallery: " + qrContent, e);
                                    isProcessing = false;
                                }
                            } else {
                                showCustomToastWithVibration("No QR code found in image");
                                Log.d(TAG, "No QR codes detected in gallery image");
                                isProcessing = false;
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to scan QR code from gallery", e);
                            showCustomToastWithVibration("Scan failed: " + e.getMessage());
                            isProcessing = false;
                        });
            } catch (IOException e) {
                Log.e(TAG, "Error loading gallery image", e);
                showCustomToastWithVibration("Error loading image");
                isProcessing = false;
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            if (camera == null) {
                startCamera();
            }
        }
        Log.d(TAG, "Activity resumed");
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (cameraProvider != null) {
            try {
                cameraProvider.unbindAll();
                camera = null;
                Log.d(TAG, "Camera unbound");
            } catch (Exception e) {
                Log.e(TAG, "Error unbinding camera", e);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        barcodeScanner.close();
        Log.d(TAG, "Activity destroyed");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                showCustomToastWithVibration("Camera permission denied");
                Log.e(TAG, "Camera permission denied");
                finish();
            }
        }
    }
}
