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

        // 방 번호 기준으로 해당 날짜에 머무는 예약 매핑
        Map<String, Reservation> roomToResMap = new HashMap<>();
        if (activeReservations != null) {
            for (Reservation res : activeReservations) {
                if (res.isAssigned() && res.getCheckInDate() != null) {
                    LocalDate checkIn = res.getCheckInDate();
                    LocalDate checkOut = res.getCheckOutDate();
                    if (checkOut == null) {
                        checkOut = checkIn.plusDays(res.getStayNights());
                    }

                    // [checkIn, checkOut) 구간 검증: checkIn <= date && date < checkOut
                    boolean isStayDuringDate = !date.isBefore(checkIn) && date.isBefore(checkOut);
                    if (isStayDuringDate && res.getAssignedRoomNumber() != null) {
                        roomToResMap.put(res.getAssignedRoomNumber().trim(), res);
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
                // 당일 활성 예약이 배정된 경우
                status = matchedRes.getStatus().isInHouse() ? RoomStatus.OCCUPIED : RoomStatus.ASSIGNED;
                rsvId = matchedRes.getReservationId();
                guestName = matchedRes.getGuestName();
                stayPeriodStr = String.format("%s ~ %s", matchedRes.getCheckInDate(), matchedRes.getCheckOutDate());
            } else if (room.isOccupiedOn(date)) {
                status = RoomStatus.OCCUPIED;
            } else {
                // 예약/스케줄이 없는 방은 실물 룸 랙 상태(OUT, CLEANING, BREAK, VACANT 등)를 온전히 반영
                status = room.getStatus();
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