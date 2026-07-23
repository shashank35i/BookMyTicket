package com.example.bookmyticket.roles.tourist;

import com.example.bookmyticket.R;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;

public class FullScreenQRActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_full_screen_qr);
        setupWindow();
        loadQRImage();
    }

    private void setupWindow() {
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    private void loadQRImage() {
        ImageView qrImage = findViewById(R.id.fullScreenImage);
        ImageView btnBack = findViewById(R.id.btnBack);

        String imagePath = getIntent().getStringExtra("image_path");
        if (imagePath != null) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 1; // Full quality for full screen
            Bitmap bitmap = BitmapFactory.decodeFile(imagePath, options);
            qrImage.setImageBitmap(bitmap);
        }

        btnBack.setOnClickListener(v -> supportFinishAfterTransition());
    }

    @Override
    public void onBackPressed() {
        supportFinishAfterTransition();
    }
}
