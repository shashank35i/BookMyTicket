package com.example.bookmyticket.roles.parkingadmin;

import com.example.bookmyticket.R;
import com.example.bookmyticket.model.Vehicle;

import android.animation.LayoutTransition;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ParkingSettingsActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private TextView emptyStateText;
    private SwipeRefreshLayout swipeRefreshLayout;
    private NotificationAdapter adapter;
    private List<Notification> notifications;
    private DatabaseReference db;
    private FirebaseAuth auth;

    // Notification model
    private static class Notification {
        private String id; // For deletion
        private String amount;
        private String paymentId;
        private long timestamp;
        private String type;
        private String userUid;
        private String vehicleNumber;
        private String status; // pending or completed
        private boolean isExpanded; // Added to track dropdown state

        Notification(String id, String amount, String paymentId, long timestamp, String type, String userUid, String vehicleNumber, String status) {
            this.id = id != null ? id : "N/A";
            this.amount = amount != null ? amount : "N/A";
            this.paymentId = paymentId != null ? paymentId : "N/A";
            this.timestamp = timestamp;
            this.type = type != null ? type : "Unknown";
            this.userUid = userUid != null ? userUid : "N/A";
            this.vehicleNumber = vehicleNumber != null ? vehicleNumber : "N/A";
            this.status = status != null ? status : "pending";
            this.isExpanded = false; // Default to collapsed
        }

        String getId() { return id; }
        String getAmount() { return amount; }
        String getPaymentId() { return paymentId; }
        long getTimestamp() { return timestamp; }
        String getType() { return type; }
        String getUserUid() { return userUid; }
        String getVehicleNumber() { return vehicleNumber; }
        String getStatus() { return status; }
    }

    // RecyclerView Adapter
    private static class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder> {
        private List<Notification> notifications;
        private Context context;
        private DatabaseReference db;

        NotificationAdapter(Context context, List<Notification> notifications, DatabaseReference db) {
            this.context = context;
            this.notifications = notifications;
            this.db = db;
        }

        @Override
        public NotificationViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_settings, parent, false);
            return new NotificationViewHolder(view);
        }

        @Override
        public void onBindViewHolder(NotificationViewHolder holder, int position) {
            Notification notification = notifications.get(position);
            if (holder.title != null) {
                holder.title.setText("Payment Request");
            }
            if (holder.description != null) {
                String description = notification.getStatus().equals("completed")
                        ? "Payment is done for vehicle " + notification.getVehicleNumber()
                        : "Payment request sent for vehicle " + notification.getVehicleNumber();
                holder.description.setText(description);
            }
            if (holder.timestamp != null) {
                holder.timestamp.setText(formatTimestamp(notification.getTimestamp()));
            }
            if (holder.detailsLayout != null) {
                holder.detailsLayout.setVisibility(notification.isExpanded ? View.VISIBLE : View.GONE);
                if (holder.userUidText != null) {
                    holder.userUidText.setText("User ID: " + notification.getUserUid());
                }
                if (holder.amountText != null) {
                    holder.amountText.setText("Amount: $" + notification.getAmount());
                }
                if (holder.vehicleNumberText != null) {
                    holder.vehicleNumberText.setText("Vehicle Number: " + notification.getVehicleNumber());
                }
                // Enable smooth animation for dropdown
                LayoutTransition transition = holder.detailsLayout.getLayoutTransition();
                if (transition != null) {
                    transition.enableTransitionType(LayoutTransition.CHANGING);
                }
            }
            // Card click to toggle dropdown
            if (holder.notificationCard != null) {
                holder.notificationCard.setOnClickListener(v -> {
                    notification.isExpanded = !notification.isExpanded;
                    notifyItemChanged(position);
                });
            }
            // Three dots click to show bottom sheet
            if (holder.threeDotsButton != null) {
                holder.threeDotsButton.setOnClickListener(v -> showBottomSheet(notification, position));
            }
        }

        @Override
        public int getItemCount() {
            return notifications.size();
        }

        private String formatTimestamp(long timestamp) {
            if (timestamp == 0) return "Unknown time";
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        }

        private void showBottomSheet(Notification notification, int position) {
            BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(context);
            View bottomSheetView = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_notification, null);
            bottomSheetDialog.setContentView(bottomSheetView);

            LinearLayout deleteLayout = bottomSheetView.findViewById(R.id.delete_layout);
            if (deleteLayout != null) {
                deleteLayout.setOnClickListener(v -> {
                    deleteNotification(notification.getId(), position);
                    bottomSheetDialog.dismiss();
                });
            }
            bottomSheetDialog.show();
        }

        private void deleteNotification(String notificationId, int position) {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                Toast.makeText(context, "User not logged in", Toast.LENGTH_SHORT).show();
                return;
            }
            db.child("admin_notifications").child(user.getUid()).child(notificationId)
                    .removeValue((error, ref) -> {
                        if (error == null) {
                            notifications.remove(position);
                            notifyItemRemoved(position);
                            notifyItemRangeChanged(position, notifications.size());
                            Toast.makeText(context, "Notification deleted", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(context, "Failed to delete notification: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
        }

        static class NotificationViewHolder extends RecyclerView.ViewHolder {
            TextView title, description, timestamp, userUidText, amountText, vehicleNumberText;
            LinearLayout detailsLayout;
            androidx.cardview.widget.CardView notificationCard;
            ImageButton threeDotsButton;

            NotificationViewHolder(View itemView) {
                super(itemView);
                notificationCard = itemView.findViewById(R.id.notificationCard);
                title = itemView.findViewById(R.id.notificationTitle);
                description = itemView.findViewById(R.id.notificationDescription);
                timestamp = itemView.findViewById(R.id.notificationTimestamp);
                detailsLayout = itemView.findViewById(R.id.detailsLayout);
                userUidText = itemView.findViewById(R.id.userUidText);
                amountText = itemView.findViewById(R.id.amountText);
                vehicleNumberText = itemView.findViewById(R.id.vehicleNumberText);
                threeDotsButton = itemView.findViewById(R.id.threeDotsButton);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parking_settings);
        // Set status and navigation bar colors
        Window window = getWindow();
        window.setStatusBarColor(0xFFFFFFFF);
        window.setNavigationBarColor(0xFFFFFFFF);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.getInsetsController().setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            );
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            );
        }

        // Hide ActionBar
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Initialize Firebase
        db = FirebaseDatabase.getInstance("https://bookmyticket-a8b92-default-rtdb.firebaseio.com/").getReference();
        auth = FirebaseAuth.getInstance();

        // Initialize UI components
        recyclerView = findViewById(R.id.notificationsRecyclerView);
        emptyStateText = findViewById(R.id.emptyStateText);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        ImageButton backButton = findViewById(R.id.backButton);

        // Set up RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        notifications = new ArrayList<>();
        adapter = new NotificationAdapter(this, notifications, db);
        recyclerView.setAdapter(adapter);

        // Set up pull-to-refresh
        swipeRefreshLayout.setColorSchemeColors(
                0xFF2196F3, // Blue
                0xFF4CAF50, // Green
                0xFFFF9800, // Orange
                0xFFF44336  // Red
        );
        swipeRefreshLayout.setOnRefreshListener(this::fetchNotifications);

        // Set up back button
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        // Fetch notifications
        fetchNotifications();
    }

    private void fetchNotifications() {
        swipeRefreshLayout.setRefreshing(true);
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            emptyStateText.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            swipeRefreshLayout.setRefreshing(false);
            return;
        }

        String adminUid = user.getUid();
        db.child("admin_notifications").child(adminUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        notifications.clear();
                        for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                            try {
                                String id = snapshot.getKey();
                                String amount = snapshot.child("amount").getValue(String.class);
                                String paymentId = snapshot.child("paymentId").getValue(String.class);
                                Long timestamp = snapshot.child("timestamp").getValue(Long.class);
                                String type = snapshot.child("type").getValue(String.class);
                                String userUid = snapshot.child("userUid").getValue(String.class);
                                String vehicleNumber = snapshot.child("vehicleNumber").getValue(String.class);
                                String status = snapshot.child("status").getValue(String.class);
                                if (type != null && type.equals("payment_request_sent")) {
                                    notifications.add(new Notification(
                                            id,
                                            amount,
                                            paymentId,
                                            timestamp != null ? timestamp : 0,
                                            type,
                                            userUid,
                                            vehicleNumber,
                                            status
                                    ));
                                }
                            } catch (Exception e) {
                                // Skip malformed data
                                continue;
                            }
                        }
                        updateUI();
                        showSystemTrayNotification(notifications.size());
                        swipeRefreshLayout.setRefreshing(false);
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Toast.makeText(ParkingSettingsActivity.this, "Failed to fetch notifications: " + databaseError.getMessage(), Toast.LENGTH_SHORT).show();
                        updateUI();
                        swipeRefreshLayout.setRefreshing(false);
                    }
                });
    }

    private void updateUI() {
        if (notifications.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyStateText.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyStateText.setVisibility(View.GONE);
            adapter.notifyDataSetChanged();
        }
    }

    private void showSystemTrayNotification(int count) {
        if (count == 0) return;

        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        String channelId = "parking_notifications";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Parking Notifications", NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.initial)
                .setContentTitle("New Parking Notifications")
                .setContentText("You have " + count + " new notification(s)")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        notificationManager.notify(1, builder.build());
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetchNotifications();
    }
}
