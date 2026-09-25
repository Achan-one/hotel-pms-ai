package com.hotel.repository.rdb;

import com.hotel.domain.Room;
import com.hotel.domain.StayPeriod;
import com.hotel.entity.RoomEntity;
import com.hotel.entity.RoomScheduleEntity;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.jpa.SpringDataRoomRepository;
import com.hotel.repository.jpa.SpringDataScheduleRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class JpaRoomRepository implements RoomRepository {

    private final SpringDataRoomRepository roomJpaRepo;
    private final SpringDataScheduleRepository scheduleJpaRepo;

    public JpaRoomRepository(SpringDataRoomRepository roomJpaRepo,
                             SpringDataScheduleRepository scheduleJpaRepo) {
        this.roomJpaRepo = Objects.requireNonNull(roomJpaRepo);
        this.scheduleJpaRepo = Objects.requireNonNull(scheduleJpaRepo);
    }

    @Override
    @Transactional
    public void save(Room room) {
        if (room == null) return;

        RoomEntity entity = roomJpaRepo.findById(room.getRoomNumber())
                .orElseGet(() -> new RoomEntity(
                        room.getRoomNumber(), room.getFloor(), room.getRoomType(),
                        room.isNearElevator(), room.isCorner(), room.getStatus()
                ));

        entity.setStatus(room.getStatus());
        room.getTags().forEach(entity::addTag);
        roomJpaRepo.save(entity);

        // 스케줄 동기화 (기존 스케줄과 도메인의 bookedPeriods 동기화)
        List<RoomScheduleEntity> existingSchedules = scheduleJpaRepo.findByRoomNumber(room.getRoomNumber());
        List<StayPeriod> currentPeriods = room.getBookedPeriods();

        // 삭제된 스케줄 제거
        for (RoomScheduleEntity ex : existingSchedules) {
            StayPeriod p = new StayPeriod(ex.getCheckInDate(), ex.getCheckOutDate());
            if (!currentPeriods.contains(p)) {
                scheduleJpaRepo.delete(ex);
            }
        }

        // 신규 스케줄 추가
        for (StayPeriod period : currentPeriods) {
            boolean alreadyPersisted = existingSchedules.stream().anyMatch(ex ->
                    ex.getCheckInDate().equals(period.getCheckInDate()) &&
                            ex.getCheckOutDate().equals(period.getCheckOutDate())
            );
            if (!alreadyPersisted) {
                scheduleJpaRepo.save(new RoomScheduleEntity(
                        room.getRoomNumber(), "ASSIGNED", period.getCheckInDate(), period.getCheckOutDate()
                ));
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Room> findByRoomNumber(String roomNumber) {
        if (roomNumber == null || roomNumber.isBlank()) return Optional.empty();

        return roomJpaRepo.findById(roomNumber.trim()).map(entity -> {
            List<RoomScheduleEntity> schedules = scheduleJpaRepo.findByRoomNumber(entity.getRoomNumber());
            return toDomain(entity, schedules);
        });
    }
    @Override
    @Transactional
    public Optional<Room> findByRoomNumberForUpdate(String roomNumber) {
        if (roomNumber == null || roomNumber.isBlank()) return Optional.empty();

        return roomJpaRepo.findByRoomNumberForUpdate(roomNumber.trim()).map(entity -> {
            List<RoomScheduleEntity> schedules = scheduleJpaRepo.findByRoomNumber(entity.getRoomNumber());
            return toDomain(entity, schedules);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<Room> findAll() {
        List<RoomEntity> entities = roomJpaRepo.findAll();
        List<Room> result = new ArrayList<>();
        for (RoomEntity e : entities) {
            List<RoomScheduleEntity> schedules = scheduleJpaRepo.findByRoomNumber(e.getRoomNumber());
            result.add(toDomain(e, schedules));
        }
        return result;
    }

    private Room toDomain(RoomEntity entity, List<RoomScheduleEntity> schedules) {
        Room room = new Room(
                entity.getRoomNumber(),
                entity.getFloor(),
                entity.getRoomType(),
                entity.isNearElevator(),
                entity.isCornerRoom()
        );
        room.setStatus(entity.getStatus());
        entity.getTags().forEach(room::addTag);

        for (RoomScheduleEntity s : schedules) {
            room.tryBookPeriod(new StayPeriod(s.getCheckInDate(), s.getCheckOutDate()));
        }
        return room;
    }
}