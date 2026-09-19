package com.hotel.service.validator;

import com.hotel.domain.Reservation;

public class ValidationResult {
    private final boolean valid;
    private final String reason;
    private final Reservation validReservation;

    private ValidationResult(boolean valid, String reason, Reservation validReservation) {
        this.valid = valid;
        this.reason = reason;
        this.validReservation = validReservation;
    }

    public static ValidationResult success(Reservation reservation) {
        return new ValidationResult(true, null, reservation);
    }

    public static ValidationResult failure(String reason) {
        return new ValidationResult(false, reason, null);
    }

    public boolean isValid() {
        return valid;
    }

    public String getReason() {
        return reason;
    }

    public Reservation getValidReservation() {
        return validReservation;
    }
}