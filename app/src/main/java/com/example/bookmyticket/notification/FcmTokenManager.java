package com.example.bookmyticket.notification;

import android.util.Log;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.messaging.FirebaseMessaging;

public class FcmTokenManager {
    private static final String TAG = "FcmTokenManager";

    public static void updateFcmToken() {
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                String token = task.getResult();
                String userId = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
                if (userId != null) {
                    FirebaseDatabase.getInstance()
                            .getReference("users")
                            .child(userId)
                            .child("fcmToken")
                            .setValue(token)
                            .addOnSuccessListener(aVoid -> Log.d(TAG, "FCM token updated: " + token))
                            .addOnFailureListener(e -> Log.e(TAG, "Failed to update FCM token: " + e.getMessage()));
                }
            } else {
                Log.e(TAG, "Failed to get FCM token: " + task.getException().getMessage());
            }
        });
    }
}
