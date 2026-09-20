package com.hotel.service.dto;

public record RoomChangeResult(
        boolean success,
        String reservationId,
        String fromRoomNumber,
        String toRoomNumber,
        String message
) {
    public static RoomChangeResult success(String reservationId, String fromRoom, String toRoom, String message) {
        return new RoomChangeResult(true, reservationId, fromRoom, toRoom, message);
    }

    public static RoomChangeResult failure(String reservationId, String message) {
        return new RoomChangeResult(false, reservationId, null, null, message);
    }
}