package com.example.bookmyticket.features.tickets;

import com.example.bookmyticket.R;
import com.example.bookmyticket.model.Ticket;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TicketHistoryActivity extends AppCompatActivity {

    private static final String TAG = "TicketHistoryActivity";
    private RecyclerView recyclerViewTickets;
    private ImageView backButton, filterButton;
    private EditText searchEditText;

    private LinearLayout tvNoTickets; // Changed from TextView to LinearLayout
    private TicketAdapter ticketAdapter;
    private List<Ticket> ticketList = new ArrayList<>();
    private List<Ticket> filteredTicketList = new ArrayList<>();
    private FirebaseUser currentUser;
    private String currentSort = "newest";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ticket_history);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        initializeViews();
        setupRecyclerView();
        setupSystemUI();
        setupSearchView();
        loadUserTickets();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    private void initializeViews() {
        recyclerViewTickets = findViewById(R.id.recyclerViewTickets);
        backButton = findViewById(R.id.backButton);
        filterButton = findViewById(R.id.filterButton);
        searchEditText = findViewById(R.id.searchEditText);
        tvNoTickets = findViewById(R.id.tvNoTickets);
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        backButton.setOnClickListener(v -> finish());
        filterButton.setOnClickListener(v -> showSortMenu());
    }

    private void setupRecyclerView() {
        ticketAdapter = new TicketAdapter(filteredTicketList, ticket -> {
            // Handle ticket click with animation
            View view = recyclerViewTickets.findViewHolderForAdapterPosition(
                    filteredTicketList.indexOf(ticket)).itemView;
            view.startAnimation(AnimationUtils.loadAnimation(this, R.anim.click_scale));

            Toast.makeText(this,
                    "Ticket ID: " + ticket.getTicketId() +
                            "\nAmount: " + ticket.getFormattedAmount() +
                            "\nDate: " + ticket.getFormattedDate() +
                            "\nPersons: " + ticket.getTotalPersons(),
                    Toast.LENGTH_LONG).show();
        });

        recyclerViewTickets.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewTickets.setAdapter(ticketAdapter);
        recyclerViewTickets.setHasFixedSize(true);
        recyclerViewTickets.setItemAnimator(null);
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

    private void setupSearchView() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterTickets(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void showSortMenu() {
        filterButton.startAnimation(AnimationUtils.loadAnimation(this, R.anim.click_scale));

        PopupMenu popupMenu = new PopupMenu(this, filterButton);
        popupMenu.getMenuInflater().inflate(R.menu.sort_menu, popupMenu.getMenu());
        popupMenu.setOnMenuItemClickListener(this::handleSortSelection);
        popupMenu.show();
    }

    private boolean handleSortSelection(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_newest) {
            currentSort = "newest";
        } else if (id == R.id.menu_oldest) {
            currentSort = "oldest";
        } else if (id == R.id.menu_amount_high) {
            currentSort = "amount_high";
        } else if (id == R.id.menu_amount_low) {
            currentSort = "amount_low";
        }

        applySortingAndFiltering();
        return true;
    }

    private void loadUserTickets() {
        DatabaseReference payoutsRef = FirebaseDatabase.getInstance()
                .getReference("payouts")
                .child(currentUser.getUid());

        payoutsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                ticketList.clear();
                for (DataSnapshot ticketSnapshot : snapshot.getChildren()) {
                    Ticket ticket = parseTicketData(ticketSnapshot);
                    if (ticket != null) {
                        ticketList.add(ticket);
                    }
                }
                applySortingAndFiltering();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(TicketHistoryActivity.this,
                        "Failed to load tickets: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Database error: ", error.toException());
            }
        });
    }

    private void applySortingAndFiltering() {
        // Apply search filter first
        String query = searchEditText.getText().toString().trim().toLowerCase();
        List<Ticket> tempList = new ArrayList<>();

        if (query.isEmpty()) {
            tempList.addAll(ticketList);
        } else {
            for (Ticket ticket : ticketList) {
                if (ticket.getTicketId().toLowerCase().contains(query)) {
                    tempList.add(ticket);
                }
            }
        }

        // Then apply sorting
        switch (currentSort) {
            case "newest":
                Collections.sort(tempList, (t1, t2) -> Long.compare(t2.getTimestamp(), t1.getTimestamp()));
                break;
            case "oldest":
                Collections.sort(tempList, (t1, t2) -> Long.compare(t1.getTimestamp(), t2.getTimestamp()));
                break;
            case "amount_high":
                Collections.sort(tempList, (t1, t2) -> Double.compare(t2.getBaseAmount(), t1.getBaseAmount()));
                break;
            case "amount_low":
                Collections.sort(tempList, (t1, t2) -> Double.compare(t1.getBaseAmount(), t2.getBaseAmount()));
                break;
        }

        // Update UI
        filteredTicketList.clear();
        filteredTicketList.addAll(tempList);
        updateUI();
    }

    private void filterTickets(String query) {
        applySortingAndFiltering();
    }

    private void updateUI() {
        runOnUiThread(() -> {
            if (filteredTicketList.isEmpty()) {
                tvNoTickets.setVisibility(View.VISIBLE);
                recyclerViewTickets.setVisibility(View.GONE);
            } else {
                tvNoTickets.setVisibility(View.GONE);
                recyclerViewTickets.setVisibility(View.VISIBLE);
                ticketAdapter.updateTickets(filteredTicketList);
            }
        });
    }

    private Ticket parseTicketData(DataSnapshot snapshot) {
        try {
            String ticketId = snapshot.getKey();
            Double baseAmount = snapshot.child("baseAmount").getValue(Double.class);
            Long timestamp = snapshot.child("timestamp").getValue(Long.class);
            String status = snapshot.child("status").getValue(String.class);
            Integer totalPersons = snapshot.child("totalPersons").getValue(Integer.class);

            if (ticketId == null || ticketId.isEmpty() ||
                    baseAmount == null || baseAmount <= 0 ||
                    timestamp == null) {
                return null;
            }

            return new Ticket(ticketId, baseAmount, timestamp,
                    status != null ? status : "",
                    totalPersons != null ? totalPersons : 1);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing ticket data", e);
            return null;
        }
    }
}
