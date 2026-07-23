package com.example.bookmyticket.roles.placeadmin;

import com.example.bookmyticket.R;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

class AppNotificationManager {
    private static AppNotificationManager instance;
    private DatabaseReference notificationsRef;
    private MediaPlayer mediaPlayer;
    private boolean isInitialized = false;
    private AppCompatActivity currentActivity;
    private static final String CHANNEL_ID = "placeadmin_notifications";
    private static final String CHANNEL_NAME = "PlaceAdmin Notifications";
    private int notificationId = 0;

    private AppNotificationManager() {}

    public static AppNotificationManager getInstance() {
        if (instance == null) {
            instance = new AppNotificationManager();
        }
        return instance;
    }

    public void initialize(AppCompatActivity activity, String userUid) {
        if (!isInitialized && userUid != null) {
            this.currentActivity = activity;
            notificationsRef = FirebaseDatabase.getInstance().getReference("placeadmin_notifications").child(userUid);
            createNotificationChannel();
            setupNotificationListener();
            isInitialized = true;
        }
    }

    public void setCurrentActivity(AppCompatActivity activity) {
        this.currentActivity = activity;
    }

    public AppCompatActivity getCurrentActivity() {
        return currentActivity;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && currentActivity != null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Notifications for PlaceAdmin updates");
            NotificationManager manager = currentActivity.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void showSystemTrayNotification(String message) {
        if (currentActivity == null || currentActivity.isFinishing()) return;

        currentActivity.runOnUiThread(() -> {
            Intent intent = new Intent(currentActivity, PlaceAdminNotificationsActivity.class);
            intent.putExtra("USER_UID", notificationsRef.getParent().getKey());
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    currentActivity,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            NotificationCompat.Builder builder = new NotificationCompat.Builder(currentActivity, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notifications)
                    .setContentTitle("New Notification")
                    .setContentText(message)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true);

            NotificationManager manager = (NotificationManager) currentActivity.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.notify(notificationId++, builder.build());
            }
        });
    }

    private void playNotificationSound() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (currentActivity != null) {
            Uri notificationSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            mediaPlayer = MediaPlayer.create(currentActivity, notificationSoundUri);
            if (mediaPlayer != null) {
                mediaPlayer.start();
            }
        }
    }

    private void setupNotificationListener() {
        notificationsRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, String previousChildName) {
                String type = snapshot.child("type").getValue(String.class);
                String status = snapshot.child("status").getValue(String.class);
                if (type != null && "SUCCESS".equals(status)) {
                    showSystemTrayNotification("New notification: " + type.replace("_", " "));
                    playNotificationSound();
                }
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, String previousChildName) {}
            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {}
            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, String previousChildName) {}
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (currentActivity != null && !currentActivity.isFinishing()) {
                    currentActivity.runOnUiThread(() -> {
                        Toast toast = Toast.makeText(currentActivity, "Notification error: " + error.getMessage(), Toast.LENGTH_LONG);
                        toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 32);
                        toast.show();
                    });
                }
            }
        });
    }

    public void cleanup() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        currentActivity = null;
        isInitialized = false;
    }
}

public class PlaceAdminNotificationsActivity extends AppCompatActivity {

    private static final String TAG = "PlaceAdminNotificationsActivity";
    private RecyclerView recyclerView;
    private ImageView backButton;
    private NotificationAdapter adapter;
    private DatabaseReference notificationsRef;
    private String userUid;
    private boolean isActivityDestroyed = false;
    private List<Notification> notifications = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_placeadmin_notifications);

        // Setup UI
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        Window window = getWindow();
        window.setStatusBarColor(Color.parseColor("#FFFFFF"));
        window.setNavigationBarColor(Color.parseColor("#FFFFFF"));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Objects.requireNonNull(window.getInsetsController()).setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            );
        }

        // Initialize views
        recyclerView = findViewById(R.id.recyclerViewNotifications);
        backButton = findViewById(R.id.backButton);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotificationAdapter();
        recyclerView.setAdapter(adapter);

        // Process intent and authentication
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        Intent intent = getIntent();
        userUid = intent.getStringExtra("USER_UID");
        if (userUid == null && user != null) {
            userUid = user.getUid(); // Fallback to FirebaseAuth
        }

        // Validate authentication
        if (user == null || userUid == null || !userUid.equals(user.getUid())) {
            showToast("Authentication failed");
            finish();
            return;
        }

        // Initialize Firebase
        notificationsRef = FirebaseDatabase.getInstance().getReference("placeadmin_notifications").child(userUid);

        // Initialize AppNotificationManager
        AppNotificationManager.getInstance().initialize(this, userUid);
        AppNotificationManager.getInstance().setCurrentActivity(this);

        // Load notifications
        loadNotifications();

        // Set listeners
        backButton.setOnClickListener(v -> finish());
    }

    private void loadNotifications() {
        notificationsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isActivityDestroyed) return;
                notifications.clear();
                for (DataSnapshot data : snapshot.getChildren()) {
                    String type = data.child("type").getValue(String.class);
                    String details = data.child("details").getValue(String.class);
                    String status = data.child("status").getValue(String.class);
                    Long timestamp = data.child("timestamp").getValue(Long.class);
                    String userId = data.child("userId").getValue(String.class);
                    String key = data.getKey();
                    if (type != null && timestamp != null && status != null && "SUCCESS".equals(status)) {
                        notifications.add(new Notification(type, details, timestamp, userId, key));
                    }
                }
                Collections.sort(notifications, (n1, n2) -> Long.compare(n2.timestamp, n1.timestamp));
                adapter.setNotifications(notifications);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (isActivityDestroyed) return;
                showToast("Failed to load notifications: " + error.getMessage());
            }
        });
    }

    private void showToast(String message) {
        if (isActivityDestroyed) return;
        View toastView = getLayoutInflater().inflate(R.layout.custom_toast1, null);
        TextView toastText = toastView.findViewById(R.id.toast_message);
        ImageView toastIcon = toastView.findViewById(R.id.toast_icon);
        toastText.setText(message);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            toastText.setTypeface(getResources().getFont(R.font.poppins_regular));
        }
        toastView.setBackgroundColor(getResources().getColor(R.color.success_green));
        toastIcon.setImageResource(R.drawable.ic_success);

        Toast toast = new Toast(this);
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setView(toastView);
        toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 32);
        toast.show();
    }

    private static class Notification {
        String type, details, userId, key;
        long timestamp;

        Notification(String type, String details, long timestamp, String userId, String key) {
            this.type = type;
            this.details = details != null ? details : "N/A";
            this.timestamp = timestamp;
            this.userId = userId != null ? userId : "N/A";
            this.key = key;
        }
    }

    private class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {
        private List<Notification> notifications = new ArrayList<>();

        void setNotifications(List<Notification> notifications) {
            this.notifications = notifications;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_placeadmin_notification, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Notification notification = notifications.get(position);
            holder.bind(notification);
        }

        @Override
        public int getItemCount() {
            return notifications.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView textType, textDetails, textTimestamp;
            ImageView menuButton, notificationIcon;

            ViewHolder(View itemView) {
                super(itemView);
                textType = itemView.findViewById(R.id.textType);
                textDetails = itemView.findViewById(R.id.textDetails);
                textTimestamp = itemView.findViewById(R.id.textTimestamp);
                menuButton = itemView.findViewById(R.id.menuButton);
                notificationIcon = itemView.findViewById(R.id.notificationIcon);

                // Make the entire item clickable to show full notification
                itemView.setOnClickListener(v -> {
                    Notification notification = notifications.get(getAdapterPosition());
                    showFullNotificationDialog(notification);
                });
            }

            void bind(Notification notification) {
                textType.setText(notification.type.replace("_", " "));
                textDetails.setText(notification.details);
                textDetails.setMaxLines(2); // Match item_placeadmin_notification.xml
                textDetails.setEllipsize(android.text.TextUtils.TruncateAt.END);
                textTimestamp.setText(formatTimestamp(notification.timestamp));
                menuButton.setOnClickListener(v -> showBottomSheet(notification));

                // Set icon based on notification type
                switch (notification.type) {
                    case "BANK_DETAILS_UPDATED":
                        notificationIcon.setImageResource(R.drawable.ic_bank);
                        break;
                    case "QR_UPDATED":
                    case "QR_GENERATED":
                    case "QR_DELETED":
                        notificationIcon.setImageResource(R.drawable.ic_generate_qr);
                        break;
                    case "PAYOUT":
                        notificationIcon.setImageResource(R.drawable.ic_revenue);
                        break;
                    case "PROFILE_UPDATED":
                        notificationIcon.setImageResource(R.drawable.ic_profile);
                        break;
                    default:
                        notificationIcon.setImageResource(R.drawable.ic_notifications);
                        break;
                }
            }

            private String formatTimestamp(long timestamp) {
                Calendar now = Calendar.getInstance();
                Calendar notificationTime = Calendar.getInstance();
                notificationTime.setTimeInMillis(timestamp);

                SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
                if (now.get(Calendar.YEAR) == notificationTime.get(Calendar.YEAR) &&
                        now.get(Calendar.DAY_OF_YEAR) == notificationTime.get(Calendar.DAY_OF_YEAR)) {
                    return "Today, " + timeFormat.format(new Date(timestamp));
                } else if (now.get(Calendar.YEAR) == notificationTime.get(Calendar.YEAR) &&
                        now.get(Calendar.DAY_OF_YEAR) - notificationTime.get(Calendar.DAY_OF_YEAR) == 1) {
                    return "Yesterday, " + timeFormat.format(new Date(timestamp));
                } else {
                    SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault());
                    return dateFormat.format(new Date(timestamp));
                }
            }

            private void showBottomSheet(Notification notification) {
                BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(PlaceAdminNotificationsActivity.this);
                View bottomSheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_notification, null);
                bottomSheetDialog.setContentView(bottomSheetView);

                LinearLayout deleteLayout = bottomSheetView.findViewById(R.id.delete_layout);
                deleteLayout.setOnClickListener(v -> {
                    notificationsRef.child(notification.key).removeValue((error, ref) -> {
                        if (error == null) {
                            showToast("Notification deleted");
                        } else {
                            showToast("Failed to delete notification: " + error.getMessage());
                        }
                    });
                    bottomSheetDialog.dismiss();
                });

                bottomSheetDialog.show();
            }

            private void showFullNotificationDialog(Notification notification) {
                Dialog dialog = new Dialog(PlaceAdminNotificationsActivity.this, android.R.style.Theme_Black_NoTitleBar);
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                dialog.setContentView(R.layout.dialog_full_notification);

                // Set dialog background to semi-transparent for blur effect
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.argb(200, 0, 0, 0)));

                TextView dialogTitle = dialog.findViewById(R.id.textType);
                TextView dialogDetails = dialog.findViewById(R.id.textDetails);
                TextView dialogTimestamp = dialog.findViewById(R.id.textTimestamp);
                ImageView dialogCloseButton = dialog.findViewById(R.id.dialog_close_button);
                ImageView dialogNotificationIcon = dialog.findViewById(R.id.notificationIcon);

                dialogTitle.setText(notification.type.replace("_", " "));
                dialogDetails.setText(notification.details);
                dialogTimestamp.setText(formatTimestamp(notification.timestamp));

                // Set icon based on notification type
                switch (notification.type) {
                    case "BANK_DETAILS_UPDATED":
                        dialogNotificationIcon.setImageResource(R.drawable.ic_bank);
                        break;
                    case "QR_UPDATED":
                    case "QR_GENERATED":
                    case "QR_DELETED":
                        dialogNotificationIcon.setImageResource(R.drawable.ic_generate_qr);
                        break;
                    case "PAYOUT":
                        dialogNotificationIcon.setImageResource(R.drawable.ic_revenue);
                        break;
                    case "PROFILE_UPDATED":
                        dialogNotificationIcon.setImageResource(R.drawable.ic_profile);
                        break;
                    default:
                        dialogNotificationIcon.setImageResource(R.drawable.ic_notifications);
                        break;
                }

                dialogCloseButton.setOnClickListener(v -> dialog.dismiss());

                dialog.show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppNotificationManager.getInstance().setCurrentActivity(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        AppNotificationManager.getInstance().setCurrentActivity(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isActivityDestroyed = true;
        if (AppNotificationManager.getInstance().getCurrentActivity() == this) {
            AppNotificationManager.getInstance().cleanup();
        }
    }
}
