package com.example.bookmyticket.notification;

import com.example.bookmyticket.R;
import com.example.bookmyticket.roles.tourist.TouristNotificationsActivity;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.Manifest;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class NotificationService extends Service {
    private static final String TAG = "NotificationService";
    private static final String NOTIFICATION_CHANNEL_ID = "payment_notifications";
    private static final int SERVICE_NOTIFICATION_ID = 1;
    private static final int SYSTEM_NOTIFICATION_REQUEST_CODE = 200;

    private DatabaseReference notificationsReference;
    private ChildEventListener notificationListener;
    private FirebaseUser currentUser;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(SERVICE_NOTIFICATION_ID, buildForegroundNotification().build());
        initializeFirebase();
        setupNotificationListener();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
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

    private NotificationCompat.Builder buildForegroundNotification() {
        Intent intent = new Intent(this, TouristNotificationsActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                SYSTEM_NOTIFICATION_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notifications)
                .setContentTitle("BookMyTicket")
                .setContentText("Monitoring payment notifications")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .setOngoing(true);
    }

    private void initializeFirebase() {
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "User not logged in, stopping service");
            stopSelf();
            return;
        }
        notificationsReference = FirebaseDatabase.getInstance()
                .getReference("tourist_notifications")
                .child(currentUser.getUid());
        notificationsReference.keepSynced(true);
    }

    private void setupNotificationListener() {
        notificationListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, String previousChildName) {
                TouristNotificationsActivity.Notification notification = snapshot.getValue(TouristNotificationsActivity.Notification.class);
                if (notification != null && snapshot.getKey() != null) {
                    notification.setNotificationId(snapshot.getKey());
                    Boolean seen = snapshot.child("seen").getValue(Boolean.class);
                    notification.setSeen(seen != null && seen);
                    if (!notification.isSeen()) {
                        showSystemNotification(notification);
                        markNotificationAsSeen(notification);
                    }
                }
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, String previousChildName) {
                TouristNotificationsActivity.Notification updatedNotification = snapshot.getValue(TouristNotificationsActivity.Notification.class);
                if (updatedNotification != null && snapshot.getKey() != null) {
                    updatedNotification.setNotificationId(snapshot.getKey());
                    Boolean seen = snapshot.child("seen").getValue(Boolean.class);
                    updatedNotification.setSeen(seen != null && seen);
                    if (!updatedNotification.isSeen()) {
                        showSystemNotification(updatedNotification);
                        markNotificationAsSeen(updatedNotification);
                    }
                }
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                String removedId = snapshot.getKey();
                NotificationManagerCompat.from(NotificationService.this)
                        .cancel(removedId.hashCode());
            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, String previousChildName) {}

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Notification listener cancelled: " + error.getMessage());
            }
        };
        notificationsReference.addChildEventListener(notificationListener);
    }

    private void showSystemNotification(TouristNotificationsActivity.Notification notification) {
        Intent intent = new Intent(this, TouristNotificationsActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                SYSTEM_NOTIFICATION_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        boolean isVehicleNotification = notification.getVehicleNumber() != null && notification.getVehicleType() != null;
        String title = isVehicleNotification ?
                (notification.getType().equals("payment_request") ? "New Payment Request" :
                        notification.getType().equals("payment_success") ? "Payment Successful" : "Payment Failed") :
                (notification.getType().equals("payment_request") ? "New Ticket Payment Request" :
                        notification.getType().equals("payment_success") ? "Ticket Payment Successful" : "Ticket Payment Failed");
        String message = isVehicleNotification ?
                (notification.getType().equals("payment_request") ?
                        "Pending payment for " + (notification.getVehicleNumber() != null ? notification.getVehicleNumber() : "Unknown") :
                        notification.getType().equals("payment_success") ?
                                "Payment of ₹" + (notification.getAmount() != null ? notification.getAmount() : "0") + " for " + (notification.getVehicleNumber() != null ? notification.getVehicleNumber() : "Unknown") + " completed" :
                                "Payment failed for " + (notification.getVehicleNumber() != null ? notification.getVehicleNumber() : "Unknown")) :
                (notification.getType().equals("payment_request") ?
                        "Pending payment for ticket " + (notification.getTicketId() != null ? notification.getTicketId() : "Unknown") + " at " + (notification.getPlace() != null ? notification.getPlace() : "Unknown") :
                        notification.getType().equals("payment_success") ?
                                "Payment of ₹" + (notification.getAmount() != null ? notification.getAmount() : "0") + " for ticket " + (notification.getTicketId() != null ? notification.getTicketId() : "Unknown") + " at " + (notification.getPlace() != null ? notification.getPlace() : "Unknown") + " completed" :
                                "Payment failed for ticket " + (notification.getTicketId() != null ? notification.getTicketId() : "Unknown") + " at " + (notification.getPlace() != null ? notification.getPlace() : "Unknown"));

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notifications)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
                .setDefaults(NotificationCompat.DEFAULT_VIBRATE | NotificationCompat.DEFAULT_LIGHTS);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)) {
            notificationManager.notify(notification.getNotificationId().hashCode(), builder.build());
        } else {
            Log.w(TAG, "Notification permission not granted, skipping system notification");
        }
    }

    private void markNotificationAsSeen(TouristNotificationsActivity.Notification notification) {
        if (notification == null || notification.getNotificationId() == null) {
            return;
        }
        notificationsReference.child(notification.getNotificationId()).child("seen").setValue(true)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Marked notification as seen: " + notification.getNotificationId()))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to mark notification as seen: " + e.getMessage()));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (notificationListener != null) {
            notificationsReference.removeEventListener(notificationListener);
        }
        stopForeground(true);
    }
}
