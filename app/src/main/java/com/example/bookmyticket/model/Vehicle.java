package com.example.bookmyticket.model;

public class Vehicle {
    private String number;
    private String type;
    private long timestamp;
    private boolean isPrimary;  // Add this field

    // Required empty constructor for Firebase
    public Vehicle() {}

    public Vehicle(String number, String type, boolean isPrimary) {
        this.number = number;
        this.type = type;
        this.isPrimary = isPrimary;
        this.timestamp = System.currentTimeMillis();
    }

    // Getters and setters
    public String getNumber() { return number; }
    public String getType() { return type; }
    public long getTimestamp() { return timestamp; }
    public boolean isPrimary() { return isPrimary; }

    public void setNumber(String number) { this.number = number; }
    public void setType(String type) { this.type = type; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public void setPrimary(boolean primary) { isPrimary = primary; }
}
