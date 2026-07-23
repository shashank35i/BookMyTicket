package com.example.bookmyticket.model;

public class QRData {
    private String qrData;
    private String status;
    private String timestamp;

    // Default constructor required for Firebase
    public QRData() {
    }

    // Constructor
    public QRData(String qrData, String status, String timestamp) {
        this.qrData = qrData;
        this.status = status;
        this.timestamp = timestamp;
    }

    // Getters and Setters
    public String getQrData() {
        return qrData;
    }

    public void setQrData(String qrData) {
        this.qrData = qrData;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
