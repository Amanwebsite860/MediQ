package com.example.mediq.data.model;

import java.io.Serializable;

/**
 * Model representing an Appointment in the MediQ application.
 */
public class Appointment implements Serializable {

    public static final String STATUS_UPCOMING = "Upcoming";
    public static final String STATUS_COMPLETED = "Completed";
    public static final String STATUS_CANCELLED = "Cancelled";

    private String id;
    private String doctorId;
    private String hospitalId;
    private String queueId;
    private String doctorName;
    private String doctorSpecialty;
    private String doctorAvatarUrl;
    private int doctorAvatarResId;
    private String hospitalName;
    private String appointmentDate;
    private String appointmentTime;
    private String tokenNumber;
    private String status;

    public Appointment() {
    }

    public Appointment(String id, String doctorName, String doctorSpecialty, String doctorAvatarUrl,
                       String hospitalName, String appointmentDate, String appointmentTime,
                       String tokenNumber, String status) {
        this.id = id;
        this.doctorName = doctorName;
        this.doctorSpecialty = doctorSpecialty;
        this.doctorAvatarUrl = doctorAvatarUrl;
        this.hospitalName = hospitalName;
        this.appointmentDate = appointmentDate;
        this.appointmentTime = appointmentTime;
        this.tokenNumber = tokenNumber;
        this.status = status;
    }

    public Appointment(String id, String doctorName, String doctorSpecialty, int doctorAvatarResId,
                       String hospitalName, String appointmentDate, String appointmentTime,
                       String tokenNumber, String status) {
        this.id = id;
        this.doctorName = doctorName;
        this.doctorSpecialty = doctorSpecialty;
        this.doctorAvatarResId = doctorAvatarResId;
        this.hospitalName = hospitalName;
        this.appointmentDate = appointmentDate;
        this.appointmentTime = appointmentTime;
        this.tokenNumber = tokenNumber;
        this.status = status;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDoctorId() {
        return doctorId;
    }

    public void setDoctorId(String doctorId) {
        this.doctorId = doctorId;
    }

    public String getHospitalId() {
        return hospitalId;
    }

    public void setHospitalId(String hospitalId) {
        this.hospitalId = hospitalId;
    }

    public String getQueueId() {
        return queueId;
    }

    public void setQueueId(String queueId) {
        this.queueId = queueId;
    }

    public String getDoctorName() {
        return doctorName;
    }

    public void setDoctorName(String doctorName) {
        this.doctorName = doctorName;
    }

    public String getDoctorSpecialty() {
        return doctorSpecialty;
    }

    public void setDoctorSpecialty(String doctorSpecialty) {
        this.doctorSpecialty = doctorSpecialty;
    }

    public String getDoctorAvatarUrl() {
        return doctorAvatarUrl;
    }

    public void setDoctorAvatarUrl(String doctorAvatarUrl) {
        this.doctorAvatarUrl = doctorAvatarUrl;
    }

    public int getDoctorAvatarResId() {
        return doctorAvatarResId;
    }

    public void setDoctorAvatarResId(int doctorAvatarResId) {
        this.doctorAvatarResId = doctorAvatarResId;
    }

    public String getHospitalName() {
        return hospitalName;
    }

    public void setHospitalName(String hospitalName) {
        this.hospitalName = hospitalName;
    }

    public String getAppointmentDate() {
        return appointmentDate;
    }

    public void setAppointmentDate(String appointmentDate) {
        this.appointmentDate = appointmentDate;
    }

    public String getAppointmentTime() {
        return appointmentTime;
    }

    public void setAppointmentTime(String appointmentTime) {
        this.appointmentTime = appointmentTime;
    }

    public String getTokenNumber() {
        return tokenNumber;
    }

    public void setTokenNumber(String tokenNumber) {
        this.tokenNumber = tokenNumber;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isUpcoming() {
        return "Upcoming".equalsIgnoreCase(status) || "confirmed".equalsIgnoreCase(status) || "pending".equalsIgnoreCase(status);
    }

    public boolean isCancelled() {
        return "Cancelled".equalsIgnoreCase(status);
    }

    public boolean isCompleted() {
        return "Completed".equalsIgnoreCase(status);
    }
}
