package com.example.bookmyticket.notification;

import com.example.bookmyticket.R;
import com.example.bookmyticket.roles.placeadmin.PlaceAdminNotificationsActivity;
import com.example.bookmyticket.roles.tourist.TouristNotificationsActivity;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class MyFirebaseMessagingService extends FirebaseMessagingService {
    private static final String CHANNEL_ID = "payment_notifications";
    private static final String CHANNEL_NAME = "Payment Notifications";
    private static final int SYSTEM_NOTIFICATION_REQUEST_CODE = 200;

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        // Create notification channel (same as NotificationService)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
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

        // Extract notification data
        String title = remoteMessage.getNotification().getTitle();
        String body = remoteMessage.getNotification().getBody();
        String type = remoteMessage.getData().get("type");
        String userId = remoteMessage.getData().get("userId");
        String notificationId = remoteMessage.getData().get("notificationId");
        String vehicleNumber = remoteMessage.getData().get("vehicleNumber");
        String ticketId = remoteMessage.getData().get("ticketId");
        String place = remoteMessage.getData().get("place");
        String amount = remoteMessage.getData().get("amount");

        // Determine target activity
        Intent intent;
        if (type != null && type.contains("payment")) {
            intent = new Intent(this, TouristNotificationsActivity.class);
        } else {
            intent = new Intent(this, PlaceAdminNotificationsActivity.class);
        }
        intent.putExtra("USER_UID", userId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                SYSTEM_NOTIFICATION_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        // Build notification (match NotificationService logic)
        boolean isVehicleNotification = vehicleNumber != null && !vehicleNumber.isEmpty();
        String finalTitle = title != null ? title : (isVehicleNotification ?
                (type.equals("payment_request") ? "New Payment Request" :
                        type.equals("payment_success") ? "Payment Successful" : "Payment Failed") :
                (type.equals("payment_request") ? "New Ticket Payment Request" :
                        type.equals("payment_success") ? "Ticket Payment Successful" : "Ticket Payment Failed"));
        String finalBody = body != null ? body : (isVehicleNotification ?
                (type.equals("payment_request") ?
                        "Pending payment for " + (vehicleNumber != null ? vehicleNumber : "Unknown") :
                        type.equals("payment_success") ?
                                "Payment of ₹" + (amount != null ? amount : "0") + " for " + (vehicleNumber != null ? vehicleNumber : "Unknown") + " completed" :
                                "Payment failed for " + (vehicleNumber != null ? vehicleNumber : "Unknown")) :
                (type.equals("payment_request") ?
                        "Pending payment for ticket " + (ticketId != null ? ticketId : "Unknown") + " at " + (place != null ? place : "Unknown") :
                        type.equals("payment_success") ?
                                "Payment of ₹" + (amount != null ? amount : "0") + " for ticket " + (ticketId != null ? ticketId : "Unknown") + " at " + (place != null ? place : "Unknown") + " completed" :
                                "Payment failed for ticket " + (ticketId != null ? ticketId : "Unknown") + " at " + (place != null ? place : "Unknown")));

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notifications)
                .setContentTitle(finalTitle)
                .setContentText(finalBody)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
                .setDefaults(NotificationCompat.DEFAULT_VIBRATE | NotificationCompat.DEFAULT_LIGHTS);

        // Show notification
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(notificationId != null ? notificationId.hashCode() : (int) System.currentTimeMillis(), builder.build());
        }
    }

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        FcmTokenManager.updateFcmToken();
    }
}
