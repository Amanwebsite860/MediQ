package com.example.mediq.data.model;

import java.io.Serializable;

/**
 * Model representing a Doctor in the MediQ application.
 */
public class Doctor implements Serializable {
    private String id;
    private String name;
    private String specialty;
    private String bio;
    private int experienceYears;
    private int consultationFee;
    private int consultationDurationMin;
    private String avatarUrl;
    private int avatarResId;
    private double rating;
    private int reviewCount;
    private String hospitalName;
    private String hospitalId;
    private boolean isAvailableToday;
    private String nextSlotText;
    private String qualification;

    public Doctor() {
    }

    public Doctor(String id, String name, String specialty, String bio,
                  int experienceYears, int consultationFee, int consultationDurationMin,
                  String avatarUrl, int avatarResId, double rating, int reviewCount,
                  String hospitalName, String hospitalId, boolean isAvailableToday) {
        this.id = id;
        this.name = name;
        this.specialty = specialty;
        this.bio = bio;
        this.experienceYears = experienceYears;
        this.consultationFee = consultationFee;
        this.consultationDurationMin = consultationDurationMin;
        this.avatarUrl = avatarUrl;
        this.avatarResId = avatarResId;
        this.rating = rating;
        this.reviewCount = reviewCount;
        this.hospitalName = hospitalName;
        this.hospitalId = hospitalId;
        this.isAvailableToday = isAvailableToday;
    }

    public Doctor(String id, String name, String specialty, String bio,
                  int experienceYears, int consultationFee, int consultationDurationMin,
                  double rating, int reviewCount,
                  String hospitalName, String hospitalId, boolean isAvailableToday) {
        this(id, name, specialty, bio, experienceYears, consultationFee,
             consultationDurationMin, null, 0, rating, reviewCount,
             hospitalName, hospitalId, isAvailableToday);
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSpecialty() {
        return specialty;
    }

    public void setSpecialty(String specialty) {
        this.specialty = specialty;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public int getExperienceYears() {
        return experienceYears;
    }

    public void setExperienceYears(int experienceYears) {
        this.experienceYears = experienceYears;
    }

    public int getConsultationFee() {
        return consultationFee;
    }

    public void setConsultationFee(int consultationFee) {
        this.consultationFee = consultationFee;
    }

    public int getConsultationDurationMin() {
        return consultationDurationMin;
    }

    public void setConsultationDurationMin(int consultationDurationMin) {
        this.consultationDurationMin = consultationDurationMin;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public int getAvatarResId() {
        return avatarResId;
    }

    public void setAvatarResId(int avatarResId) {
        this.avatarResId = avatarResId;
    }

    public double getRating() {
        return rating;
    }

    public void setRating(double rating) {
        this.rating = rating;
    }

    public int getReviewCount() {
        return reviewCount;
    }

    public void setReviewCount(int reviewCount) {
        this.reviewCount = reviewCount;
    }

    public String getHospitalName() {
        return hospitalName;
    }

    public void setHospitalName(String hospitalName) {
        this.hospitalName = hospitalName;
    }

    public String getHospitalId() {
        return hospitalId;
    }

    public void setHospitalId(String hospitalId) {
        this.hospitalId = hospitalId;
    }

    public String getNextSlotText() { return nextSlotText; }
    public void setNextSlotText(String t) { this.nextSlotText = t; }
    public String getQualification() { return qualification; }
    public void setQualification(String q) { this.qualification = q; }

    public boolean isAvailableToday() {
        return isAvailableToday;
    }

    public void setAvailableToday(boolean availableToday) {
        isAvailableToday = availableToday;
    }

    public String getFormattedRating() {
        return String.format(java.util.Locale.US, "%.1f", rating);
    }

    public String getRatingAndReviewsText() {
        return String.format(java.util.Locale.US, "%.1f (%d reviews)", rating, reviewCount);
    }

    public String getRatingAndCountText() {
        return String.format(java.util.Locale.US, "%.1f (%d)", rating, reviewCount);
    }

    public String getFormattedExperience() {
        return experienceYears + "+ Years Experience";
    }

    public String getFormattedFee() {
        return "₹" + consultationFee;
    }

    public String getFormattedDuration() {
        int minDuration = Math.max(10, consultationDurationMin - 5);
        return minDuration + " - " + consultationDurationMin + " minutes";
    }
}
