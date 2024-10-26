package com.example;

import org.bson.types.ObjectId;

public class User {
    private ObjectId id;
    private String username;
    private String role;
    private String partyAffiliation;
    private int electoralStrength;
    private boolean present;
    private String seatStatus;
    private int fines;

    // Getters and Setters

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getPartyAffiliation() {
        return partyAffiliation;
    }

    public void setPartyAffiliation(String partyAffiliation) {
        this.partyAffiliation = partyAffiliation;
    }

    public int getElectoralStrength() {
        return electoralStrength;
    }

    public void setElectoralStrength(int electoralStrength) {
        this.electoralStrength = electoralStrength;
    }

    public boolean isPresent() {
        return present;
    }

    public void setPresent(boolean present) {
        this.present = present;
    }

    public String getSeatStatus() {
        return seatStatus;
    }

    public void setSeatStatus(String seatStatus) {
        this.seatStatus = seatStatus;
    }

    public int getFines() {
        return fines;
    }

    public void setFines(int fines) {
        this.fines = fines;
    }
}
