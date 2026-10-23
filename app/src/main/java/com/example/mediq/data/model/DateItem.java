package com.example.mediq.data.model;

import java.io.Serializable;

/**
 * Model representing a date selectable card item.
 */
public class DateItem implements Serializable {
    private String dayOfWeek;
    private String dayNumber;
    private String month;
    private String fullDate;
    private String isoDate;
    private boolean isSelected;

    public DateItem() {
    }

    public DateItem(String dayOfWeek, String dayNumber, String month, String fullDate, boolean isSelected) {
        this(dayOfWeek, dayNumber, month, fullDate, "", isSelected);
    }

    public DateItem(String dayOfWeek, String dayNumber, String month, String fullDate, String isoDate, boolean isSelected) {
        this.dayOfWeek = dayOfWeek;
        this.dayNumber = dayNumber;
        this.month = month;
        this.fullDate = fullDate;
        this.isoDate = isoDate;
        this.isSelected = isSelected;
    }

    public String getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(String dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public String getDayNumber() {
        return dayNumber;
    }

    public void setDayNumber(String dayNumber) {
        this.dayNumber = dayNumber;
    }

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public String getFullDate() {
        return fullDate;
    }

    public void setFullDate(String fullDate) {
        this.fullDate = fullDate;
    }

    public String getIsoDate() {
        return isoDate;
    }

    public void setIsoDate(String isoDate) {
        this.isoDate = isoDate;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }
}
