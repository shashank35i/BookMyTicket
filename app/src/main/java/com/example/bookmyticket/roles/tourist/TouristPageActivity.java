package com.example.bookmyticket.roles.tourist;

import com.example.bookmyticket.R;
import com.example.bookmyticket.auth.LoginActivity;
import com.example.bookmyticket.features.scanner.QRScannerActivity;
import com.example.bookmyticket.features.settings.SettingsActivity;
import com.example.bookmyticket.model.Ticket;
import com.example.bookmyticket.model.Vehicle;
import com.example.bookmyticket.ui.SparkleView;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import androidx.viewpager2.widget.ViewPager2;

import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.razorpay.Checkout;
import com.razorpay.PaymentResultListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("unchecked")
public class TouristPageActivity extends AppCompatActivity implements PaymentResultListener {

    private static final String TAG = "TouristPageActivity";
    private static final String RAZORPAY_KEY_ID = "rzp_test_kEE4xn2vlWKwiw";
    private static final long MIN_CLICK_INTERVAL = 500;
    private FirebaseAuth mAuth;
    private DatabaseReference databaseReference;
    private DatabaseReference notificationsReference;
    private SharedPreferences sharedPreferences;
    private String userUid;
    private String phoneNumber;
    private boolean isLoggedIn;
    private ExecutorService executorService;
    private FirebaseUser cachedUser;
    private long lastClickTime = 0;
    private LinearLayout vehicleContainer; // For vehicle views in ScrollView
    private List<Vehicle> cachedVehicles; // In-memory cache for vehicles
    private ImageView profileIcon, notificationIcon, celebrationCloseBtn;
    private CardView scanQrCode, celebrationBanner;
    private TextView home, logout, seeMoreTickets, welcomeText;
    private TextView celebrationMessage;
    private MaterialButton celebrationButton;
    private Button addVehicleBtn;
    private View celebrationOverlay, notificationDot;
    private SparkleView celebrationSparkles;
    private View headerBar, footerNavigation;
    private RecyclerView vehiclesRecyclerView;
    private VehicleAdapter vehicleAdapter;
    private AppBarLayout appBarLayout;
    private TextView ticket1Place, ticket1DateTime, ticket1Price;
    private TextView ticket2Place, ticket2DateTime, ticket2Price;
    private LinearLayout ticket1Layout, ticket2Layout;
    private ViewPager2 viewPager;
    private LinearLayout dotsContainer;
    private final ImageView[] dots = new ImageView[4]; // For 4 images
    private final Handler sliderHandler = new Handler(Looper.getMainLooper());
    private final Runnable sliderRunnable = new Runnable() {
        @Override
        public void run() {
            if (viewPager != null) {
                viewPager.setCurrentItem((viewPager.getCurrentItem() + 1) % 4, true);
                sliderHandler.postDelayed(this, 3000);
            }
        }
    };
    private boolean isCelebrationShowing = false;
    private DatabaseReference paymentsRef;
    private ChildEventListener paymentListener;
    private ChildEventListener notificationListener;
    private final Map<String, DatabaseReference> firebaseRefs = new HashMap<>();
    private static final int MAX_RETRY_ATTEMPTS = 2;
    private String currentPaymentId;
    private String currentVehicleNumber;
    private String currentAmount;
    private String currentAdminUid;

    private ShimmerFrameLayout shimmerLayout;
    private LinearLayout placeholderLayout;
    private NestedScrollView mainContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            // Lock screen to portrait orientation
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            setContentView(R.layout.activity_touristpage);
            if (getSupportActionBar() != null) {
                getSupportActionBar().hide();
            }

            // Initialize core components
            executorService = Executors.newCachedThreadPool();
            initializeSharedPreferences();
            initializeFirebase();
            Checkout.preload(getApplicationContext());

            setSystemColors();
            initializeViews();
            setupScrollListener();

            // Start shimmer effect and hide main content
            if (shimmerLayout != null) {
                shimmerLayout.startShimmer();
            } else {
                Log.e(TAG, "shimmerLayout is null");
            }
            if (mainContent != null) {
                mainContent.setVisibility(View.GONE);
            } else {
                Log.e(TAG, "mainContent is null");
            }

            // Display cached data immediately
            List<Vehicle> cachedVehicles = loadCachedVehicles();
            updateVehicleUI(cachedVehicles);
            List<Ticket> cachedTickets = loadCachedTickets();
            updateTicketUI(cachedTickets);
            Log.d(TAG, "Displayed cached data: " + cachedVehicles.size() + " vehicles, " + cachedTickets.size() + " tickets");

            // Start data loading immediately
            loadDataAsync();

            // Setup click listeners and handle intent after initialization
            setupClickListeners();
            handleIntent(getIntent());

            // Apply enter animation
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate: " + e.getMessage(), e);
            Toast.makeText(this, "Initialization error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setSystemColors() {
        Window window = getWindow();

        // Set status bar background to white
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.setStatusBarColor(Color.WHITE);

        // Set navigation bar color
        window.setNavigationBarColor(Color.parseColor("#F5F5F5")); // Light gray navigation bar

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // For Android 11+ (API 30+)
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                // Set light status bar icons (black)
                controller.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                );
                // Optional: Set light navigation bar icons if you want them dark too
                // controller.setSystemBarsAppearance(
                //     WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                //     WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                // );
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // For Android 6.0 to Android 10 (API 23-29)
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            );
        }
        // Below Android 6.0 (API 23), the icons are white by default and can't be changed

        Log.d(TAG, "System colors set: Status bar white with dark icons");
    }

    private void setupScrollListener() {
        if (appBarLayout != null && headerBar != null) {
            appBarLayout.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
                float range = appBarLayout.getTotalScrollRange();
                float offset = Math.abs(verticalOffset / range);

                Window window = getWindow();
                if (offset >= 1.0f) {
                    // Fully collapsed: Set Toolbar to #4C28E0 and status bar to match
                    headerBar.setBackgroundColor(Color.parseColor("#4C28E0"));
                    window.setStatusBarColor(Color.parseColor("#4C28E0"));

                    // Set status bar icons to light (white)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        WindowInsetsController controller = window.getInsetsController();
                        if (controller != null) {
                            controller.setSystemBarsAppearance(
                                    0, // Clear light status bar flag
                                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            );
                        }
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        window.getDecorView().setSystemUiVisibility(
                                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        );
                    }

                    // Update welcome text to white when collapsed
                    if (welcomeText != null) {
                        welcomeText.setVisibility(View.VISIBLE);
                        welcomeText.setTextColor(Color.WHITE);
                    }
                } else {
                    // Expanded or partially collapsed: Set status bar to white with dark icons
                    headerBar.setBackgroundColor(Color.TRANSPARENT);
                    window.setStatusBarColor(Color.WHITE);

                    // Set status bar icons to dark
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        WindowInsetsController controller = window.getInsetsController();
                        if (controller != null) {
                            controller.setSystemBarsAppearance(
                                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            );
                        }
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        window.getDecorView().setSystemUiVisibility(
                                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                                        View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                        );
                    }

                    // Update welcome text to black when expanded
                    if (welcomeText != null) {
                        welcomeText.setVisibility(View.VISIBLE);
                        welcomeText.setTextColor(Color.BLACK);
                    }
                }

                // Update other views as needed
                if (profileIcon != null) profileIcon.setColorFilter(Color.WHITE);
                if (notificationIcon != null) notificationIcon.setColorFilter(Color.WHITE);
                if (notificationDot != null) notificationDot.setBackgroundResource(R.drawable.red_dot);
            });

            // Set initial state (expanded)
            if (welcomeText != null) {
                welcomeText.setTextColor(Color.BLACK);
                welcomeText.setVisibility(View.VISIBLE);
            }
        }
    }




    private void initializeSharedPreferences() {
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
    }

    private void initializeFirebase() {
        mAuth = FirebaseAuth.getInstance();
        cachedUser = mAuth.getCurrentUser();
        databaseReference = FirebaseDatabase.getInstance().getReference();
        firebaseRefs.put("root", databaseReference);
        firebaseRefs.put("users", databaseReference.child("users"));
        firebaseRefs.put("tourist", databaseReference.child("tourist"));
        firebaseRefs.put("tickets", databaseReference.child("tickets"));
        firebaseRefs.put("vehicle_details", databaseReference.child("vehicle_details"));
        firebaseRefs.put("payments", databaseReference.child("payments"));
        firebaseRefs.put("admin_notifications", databaseReference.child("admin_notifications"));
        firebaseRefs.put("tourist_notifications", databaseReference.child("tourist_notifications"));
    }


    private void initializeViews() {
        profileIcon = findViewById(R.id.profile_icon);
        welcomeText = findViewById(R.id.welcome_text);
        notificationIcon = findViewById(R.id.notification_icon);
        notificationDot = findViewById(R.id.notification_dot);
        scanQrCode = findViewById(R.id.scan_qr_card);
        seeMoreTickets = findViewById(R.id.see_more_tickets);
        ticket1Place = findViewById(R.id.ticket1_place);
        ticket1DateTime = findViewById(R.id.ticket1_date_time);
        ticket1Price = findViewById(R.id.ticket1_price);
        ticket1Layout = findViewById(R.id.recent_ticket_item);
        ticket2Place = findViewById(R.id.ticket2_place);
        ticket2DateTime = findViewById(R.id.ticket2_date_time);
        ticket2Price = findViewById(R.id.ticket2_price);
        ticket2Layout = findViewById(R.id.recent_ticket_item2);
        addVehicleBtn = findViewById(R.id.add_vehicle_btn);
        home = findViewById(R.id.home);

        celebrationBanner = findViewById(R.id.celebration_banner);
        celebrationMessage = findViewById(R.id.celebration_message);
        celebrationButton = findViewById(R.id.celebration_button);
        celebrationOverlay = findViewById(R.id.celebration_overlay);
        celebrationSparkles = findViewById(R.id.celebration_sparkles);
        celebrationCloseBtn = findViewById(R.id.celebration_close_btn);
        vehicleContainer = findViewById(R.id.vehicle_container);
        shimmerLayout = findViewById(R.id.shimmer_layout);
        placeholderLayout = findViewById(R.id.placeholder_layout);
        mainContent = findViewById(R.id.main_content);
        headerBar = findViewById(R.id.header_bar);
        footerNavigation = findViewById(R.id.footer_navigation);
        appBarLayout = findViewById(R.id.app_bar_layout);
        viewPager = findViewById(R.id.image_slider);
        dotsContainer = findViewById(R.id.dots_container);

        if (celebrationBanner != null) celebrationBanner.setVisibility(View.GONE);
        if (celebrationOverlay != null) celebrationOverlay.setVisibility(View.GONE);
        if (celebrationSparkles != null) celebrationSparkles.setVisibility(View.GONE);
        if (notificationDot != null) notificationDot.setVisibility(View.GONE);

        // Setup image slider and dots
        setupImageSlider();
    }
    private void setupImageSlider() {
        if (viewPager == null || dotsContainer == null) {
            Log.e(TAG, "ViewPager2 or dotsContainer is null, cannot setup image slider");
            return;
        }
        List<Integer> images = Arrays.asList(
                R.drawable.slides1,
                R.drawable.slides2,
                R.drawable.slides3,
                R.drawable.slides4
        );
        viewPager.setAdapter(new SliderAdapter(images));
        // Setup dots
        setupDots();
        // Start auto-slide
        startAutoSlide();
    }

    private void setupDots() {
        if (dotsContainer == null) {
            Log.e(TAG, "dotsContainer is null, cannot setup dots");
            return;
        }
        dotsContainer.removeAllViews();
        for (int i = 0; i < 4; i++) {
            dots[i] = new ImageView(this);
            dots[i].setImageResource(R.drawable.dot_selector);
            dots[i].setSelected(i == 0); // Select first dot
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(8, 0, 8, 0);
            dotsContainer.addView(dots[i], params);
        }
    }
    private static class SliderAdapter extends RecyclerView.Adapter<SliderAdapter.SliderViewHolder> {
        private final List<Integer> images;

        SliderAdapter(List<Integer> images) {
            this.images = images;
        }

        static class SliderViewHolder extends RecyclerView.ViewHolder {
            ImageView imageView;

            SliderViewHolder(View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.slider_image);
            }
        }

        @NonNull
        @Override
        public SliderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_slider_image, parent, false);
            return new SliderViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull SliderViewHolder holder, int position) {
            holder.imageView.setImageResource(images.get(position));
        }

        @Override
        public int getItemCount() {
            return images.size();
        }
    }
    private void startAutoSlide() {
        if (viewPager == null) return;
        sliderHandler.removeCallbacks(sliderRunnable);
        sliderHandler.postDelayed(sliderRunnable, 3000);
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                // Update dots
                for (int i = 0; i < 4; i++) {
                    dots[i].setSelected(i == position);
                }
                // Reset timer on manual interaction
                sliderHandler.removeCallbacks(sliderRunnable);
                sliderHandler.postDelayed(sliderRunnable, 3000);
            }
        });
    }
    private void setupClickListeners() {
        LinearLayout historyLayout = findViewById(R.id.history_layout);

        if (historyLayout != null) {
            historyLayout.setOnClickListener(view -> {
                if (debounceClick()) return;
                if (isLoggedIn) {
                    Intent intent = new Intent(this, TicketHistoryTouristActivity.class);
                    intent.putExtra("USER_UID", userUid);
                    intent.putExtra("phoneNumber", phoneNumber);
                    startActivity(intent);
                } else {
                    Toast.makeText(this, "Please login to view history", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            Log.e(TAG, "history_layout is null");
        }

        LinearLayout navSettings = findViewById(R.id.nav_settings);
        if (navSettings != null) {
            navSettings.setOnClickListener(view -> {
                if (debounceClick()) return;
                if (isLoggedIn) {
                    Log.d(TAG, "Settings icon clicked");
                    Intent intent = new Intent(this, SettingsActivity.class);
                    intent.putExtra("USER_UID", userUid);
                    intent.putExtra("phoneNumber", phoneNumber);
                    intent.putExtra("isLoggedIn", isLoggedIn);
                    startActivity(intent);
                } else {
                    Toast.makeText(this, "Please login to access settings", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            Log.e(TAG, "nav_settings is null");
        }

        if (scanQrCode != null) {
            scanQrCode.setOnClickListener(view -> {
                if (debounceClick()) return;
                openQrScannerWithAnimation();
            });
        }
        if (profileIcon != null) {
            profileIcon.setOnClickListener(view -> {
                if (debounceClick()) return;
                if (isLoggedIn) {
                    openUserProfile();
                } else {
                    Toast.makeText(this, "Please login to access profile", Toast.LENGTH_SHORT).show();
                }
            });
        }
        if (notificationIcon != null) {
            notificationIcon.setOnClickListener(view -> {
                if (debounceClick()) return;
                if (isLoggedIn) {
                    openNotifications();
                } else {
                    Toast.makeText(this, "Please login to view notifications", Toast.LENGTH_SHORT).show();
                }
            });
        }
        if (seeMoreTickets != null) {
            seeMoreTickets.setOnClickListener(view -> {
                if (debounceClick()) return;
                if (userUid != null) {
                    openDownloadedQRActivity();
                } else {
                    Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show();
                }
            });
        }
        if (home != null) {
            home.setOnClickListener(view -> {
                if (debounceClick()) return;
                Toast.makeText(this, "You're already on home", Toast.LENGTH_SHORT).show();
            });
        } else {
            Log.e(TAG, "home TextView is null");
        }

        if (addVehicleBtn != null) {
            addVehicleBtn.setOnClickListener(view -> {
                if (debounceClick()) return;
                if (isLoggedIn) {
                    openAddVehicle();
                } else {
                    Toast.makeText(this, "Please login to add vehicle", Toast.LENGTH_SHORT).show();
                }
            });
        }
        if (celebrationButton != null) {
            celebrationButton.setOnClickListener(v -> {
                if (debounceClick()) return;
                dismissCelebration();
            });
        }
        if (celebrationCloseBtn != null) {
            celebrationCloseBtn.setOnClickListener(v -> {
                if (debounceClick()) return;
                dismissCelebration();
            });
        }
        if (celebrationOverlay != null) {
            celebrationOverlay.setOnClickListener(v -> {});
        }
    }

    private void openQrScannerWithAnimation() {
        if (!isLoggedIn || userUid == null || phoneNumber == null) {
            Log.e(TAG, "Cannot open QR scanner: isLoggedIn=" + isLoggedIn + ", userUid=" + userUid + ", phoneNumber=" + phoneNumber);
            Toast.makeText(this, "Please log in to scan QR", Toast.LENGTH_SHORT).show();
            redirectToLogin();
            return;
        }

        Intent intent = new Intent(this, QRScannerActivity.class);
        intent.putExtra("USER_UID", userUid);
        intent.putExtra("phoneNumber", phoneNumber);
        intent.putExtra("isLoggedIn", isLoggedIn);
        intent.putExtra("FROM_SCAN_LAYOUT", true);

        ActivityOptionsCompat options = ActivityOptionsCompat.makeCustomAnimation(
                this, R.anim.slide_in_bottom, R.anim.fade_out);
        ActivityCompat.startActivity(this, intent, options.toBundle());
        Log.d(TAG, "Navigated to QRScannerActivity with fast bottom-to-top animation, passed userUid=" + userUid + ", phoneNumber=" + phoneNumber + ", isLoggedIn=" + isLoggedIn);
    }

    private boolean debounceClick() {
        long currentTime = SystemClock.elapsedRealtime();
        if (currentTime - lastClickTime < MIN_CLICK_INTERVAL) {
            return true;
        }
        lastClickTime = currentTime;
        return false;
    }

    private void openNotifications() {
        Intent intent = new Intent(this, TouristNotificationsActivity.class);
        intent.putExtra("USER_UID", userUid);
        startActivity(intent);
    }

    private void handleIntent(Intent intent) {
        boolean fromPaymentSuccess = intent.getBooleanExtra("FROM_PAYMENT_SUCCESS", false);
        boolean paymentFailed = intent.getBooleanExtra("PAYMENT_FAILED", false);
        boolean paymentPending = intent.getBooleanExtra("PAYMENT_PENDING", false);

        if (fromPaymentSuccess) {
            userUid = intent.getStringExtra("USER_UID");
            phoneNumber = intent.getStringExtra("phoneNumber");
            isLoggedIn = intent.getBooleanExtra("isLoggedIn", false);
            if (userUid != null && phoneNumber != null && isLoggedIn && phoneNumber.matches("^[0-9]{10}$")) {
                sharedPreferences.edit()
                        .putString("userUid", userUid)
                        .putString("phoneNumber", phoneNumber)
                        .putBoolean("isLoggedIn", isLoggedIn)
                        .commit();
                new Handler(Looper.getMainLooper()).postDelayed(this::checkAndShowFirstPaymentCelebration, 2500);
            } else {
                Log.e(TAG, "Invalid intent data: userUid=" + userUid + ", phoneNumber=" + phoneNumber + ", isLoggedIn=" + isLoggedIn);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Invalid user data, please log in again", Toast.LENGTH_LONG).show();
                    redirectToLogin();
                });
                return;
            }
        } else if (paymentFailed) {
            runOnUiThread(() -> Toast.makeText(this, "Payment failed, please try again", Toast.LENGTH_LONG).show());
        } else if (paymentPending) {
            runOnUiThread(() -> Toast.makeText(this, "A payment is pending, please try again or cancel", Toast.LENGTH_LONG).show());
        }

        String vehicleNumberFromQR = intent.getStringExtra("VEHICLE_NUMBER");
        String vehicleTypeFromQR = intent.getStringExtra("VEHICLE_TYPE");
        if (vehicleNumberFromQR != null && vehicleTypeFromQR != null && userUid != null) {
            DatabaseReference vehiclesRef = firebaseRefs.get("vehicle_details").child(vehicleNumberFromQR);
            vehiclesRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (!snapshot.exists()) {
                        Map<String, Object> vehicleData = new HashMap<>();
                        vehicleData.put("phoneNumber", phoneNumber);
                        vehicleData.put("userUid", userUid);
                        vehicleData.put("vehicleType", vehicleTypeFromQR);
                        vehicleData.put("timestamp", System.currentTimeMillis());
                        vehiclesRef.setValue(vehicleData)
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "Vehicle synced to Firebase: " + vehicleNumberFromQR);
                                    sharedPreferences.edit()
                                            .putString("vehicleNumber", vehicleNumberFromQR)
                                            .putString("vehicleType", vehicleTypeFromQR)
                                            .commit();
                                    runOnUiThread(() -> {
                                        if (addVehicleBtn != null) {
                                            addVehicleBtn.setText("Add Another Vehicle");
                                        }
                                        loadDataAsync();
                                    });
                                })
                                .addOnFailureListener(e -> Log.e(TAG, "Failed to sync vehicle: " + e.getMessage()));
                    } else {
                        Log.d(TAG, "Vehicle already exists in Firebase: " + vehicleNumberFromQR);
                        runOnUiThread(() -> {
                            if (addVehicleBtn != null) {
                                addVehicleBtn.setText("Add Another Vehicle");
                            }
                        });
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e(TAG, "Failed to check vehicle: " + error.getMessage());
                }
            });
        }
    }


    private void setupSession() {
        if (isLoggedIn) {
            if (profileIcon != null) profileIcon.setEnabled(true);
            if (logout != null) {
                logout.setText("Logout");
                logout.setOnClickListener(v -> {
                    if (debounceClick()) return;
                    logoutUser();
                });
            }
            if (welcomeText != null) {
                String cachedWelcome = sharedPreferences.getString("welcomeText", null);
                welcomeText.setText(cachedWelcome != null ? cachedWelcome : "Loading...");
            }
        } else {
            if (profileIcon != null) profileIcon.setEnabled(false);
            if (logout != null) {
                logout.setText("Login");
                logout.setOnClickListener(v -> {
                    if (debounceClick()) return;
                    redirectToLogin();
                });
            }
            if (welcomeText != null) {
                String cachedWelcome = sharedPreferences.getString("welcomeText", null);
                welcomeText.setText(cachedWelcome != null ? cachedWelcome : "Welcome, Visitor!");
            }
        }
    }



    private void loadUserData() {
        if (userUid == null) return;
        DatabaseReference touristRef = firebaseRefs.get("tourist");
        touristRef.orderByChild("uid").equalTo(userUid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    for (DataSnapshot touristSnapshot : snapshot.getChildren()) {
                        String phone = touristSnapshot.child("phone").getValue(String.class);
                        String name = touristSnapshot.child("name").getValue(String.class);
                        if (phone != null) {
                            sharedPreferences.edit().putString("phoneNumber", phone).apply();
                            phoneNumber = phone;
                        }
                        if (name != null && welcomeText != null) {
                            runOnUiThread(() -> welcomeText.setText("Welcome, " + name + "!"));
                        }
                        break;
                    }
                }
                runOnUiThread(() -> setupSession());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Database error in loadUserData: " + error.getMessage());
                runOnUiThread(() -> Toast.makeText(TouristPageActivity.this, "Error loading user data", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void checkAndShowFirstPaymentCelebration() {
        if (userUid == null || !getIntent().getBooleanExtra("FROM_PAYMENT_SUCCESS", false)) return;
        boolean hasShownFirstPayment = sharedPreferences.getBoolean("hasShownFirstPayment", false);
        if (hasShownFirstPayment) return;
        DatabaseReference ticketsRef = firebaseRefs.get("tickets").child(userUid);
        ticketsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.getChildrenCount() == 1) {
                    showFirstPaymentCelebration();
                    addFirstPaymentNotification();
                    sharedPreferences.edit().putBoolean("hasShownFirstPayment", true).apply();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error checking tickets: " + error.getMessage());
            }
        });
    }

    private void addFirstPaymentNotification() {
        if (userUid == null) return;
        DatabaseReference touristNotificationsRef = firebaseRefs.get("tourist_notifications").child(userUid);
        String notificationId = touristNotificationsRef.push().getKey();
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("type", "first_payment");
        notificationData.put("message", "You successfully made your first ticket!");
        notificationData.put("timestamp", System.currentTimeMillis());
        notificationData.put("seen", false);
        touristNotificationsRef.child(notificationId).setValue(notificationData)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "First payment notification added: " + notificationId))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to add first payment notification: " + e.getMessage()));
    }

    private void showFirstPaymentCelebration() {
        if (celebrationBanner == null || celebrationMessage == null || celebrationOverlay == null || celebrationSparkles == null) {
            Log.e(TAG, "Celebration views are null");
            return;
        }
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.parseColor("#80000000")); // Semi-transparent black for overlay

        if (headerBar != null) headerBar.setAlpha(0.4f);
        if (footerNavigation != null) footerNavigation.setAlpha(0.4f);

        celebrationOverlay.setVisibility(View.VISIBLE);
        celebrationBanner.setVisibility(View.VISIBLE);
        celebrationSparkles.setVisibility(View.VISIBLE);
        celebrationMessage.setText("Congratulations! You successfully made your first ticket!");
        isCelebrationShowing = true;

        celebrationBanner.post(() -> {
            float centerX = celebrationBanner.getX() + celebrationBanner.getWidth() / 2f;
            float centerY = celebrationBanner.getY() + celebrationBanner.getHeight() / 2f;
            celebrationSparkles.setDialogCenter(centerX, centerY);
            celebrationSparkles.startAnimation();
            Log.d(TAG, "Celebration banner center: x=" + centerX + ", y=" + centerY);
        });

        ObjectAnimator overlayFade = ObjectAnimator.ofFloat(celebrationOverlay, "alpha", 0f, 1f);
        overlayFade.setDuration(300);

        Animation slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up);
        celebrationBanner.startAnimation(slideUp);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.play(overlayFade);
        animatorSet.start();
    }

    private void dismissCelebration() {
        if (celebrationBanner == null || celebrationOverlay == null || celebrationSparkles == null) return;
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT); // Restore transparent status bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
            }
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            );
        }

        if (headerBar != null) headerBar.setAlpha(1f);
        if (footerNavigation != null) footerNavigation.setAlpha(1f);

        Animation slideDown = AnimationUtils.loadAnimation(this, R.anim.slide_down);
        slideDown.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
                celebrationBanner.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        });
        celebrationBanner.startAnimation(slideDown);

        AnimatorSet dismissSet = new AnimatorSet();
        ObjectAnimator overlayFade = ObjectAnimator.ofFloat(celebrationOverlay, "alpha", 1f, 0f);
        ObjectAnimator sparkleFade = ObjectAnimator.ofFloat(celebrationSparkles, "alpha", 1f, 0f);
        dismissSet.playTogether(overlayFade, sparkleFade);
        dismissSet.setDuration(300);
        dismissSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                celebrationOverlay.setVisibility(View.GONE);
                celebrationSparkles.setVisibility(View.GONE);
                celebrationSparkles.stopAnimation();
                isCelebrationShowing = false;
                if (headerBar != null) {
                    headerBar.requestLayout();
                    Log.d(TAG, "Header bar layout refreshed after celebration");
                }
                logHeaderPositions();
            }
        });
        dismissSet.start();
    }



    private void listenForPaymentRequests() {
        if (userUid == null || !isLoggedIn) {
            Log.d(TAG, "Cannot listen for payments: userUid is null or not logged in");
            return;
        }
        paymentsRef = firebaseRefs.get("payments").child(userUid);
        paymentListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, String previousChildName) {
                String status = snapshot.child("status").getValue(String.class);
                if ("pending".equals(status)) {
                    String paymentId = snapshot.getKey();
                    String vehicleNumber = snapshot.child("vehicleNumber").getValue(String.class);
                    String amount = snapshot.child("amount").getValue(String.class);
                    String adminUid = snapshot.child("adminUid").getValue(String.class);
                    String vehicleType = snapshot.child("vehicleType").getValue(String.class);
                    if (vehicleNumber != null && amount != null && adminUid != null) {
                        Log.d(TAG, "Payment detected: " + paymentId + ", vehicle: " + vehicleNumber);
                        showPaymentDialog(paymentId, vehicleNumber, amount, adminUid, vehicleType);
                    } else {
                        Log.e(TAG, "Invalid payment data: " + snapshot.toString());
                    }
                }
            }
            @Override public void onChildChanged(@NonNull DataSnapshot snapshot, String previousChildName) {}
            @Override public void onChildRemoved(@NonNull DataSnapshot snapshot) {}
            @Override public void onChildMoved(@NonNull DataSnapshot snapshot, String previousChildName) {}
            @Override public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error listening for payments: " + error.getMessage());
            }
        };
        paymentsRef.addChildEventListener(paymentListener);
    }

    private void showPaymentDialog(String paymentId, String vehicleNumber, String amount, String adminUid, String vehicleType) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_payment_tourist, null);
        builder.setView(dialogView);
        TextView txtTitle = dialogView.findViewById(R.id.txt_title);
        TextView txtVehicleNumber = dialogView.findViewById(R.id.txt_vehicle_number);
        TextView txtPaymentAmount = dialogView.findViewById(R.id.txt_payment_amount);
        MaterialButton btnAccept = dialogView.findViewById(R.id.btn_accept);
        MaterialButton btnDeny = dialogView.findViewById(R.id.btn_deny);
        txtTitle.setText("Payment Request");
        txtVehicleNumber.setText(vehicleNumber);
        txtPaymentAmount.setText("₹" + amount);
        AlertDialog dialog = builder.create();
        btnAccept.setOnClickListener(v -> {
            if (debounceClick()) return;
            initiatePayment(paymentId, vehicleNumber, amount, adminUid, vehicleType);
            dialog.dismiss();
        });
        btnDeny.setOnClickListener(v -> {
            if (debounceClick()) return;
            updatePaymentStatus(paymentId, vehicleNumber, amount, "denied", adminUid, vehicleType);
            dialog.dismiss();
        });
        dialog.setOnShowListener(d -> {
            dialogView.setTranslationY(dialogView.getHeight());
            dialogView.animate()
                    .translationY(0)
                    .setDuration(300)
                    .start();
        });
        dialog.show();
    }

    private void initiatePayment(String paymentId, String vehicleNumber, String amount, String adminUid, String vehicleType) {
        if (paymentId == null || amount == null) {
            Toast.makeText(this, "Invalid payment data", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            currentPaymentId = paymentId;
            currentVehicleNumber = vehicleNumber;
            currentAmount = amount;
            currentAdminUid = adminUid;

            Checkout checkout = new Checkout();
            checkout.setKeyID(RAZORPAY_KEY_ID);
            checkout.setImage(R.drawable.ic_notifications);

            JSONObject options = new JSONObject();
            options.put("name", "Parking Payment");
            options.put("description", "Payment for " + (vehicleNumber != null ? vehicleNumber : "Unknown"));
            options.put("currency", "INR");
            double amountValue = Double.parseDouble(amount) * 100;
            options.put("amount", (int) amountValue);
            options.put("prefill", getPrefillDetails());
            options.put("theme", new JSONObject().put("color", "#6200EE"));

            checkout.open(this, options);
        } catch (Exception e) {
            Log.e(TAG, "Error in starting Razorpay Checkout: " + e.getMessage());
            Toast.makeText(this, "Error initiating payment", Toast.LENGTH_SHORT).show();
        }
    }

    private JSONObject getPrefillDetails() {
        try {
            JSONObject prefill = new JSONObject();
            prefill.put("email", cachedUser != null && cachedUser.getEmail() != null ? cachedUser.getEmail() : "user@example.com");
            prefill.put("contact", phoneNumber != null ? phoneNumber : "1234567890");
            return prefill;
        } catch (Exception e) {
            Log.e(TAG, "Error creating prefill details: " + e.getMessage());
            return new JSONObject();
        }
    }

    @Override
    public void onPaymentSuccess(String paymentId) {
        Log.d(TAG, "Payment successful: " + paymentId);
        Toast.makeText(this, "Payment successful!", Toast.LENGTH_SHORT).show();

        if (currentPaymentId == null) {
            Log.e(TAG, "Current payment ID is null during success");
            return;
        }

        DatabaseReference paymentRef = firebaseRefs.get("payments").child(userUid).child(currentPaymentId);
        paymentRef.child("status").setValue("completed")
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Payment status updated to completed"))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update payment status: " + e.getMessage());
                    Toast.makeText(this, "Error updating payment status", Toast.LENGTH_SHORT).show();
                });

        DatabaseReference touristNotificationsRef = firebaseRefs.get("tourist_notifications").child(userUid);
        touristNotificationsRef.orderByChild("paymentId").equalTo(currentPaymentId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot child : snapshot.getChildren()) {
                            Map<String, Object> updates = new HashMap<>();
                            updates.put("type", "payment_success");
                            updates.put("seen", true);
                            touristNotificationsRef.child(child.getKey())
                                    .updateChildren(updates)
                                    .addOnSuccessListener(aVoid -> {
                                        Log.d(TAG, "Notification updated to payment_success");
                                        updateNotificationDot();
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "Failed to update notification: " + e.getMessage());
                                        Toast.makeText(TouristPageActivity.this, "Error updating notification", Toast.LENGTH_SHORT).show();
                                    });
                            break;
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Error querying notification: " + error.getMessage());
                    }
                });

        if (currentAdminUid != null) {
            DatabaseReference adminNotificationsRef = firebaseRefs.get("admin_notifications").child(currentAdminUid);
            String notificationId = adminNotificationsRef.push().getKey();
            Map<String, Object> adminNotificationData = new HashMap<>();
            adminNotificationData.put("type", "payment_completed");
            adminNotificationData.put("paymentId", currentPaymentId);
            adminNotificationData.put("vehicleNumber", currentVehicleNumber != null ? currentVehicleNumber : "Unknown");
            adminNotificationData.put("amount", currentAmount != null ? currentAmount : "0");
            adminNotificationData.put("userUid", userUid);
            adminNotificationData.put("timestamp", System.currentTimeMillis());
            adminNotificationsRef.child(notificationId).setValue(adminNotificationData)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Admin notified for payment"))
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to notify admin: " + e.getMessage()));
        }

        String currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        DatabaseReference realTimeRef = FirebaseDatabase.getInstance().getReference("realTimeData").child(currentDate);
        realTimeRef.runTransaction(new com.google.firebase.database.Transaction.Handler() {
            @Override
            public com.google.firebase.database.Transaction.Result doTransaction(@NonNull com.google.firebase.database.MutableData mutableData) {
                Long vehicles = mutableData.child("vehicles").getValue(Long.class);
                Long parkingRevenue = mutableData.child("parkingRevenue").getValue(Long.class);
                Long activeBookings = mutableData.child("activeBookings").getValue(Long.class);

                if (vehicles == null) vehicles = 0L;
                if (parkingRevenue == null) parkingRevenue = 0L;
                if (activeBookings == null) activeBookings = 0L;

                mutableData.child("vehicles").setValue(vehicles + 1);
                mutableData.child("parkingRevenue").setValue(parkingRevenue + (currentAmount != null ? Long.parseLong(currentAmount) : 0));
                mutableData.child("activeBookings").setValue(activeBookings + 1);

                return com.google.firebase.database.Transaction.success(mutableData);
            }

            @Override
            public void onComplete(DatabaseError databaseError, boolean committed, DataSnapshot dataSnapshot) {
                if (databaseError != null) {
                    Log.e(TAG, "Failed to update real-time data: " + databaseError.getMessage());
                    Toast.makeText(TouristPageActivity.this, "Error updating metrics", Toast.LENGTH_SHORT).show();
                }
            }
        });

        new Handler(Looper.getMainLooper()).postDelayed(this::checkAndShowFirstPaymentCelebration, 500);
    }

    @Override
    public void onPaymentError(int code, String response) {
        Log.e(TAG, "Payment failed: Code=" + code + ", Response=" + response);
        Toast.makeText(this, "Payment failed: " + response, Toast.LENGTH_LONG).show();
    }

    private void updatePaymentStatus(String paymentId, String vehicleNumber, String amount, String status, String adminUid, String vehicleType) {
        DatabaseReference paymentRef = firebaseRefs.get("payments").child(userUid).child(paymentId);
        paymentRef.child("status").setValue(status)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Payment " + paymentId + " marked as " + status);
                    Toast.makeText(this, "Payment " + status, Toast.LENGTH_SHORT).show();
                    DatabaseReference adminNotificationsRef = firebaseRefs.get("admin_notifications").child(adminUid);
                    String notificationId = adminNotificationsRef.push().getKey();
                    Map<String, Object> notificationData = new HashMap<>();
                    notificationData.put("type", "payment_" + status);
                    notificationData.put("paymentId", paymentId);
                    notificationData.put("vehicleNumber", vehicleNumber);
                    notificationData.put("amount", amount);
                    notificationData.put("vehicleType", vehicleType);
                    notificationData.put("userUid", userUid);
                    notificationData.put("timestamp", System.currentTimeMillis());
                    adminNotificationsRef.child(notificationId).setValue(notificationData)
                            .addOnSuccessListener(a -> Log.d(TAG, "Admin notified for payment " + paymentId))
                            .addOnFailureListener(e -> Log.e(TAG, "Failed to notify admin: " + e.getMessage()));

                    if ("accepted".equals(status)) {
                        String currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
                        DatabaseReference realTimeRef = FirebaseDatabase.getInstance().getReference("realTimeData").child(currentDate);
                        realTimeRef.runTransaction(new com.google.firebase.database.Transaction.Handler() {
                            @Override
                            public com.google.firebase.database.Transaction.Result doTransaction(@NonNull com.google.firebase.database.MutableData mutableData) {
                                Long vehicles = mutableData.child("vehicles").getValue(Long.class);
                                Long parkingRevenue = mutableData.child("parkingRevenue").getValue(Long.class);
                                Long activeBookings = mutableData.child("activeBookings").getValue(Long.class);

                                if (vehicles == null) vehicles = 0L;
                                if (parkingRevenue == null) parkingRevenue = 0L;
                                if (activeBookings == null) activeBookings = 0L;

                                mutableData.child("vehicles").setValue(vehicles + 1);
                                mutableData.child("parkingRevenue").setValue(parkingRevenue + Long.parseLong(amount));
                                mutableData.child("activeBookings").setValue(activeBookings + 1);

                                return com.google.firebase.database.Transaction.success(mutableData);
                            }

                            @Override
                            public void onComplete(DatabaseError databaseError, boolean committed, DataSnapshot dataSnapshot) {
                                if (databaseError != null) {
                                    Log.e(TAG, "Failed to update real-time data: " + databaseError.getMessage());
                                } else {
                                    Log.d(TAG, "Real-time data updated for payment " + paymentId);
                                }
                            }
                        });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update payment status: " + e.getMessage());
                    Toast.makeText(this, "Failed to update payment: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void listenForNotifications() {
        if (userUid == null || !isLoggedIn) return;
        notificationsReference = firebaseRefs.get("tourist_notifications").child(userUid);
        notificationListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, String previousChildName) {
                updateNotificationDot();
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, String previousChildName) {
                updateNotificationDot();
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                updateNotificationDot();
            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, String previousChildName) {}

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error listening for notifications: " + error.getMessage());
                runOnUiThread(() -> Toast.makeText(TouristPageActivity.this, "Error checking notifications", Toast.LENGTH_SHORT).show());
            }
        };
        notificationsReference.addChildEventListener(notificationListener);
        updateNotificationDot();
    }

    private void updateNotificationDot() {
        if (notificationsReference == null) return;
        notificationsReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean hasUnseen = false;
                for (DataSnapshot child : snapshot.getChildren()) {
                    Boolean seen = child.child("seen").getValue(Boolean.class);
                    if (seen == null || !seen) {
                        hasUnseen = true;
                        break;
                    }
                }
                final boolean finalHasUnseen = hasUnseen;
                runOnUiThread(() -> {
                    if (notificationDot != null) {
                        notificationDot.setVisibility(finalHasUnseen ? View.VISIBLE : View.GONE);
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error checking notifications: " + error.getMessage());
                runOnUiThread(() -> Toast.makeText(TouristPageActivity.this, "Error checking notifications", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void loadDataAsync() {
        if (isFinishing() || isDestroyed()) {
            Log.w(TAG, "Activity is finishing or destroyed, aborting loadDataAsync");
            return;
        }
        if (executorService.isShutdown()) {
            Log.w(TAG, "ExecutorService is shut down, reinitializing");
            executorService = Executors.newCachedThreadPool();
        }
        executorService.execute(() -> {
            if (!isNetworkAvailable()) {
                Log.d(TAG, "No internet connection, using cached data");
                runOnUiThread(() -> {
                    List<Vehicle> cachedVehicles = loadCachedVehicles();
                    List<Ticket> cachedTickets = loadCachedTickets();
                    updateVehicleUI(cachedVehicles);
                    updateTicketUI(cachedTickets);
                    stopShimmerAndShowContent();
                    Toast.makeText(TouristPageActivity.this, "Offline: Loaded cached data", Toast.LENGTH_SHORT).show();
                });
                return;
            }

            List<Runnable> tasks = new ArrayList<>();
            tasks.add(this::loadRecentTicketsTask);
            tasks.add(this::loadVehicleDetails);
            tasks.add(this::loadUserDataTask);

            for (Runnable task : tasks) {
                executeWithRetry(task, 0);
            }

            runOnUiThread(() -> {
                stopShimmerAndShowContent();
                listenForPaymentRequests();
                Log.d(TAG, "Data loading completed, shimmer stopped");
            });
        });
    }
    private void stopShimmerAndShowContent() {
        runOnUiThread(() -> {
            shimmerLayout.stopShimmer();
            shimmerLayout.setVisibility(View.GONE);
            mainContent.setVisibility(View.VISIBLE);

            Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
            mainContent.startAnimation(fadeIn);

            vehicleContainer.post(() -> vehicleContainer.requestLayout());
            ticket1Layout.post(() -> ticket1Layout.requestLayout());
            if (ticket2Layout != null) {
                ticket2Layout.post(() -> ticket2Layout.requestLayout());
            }
            Log.d(TAG, "Shimmer stopped, ticket and vehicle containers refreshed");
        });
    }
    private void executeWithRetry(Runnable task, int attempt) {
        if (isFinishing() || isDestroyed()) {
            Log.w(TAG, "Activity is finishing or destroyed, skipping task execution");
            return;
        }
        if (executorService.isShutdown()) {
            Log.w(TAG, "ExecutorService is shut down, reinitializing");
            executorService = Executors.newCachedThreadPool();
        }
        try {
            executorService.execute(() -> {
                try {
                    task.run();
                } catch (Exception e) {
                    if (attempt < MAX_RETRY_ATTEMPTS) {
                        Log.w(TAG, "Task attempt " + (attempt + 1) + " failed: " + e.getMessage());
                        new Handler(Looper.getMainLooper()).postDelayed(() -> executeWithRetry(task, attempt + 1), 1000 * (attempt + 1));
                    } else {
                        Log.e(TAG, "Task failed after " + MAX_RETRY_ATTEMPTS + " attempts: " + e.getMessage());
                        runOnUiThread(() -> {
                            Toast.makeText(TouristPageActivity.this, "Failed to load some data", Toast.LENGTH_LONG).show();
                            List<Vehicle> cachedVehicles = loadCachedVehicles();
                            List<Ticket> cachedTickets = loadCachedTickets();
                            updateVehicleUI(cachedVehicles);
                            updateTicketUI(cachedTickets);
                        });
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            Log.e(TAG, "Task rejected: " + e.getMessage());
            if (attempt < MAX_RETRY_ATTEMPTS) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> executeWithRetry(task, attempt + 1), 1000 * (attempt + 1));
            } else {
                runOnUiThread(() -> Toast.makeText(TouristPageActivity.this, "Error loading data", Toast.LENGTH_LONG).show());
            }
        }
    }

    @FunctionalInterface
    interface Supplier<T> {
        T get();
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    private void loadRecentTicketsTask() {
        if (userUid == null || userUid.isEmpty()) {
            Log.d(TAG, "userUid is null, showing no tickets message");
            runOnUiThread(() -> updateTicketUI(new ArrayList<>()));
            return;
        }

        Query ticketsRef = firebaseRefs.get("tickets").child(userUid).orderByChild("timestamp").limitToLast(2);
        try {
            DataSnapshot snapshot = Tasks.await(ticketsRef.get(), 5, TimeUnit.SECONDS);
            Log.d(TAG, "Fetched tickets snapshot, exists: " + snapshot.exists());
            List<Ticket> tickets = new ArrayList<>();
            if (snapshot.exists()) {
                for (DataSnapshot ticketSnapshot : snapshot.getChildren()) {
                    try {
                        String ticketId = ticketSnapshot.getKey();
                        String place = ticketSnapshot.child("place").getValue(String.class);
                        String date = ticketSnapshot.child("date").getValue(String.class);
                        String time = ticketSnapshot.child("time").getValue(String.class);
                        String totalAmount = ticketSnapshot.child("totalAmount").getValue(String.class);
                        Long timestamp = ticketSnapshot.child("timestamp").getValue(Long.class);
                        String validFrom = ticketSnapshot.child("validFrom").getValue(String.class);
                        String validTo = ticketSnapshot.child("validTo").getValue(String.class);

                        Ticket ticket = new Ticket();
                        ticket.ticketId = ticketId;
                        ticket.place = place != null ? place : "Unknown Place";
                        ticket.date = date != null ? date : (timestamp != null ? formatDate(timestamp) : "Unknown Date");
                        ticket.time = time != null ? time : (timestamp != null ? formatTime(timestamp) : "Unknown Time");
                        ticket.totalAmount = totalAmount != null ? totalAmount : "0";
                        ticket.timestamp = timestamp != null ? timestamp : 0L;
                        ticket.dateTime = ticket.date + ", " + ticket.time;
                        ticket.price = "₹" + ticket.totalAmount;
                        ticket.validFrom = validFrom != null ? validFrom : "N/A";
                        ticket.validTo = validTo != null ? validTo : "N/A";
                        tickets.add(ticket);
                        Log.d(TAG, "Parsed ticket: " + ticketId + ", Timestamp: " + ticket.timestamp);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing ticket: " + e.getMessage());
                    }
                }
                Collections.sort(tickets, (t1, t2) -> Long.compare(t2.timestamp, t1.timestamp));
                Log.d(TAG, "Sorted tickets by timestamp, count: " + tickets.size());

                try {
                    JSONArray jsonArray = new JSONArray();
                    for (Ticket ticket : tickets) {
                        JSONObject ticketJson = new JSONObject();
                        ticketJson.put("ticketId", ticket.ticketId);
                        ticketJson.put("place", ticket.place);
                        ticketJson.put("date", ticket.date);
                        ticketJson.put("time", ticket.time);
                        ticketJson.put("totalAmount", ticket.totalAmount);
                        ticketJson.put("timestamp", ticket.timestamp);
                        ticketJson.put("validFrom", ticket.validFrom);
                        ticketJson.put("validTo", ticket.validTo);
                        jsonArray.put(ticketJson);
                    }
                    sharedPreferences.edit()
                            .putString("cachedTickets", jsonArray.toString())
                            .apply();
                    Log.d(TAG, "Cached " + tickets.size() + " tickets as JSON");
                } catch (JSONException e) {
                    Log.e(TAG, "Failed to cache tickets as JSON: " + e.getMessage());
                }
            } else {
                Log.d(TAG, "No tickets found for userUid: " + userUid);
            }
            runOnUiThread(() -> updateTicketUI(tickets));
        } catch (Exception e) {
            Log.e(TAG, "Failed to fetch tickets: " + e.getMessage());
            runOnUiThread(() -> updateTicketUI(loadCachedTickets()));
        }
    }
    private List<Vehicle> loadCachedVehicles() {
        List<Vehicle> cachedVehicles = new ArrayList<>();
        String cachedVehiclesJson = sharedPreferences.getString("cachedVehicles", null);
        if (cachedVehiclesJson != null) {
            try {
                JSONArray jsonArray = new JSONArray(cachedVehiclesJson);
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject vehicleJson = jsonArray.getJSONObject(i);
                    String number = vehicleJson.getString("number");
                    String type = vehicleJson.getString("vehicleType");
                    if (number != null && type != null) {
                        cachedVehicles.add(new Vehicle(number, type));
                        Log.d(TAG, "Loaded cached vehicle: " + number + ", Type: " + type);
                    }
                }
            } catch (JSONException e) {
                Log.e(TAG, "Failed to parse cached vehicles: " + e.getMessage());
            }
        }
        return cachedVehicles;
    }

    private List<Ticket> loadCachedTickets() {
        List<Ticket> cachedTickets = new ArrayList<>();
        String cachedTicketsJson = sharedPreferences.getString("cachedTickets", null);
        if (cachedTicketsJson != null) {
            try {
                JSONArray jsonArray = new JSONArray(cachedTicketsJson);
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject ticketJson = jsonArray.getJSONObject(i);
                    Ticket ticket = new Ticket();
                    ticket.ticketId = ticketJson.getString("ticketId");
                    ticket.place = ticketJson.getString("place");
                    ticket.date = ticketJson.getString("date");
                    ticket.time = ticketJson.getString("time");
                    ticket.totalAmount = ticketJson.getString("totalAmount");
                    ticket.timestamp = ticketJson.getLong("timestamp");
                    ticket.dateTime = ticket.date + ", " + ticket.time;
                    ticket.price = "₹" + ticket.totalAmount;
                    ticket.validFrom = ticketJson.getString("validFrom");
                    ticket.validTo = ticketJson.getString("validTo");
                    cachedTickets.add(ticket);
                    Log.d(TAG, "Loaded cached ticket: " + ticket.ticketId);
                }
            } catch (JSONException e) {
                Log.e(TAG, "Failed to parse cached tickets: " + e.getMessage());
            }
        }
        return cachedTickets;
    }

    private void updateVehicleUI(List<Vehicle> vehicles) {
        runOnUiThread(() -> {
            vehicleContainer.removeAllViews();
            Log.d(TAG, "Updating vehicle UI with " + vehicles.size() + " vehicles");

            if (vehicles.isEmpty()) {
                Log.d(TAG, "No vehicles to display");
                if (addVehicleBtn != null) {
                    addVehicleBtn.setText("Add Your Vehicle");
                }

                TextView noVehiclesText = new TextView(this);
                noVehiclesText.setText("No vehicles added yet");
                noVehiclesText.setTextColor(Color.BLACK);
                noVehiclesText.setTextSize(16);
                noVehiclesText.setGravity(Gravity.CENTER);
                noVehiclesText.setPadding(0, 20, 0, 20);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                noVehiclesText.setLayoutParams(params);
                vehicleContainer.addView(noVehiclesText);
            } else {
                for (Vehicle vehicle : vehicles) {
                    View vehicleView = LayoutInflater.from(this).inflate(R.layout.item_vehicle, vehicleContainer, false);
                    TextView tvVehicleNumber = vehicleView.findViewById(R.id.vehicle_number);
                    TextView tvVehicleType = vehicleView.findViewById(R.id.vehicle_type);
                    ImageView btnEdit = vehicleView.findViewById(R.id.btn_edit_vehicle);
                    ImageView btnDelete = vehicleView.findViewById(R.id.btn_delete_vehicle);

                    tvVehicleNumber.setText(vehicle.number);
                    tvVehicleType.setText(vehicle.vehicleType);
                    btnEdit.setOnClickListener(v -> {
                        if (debounceClick()) return;
                        openEditVehicle(vehicle.number);
                    });
                    btnDelete.setOnClickListener(v -> {
                        if (debounceClick()) return;
                        confirmDeleteVehicle(vehicle.number);
                    });
                    vehicleContainer.addView(vehicleView);
                    Log.d(TAG, "Added vehicle view: " + vehicle.number);
                }

                if (addVehicleBtn != null) {
                    addVehicleBtn.setText("Add Another Vehicle");
                }
            }

            vehicleContainer.setVisibility(View.VISIBLE);
            vehicleContainer.post(() -> {
                vehicleContainer.requestLayout();
                Log.d(TAG, "Refreshed vehicleContainer layout, vehicle count: " + vehicles.size());
            });
        });
    }

    private void updateTicketUI(List<Ticket> tickets) {
        runOnUiThread(() -> {
            if (tickets.isEmpty()) {
                showNoTicketsMessage();
                return;
            }

            if (ticket1Place != null) ticket1Place.setText(tickets.get(0).place);
            if (ticket1DateTime != null) ticket1DateTime.setText(tickets.get(0).dateTime);
            if (ticket1Price != null) ticket1Price.setText(tickets.get(0).price);
            if (ticket1Layout != null) ticket1Layout.setVisibility(View.VISIBLE);

            if (tickets.size() > 1) {
                if (ticket2Place != null) ticket2Place.setText(tickets.get(1).place);
                if (ticket2DateTime != null) ticket2DateTime.setText(tickets.get(1).dateTime);
                if (ticket2Price != null) ticket2Price.setText(tickets.get(1).price);
                if (ticket2Layout != null) ticket2Layout.setVisibility(View.VISIBLE);
            } else {
                if (ticket2Layout != null) ticket2Layout.setVisibility(View.GONE);
            }

            ticket1Layout.post(() -> {
                ticket1Layout.requestLayout();
                if (ticket2Layout != null) {
                    ticket2Layout.requestLayout();
                }
                Log.d(TAG, "Refreshed ticket layouts, tickets displayed: " + tickets.size());
            });
        });
    }

    private void loadVehicleDetails() {
        if (userUid == null) {
            Log.d(TAG, "userUid is null, clearing vehicle UI");
            runOnUiThread(() -> updateVehicleUI(new ArrayList<>()));
            return;
        }

        DatabaseReference vehiclesRef = firebaseRefs.get("vehicle_details");
        try {
            DataSnapshot snapshot = Tasks.await(vehiclesRef.get(), 5, TimeUnit.SECONDS);
            Log.d(TAG, "Fetched vehicle data snapshot, exists: " + snapshot.exists());
            List<Vehicle> userVehicles = new ArrayList<>();
            if (snapshot.exists()) {
                for (DataSnapshot vehicle : snapshot.getChildren()) {
                    String number = vehicle.getKey();
                    String type = vehicle.child("vehicleType").getValue(String.class);
                    String vehicleUserUid = vehicle.child("userUid").getValue(String.class);
                    if (number != null && type != null && vehicleUserUid != null && vehicleUserUid.equals(userUid)) {
                        userVehicles.add(new Vehicle(number, type));
                        Log.d(TAG, "Found vehicle: " + number + ", Type: " + type);
                    }
                }
            } else {
                Log.d(TAG, "No vehicles found in Firebase for userUid: " + userUid);
            }

            try {
                JSONArray jsonArray = new JSONArray();
                for (Vehicle vehicle : userVehicles) {
                    JSONObject vehicleJson = new JSONObject();
                    vehicleJson.put("number", vehicle.number);
                    vehicleJson.put("vehicleType", vehicle.vehicleType);
                    jsonArray.put(vehicleJson);
                }
                sharedPreferences.edit()
                        .putString("cachedVehicles", jsonArray.toString())
                        .apply();
                Log.d(TAG, "Cached " + userVehicles.size() + " vehicles as JSON");
            } catch (JSONException e) {
                Log.e(TAG, "Failed to cache vehicles as JSON: " + e.getMessage());
            }

            runOnUiThread(() -> updateVehicleUI(userVehicles));
        } catch (Exception e) {
            Log.e(TAG, "Failed to fetch vehicles: " + e.getMessage());
            runOnUiThread(() -> {
                Toast.makeText(this, "Error loading vehicles, showing cached data", Toast.LENGTH_SHORT).show();
                updateVehicleUI(loadCachedVehicles());
            });
        }
    }

    private void loadUserDataTask() {
        if (userUid == null) {
            Log.d(TAG, "userUid is null, skipping user data load");
            runOnUiThread(() -> {
                if (welcomeText != null) {
                    welcomeText.setText("Welcome, Visitor!");
                    sharedPreferences.edit().putString("welcomeText", "Welcome, Visitor!").apply();
                }
            });
            return;
        }

        DatabaseReference userRef = firebaseRefs.get("users").child(userUid);
        Log.d(TAG, "Querying user data for userUid: " + userUid);
        try {
            DataSnapshot snapshot = Tasks.await(userRef.get(), 5, TimeUnit.SECONDS);
            Log.d(TAG, "Snapshot exists: " + snapshot.exists() + ", value: " + snapshot.getValue());
            if (snapshot.exists()) {
                String phone = snapshot.child("phone").getValue(String.class);
                String name = snapshot.child("name").getValue(String.class);
                String shortUid = snapshot.child("uid").getValue(String.class);
                Log.d(TAG, "Retrieved data - phone: " + phone + ", name: " + name + ", uid: " + shortUid);
                if (phone != null) {
                    sharedPreferences.edit()
                            .putString("phoneNumber", phone)
                            .putString("userName", name != null ? name : shortUid != null ? shortUid : "Visitor")
                            .apply();
                    phoneNumber = phone;
                }
                if (welcomeText != null) {
                    String displayUid = shortUid != null ? shortUid : "Visitor";
                    String welcomeMessage = name != null ? "Welcome, " + name + "!" : "Welcome, " + displayUid + "!";
                    Log.d(TAG, "Setting welcomeText: " + welcomeMessage);
                    runOnUiThread(() -> {
                        welcomeText.setText(welcomeMessage);
                        sharedPreferences.edit().putString("welcomeText", welcomeMessage).apply();
                    });
                }
            } else {
                Log.w(TAG, "No data found for userUid: " + userUid);
                runOnUiThread(() -> {
                    String cachedWelcome = sharedPreferences.getString("welcomeText", null);
                    if (welcomeText != null) {
                        welcomeText.setText(cachedWelcome != null ? cachedWelcome : "Welcome, Visitor!");
                        sharedPreferences.edit().putString("welcomeText", "Welcome, Visitor!").apply();
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to fetch user data for userUid " + userUid + ": " + e.getMessage());
            runOnUiThread(() -> {
                String cachedWelcome = sharedPreferences.getString("welcomeText", null);
                if (welcomeText != null) {
                    welcomeText.setText(cachedWelcome != null ? cachedWelcome : "Welcome, Visitor!");
                    Toast.makeText(TouristPageActivity.this, "Error loading user data", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void logHeaderPositions() {
        if (profileIcon != null) {
            profileIcon.post(() -> {
                Log.d(TAG, "profile_icon: x=" + profileIcon.getX() + ", y=" + profileIcon.getY() +
                        ", width=" + profileIcon.getWidth() + ", height=" + profileIcon.getHeight());
            });
        }
        if (notificationIcon != null) {
            notificationIcon.post(() -> {
                Log.d(TAG, "notification_icon: x=" + notificationIcon.getX() + ", y=" + notificationIcon.getY() +
                        ", width=" + notificationIcon.getWidth() + ", height=" + notificationIcon.getHeight());
            });
        }
    }

    @Override
    public void onBackPressed() {
        if (isCelebrationShowing) {
            dismissCelebration();
        } else {
            super.onBackPressed();
            moveTaskToBack(true);
        }
    }

    private void showErrorMessage() {
        if (ticket1Place != null) ticket1Place.setText("Error loading tickets");
        if (ticket1DateTime != null) ticket1DateTime.setText("");
        if (ticket1Price != null) ticket1Price.setText("");
        if (ticket1Layout != null) ticket1Layout.setVisibility(View.VISIBLE);
        if (ticket2Layout != null) ticket2Layout.setVisibility(View.GONE);
    }

    private void showNoTicketsMessage() {
        ticket1Layout.post(() -> {
            if (ticket1Place != null) ticket1Place.setText("No recent tickets found");
            if (ticket1DateTime != null) ticket1DateTime.setText("");
            if (ticket1Price != null) ticket1Price.setText("");
            if (ticket1Layout != null) ticket1Layout.setVisibility(View.VISIBLE);
            if (ticket2Layout != null) ticket2Layout.setVisibility(View.GONE);
        });
    }

    private static class Ticket {
        public String ticketId;
        public String place;
        public String date;
        public String time;
        public String totalAmount;
        public long timestamp;
        public String dateTime;
        public String price;
        public String validFrom;
        public String validTo;
    }

    private static class Vehicle {
        String number;
        String vehicleType;

        Vehicle(String number, String vehicleType) {
            this.number = number;
            this.vehicleType = vehicleType;
        }
    }

    private String formatDate(long timestamp) {
        return new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(new Date(timestamp));
    }

    private String formatTime(long timestamp) {
        return new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date(timestamp));
    }



    private void openAddVehicle() {
        Intent intent = new Intent(this, VehicleActivity.class);
        intent.putExtra("phoneNumber", phoneNumber);
        startActivity(intent);
    }

    private void openEditVehicle(String vehicleId) {
        Intent intent = new Intent(this, VehicleActivity.class);
        intent.putExtra("vehicleId", vehicleId);
        intent.putExtra("phoneNumber", phoneNumber);
        startActivity(intent);
    }

    private void confirmDeleteVehicle(String vehicleId) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Vehicle")
                .setMessage("Are you sure you want to delete vehicle " + vehicleId + "?")
                .setPositiveButton("Delete", (dialog, which) -> deleteVehicle(vehicleId))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteVehicle(String vehicleId) {
        DatabaseReference vehicleRef = firebaseRefs.get("vehicle_details").child(vehicleId);
        vehicleRef.removeValue()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(TouristPageActivity.this, "Vehicle deleted", Toast.LENGTH_SHORT).show();
                    loadDataAsync(); // Refresh vehicle list
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to delete vehicle: " + e.getMessage());
                    Toast.makeText(TouristPageActivity.this, "Error deleting vehicle", Toast.LENGTH_SHORT).show();
                });
    }



    private void openUserProfile() {
        Intent intent = new Intent(this, TouristProfileActivity.class);
        intent.putExtra("USER_UID", userUid);
        intent.putExtra("phoneNumber", phoneNumber);
        startActivity(intent);
    }

    private void openQrScanner() {
        Intent intent = new Intent(this, QRScannerActivity.class);
        intent.putExtra("USER_UID", userUid);
        intent.putExtra("phoneNumber", phoneNumber);
        startActivity(intent);
    }

    private void openDownloadedQRActivity() {
        Intent intent = new Intent(this, TicketHistoryTouristActivity.class);
        intent.putExtra("USER_UID", userUid);
        startActivity(intent);
    }

    private void logoutUser() {
        // Step 1: Clear SharedPreferences
        try {
            MasterKey masterKey = new MasterKey.Builder(this)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            SharedPreferences sharedPreferences = EncryptedSharedPreferences.create(
                    this, "UserPrefs", masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            sharedPreferences.edit().clear().commit(); // Use commit() for synchronous clearing
            Log.d(TAG, "EncryptedSharedPreferences cleared");
        } catch (Exception e) {
            Log.e(TAG, "Failed to clear EncryptedSharedPreferences: " + e.getMessage());
        }

        // Step 2: Sign out from Firebase
        mAuth.signOut();
        cachedUser = null; // Clear cached user
        userUid = null;
        isLoggedIn = false;
        Log.d(TAG, "Firebase Auth signed out");

        // Step 3: Redirect to LoginActivity
        redirectToLogin();
    }

    private void redirectToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
        Toast.makeText(this, "Logout Successful", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Redirected to LoginActivity with cleared task");
    }

    @Override
    protected void onResume() {
        super.onResume();
        executorService.execute(() -> {
            logHeaderPositions();
            if (userUid != null && !userUid.isEmpty() && phoneNumber != null && isLoggedIn) {
                loadDataAsync();
                listenForNotifications();
                runOnUiThread(this::updateNotificationDot);
                // Restart slider
                runOnUiThread(this::startAutoSlide);
            } else {
                checkExistingSession();
            }
        });
    }

    private void checkExistingSession() {
        cachedUser = mAuth.getCurrentUser();
        if (cachedUser == null) {
            Log.w(TAG, "No authenticated user found");
            userUid = null;
            phoneNumber = null;
            isLoggedIn = false;
            sharedPreferences.edit()
                    .remove("userUid")
                    .remove("phoneNumber")
                    .remove("isLoggedIn")
                    .commit();
            runOnUiThread(this::redirectToLogin);
            return;
        }

        userUid = cachedUser.getUid();
        // Check intent data first
        Intent intent = getIntent();
        boolean fromLogin = intent.getBooleanExtra("isLoggedIn", false);
        String intentPhoneNumber = intent.getStringExtra("phoneNumber");
        if (fromLogin && intentPhoneNumber != null && intentPhoneNumber.matches("^[0-9]{10}$")) {
            phoneNumber = intentPhoneNumber;
            isLoggedIn = true;
            sharedPreferences.edit()
                    .putString("userUid", userUid)
                    .putString("phoneNumber", phoneNumber)
                    .putBoolean("isLoggedIn", true)
                    .commit();
            Log.d(TAG, "Using intent data for session: userUid=" + userUid + ", phoneNumber=" + phoneNumber);
            runOnUiThread(() -> {
                setupSession();
                loadDataAsync();
                listenForNotifications();
                updateNotificationDot();
            });
            return;
        }

        // Check SharedPreferences for cached session
        phoneNumber = sharedPreferences.getString("phoneNumber", null);
        isLoggedIn = sharedPreferences.getBoolean("isLoggedIn", false);
        if (phoneNumber != null && phoneNumber.matches("^[0-9]{10}$") && isLoggedIn) {
            Log.d(TAG, "Using cached session: userUid=" + userUid + ", phoneNumber=" + phoneNumber);
            runOnUiThread(() -> {
                setupSession();
                loadDataAsync();
                listenForNotifications();
                updateNotificationDot();
            });
            return;
        }

        // Validate with Firebase
        DatabaseReference usersRef = firebaseRefs.get("users").child(userUid);
        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String role = snapshot.child("role").getValue(String.class);
                    phoneNumber = snapshot.child("phone").getValue(String.class);
                    if (!"tourist".equals(role) || phoneNumber == null || phoneNumber.trim().isEmpty() || !phoneNumber.matches("^[0-9]{10}$")) {
                        Log.e(TAG, "Invalid role or phone number: role=" + role + ", phoneNumber=" + phoneNumber);
                        runOnUiThread(() -> {
                            Toast.makeText(TouristPageActivity.this, "Invalid user data, please log in again", Toast.LENGTH_LONG).show();
                            redirectToLogin();
                        });
                        return;
                    }
                    isLoggedIn = true;
                    sharedPreferences.edit()
                            .putString("userUid", userUid)
                            .putString("phoneNumber", phoneNumber)
                            .putBoolean("isLoggedIn", true)
                            .commit();
                    runOnUiThread(() -> {
                        setupSession();
                        loadDataAsync();
                        listenForNotifications();
                        updateNotificationDot();
                    });
                } else {
                    Log.e(TAG, "No user node found for userUid: " + userUid);
                    runOnUiThread(() -> {
                        Toast.makeText(TouristPageActivity.this, "Session expired, please log in again", Toast.LENGTH_LONG).show();
                        redirectToLogin();
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to verify user session: " + error.getMessage());
                runOnUiThread(() -> {
                    Toast.makeText(TouristPageActivity.this, "Error verifying session", Toast.LENGTH_LONG).show();
                    redirectToLogin();
                });
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (paymentsRef != null && paymentListener != null) {
            paymentsRef.removeEventListener(paymentListener);
        }
        if (notificationsReference != null && notificationListener != null) {
            notificationsReference.removeEventListener(notificationListener);
        }
        // Stop slider
        sliderHandler.removeCallbacks(sliderRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
        if (shimmerLayout != null) {
            shimmerLayout.stopShimmer();
        }
        // Cleanup slider handler
        sliderHandler.removeCallbacks(sliderRunnable);
    }

    private static class VehicleAdapter extends RecyclerView.Adapter<VehicleAdapter.VehicleViewHolder> {
        private List<Vehicle> vehicles;
        private final OnVehicleClickListener editClickListener;
        private final OnVehicleClickListener deleteClickListener;

        interface OnVehicleClickListener {
            void onVehicleClick(String vehicleId);
        }

        VehicleAdapter(List<Vehicle> vehicles, OnVehicleClickListener editClickListener, OnVehicleClickListener deleteClickListener) {
            this.vehicles = vehicles;
            this.editClickListener = editClickListener;
            this.deleteClickListener = deleteClickListener;
        }

        void updateVehicles(List<Vehicle> vehicles) {
            this.vehicles = new ArrayList<>(vehicles);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VehicleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_vehicle, parent, false);
            return new VehicleViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VehicleViewHolder holder, int position) {
            Vehicle vehicle = vehicles.get(position);
            holder.bind(vehicle);
        }

        @Override
        public int getItemCount() {
            return vehicles.size();
        }

        class VehicleViewHolder extends RecyclerView.ViewHolder {
            TextView tvVehicleNumber;
            TextView tvVehicleType;
            ImageView btnEdit;
            ImageView btnDelete;

            VehicleViewHolder(View itemView) {
                super(itemView);
                tvVehicleNumber = itemView.findViewById(R.id.vehicle_number);
                tvVehicleType = itemView.findViewById(R.id.vehicle_type);
                btnEdit = itemView.findViewById(R.id.btn_edit_vehicle);
                btnDelete = itemView.findViewById(R.id.btn_delete_vehicle);
            }

            void bind(Vehicle vehicle) {
                tvVehicleNumber.setText(vehicle.number);
                tvVehicleType.setText(vehicle.vehicleType);
                btnEdit.setOnClickListener(v -> editClickListener.onVehicleClick(vehicle.number));
                btnDelete.setOnClickListener(v -> deleteClickListener.onVehicleClick(vehicle.number));
            }
        }
    }
}






