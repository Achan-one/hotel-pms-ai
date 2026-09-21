package com.hotel.service;

import com.hotel.domain.Reservation;
import com.hotel.domain.Room;
import com.hotel.domain.RoomStatus;
import com.hotel.repository.RoomRepository;
import com.hotel.service.dto.FloorMapResponseDto;
import com.hotel.service.dto.RoomMatrixItemDto;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class FloorStatusService {

    private final RoomRepository roomRepository;

    public FloorStatusService(RoomRepository roomRepository) {
        this.roomRepository = Objects.requireNonNull(roomRepository, "roomRepository는 필수입니다.");
    }

    public FloorMapResponseDto getFloorMatrix(LocalDate targetDate, List<Reservation> activeReservations) {
        LocalDate date = (targetDate != null) ? targetDate : LocalDate.now();

        // 방 번호 기준으로 해당 날짜에 머무는 유효 예약 매핑 (현재 호실 + 룸체인지 이전 호실 이력 포함)
        Map<String, Reservation> roomToResMap = new HashMap<>();
        if (activeReservations != null) {
            for (Reservation res : activeReservations) {
                if (res.getCheckInDate() != null) {
                    LocalDate checkIn = res.getCheckInDate();
                    LocalDate checkOut = (res.getActualCheckOutDate() != null) ? res.getActualCheckOutDate() : res.getCheckOutDate();

                    // 1. 현재 배정된 호실 매핑
                    if (res.isAssigned() && res.getAssignedRoomNumber() != null) {
                        boolean isStayDuringDate = !date.isBefore(checkIn) && date.isBefore(checkOut);
                        if (isStayDuringDate) {
                            roomToResMap.put(res.getAssignedRoomNumber().trim(), res);
                        }
                    }

                    // 2. [룸 무브 역추적 방어] 이전 호실(previousRoomNumber)에 머물렀던 과거 날짜도 유령 객실이 되지 않도록 고객 정보 매핑
                    if (res.getPreviousRoomNumber() != null && !date.isBefore(checkIn)) {
                        String prevRoom = res.getPreviousRoomNumber().trim();
                        // 이전 객실에 여전히 스케줄 이력(bookedPeriods)이 남아있는 경우 고객 정보 연결
                        roomRepository.findByRoomNumber(prevRoom).ifPresent(r -> {
                            if (r.isOccupiedOn(date)) {
                                roomToResMap.put(prevRoom, res);
                            }
                        });
                    }
                }
            }
        }

        List<Room> allRooms = roomRepository.findAll();
        List<RoomMatrixItemDto> dtoList = new ArrayList<>();

        for (Room room : allRooms) {
            String roomNo = room.getRoomNumber().trim();
            Reservation matchedRes = roomToResMap.get(roomNo);

            RoomStatus status;
            String rsvId = null;
            String guestName = null;
            String stayPeriodStr = null;

            if (matchedRes != null) {
                status = matchedRes.getStatus().isInHouse() ? RoomStatus.OCCUPIED : RoomStatus.ASSIGNED;
                rsvId = matchedRes.getReservationId();
                guestName = matchedRes.getGuestName();
                stayPeriodStr = String.format("%s ~ %s", matchedRes.getCheckInDate(), matchedRes.getCheckOutDate());
            } else if (room.getStatus() != RoomStatus.VACANT) {
                status = room.getStatus();
            } else if (room.isOccupiedOn(date)) {
                status = RoomStatus.OCCUPIED;
            } else {
                status = RoomStatus.VACANT;
            }

            dtoList.add(new RoomMatrixItemDto(
                    room.getRoomNumber(),
                    room.getFloor(),
                    room.getRoomType(),
                    room.getRoomType().getDescription(),
                    room.isNearElevator(),
                    room.isCorner(),
                    status,
                    rsvId,
                    guestName,
                    stayPeriodStr
            ));
        }

        Map<Integer, List<RoomMatrixItemDto>> floorRooms = dtoList.stream()
                .sorted(Comparator.comparing(RoomMatrixItemDto::floor)
                        .thenComparing(RoomMatrixItemDto::roomNumber))
                .collect(Collectors.groupingBy(
                        RoomMatrixItemDto::floor,
                        TreeMap::new,
                        Collectors.toList()
                ));

        int totalRooms = allRooms.size();
        int occupiedRooms = (int) dtoList.stream().filter(item -> item.status() != RoomStatus.VACANT).count();
        int vacantRooms = totalRooms - occupiedRooms;
        double occ = totalRooms > 0 ? ((double) occupiedRooms / totalRooms) * 100.0 : 0.0;
        double roundedOcc = Math.round(occ * 10.0) / 10.0;

        return new FloorMapResponseDto(
                date,
                totalRooms,
                occupiedRooms,
                vacantRooms,
                roundedOcc,
                floorRooms
        );
    }
}