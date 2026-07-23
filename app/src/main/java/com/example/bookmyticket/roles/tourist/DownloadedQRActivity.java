package com.example.bookmyticket.roles.tourist;

import com.example.bookmyticket.R;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DownloadedQRActivity extends AppCompatActivity {
    private LinearLayout qrContainer;
    private ImageView backButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_downloaded_qr);

        qrContainer = findViewById(R.id.qrContainer);
        backButton = findViewById(R.id.backButton);

        backButton.setOnClickListener(v -> onBackPressed());
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setSystemColors();
        loadDownloadedQRCodes();
    }

    private void loadDownloadedQRCodes() {
        File directory = new File(getExternalFilesDir(null), "DownloadedQRCodes");
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null && files.length > 0) {
                for (File file : files) {
                    addQRImageToView(file);
                }
            } else {
                Toast.makeText(this, "No downloaded QR codes found!", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "QR code directory does not exist!", Toast.LENGTH_SHORT).show();
        }
    }
    private void setSystemColors() {
        Window window = getWindow();
        window.setStatusBarColor(Color.parseColor("#FFFFFF"));
        window.setNavigationBarColor(Color.parseColor("#FFFFFF"));

        // Ensure status bar text/icons are visible (black)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.getInsetsController().setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            );
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            );
        }
    }
    private void addQRImageToView(File file) {
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());

        if (bitmap != null) {
            LinearLayout qrItemLayout = new LinearLayout(this);
            qrItemLayout.setOrientation(LinearLayout.VERTICAL);
            qrItemLayout.setPadding(16, 16, 16, 16);
            qrItemLayout.setGravity(android.view.Gravity.CENTER); // Center align the layout

            // QR Image
            ImageView imageView = new ImageView(this);
            imageView.setImageBitmap(Bitmap.createScaledBitmap(bitmap, 400, 400, false)); // Increased size
            LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            imageParams.gravity = android.view.Gravity.CENTER; // Center the image
            imageView.setLayoutParams(imageParams);
            imageView.setAdjustViewBounds(true);

            // Date & Time Text
            TextView dateTextView = new TextView(this);
            dateTextView.setText("Downloaded on: " + getFormattedDate(file.lastModified()));
            dateTextView.setTextSize(16); // Slightly larger text size
            dateTextView.setTextColor(Color.BLACK);
            dateTextView.setPadding(0, 12, 0, 0); // Increased padding
            dateTextView.setGravity(android.view.Gravity.CENTER); // Center the text

            // Add to container
            qrItemLayout.addView(imageView);
            qrItemLayout.addView(dateTextView);

            LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            containerParams.gravity = android.view.Gravity.CENTER; // Center the entire QR item layout
            qrItemLayout.setLayoutParams(containerParams);

            qrContainer.addView(qrItemLayout);
        }
    }



    private String getFormattedDate(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }
}
