package com.hotel.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hotel.domain.GuestPreference;
import com.hotel.domain.GuestPreference.CornerPref;
import com.hotel.domain.GuestPreference.ElevatorPref;
import com.hotel.domain.GuestPreference.FloorPref;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GeminiBatchPreferenceDto {

    private String reservationId;
    private String floorPref;
    private String elevatorPref;
    private String cornerPref;
    private boolean preferQuiet;

    public GeminiBatchPreferenceDto() {
    }

    public String getReservationId() {
        return reservationId;
    }

    public void setReservationId(String reservationId) {
        this.reservationId = reservationId;
    }

    public String getFloorPref() {
        return floorPref;
    }

    public void setFloorPref(String floorPref) {
        this.floorPref = floorPref;
    }

    public String getElevatorPref() {
        return elevatorPref;
    }

    public void setElevatorPref(String elevatorPref) {
        this.elevatorPref = elevatorPref;
    }

    public String getCornerPref() {
        return cornerPref;
    }

    public void setCornerPref(String cornerPref) {
        this.cornerPref = cornerPref;
    }

    public boolean isPreferQuiet() {
        return preferQuiet;
    }

    public void setPreferQuiet(boolean preferQuiet) {
        this.preferQuiet = preferQuiet;
    }

    public GuestPreference toDomain() {
        FloorPref floor = parseEnum(FloorPref.class, floorPref, FloorPref.NONE);
        ElevatorPref elevator = parseEnum(ElevatorPref.class, elevatorPref, ElevatorPref.NONE);
        CornerPref corner = parseEnum(CornerPref.class, cornerPref, CornerPref.NONE);
        return new GuestPreference(floor, elevator, corner, preferQuiet);
    }

    private <T extends Enum<T>> T parseEnum(Class<T> enumType, String value, T defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }
}