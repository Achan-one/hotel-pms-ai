package com.hotel.service.validator;

import com.hotel.domain.Reservation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ValidationResult {

    private final List<Reservation> validReservations;
    private final List<RejectedReservation> rejectedReservations;

    public ValidationResult(List<Reservation> validReservations, List<RejectedReservation> rejectedReservations) {
        this.validReservations = Collections.unmodifiableList(new ArrayList<>(validReservations));
        this.rejectedReservations = Collections.unmodifiableList(new ArrayList<>(rejectedReservations));
    }

    public List<Reservation> getValidReservations() {
        return validReservations;
    }

    public List<RejectedReservation> getRejectedReservations() {
        return rejectedReservations;
    }

    public boolean hasRejections() {
        return !rejectedReservations.isEmpty();
    }

    public record RejectedReservation(Reservation reservation, String reason) {
        @Override
        public String toString() {
            String rsvId = (reservation != null) ? reservation.getReservationId() : "NULL";
            return String.format("[%s] 거절 사유: %s", rsvId, reason);
        }
    }
}