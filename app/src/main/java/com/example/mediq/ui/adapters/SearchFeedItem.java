package com.example.mediq.ui.adapters;

import com.example.mediq.data.model.Doctor;
import com.example.mediq.data.model.Hospital;

/**
 * Encapsulates either a Hospital or a Doctor for the unified search and explore feed.
 */
public class SearchFeedItem {
    public static final int TYPE_HOSPITAL = 1;
    public static final int TYPE_DOCTOR = 2;

    private final int type;
    private final Hospital hospital;
    private final Doctor doctor;

    public SearchFeedItem(Hospital hospital) {
        this.type = TYPE_HOSPITAL;
        this.hospital = hospital;
        this.doctor = null;
    }

    public SearchFeedItem(Doctor doctor) {
        this.type = TYPE_DOCTOR;
        this.doctor = doctor;
        this.hospital = null;
    }

    public int getType() {
        return type;
    }

    public Hospital getHospital() {
        return hospital;
    }

    public Doctor getDoctor() {
        return doctor;
    }
}
