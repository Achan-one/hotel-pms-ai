package com.hotel.repository.rdb;

import com.hotel.domain.Room;
import com.hotel.domain.StayPeriod;
import com.hotel.entity.RoomEntity;
import com.hotel.entity.RoomNightOccupancyEntity;
import com.hotel.entity.RoomScheduleEntity;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.jpa.SpringDataRoomNightRepository;
import com.hotel.repository.jpa.SpringDataRoomRepository;
import com.hotel.repository.jpa.SpringDataScheduleRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class JpaRoomRepository implements RoomRepository {

    private final SpringDataRoomRepository roomJpaRepo;
    private final SpringDataScheduleRepository scheduleJpaRepo;
    private final SpringDataRoomNightRepository nightJpaRepo;

    public JpaRoomRepository(SpringDataRoomRepository roomJpaRepo,
                             SpringDataScheduleRepository scheduleJpaRepo,
                             SpringDataRoomNightRepository nightJpaRepo) {
        this.roomJpaRepo = Objects.requireNonNull(roomJpaRepo);
        this.scheduleJpaRepo = Objects.requireNonNull(scheduleJpaRepo);
        this.nightJpaRepo = Objects.requireNonNull(nightJpaRepo);
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

        // 잠금 읽기로 최신 스케줄을 가져와야, 다른 트랜잭션이 방금 커밋한 예약을 지우지 않는다.
        List<RoomScheduleEntity> existingSchedules = scheduleJpaRepo.findByRoomNumberForUpdate(room.getRoomNumber());
        List<StayPeriod> currentPeriods = room.getBookedPeriods();

        removeStaleSchedules(existingSchedules, currentPeriods);
        insertNewSchedules(room.getRoomNumber(), existingSchedules, currentPeriods);
        syncNightOccupancy(room.getRoomNumber(), currentPeriods);
    }

    // 도메인에서 사라진 기간의 스케줄 행을 지운다.
    private void removeStaleSchedules(List<RoomScheduleEntity> existingSchedules, List<StayPeriod> currentPeriods) {
        for (RoomScheduleEntity ex : existingSchedules) {
            StayPeriod p = new StayPeriod(ex.getCheckInDate(), ex.getCheckOutDate());
            if (!currentPeriods.contains(p)) {
                scheduleJpaRepo.delete(ex);
            }
        }
    }

    // 도메인에 새로 생긴 기간을 스케줄 행으로 추가한다.
    private void insertNewSchedules(String roomNumber,
                                    List<RoomScheduleEntity> existingSchedules,
                                    List<StayPeriod> currentPeriods) {
        for (StayPeriod period : currentPeriods) {
            boolean alreadyPersisted = existingSchedules.stream().anyMatch(ex ->
                    ex.getCheckInDate().equals(period.getCheckInDate()) &&
                            ex.getCheckOutDate().equals(period.getCheckOutDate())
            );
            if (!alreadyPersisted) {
                scheduleJpaRepo.save(new RoomScheduleEntity(
                        roomNumber, "ASSIGNED", period.getCheckInDate(), period.getCheckOutDate()
                ));
            }
        }
    }

    // 현재 기간을 박 단위 집합으로 펴서 DB와 맞춘다. 겹치면 기본키 위반이 나는데,
    // 그건 방 락을 우회한 경로가 있다는 뜻이라 조용히 넘기지 않고 예외로 올린다.
    private void syncNightOccupancy(String roomNumber, List<StayPeriod> currentPeriods) {
        Set<LocalDate> wanted = expandToNights(currentPeriods);
        List<RoomNightOccupancyEntity> stored = nightJpaRepo.findByRoomNumberForUpdate(roomNumber);

        Set<LocalDate> storedDates = stored.stream()
                .map(RoomNightOccupancyEntity::getStayDate)
                .collect(Collectors.toSet());

        List<RoomNightOccupancyEntity> toDelete = stored.stream()
                .filter(n -> !wanted.contains(n.getStayDate()))
                .toList();
        List<RoomNightOccupancyEntity> toInsert = wanted.stream()
                .filter(d -> !storedDates.contains(d))
                .map(d -> new RoomNightOccupancyEntity(roomNumber, d))
                .toList();

        if (toDelete.isEmpty() && toInsert.isEmpty()) return;

        try {
            nightJpaRepo.deleteAll(toDelete);
            nightJpaRepo.saveAll(toInsert);
            nightJpaRepo.flush();
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException(
                    String.format("[%s호] 이미 다른 예약이 점유한 날짜가 있어 스케줄을 저장할 수 없습니다.", roomNumber), e);
        }
    }

    // [체크인, 체크아웃) 구간을 날짜 단위로 푼다. 체크아웃일은 넣지 않는다.
    private Set<LocalDate> expandToNights(List<StayPeriod> periods) {
        Set<LocalDate> nights = new HashSet<>();
        for (StayPeriod period : periods) {
            for (LocalDate d = period.getCheckInDate(); d.isBefore(period.getCheckOutDate()); d = d.plusDays(1)) {
                nights.add(d);
            }
        }
        return nights;
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
            // 방 행 락을 잡은 뒤이므로 스케줄도 잠금 읽기로 최신 값을 가져온다.
            List<RoomScheduleEntity> schedules = scheduleJpaRepo.findByRoomNumberForUpdate(entity.getRoomNumber());
            return toDomain(entity, schedules);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<Room> findAll() {
        List<RoomEntity> entities = roomJpaRepo.findAll();
        if (entities.isEmpty()) return Collections.emptyList();

        List<String> roomNumbers = entities.stream().map(RoomEntity::getRoomNumber).toList();

        // N+1 방지: 객실별 개별 쿼리 대신 IN 절 한 번으로 스케줄 조회
        List<RoomScheduleEntity> allSchedules = scheduleJpaRepo.findByRoomNumberIn(roomNumbers);
        Map<String, List<RoomScheduleEntity>> scheduleMap = allSchedules.stream()
                .collect(Collectors.groupingBy(RoomScheduleEntity::getRoomNumber));

        List<Room> result = new ArrayList<>();
        for (RoomEntity e : entities) {
            List<RoomScheduleEntity> schedules = scheduleMap.getOrDefault(e.getRoomNumber(), Collections.emptyList());
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