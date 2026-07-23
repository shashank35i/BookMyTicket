package com.example.bookmyticket.core;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import androidx.camera.lifecycle.ProcessCameraProvider;

import com.google.firebase.database.FirebaseDatabase;
import com.razorpay.Checkout;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);
        super.onCreate();
        ProcessCameraProvider.getInstance(this);
        Handler mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.post(() -> {
            Checkout.preload(this);
            Checkout.clearUserData(this);
        });
    }
}
