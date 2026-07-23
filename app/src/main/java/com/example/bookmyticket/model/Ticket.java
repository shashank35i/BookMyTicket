package com.example.bookmyticket.model;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Ticket {
    private String ticketId;
    private double baseAmount;
    private long timestamp;
    private String status;
    private int totalPersons;

    public Ticket(String ticketId, double baseAmount, long timestamp, String status, int totalPersons) {
        this.ticketId = ticketId;
        this.baseAmount = baseAmount;
        this.timestamp = timestamp;
        this.status = status;
        this.totalPersons = totalPersons;
    }

    // Getters
    public String getTicketId() { return ticketId; }
    public double getBaseAmount() { return baseAmount; }
    public long getTimestamp() { return timestamp; }
    public String getStatus() { return status; }
    public int getTotalPersons() { return totalPersons; }

    // Formatted methods for display
    public String getFormattedAmount() {
        return String.format("₹%.2f", baseAmount);
    }

    public String getFormattedDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    public String getStatusColor() {
        switch (status.toLowerCase()) {
            case "completed": return "#4CAF50";
            case "processing": return "#FFC107";
            case "pending": return "#F44336";
            default: return "#9E9E9E";
        }
    }
}
