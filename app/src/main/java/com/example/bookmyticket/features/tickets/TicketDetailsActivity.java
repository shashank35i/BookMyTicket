package com.example.bookmyticket.features.tickets;

import com.example.bookmyticket.R;
import com.example.bookmyticket.model.TouristTicket;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TicketDetailsActivity extends AppCompatActivity {

    private static final String TAG = "TicketDetailsActivity";
    private ImageView imgQRCode;
    private ImageView backButton;
    private DecodeQRTask decodeQRTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ticket_details);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        initViews();
        setupBackButton();
        loadTicketData();
        setupSystemUI();
    }

    @Override
    protected void onDestroy() {
        if (decodeQRTask != null) {
            decodeQRTask.cancel(true);
        }
        super.onDestroy();
    }

    private void setupSystemUI() {
        Window window = getWindow();
        window.setStatusBarColor(Color.WHITE);
        window.setNavigationBarColor(Color.WHITE);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.getInsetsController().setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            );
        } else {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    private void initViews() {
        imgQRCode = findViewById(R.id.imgQRCode);
        backButton = findViewById(R.id.back_button);
    }

    private void setupBackButton() {
        if (backButton != null) {
            backButton.setOnClickListener(v -> onBackPressed());
        }
    }

    private void loadTicketData() {
        TouristTicket ticket = (TouristTicket) getIntent().getSerializableExtra("TICKET_DATA");
        if (ticket == null) {
            finish();
            return;
        }

        // Set basic info
        setText(R.id.txtTicketId, "#" + ticket.getTicketId());
        setText(R.id.txtPlace, ticket.getPlace());
        setText(R.id.txtDate, ticket.getDate());
        setText(R.id.txtTotalAmount, "₹" + ticket.getTotalAmount());

        // Load QR code
        Log.d(TAG, "Original QR string: " + ticket.getQrImage());
        decodeQRTask = new DecodeQRTask(imgQRCode);
        decodeQRTask.execute(ticket.getQrImage());

        // Set validity period
        setValidityPeriod(ticket);

        // Set visitor info
        setVisitorInfo(ticket);

        // Set additional info
        setAdditionalInfo(ticket);
    }

    private void setText(int viewId, String text) {
        TextView textView = findViewById(viewId);
        if (textView != null) {
            textView.setText(text != null ? text : "");
        }
    }

    private void setValidityPeriod(TouristTicket ticket) {
        TextView txtTime = findViewById(R.id.txtTime);
        if (txtTime == null) {
            Log.e(TAG, "txtTime TextView is null");
            return;
        }

        // Log ticket values for debugging
        Log.d(TAG, "TouristTicket values - validFrom: " + ticket.getValidFrom() +
                ", validTo: " + ticket.getValidTo() + ", time: " + ticket.getTime());

        if (ticket.getValidFrom() != null && !ticket.getValidFrom().isEmpty() &&
                ticket.getValidTo() != null && !ticket.getValidTo().isEmpty()) {
            txtTime.setText(String.format("%s to %s", ticket.getValidFrom(), ticket.getValidTo()));
        } else {
            // Fallback to ticket.getTime() or a default value
            SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
            String fallbackFrom = sdf.format(new Date(System.currentTimeMillis()));
            String fallbackTo = sdf.format(new Date(System.currentTimeMillis() + 3 * 60 * 60 * 1000));
            txtTime.setText(String.format("%s to %s", fallbackFrom, fallbackTo));
            Log.w(TAG, "validFrom or validTo is null/empty, using fallback: " +
                    fallbackFrom + " to " + fallbackTo);
        }
    }

    private void setVisitorInfo(TouristTicket ticket) {
        TextView txtTotalVisitors = findViewById(R.id.txtTotalVisitors);
        TextView txtVisitorDetails = findViewById(R.id.txtVisitorDetails);
        View cameraContainer = findViewById(R.id.cameraContainer);
        TextView txtCameraCount = findViewById(R.id.txtCameraCount);

        // Calculate total visitors
        int totalVisitors = ticket.getAdults() + ticket.getStudents() + ticket.getSeniorCitizens() + ticket.getChildren();

        // Set total visitors
        if (txtTotalVisitors != null) {
            txtTotalVisitors.setText(totalVisitors + (totalVisitors == 1 ? " Visitor" : " Visitors"));
        }

        // Set detailed visitor breakdown
        StringBuilder visitorDetails = new StringBuilder();
        if (ticket.getAdults() > 0) {
            visitorDetails.append(ticket.getAdults()).append(ticket.getAdults() > 1 ? " Adults" : " Adult");
        }
        if (ticket.getStudents() > 0) {
            if (visitorDetails.length() > 0) visitorDetails.append(", ");
            visitorDetails.append(ticket.getStudents()).append(ticket.getStudents() > 1 ? " Students" : " Student");
        }
        if (ticket.getChildren() > 0) {
            if (visitorDetails.length() > 0) visitorDetails.append(", ");
            visitorDetails.append(ticket.getChildren()).append(ticket.getChildren() > 1 ? " Children" : " Child");
        }
        if (ticket.getSeniorCitizens() > 0) {
            if (visitorDetails.length() > 0) visitorDetails.append(", ");
            visitorDetails.append(ticket.getSeniorCitizens()).append(ticket.getSeniorCitizens() > 1 ? " Senior Citizens" : " Senior Citizen");
        }

        if (txtVisitorDetails != null) {
            txtVisitorDetails.setText(visitorDetails.length() > 0 ? visitorDetails.toString() : "");
        }

        // Camera info
        if (ticket.getCameras() > 0 && txtCameraCount != null) {
            txtCameraCount.setText(ticket.getCameras() + (ticket.getCameras() == 1 ? " Camera" : " Cameras"));
            if (cameraContainer != null) {
                cameraContainer.setVisibility(View.VISIBLE);
            }
        } else if (cameraContainer != null) {
            cameraContainer.setVisibility(View.GONE);
        }
    }

    private void setAdditionalInfo(TouristTicket ticket) {
        TextView txtInstructions = findViewById(R.id.txtInstructions);
        TextView txtHelpline = findViewById(R.id.txtHelpline);

        if (txtInstructions != null) {
            if (ticket.getInstructions() != null && !ticket.getInstructions().isEmpty()) {
                txtInstructions.setText(ticket.getInstructions());
                txtInstructions.setVisibility(View.VISIBLE);
            } else {
                txtInstructions.setVisibility(View.GONE);
            }
        }

        if (txtHelpline != null) {
            if (ticket.getHelpline() != null && !ticket.getHelpline().isEmpty()) {
                txtHelpline.setText(ticket.getHelpline());
                txtHelpline.setVisibility(View.VISIBLE);
            } else {
                txtHelpline.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    private static class DecodeQRTask extends AsyncTask<String, Void, Bitmap> {
        private final WeakReference<ImageView> imageViewReference;

        DecodeQRTask(ImageView imageView) {
            this.imageViewReference = new WeakReference<>(imageView);
        }

        @Override
        protected Bitmap doInBackground(String... qrStrings) {
            if (isCancelled() || qrStrings == null || qrStrings.length == 0 || qrStrings[0] == null) {
                Log.e(TAG, "Invalid input parameters");
                return null;
            }

            String qrImageString = qrStrings[0];
            try {
                byte[] decodedBytes = Base64.decode(qrImageString, Base64.DEFAULT);

                if (isCancelled() || decodedBytes == null || decodedBytes.length == 0) {
                    Log.e(TAG, "Failed to decode Base64 or empty result");
                    return null;
                }

                return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);

            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Base64 decoding error", e);
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error decoding QR", e);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (isCancelled()) {
                return;
            }

            ImageView imageView = imageViewReference.get();
            if (imageView == null || imageView.getContext() == null) {
                return;
            }

            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
                Log.d(TAG, "QR code displayed successfully");
            } else {
                imageView.setImageResource(R.drawable.ic_qr_placeholder);
                Log.e(TAG, "Failed to display QR code - using placeholder");
            }
        }
    }
}
