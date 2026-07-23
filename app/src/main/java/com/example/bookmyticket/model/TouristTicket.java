package com.example.bookmyticket.model;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TouristTicket implements Serializable {
    private String ticketId;
    private String place;
    private String date;
    private String time;
    private String totalAmount;
    private long timestamp;
    private String validityTime;
    private int totalPersons;
    private boolean isExpired;
    private String validFrom;
    private String validTo;
    private String qrImage;
    private String instructions;
    private String helpline;
    private int adults;
    private int students;
    private int seniorCitizens;
    private int children;
    private int cameras;
    private String formattedDate;
    private String formattedPrice;

    // Getters
    public String getQrImage() {
        return qrImage;
    }
    public String getTicketId() { return ticketId; }
    public String getPlace() { return place; }
    public String getDate() { return date; }
    public String getTime() { return time; }
    public String getTotalAmount() { return totalAmount; }
    public long getTimestamp() { return timestamp; }
    public String getValidityTime() { return validityTime; }
    public int getTotalPersons() { return totalPersons; }
    public boolean isExpired() { return isExpired; }
    public String getValidFrom() { return validFrom; }
    public String getValidTo() { return validTo; }

    public String getInstructions() { return instructions; }
    public String getHelpline() { return helpline; }
    public int getAdults() { return adults; }
    public int getStudents() { return students; }
    public int getSeniorCitizens() { return seniorCitizens; }
    public int getChildren() { return children; }
    public int getCameras() { return cameras; }
    public String getFormattedDate() { return formattedDate; }
    public String getFormattedPrice() { return formattedPrice; }

    // Setters
    public void setTicketId(String ticketId) { this.ticketId = ticketId; }
    public void setPlace(String place) { this.place = place; }
    public void setDate(String date) { this.date = date; }
    public void setTime(String time) { this.time = time; }
    public void setTotalAmount(String totalAmount) { this.totalAmount = totalAmount; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public void setValidityTime(String validityTime) { this.validityTime = validityTime; }
    public void setTotalPersons(int totalPersons) { this.totalPersons = totalPersons; }
    public void setExpired(boolean expired) { isExpired = expired; }
    public void setValidFrom(String validFrom) { this.validFrom = validFrom; }
    public void setValidTo(String validTo) { this.validTo = validTo; }
    public void setQrImage(String qrImage) { this.qrImage = qrImage; }
    public void setInstructions(String instructions) { this.instructions = instructions; }
    public void setHelpline(String helpline) { this.helpline = helpline; }
    public void setAdults(int adults) { this.adults = adults; }
    public void setStudents(int students) { this.students = students; }
    public void setSeniorCitizens(int seniorCitizens) { this.seniorCitizens = seniorCitizens; }
    public void setChildren(int children) { this.children = children; }
    public void setCameras(int cameras) { this.cameras = cameras; }
    public void setFormattedDate(String formattedDate) { this.formattedDate = formattedDate; }
    public void setFormattedPrice(String formattedPrice) { this.formattedPrice = formattedPrice; }

    // Helper method to format date
    public void formatDate() {
        if (timestamp > 0) {
            this.formattedDate = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                    .format(new Date(timestamp));
        } else {
            this.formattedDate = date;
        }
    }

    // Helper method to format price
    public void formatPrice() {
        this.formattedPrice = "₹" + totalAmount;
    }

    // Method to check if ticket is active
    public boolean checkIfActive() {
        if (validFrom != null && validTo != null) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                Date validFromDate = sdf.parse(validFrom);
                Date validToDate = sdf.parse(validTo);
                long currentTime = System.currentTimeMillis();

                if (validFromDate != null && validToDate != null) {
                    return currentTime >= validFromDate.getTime() &&
                            currentTime <= validToDate.getTime();
                }
            } catch (Exception e) {
                return !isExpired;
            }
        }
        return !isExpired;
    }
}
