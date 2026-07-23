package com.example.bookmyticket.features.reports;

import com.example.bookmyticket.R;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;

public class ReportsActivity extends AppCompatActivity {
    private ListView reportList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        reportList = findViewById(R.id.report_list);

        // Fetch and display reports
        List<String> reports = new ArrayList<>();
        reports.add("2023-10-01: ₹5000 (10 vehicles)");
        reports.add("2023-10-02: ₹7500 (15 vehicles)");

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, reports);
        reportList.setAdapter(adapter);
    }
}
