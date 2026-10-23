package com.example.mediq.data.model;

import java.io.Serializable;

/**
 * Model representing a Hospital in the MediQ application.
 */
public class Hospital implements Serializable {
    private String id;
    private String name;
    private String type;
    private String address;
    private String city;
    private String distance;
    private String phone;
    private String openingHours;
    private String[] facilities;
    private String imageUrl;
    private int imageResId;
    private double rating;
    private int reviewCount;
    private boolean isOpen;
    private double latitude;
    private double longitude;

    public Hospital() {
    }

    public Hospital(String id, String name, String type, String address, String city,
                    String distance, String phone, String openingHours, String[] facilities,
                    int imageResId, double rating, int reviewCount, boolean isOpen,
                    double latitude, double longitude) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.address = address;
        this.city = city;
        this.distance = distance;
        this.phone = phone;
        this.openingHours = openingHours;
        this.facilities = facilities;
        this.imageResId = imageResId;
        this.rating = rating;
        this.reviewCount = reviewCount;
        this.isOpen = isOpen;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getDistance() { return distance; }
    public void setDistance(String distance) { this.distance = distance; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getOpeningHours() { return openingHours; }
    public void setOpeningHours(String openingHours) { this.openingHours = openingHours; }

    public String[] getFacilities() { return facilities; }
    public void setFacilities(String[] facilities) { this.facilities = facilities; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public int getImageResId() { return imageResId; }
    public void setImageResId(int imageResId) { this.imageResId = imageResId; }

    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }

    public int getReviewCount() { return reviewCount; }
    public void setReviewCount(int reviewCount) { this.reviewCount = reviewCount; }

    public boolean isOpen() { return isOpen; }
    public void setOpen(boolean open) { isOpen = open; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public String getDistanceAndCity() {
        boolean hasDist = distance != null && !distance.isEmpty();
        boolean hasCity = city != null && !city.isEmpty();
        if (hasDist && hasCity) return distance + " \u2022 " + city;
        if (hasDist) return distance;
        return hasCity ? city : "";
    }

    public String getFormattedRating() {
        return rating + " (" + reviewCount + ")";
    }

    public String getClosingTime() {
        if (openingHours == null) return "8:00 PM";
        if (openingHours.contains("-")) {
            String[] parts = openingHours.split("-");
            return parts[parts.length - 1].trim();
        }
        return openingHours.trim();
    }

    public String getOpenStatusText() {
        return com.example.mediq.util.OpeningHours.status(openingHours).text;
    }

    /** true = open now, false = closed now, null = hours unknown / not parseable. */
    public Boolean isOpenNow() {
        com.example.mediq.util.OpeningHours.Status st = com.example.mediq.util.OpeningHours.status(openingHours);
        return st.known ? Boolean.valueOf(st.open) : null;
    }
}
