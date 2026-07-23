package com.example.bookmyticket.roles.tourist;

import com.example.bookmyticket.R;
import com.example.bookmyticket.features.scanner.QRScannerActivity;
import com.example.bookmyticket.features.tickets.TicketDetailsActivity;
import com.example.bookmyticket.model.Ticket;
import com.example.bookmyticket.model.TouristTicket;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class TicketHistoryTouristActivity extends AppCompatActivity {

    // UI Components
    private LinearLayout ticketsContainer;
    private LinearLayout emptyState;
    private ImageView backButton;
    private ProgressBar progressBar;
    private MaterialButton sortButton;

    // Data
    private String userUid;
    private final Executor databaseExecutor = Executors.newSingleThreadExecutor();
    private DatabaseReference ticketsRef;
    private ValueEventListener ticketsListener;
    private List<TouristTicket> allTickets = new ArrayList<>();
    private boolean sortNewestFirst = true;

    // Constants
    private static final long TWENTY_FOUR_HOURS_IN_MS = 24 * 60 * 60 * 1000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ticket_history_tourist);

        initViews();
        setSystemColors();
        getUserUid();
        setupSortButton();

        backButton.setOnClickListener(v -> onBackPressed());

        MaterialButton exploreButton = emptyState.findViewById(R.id.explore_button);
        exploreButton.setOnClickListener(v -> {
            Intent intent = new Intent(TicketHistoryTouristActivity.this, QRScannerActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        loadTickets();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (ticketsRef != null && ticketsListener != null) {
            ticketsRef.removeEventListener(ticketsListener);
        }
    }

    private void initViews() {
        ticketsContainer = findViewById(R.id.tickets_container);
        emptyState = findViewById(R.id.empty_state);
        backButton = findViewById(R.id.back_button);
        progressBar = findViewById(R.id.progress_bar);
        sortButton = findViewById(R.id.sort_button);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
    }

    private void setupSortButton() {
        sortButton.setOnClickListener(v -> {
            sortNewestFirst = !sortNewestFirst;
            sortButton.setText(sortNewestFirst ? "Sort: Newest" : "Sort: Oldest");
            displayTickets(allTickets);
        });
    }

    private void setSystemColors() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.parseColor("#DCD9D9"));

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            window.getInsetsController().setSystemBarsAppearance(
                    0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            window.getDecorView().setSystemUiVisibility(
                    window.getDecorView().getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            window.getDecorView().setSystemUiVisibility(
                    window.getDecorView().getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }

    private void getUserUid() {
        userUid = getIntent().getStringExtra("USER_UID");
        if (userUid == null) {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser != null) {
                userUid = currentUser.getUid();
            } else {
                showToast("User not logged in");
                finish();
            }
        }
    }

    private void loadTickets() {
        if (userUid == null || userUid.isEmpty()) {
            showEmptyState();
            return;
        }

        showLoading(true);
        ticketsRef = FirebaseDatabase.getInstance().getReference("tickets").child(userUid);

        ticketsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                databaseExecutor.execute(() -> {
                    try {
                        allTickets = parseTickets(snapshot);
                        runOnUiThread(() -> {
                            showLoading(false);
                            if (allTickets.isEmpty()) {
                                showEmptyState();
                            } else {
                                displayTickets(allTickets);
                            }
                        });
                    } catch (Exception e) {
                        Log.e("TicketError", "Error processing tickets", e);
                        runOnUiThread(() -> {
                            showLoading(false);
                            showError("Error loading tickets");
                        });
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                runOnUiThread(() -> {
                    showLoading(false);
                    showError("Failed to load tickets: " + error.getMessage());
                });
            }
        };

        ticketsRef.addValueEventListener(ticketsListener);
    }

    private List<TouristTicket> parseTickets(DataSnapshot snapshot) {
        List<TouristTicket> tickets = new ArrayList<>();
        long currentTime = System.currentTimeMillis();

        for (DataSnapshot ticketSnapshot : snapshot.getChildren()) {
            try {
                TouristTicket ticket = new TouristTicket();

                ticket.setTicketId(getStringValue(ticketSnapshot, "ticketId", ticketSnapshot.getKey()));
                ticket.setPlace(getStringValue(ticketSnapshot, "place", "Unknown Place"));
                ticket.setDate(getStringValue(ticketSnapshot, "date", ""));
                ticket.setTime(getStringValue(ticketSnapshot, "time", ""));
                ticket.setTotalAmount(getStringValue(ticketSnapshot, "totalAmount", "0"));
                ticket.setQrImage(getStringValue(ticketSnapshot, "qrImage", ""));
                ticket.setInstructions(getStringValue(ticketSnapshot, "instructions", ""));
                ticket.setHelpline(getStringValue(ticketSnapshot, "helpline", ""));
                ticket.setTimestamp(getLongValue(ticketSnapshot, "timestamp", 0L));
                ticket.setValidityTime(getStringValue(ticketSnapshot, "validityTime", null));
                ticket.setValidFrom(getStringValue(ticketSnapshot, "validFrom", null));
                ticket.setValidTo(getStringValue(ticketSnapshot, "validTo", null));

                ticket.setAdults(getIntValue(ticketSnapshot, "adults", 0));
                ticket.setStudents(getIntValue(ticketSnapshot, "students", 0));
                ticket.setSeniorCitizens(getIntValue(ticketSnapshot, "seniorCitizens", 0));
                ticket.setChildren(getIntValue(ticketSnapshot, "children", 0));
                ticket.setCameras(getIntValue(ticketSnapshot, "cameras", 0));

                int totalPersons = ticket.getAdults() + ticket.getStudents() +
                        ticket.getSeniorCitizens() + ticket.getChildren();
                if (totalPersons == 0) {
                    totalPersons = getIntValue(ticketSnapshot, "totalPersons", 0);
                }
                ticket.setTotalPersons(totalPersons);

                ticket.formatDate();
                ticket.formatPrice();

                tickets.add(ticket);
            } catch (Exception e) {
                Log.e("TicketError", "Error parsing ticket: " + ticketSnapshot.getKey(), e);
            }
        }
        return tickets;
    }

    private void displayTickets(List<TouristTicket> tickets) {
        ticketsContainer.removeAllViews();

        if (tickets.isEmpty()) {
            showEmptyState();
            return;
        }

        emptyState.setVisibility(View.GONE);
        ticketsContainer.setVisibility(View.VISIBLE);

        List<TouristTicket> sortedTickets = new ArrayList<>(tickets);
        Collections.sort(sortedTickets, (t1, t2) ->
                sortNewestFirst ?
                        Long.compare(t2.getTimestamp(), t1.getTimestamp()) :
                        Long.compare(t1.getTimestamp(), t2.getTimestamp()));

        for (TouristTicket ticket : sortedTickets) {
            CardView ticketCard = (CardView) getLayoutInflater()
                    .inflate(R.layout.item_ticket_history, ticketsContainer, false);

            TextView placeText = ticketCard.findViewById(R.id.ticket_place);
            TextView dateText = ticketCard.findViewById(R.id.ticket_date);
            TextView priceText = ticketCard.findViewById(R.id.ticket_price);
            TextView detailsText = ticketCard.findViewById(R.id.ticket_details);
            MaterialButton detailsButton = ticketCard.findViewById(R.id.details_button);

            placeText.setText(ticket.getPlace());
            dateText.setText(ticket.getFormattedDate());
            priceText.setText(ticket.getFormattedPrice());

            StringBuilder detailsBuilder = new StringBuilder();
            if (ticket.getTotalPersons() > 0) {
                detailsBuilder.append("Persons: ").append(ticket.getTotalPersons());
            }
            if (ticket.getTime() != null && !ticket.getTime().isEmpty()) {
                if (detailsBuilder.length() > 0) detailsBuilder.append(" • ");
                detailsBuilder.append("Time: ").append(ticket.getTime());
            }
            detailsText.setText(detailsBuilder.toString());

            detailsButton.setOnClickListener(v -> {
                Intent intent = new Intent(TicketHistoryTouristActivity.this, TicketDetailsActivity.class);
                intent.putExtra("TICKET_DATA", ticket);
                startActivity(intent);
            });

            ticketsContainer.addView(ticketCard);
        }
    }

    private String getStringValue(DataSnapshot snapshot, String key, String defaultValue) {
        try {
            String value = snapshot.child(key).getValue(String.class);
            return value != null ? value : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private long getLongValue(DataSnapshot snapshot, String key, long defaultValue) {
        try {
            Long value = snapshot.child(key).getValue(Long.class);
            return value != null ? value : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private int getIntValue(DataSnapshot snapshot, String key, int defaultValue) {
        try {
            Integer value = snapshot.child(key).getValue(Integer.class);
            return value != null ? value : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        ticketsContainer.setVisibility(show ? View.GONE : View.VISIBLE);
        emptyState.setVisibility(View.GONE);
    }

    private void showEmptyState() {
        ticketsContainer.setVisibility(View.GONE);
        emptyState.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.GONE);
    }

    private void showError(String message) {
        showToast(message);
        showEmptyState();
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
