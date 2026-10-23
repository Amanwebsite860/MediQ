package com.example.mediq.data.model;

import java.io.Serializable;

/**
 * Model representing a selectable time slot chip.
 */
public class TimeSlotItem implements Serializable {
    private String time;
    private String time24;
    private String scheduleId;
    private boolean isAvailable = true;
    private boolean isSelected;

    public TimeSlotItem() {
    }

    public TimeSlotItem(String time, boolean isSelected) {
        this.time = time;
        this.isSelected = isSelected;
    }

    public TimeSlotItem(String time, String time24, String scheduleId, boolean isAvailable, boolean isSelected) {
        this.time = time;
        this.time24 = time24;
        this.scheduleId = scheduleId;
        this.isAvailable = isAvailable;
        this.isSelected = isSelected;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getTime24() {
        return time24 != null ? time24 : time;
    }

    public void setTime24(String time24) {
        this.time24 = time24;
    }

    public String getScheduleId() {
        return scheduleId;
    }

    public void setScheduleId(String scheduleId) {
        this.scheduleId = scheduleId;
    }

    public boolean isAvailable() {
        return isAvailable;
    }

    public void setAvailable(boolean available) {
        isAvailable = available;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }
}
