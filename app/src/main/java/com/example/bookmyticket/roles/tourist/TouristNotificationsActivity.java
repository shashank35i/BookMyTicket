package com.example.bookmyticket.roles.tourist;

import com.example.bookmyticket.R;
import com.example.bookmyticket.model.Ticket;
import com.example.bookmyticket.model.Vehicle;
import com.example.bookmyticket.notification.NotificationService;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.transition.AutoTransition;
import androidx.transition.Transition;
import androidx.transition.TransitionManager;

import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.razorpay.Checkout;
import com.razorpay.PaymentResultListener;

import org.json.JSONObject;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class TouristNotificationsActivity extends AppCompatActivity implements PaymentResultListener {
    private static final String TAG = "TouristNotifications";
    private static final String RAZORPAY_KEY_ID = "rzp_test_kEE4xn2vlYKwiw";
    private static final String NOTIFICATION_CHANNEL_ID = "payment_notifications";
    private static final String PREFS_NAME = "TouristNotificationsPrefs";
    private static final String KEY_NOTIFICATIONS = "cached_notifications";
    private static final int NOTIFICATION_PERMISSION_CODE = 100;
    private static final int SYSTEM_NOTIFICATION_REQUEST_CODE = 200;
    private static final long MIN_CLICK_INTERVAL = 500;

    private static boolean isPersistenceInitialized = false;
    static {
        try {
            if (!isPersistenceInitialized) {
                FirebaseDatabase.getInstance().setPersistenceEnabled(true);
                isPersistenceInitialized = true;
                Log.d(TAG, "Firebase persistence enabled");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to enable Firebase persistence: " + e.getMessage());
        }
    }

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView rvNotifications;
    private ShimmerFrameLayout shimmerLayout;
    private TextView txtEmpty;
    private ImageView btnBack;
    private NotificationAdapter adapter;
    private DatabaseReference notificationsReference;
    private DatabaseReference paymentsReference;
    private FirebaseUser currentUser;
    private ChildEventListener notificationListener;
    private Set<String> notificationIds = new HashSet<>();
    private SharedPreferences prefs;
    private Gson gson;
    private String currentPaymentId;
    private String currentVehicleNumber;
    private String currentTicketId;
    private String currentPlace;
    private String currentAmount;
    private String currentAdminUid;
    private long lastClickTime = 0;

    public static class Notification {
        private String notificationId;
        private String type;
        private String paymentId;
        private String vehicleNumber;
        private String vehicleType;
        private String ticketId;
        private String place;
        private String amount;
        private String adminUid;
        private long timestamp;
        private boolean seen;

        public Notification() {}

        public Notification(String notificationId, String type, String paymentId, String vehicleNumber,
                            String vehicleType, String ticketId, String place, String amount,
                            String adminUid, long timestamp, boolean seen) {
            this.notificationId = notificationId;
            this.type = type;
            this.paymentId = paymentId;
            this.vehicleNumber = vehicleNumber;
            this.vehicleType = vehicleType;
            this.ticketId = ticketId;
            this.place = place;
            this.amount = amount;
            this.adminUid = adminUid;
            this.timestamp = timestamp;
            this.seen = seen;
        }

        public String getNotificationId() { return notificationId; }
        public void setNotificationId(String notificationId) { this.notificationId = notificationId; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getPaymentId() { return paymentId; }
        public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
        public String getVehicleNumber() { return vehicleNumber; }
        public void setVehicleNumber(String vehicleNumber) { this.vehicleNumber = vehicleNumber; }
        public String getVehicleType() { return vehicleType; }
        public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }
        public String getTicketId() { return ticketId; }
        public void setTicketId(String ticketId) { this.ticketId = ticketId; }
        public String getPlace() { return place; }
        public void setPlace(String place) { this.place = place; }
        public String getAmount() { return amount; }
        public void setAmount(String amount) { this.amount = amount; }
        public String getAdminUid() { return adminUid; }
        public void setAdminUid(String adminUid) { this.adminUid = adminUid; }
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        public boolean isSeen() { return seen; }
        public void setSeen(boolean seen) { this.seen = seen; }
    }

    private class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder> {
        private final List<Notification> notifications = new ArrayList<>();
        private final List<Boolean> expandedStates = new ArrayList<>();

        public void setNotifications(List<Notification> notifications) {
            this.notifications.clear();
            this.expandedStates.clear();
            this.notifications.addAll(notifications);
            this.expandedStates.addAll(Collections.nCopies(notifications.size(), false));
            notifyDataSetChanged();
        }

        public void addNotification(Notification notification) {
            if (!notificationIds.contains(notification.getNotificationId())) {
                notifications.add(0, notification);
                expandedStates.add(0, false);
                notificationIds.add(notification.getNotificationId());
                notifyItemInserted(0);
                rvNotifications.scrollToPosition(0);
                if (!notification.isSeen()) {
                    markNotificationAsSeen(notification);
                }
            }
        }

        public void clearNotifications() {
            notifications.clear();
            expandedStates.clear();
            notificationIds.clear();
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(TouristNotificationsActivity.this).inflate(R.layout.item_notification, parent, false);
            return new NotificationViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
            Notification notification = notifications.get(position);
            if (notification == null || notification.getNotificationId() == null) {
                Log.e(TAG, "Invalid notification at position " + position);
                holder.compactContainer.setVisibility(View.GONE);
                return;
            }
            holder.compactContainer.setVisibility(View.VISIBLE);

            boolean isVehicleNotification = notification.getVehicleNumber() != null && notification.getVehicleType() != null;

            String message;
            if (isVehicleNotification) {
                message = getVehicleNotificationMessage(notification);
            } else {
                message = getTicketNotificationMessage(notification);
            }
            holder.txtCompactMessage.setText(message);
            holder.txtTimestamp.setText(getRelativeTime(notification.getTimestamp()));

            if (isVehicleNotification) {
                holder.txtVehicleNumber.setText(notification.getVehicleNumber() != null ? notification.getVehicleNumber() : "N/A");
                holder.txtVehicleType.setText(notification.getVehicleType() != null ? "Vehicle Type: " + notification.getVehicleType() : "Vehicle Type: N/A");
                holder.txtVehicleType.setVisibility(View.VISIBLE);
                holder.txtPlace.setVisibility(View.GONE);
            } else {
                holder.txtVehicleNumber.setText(notification.getTicketId() != null ? notification.getTicketId() : "N/A");
                holder.txtPlace.setText(notification.getPlace() != null ? "Place: " + notification.getPlace() : "Place: N/A");
                holder.txtVehicleType.setVisibility(View.GONE);
                holder.txtPlace.setVisibility(View.VISIBLE);
            }
            holder.txtAmount.setText(notification.getAmount() != null ? "Amount: ₹" + notification.getAmount() : "Amount: ₹0");

            boolean isExpanded = expandedStates.get(position);
            holder.detailsContainer.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
            holder.divider.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
            holder.btnPay.setVisibility(View.GONE);

            boolean canExpand = notification.getType().equals("payment_success") ||
                    notification.getType().equals("payment_failed") ||
                    (notification.getType().equals("payment_request") && notification.getPaymentId() != null);

            if (canExpand && notification.getType().equals("payment_request")) {
                paymentsReference.child(notification.getPaymentId()).child("status").addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String status = snapshot.getValue(String.class);
                        boolean isPending = "pending".equals(status);
                        int currentPosition = holder.getAdapterPosition();
                        if (currentPosition != RecyclerView.NO_POSITION && notifications.get(currentPosition) == notification) {
                            holder.btnPay.setVisibility(isExpanded && isPending ? View.VISIBLE : View.GONE);
                            if (isPending) {
                                holder.btnPay.setOnClickListener(v -> initiatePayment(notification));
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to check payment status: " + error.getMessage());
                        runOnUiThread(() -> Toast.makeText(TouristNotificationsActivity.this, "Error checking payment status", Toast.LENGTH_SHORT).show());
                    }
                });
            }

            holder.compactContainer.setOnClickListener(v -> {
                long currentTime = SystemClock.elapsedRealtime();
                if (currentTime - lastClickTime < MIN_CLICK_INTERVAL) {
                    return;
                }
                lastClickTime = currentTime;

                if (!canExpand) {
                    return;
                }

                int currentPosition = holder.getAdapterPosition();
                if (currentPosition == RecyclerView.NO_POSITION) {
                    return;
                }

                boolean newExpandedState = !expandedStates.get(currentPosition);
                expandedStates.set(currentPosition, newExpandedState);

                Transition transition = new AutoTransition()
                        .setDuration(200)
                        .addTarget(holder.detailsContainer)
                        .addTarget(holder.divider);
                TransitionManager.beginDelayedTransition(holder.cardView, transition);

                holder.detailsContainer.setVisibility(newExpandedState ? View.VISIBLE : View.GONE);
                holder.divider.setVisibility(newExpandedState ? View.VISIBLE : View.GONE);

                if (notification.getType().equals("payment_request") && notification.getPaymentId() != null) {
                    paymentsReference.child(notification.getPaymentId()).child("status").addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            String status = snapshot.getValue(String.class);
                            int updatedPosition = holder.getAdapterPosition();
                            if (updatedPosition != RecyclerView.NO_POSITION && notifications.get(updatedPosition) == notification) {
                                holder.btnPay.setVisibility(newExpandedState && "pending".equals(status) ? View.VISIBLE : View.GONE);
                                if ("pending".equals(status)) {
                                    holder.btnPay.setOnClickListener(v1 -> initiatePayment(notification));
                                }
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Log.e(TAG, "Failed to check payment status: " + error.getMessage());
                            runOnUiThread(() -> Toast.makeText(TouristNotificationsActivity.this, "Error checking payment status", Toast.LENGTH_SHORT).show());
                        }
                    });
                }

                if (newExpandedState && !notification.isSeen()) {
                    markNotificationAsSeen(notification);
                }
            });

            holder.ivMenu.setOnClickListener(v -> showBottomSheetDialog(notification));
        }

        private String getVehicleNotificationMessage(Notification notification) {
            String identifier = notification.getVehicleNumber() != null ? notification.getVehicleNumber() : "Unknown";
            switch (notification.getType()) {
                case "payment_request":
                    return "Pending payment for " + identifier;
                case "payment_success":
                    return "Payment completed for " + identifier + ", ₹" + (notification.getAmount() != null ? notification.getAmount() : "0");
                case "payment_failed":
                    return "Payment failed for " + identifier;
                default:
                    return "Notification for " + identifier;
            }
        }

        private String getTicketNotificationMessage(Notification notification) {
            String identifier = notification.getTicketId() != null ? notification.getTicketId() : "Unknown";
            String place = notification.getPlace() != null ? " at " + notification.getPlace() : "";
            switch (notification.getType()) {
                case "payment_request":
                    return "Pending payment for ticket " + identifier + place;
                case "payment_success":
                    return "Payment completed for ticket " + identifier + place + ", ₹" + (notification.getAmount() != null ? notification.getAmount() : "0");
                case "payment_failed":
                    return "Payment failed for ticket " + identifier + place;
                default:
                    return "Notification for ticket " + identifier + place;
            }
        }

        @Override
        public int getItemCount() {
            return notifications.size();
        }

        class NotificationViewHolder extends RecyclerView.ViewHolder {
            CardView cardView;
            ConstraintLayout compactContainer;
            TextView txtCompactMessage;
            TextView txtTimestamp;
            ImageView ivMenu;
            View divider;
            ConstraintLayout detailsContainer;
            TextView txtVehicleNumber, txtVehicleType, txtPlace, txtAmount;
            MaterialButton btnPay;

            NotificationViewHolder(@NonNull View itemView) {
                super(itemView);
                cardView = itemView.findViewById(R.id.card_view);
                compactContainer = itemView.findViewById(R.id.compact_container);
                txtCompactMessage = itemView.findViewById(R.id.txt_compact_message);
                txtTimestamp = itemView.findViewById(R.id.txt_timestamp);
                ivMenu = itemView.findViewById(R.id.iv_menu);
                divider = itemView.findViewById(R.id.divider);
                detailsContainer = itemView.findViewById(R.id.details_container);
                txtVehicleNumber = itemView.findViewById(R.id.txt_vehicle_number);
                txtVehicleType = itemView.findViewById(R.id.txt_vehicle_type);
                txtPlace = itemView.findViewById(R.id.txt_place);
                txtAmount = itemView.findViewById(R.id.txt_amount);
                btnPay = itemView.findViewById(R.id.btn_pay);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tourist_notifications);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        gson = new Gson();

        setupSystemBars();
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        Checkout.preload(getApplicationContext());
        createNotificationChannel();
        initializeViews();
        initializeFirebase();
        setupRecyclerView();
        loadNotifications();
        setupNotificationListener();
        requestNotificationPermission();
        startNotificationService();
    }

    private void startNotificationService() {
        Intent serviceIntent = new Intent(this, NotificationService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Do not cancel notifications to allow them to persist in the system tray
        markAllNotificationsAsSeen();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    private void setupSystemBars() {
        getWindow().setStatusBarColor(getResources().getColor(android.R.color.white));
        getWindow().setNavigationBarColor(getResources().getColor(android.R.color.white));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR |
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0));
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Payment Notifications",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for payment updates");
            channel.enableVibration(true);
            channel.enableLights(true);
            channel.setShowBadge(true);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_CODE && grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Notification permission denied. System notifications will not be shown.", Toast.LENGTH_LONG).show();
        }
    }

    private void initializeViews() {
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        rvNotifications = findViewById(R.id.rv_notifications);
        shimmerLayout = findViewById(R.id.shimmer_layout);
        txtEmpty = findViewById(R.id.txt_empty);
        btnBack = findViewById(R.id.btn_back);

        swipeRefreshLayout.setOnRefreshListener(() -> {
            shimmerLayout.setVisibility(View.VISIBLE);
            shimmerLayout.startShimmer();
            loadNotifications();
        });
        swipeRefreshLayout.setColorSchemeResources(R.color.primary_purple);
        swipeRefreshLayout.setProgressBackgroundColorSchemeResource(android.R.color.white);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> onBackPressed());
        }

        rvNotifications.setAlpha(0f);
        txtEmpty.setAlpha(0f);
        rvNotifications.animate()
                .alpha(1f)
                .setDuration(400)
                .setInterpolator(new androidx.interpolator.view.animation.FastOutSlowInInterpolator())
                .start();
    }

    private void initializeFirebase() {
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        notificationsReference = FirebaseDatabase.getInstance()
                .getReference("tourist_notifications")
                .child(currentUser.getUid());
        paymentsReference = FirebaseDatabase.getInstance()
                .getReference("payments")
                .child(currentUser.getUid());
        notificationsReference.keepSynced(true);
    }

    private void setupRecyclerView() {
        adapter = new NotificationAdapter();
        rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        rvNotifications.setAdapter(adapter);
        rvNotifications.setHasFixedSize(true);
    }

    private void loadNotifications() {
        if (!isNetworkAvailable()) {
            swipeRefreshLayout.setRefreshing(false);
            shimmerLayout.stopShimmer();
            shimmerLayout.setVisibility(View.GONE);
            Toast.makeText(this, "No internet connection. Showing cached data.", Toast.LENGTH_SHORT).show();
            loadCachedNotifications();
            return;
        }

        shimmerLayout.setVisibility(View.VISIBLE);
        shimmerLayout.startShimmer();
        txtEmpty.setVisibility(View.GONE);
        rvNotifications.setVisibility(View.GONE);
        adapter.clearNotifications();

        notificationsReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Notification> notifications = new ArrayList<>();
                notificationIds.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Notification notification = child.getValue(Notification.class);
                    if (notification != null && child.getKey() != null) {
                        notification.setNotificationId(child.getKey());
                        Boolean seen = child.child("seen").getValue(Boolean.class);
                        notification.setSeen(seen != null && seen);
                        if (!notificationIds.contains(notification.getNotificationId())) {
                            notifications.add(notification);
                            notificationIds.add(notification.getNotificationId());
                        }
                    }
                }
                Collections.sort(notifications, (n1, n2) -> Long.compare(n2.getTimestamp(), n1.getTimestamp()));
                adapter.setNotifications(notifications);
                cacheNotifications(notifications);
                shimmerLayout.stopShimmer();
                shimmerLayout.setVisibility(View.GONE);
                rvNotifications.setVisibility(View.VISIBLE);
                txtEmpty.setVisibility(notifications.isEmpty() ? View.VISIBLE : View.GONE);
                swipeRefreshLayout.setRefreshing(false);
                if (notifications.isEmpty()) {
                    txtEmpty.animate()
                            .alpha(1f)
                            .setDuration(400)
                            .setInterpolator(new androidx.interpolator.view.animation.FastOutSlowInInterpolator())
                            .start();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                shimmerLayout.stopShimmer();
                shimmerLayout.setVisibility(View.GONE);
                txtEmpty.setVisibility(View.VISIBLE);
                rvNotifications.setVisibility(View.GONE);
                swipeRefreshLayout.setRefreshing(false);
                Toast.makeText(TouristNotificationsActivity.this, "Failed to load notifications: " + error.getMessage(), Toast.LENGTH_LONG).show();
                loadCachedNotifications();
            }
        });
    }

    private void markAllNotificationsAsSeen() {
        if (!isNetworkAvailable()) {
            return;
        }
        notificationsReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot child : snapshot.getChildren()) {
                    Boolean seen = child.child("seen").getValue(Boolean.class);
                    if (seen == null || !seen) {
                        notificationsReference.child(child.getKey()).child("seen").setValue(true)
                                .addOnFailureListener(e -> Log.e(TAG, "Failed to mark notification as seen: " + e.getMessage()));
                    }
                }
                for (Notification notification : adapter.notifications) {
                    notification.setSeen(true);
                }
                cacheNotifications(adapter.notifications);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error marking notifications as seen: " + error.getMessage());
                Toast.makeText(TouristNotificationsActivity.this, "Error updating notifications", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void markNotificationAsSeen(Notification notification) {
        if (!isNetworkAvailable() || notification == null || notification.getNotificationId() == null) {
            return;
        }
        notificationsReference.child(notification.getNotificationId()).child("seen").setValue(true)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Marked notification as seen: " + notification.getNotificationId());
                    notification.setSeen(true);
                    cacheNotifications(adapter.notifications);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to mark notification as seen: " + e.getMessage());
                    Toast.makeText(TouristNotificationsActivity.this, "Error updating notification", Toast.LENGTH_SHORT).show();
                });
    }

    private void setupNotificationListener() {
        notificationListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, String previousChildName) {
                Notification notification = snapshot.getValue(Notification.class);
                if (notification != null && snapshot.getKey() != null) {
                    notification.setNotificationId(snapshot.getKey());
                    Boolean seen = snapshot.child("seen").getValue(Boolean.class);
                    notification.setSeen(seen != null && seen);
                    if (!notificationIds.contains(notification.getNotificationId())) {
                        adapter.addNotification(notification);
                        cacheNotifications(adapter.notifications);
                    }
                }
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, String previousChildName) {
                Notification updatedNotification = snapshot.getValue(Notification.class);
                if (updatedNotification != null && snapshot.getKey() != null) {
                    updatedNotification.setNotificationId(snapshot.getKey());
                    Boolean seen = snapshot.child("seen").getValue(Boolean.class);
                    updatedNotification.setSeen(seen != null && seen);
                    for (int i = 0; i < adapter.notifications.size(); i++) {
                        if (adapter.notifications.get(i).getNotificationId().equals(updatedNotification.getNotificationId())) {
                            adapter.notifications.set(i, updatedNotification);
                            adapter.notifyItemChanged(i);
                            cacheNotifications(adapter.notifications);
                            break;
                        }
                    }
                }
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                String removedId = snapshot.getKey();
                for (int i = 0; i < adapter.notifications.size(); i++) {
                    if (adapter.notifications.get(i).getNotificationId().equals(removedId)) {
                        adapter.notifications.remove(i);
                        adapter.expandedStates.remove(i);
                        adapter.notifyItemRemoved(i);
                        notificationIds.remove(removedId);
                        cacheNotifications(adapter.notifications);
                        break;
                    }
                }
            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, String previousChildName) {}

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Notification listener cancelled: " + error.getMessage());
                runOnUiThread(() -> Toast.makeText(TouristNotificationsActivity.this, "Error listening for notifications", Toast.LENGTH_SHORT).show());
            }
        };
        notificationsReference.addChildEventListener(notificationListener);
    }

    private void cacheNotifications(List<Notification> notifications) {
        SharedPreferences.Editor editor = prefs.edit();
        String json = gson.toJson(notifications);
        editor.putString(KEY_NOTIFICATIONS, json);
        editor.apply();
    }

    private void loadCachedNotifications() {
        String json = prefs.getString(KEY_NOTIFICATIONS, null);
        if (json != null) {
            Type listType = new TypeToken<List<Notification>>(){}.getType();
            List<Notification> cachedNotifications = gson.fromJson(json, listType);
            if (cachedNotifications != null && !cachedNotifications.isEmpty()) {
                notificationIds.clear();
                for (Notification notification : cachedNotifications) {
                    if (notification.getNotificationId() != null) {
                        notificationIds.add(notification.getNotificationId());
                    }
                }
                adapter.setNotifications(cachedNotifications);
                rvNotifications.setVisibility(View.VISIBLE);
                txtEmpty.setVisibility(View.GONE);
                Toast.makeText(this, "Loaded cached notifications", Toast.LENGTH_SHORT).show();
            } else {
                txtEmpty.setVisibility(View.VISIBLE);
                rvNotifications.setVisibility(View.GONE);
            }
        } else {
            txtEmpty.setVisibility(View.VISIBLE);
            rvNotifications.setVisibility(View.GONE);
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    private void initiatePayment(Notification notification) {
        if (notification == null || notification.getPaymentId() == null || notification.getAmount() == null) {
            Toast.makeText(this, "Invalid payment data", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            currentPaymentId = notification.getPaymentId();
            currentVehicleNumber = notification.getVehicleNumber();
            currentTicketId = notification.getTicketId();
            currentPlace = notification.getPlace();
            currentAmount = notification.getAmount();
            currentAdminUid = notification.getAdminUid();

            Checkout checkout = new Checkout();
            checkout.setKeyID(RAZORPAY_KEY_ID);
            checkout.setImage(R.drawable.ic_notifications);

            JSONObject options = new JSONObject();
            options.put("name", notification.getVehicleNumber() != null ? "Parking Payment" : "Ticket Payment");
            options.put("description", notification.getVehicleNumber() != null ?
                    "Payment for " + (notification.getVehicleNumber() != null ? notification.getVehicleNumber() : "Unknown") :
                    "Payment for ticket " + (notification.getTicketId() != null ? notification.getTicketId() : "Unknown"));
            options.put("currency", "INR");
            double amount = Double.parseDouble(notification.getAmount()) * 100;
            options.put("amount", (int) amount);
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
            prefill.put("email", currentUser.getEmail() != null ? currentUser.getEmail() : "user@example.com");
            prefill.put("contact", currentUser.getPhoneNumber() != null ? currentUser.getPhoneNumber() : "1234567890");
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

        paymentsReference.child(currentPaymentId)
                .child("status")
                .setValue("completed")
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Payment status updated to completed"))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update payment status: " + e.getMessage());
                    Toast.makeText(this, "Error updating payment status", Toast.LENGTH_SHORT).show();
                });

        for (Notification notification : adapter.notifications) {
            if (notification.getPaymentId() != null && notification.getPaymentId().equals(currentPaymentId)) {
                Map<String, Object> updates = new HashMap<>();
                updates.put("type", "payment_success");
                updates.put("seen", true);
                notificationsReference.child(notification.getNotificationId())
                        .updateChildren(updates)
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "Notification updated to payment_success");
                            notification.setType("payment_success");
                            notification.setSeen(true);
                            cacheNotifications(adapter.notifications);
                            adapter.notifyDataSetChanged();
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to update notification: " + e.getMessage());
                            Toast.makeText(this, "Error updating notification", Toast.LENGTH_SHORT).show();
                        });
                break;
            }
        }

        if (currentAdminUid != null) {
            DatabaseReference adminNotificationsRef = FirebaseDatabase.getInstance()
                    .getReference("admin_notifications").child(currentAdminUid);
            String notificationId = adminNotificationsRef.push().getKey();
            Map<String, Object> adminNotificationData = new HashMap<>();
            adminNotificationData.put("type", "payment_completed");
            adminNotificationData.put("paymentId", currentPaymentId);
            adminNotificationData.put("vehicleNumber", currentVehicleNumber != null ? currentVehicleNumber : "Unknown");
            adminNotificationData.put("ticketId", currentTicketId != null ? currentTicketId : null);
            adminNotificationData.put("place", currentPlace != null ? currentPlace : null);
            adminNotificationData.put("amount", currentAmount != null ? currentAmount : "0");
            adminNotificationData.put("userUid", currentUser.getUid());
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
                Long tickets = mutableData.child("tickets").getValue(Long.class);
                Long ticketRevenue = mutableData.child("ticketRevenue").getValue(Long.class);

                if (vehicles == null) vehicles = 0L;
                if (parkingRevenue == null) parkingRevenue = 0L;
                if (activeBookings == null) activeBookings = 0L;
                if (tickets == null) tickets = 0L;
                if (ticketRevenue == null) ticketRevenue = 0L;

                if (currentVehicleNumber != null) {
                    mutableData.child("vehicles").setValue(vehicles + 1);
                    mutableData.child("parkingRevenue").setValue(parkingRevenue + (currentAmount != null ? Long.parseLong(currentAmount) : 0));
                    mutableData.child("activeBookings").setValue(activeBookings + 1);
                } else if (currentTicketId != null) {
                    mutableData.child("tickets").setValue(tickets + 1);
                    mutableData.child("ticketRevenue").setValue(ticketRevenue + (currentAmount != null ? Long.parseLong(currentAmount) : 0));
                    mutableData.child("activeBookings").setValue(activeBookings + 1);
                }

                return com.google.firebase.database.Transaction.success(mutableData);
            }

            @Override
            public void onComplete(DatabaseError databaseError, boolean committed, DataSnapshot dataSnapshot) {
                if (databaseError != null) {
                    Log.e(TAG, "Failed to update real-time data: " + databaseError.getMessage());
                    Toast.makeText(TouristNotificationsActivity.this, "Error updating metrics", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public void onPaymentError(int code, String response) {
        Log.e(TAG, "Payment failed: Code=" + code + ", Response=" + response);
        Toast.makeText(this, "Payment failed: " + response, Toast.LENGTH_LONG).show();

        if (currentPaymentId == null) {
            Log.e(TAG, "Current payment ID is null during failure");
            return;
        }

        for (Notification notification : adapter.notifications) {
            if (notification.getPaymentId() != null && notification.getPaymentId().equals(currentPaymentId)) {
                Map<String, Object> updates = new HashMap<>();
                updates.put("type", "payment_failed");
                updates.put("seen", true);
                notificationsReference.child(notification.getNotificationId())
                        .updateChildren(updates)
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "Notification updated to payment_failed");
                            notification.setType("payment_failed");
                            notification.setSeen(true);
                            cacheNotifications(adapter.notifications);
                            adapter.notifyDataSetChanged();
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to update notification: " + e.getMessage());
                            Toast.makeText(this, "Error updating notification", Toast.LENGTH_SHORT).show();
                        });
                break;
            }
        }
    }

    private String getRelativeTime(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;
        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 7) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        } else if (days > 0) {
            return days + "d ago";
        } else if (hours > 0) {
            return hours + "h ago";
        } else if (minutes > 0) {
            return minutes + "m ago";
        } else {
            return "Just now";
        }
    }

    private void showBottomSheetDialog(Notification notification) {
        if (notification == null || notification.getNotificationId() == null) {
            Toast.makeText(this, "Invalid notification data", Toast.LENGTH_SHORT).show();
            return;
        }
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_notification, null);
        bottomSheetDialog.setContentView(sheetView);

        LinearLayout deleteLayout = sheetView.findViewById(R.id.delete_layout);
        deleteLayout.setOnClickListener(v -> {
            notificationsReference.child(notification.getNotificationId())
                    .removeValue()
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Notification deleted: " + notification.getNotificationId());
                        Toast.makeText(TouristNotificationsActivity.this, "Notification deleted", Toast.LENGTH_SHORT).show();
                        cacheNotifications(adapter.notifications);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to delete notification: " + e.getMessage());
                        Toast.makeText(TouristNotificationsActivity.this, "Failed to delete notification", Toast.LENGTH_SHORT).show();
                    });
            bottomSheetDialog.dismiss();
        });

        bottomSheetDialog.show();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (notificationListener != null) {
            notificationsReference.removeEventListener(notificationListener);
        }
        shimmerLayout.stopShimmer();
    }
}
