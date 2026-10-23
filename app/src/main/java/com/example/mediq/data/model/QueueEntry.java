package com.example.mediq.data.model;

/**
 * Model representing a queue entry on the Live Queue Status screen.
 */
public class QueueEntry {

    public static final String STATUS_COMPLETED = "Completed";
    public static final String STATUS_IN_CONSULTATION = "In Consultation";
    public static final String STATUS_YOUR_TURN = "Your Turn Next";
    public static final String STATUS_WAITING = "Waiting";

    private String id;
    private String tokenNumber;
    private int tokenSequence;
    private String status;
    private boolean isCurrentUser;

    public QueueEntry() {
    }

    public QueueEntry(String id, String tokenNumber, int tokenSequence, String status, boolean isCurrentUser) {
        this.id = id;
        this.tokenNumber = tokenNumber;
        this.tokenSequence = tokenSequence;
        this.status = status;
        this.isCurrentUser = isCurrentUser;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTokenNumber() {
        return tokenNumber;
    }

    public void setTokenNumber(String tokenNumber) {
        this.tokenNumber = tokenNumber;
    }

    public int getTokenSequence() {
        return tokenSequence;
    }

    public void setTokenSequence(int tokenSequence) {
        this.tokenSequence = tokenSequence;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isCurrentUser() {
        return isCurrentUser;
    }

    public void setCurrentUser(boolean currentUser) {
        isCurrentUser = currentUser;
    }
}
