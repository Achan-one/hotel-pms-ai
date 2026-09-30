package com.hotel.integration;

import com.hotel.domain.GuestPreference;
import com.hotel.domain.PaymentLedger;
import com.hotel.domain.Reservation;
import com.hotel.domain.Room;
import com.hotel.domain.RoomType;
import com.hotel.domain.StayPeriod;
import com.hotel.repository.RoomRepository;
import com.hotel.service.ReservationService;
import com.hotel.service.dto.RoomChangeRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 실제 MySQL 8.4에 Flyway를 적용하고 ddl-auto=validate로 띄운다.
 * H2 테스트가 검증하지 못하는 마이그레이션(V1, V2)과 InnoDB 락 동작을 확인한다.
 * Docker가 없으면 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class MySqlSchemaAndConcurrencyIT {

    // 시드 객실(V1) 중 이 테스트가 쓰는 방. 끝나면 되돌린다.
    private static final String ROOM_A = "0301";
    private static final String ROOM_B = "0304";
    private static final String ROOM_SOLO = "0303";
    private static final LocalDate FAR_FUTURE = LocalDate.of(2099, 1, 1);

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.defer-datasource-initialization", () -> "false");
        registry.add("spring.sql.init.mode", () -> "never");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private ReservationService reservationService;
    @Autowired
    private PlatformTransactionManager txManager;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM room_night_occupancy WHERE room_number IN (?, ?, ?)", ROOM_A, ROOM_B, ROOM_SOLO);
        jdbc.update("DELETE FROM room_schedules WHERE room_number IN (?, ?, ?)", ROOM_A, ROOM_B, ROOM_SOLO);
        jdbc.update("DELETE FROM reservations WHERE reservation_id LIKE 'IT-%'");
        jdbc.update("UPDATE rooms SET status = 'VACANT' WHERE room_number IN (?, ?, ?)", ROOM_A, ROOM_B, ROOM_SOLO);
    }

    @Test
    @DisplayName("V1, V2 마이그레이션이 모두 성공하고 엔티티 검증(validate)을 통과한다")
    void migrationsApplyAndSchemaValidates() {
        // 컨텍스트가 뜬 것 자체가 ddl-auto=validate 통과를 뜻한다.
        Integer applied = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1 AND version IN ('1', '2')",
                Integer.class);
        assertEquals(2, applied);
    }

    @Test
    @DisplayName("같은 방의 같은 날짜를 두 번 넣으면 기본키 위반으로 DB가 거부한다")
    void duplicateNightIsRejectedByPrimaryKey() {
        jdbc.update("INSERT INTO room_night_occupancy (room_number, stay_date) VALUES (?, ?)", ROOM_SOLO, FAR_FUTURE);

        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("INSERT INTO room_night_occupancy (room_number, stay_date) VALUES (?, ?)", ROOM_SOLO, FAR_FUTURE));
    }

    @Test
    @DisplayName("같은 방을 두 스레드가 동시에 잡으면 한 쪽만 성공한다")
    void onlyOneOfTwoConcurrentBookingsSucceeds() throws Exception {
        StayPeriod period = new StayPeriod(FAR_FUTURE, 2);
        TransactionTemplate tx = new TransactionTemplate(txManager);

        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> bookOnce = () -> {
            start.await();
            return tx.execute(status -> {
                Room room = roomRepository.findByRoomNumberForUpdate(ROOM_SOLO).orElseThrow();
                if (!room.tryBookPeriod(period)) {
                    return false;
                }
                roomRepository.save(room);
                return true;
            });
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = pool.submit(bookOnce);
            Future<Boolean> second = pool.submit(bookOnce);
            start.countDown();

            int successCount = (first.get(20, TimeUnit.SECONDS) ? 1 : 0) + (second.get(20, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successCount, "동시에 같은 방을 잡으면 정확히 한 건만 성공해야 합니다.");
        } finally {
            pool.shutdownNow();
        }

        Integer nights = jdbc.queryForObject(
                "SELECT COUNT(*) FROM room_night_occupancy WHERE room_number = ?", Integer.class, ROOM_SOLO);
        assertEquals(2, nights, "2박 점유가 박 단위로 정확히 한 번만 기록되어야 합니다.");
    }

    // 두 투숙객이 서로의 방으로 옮기려는 요청을 동시에 넣는다.
    // 상대 방이 재실 중이라 두 요청 모두 이동에는 실패하지만, 락을 잡는 경로는 그대로 지나간다.
    // 여기서는 교착이나 예외 없이 제한 시간 안에 끝나는지만 본다.
    @Test
    @DisplayName("서로 반대 방향의 룸체인지가 동시에 들어와도 멈추지 않는다")
    void oppositeRoomChangesFinishWithoutDeadlock() throws Exception {
        LocalDate checkIn = LocalDate.of(2099, 2, 1);
        checkInGuestTo("IT-A", ROOM_A, checkIn);
        checkInGuestTo("IT-B", ROOM_B, checkIn);

        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> aToB = () -> {
            start.await();
            return reservationService.processRoomChange(
                    new RoomChangeRequest("IT-A", ROOM_B, checkIn, "test")).success();
        };
        Callable<Boolean> bToA = () -> {
            start.await();
            return reservationService.processRoomChange(
                    new RoomChangeRequest("IT-B", ROOM_A, checkIn, "test")).success();
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> futures = List.of(pool.submit(aToB), pool.submit(bToA));
            start.countDown();
            for (Future<Boolean> f : futures) {
                // 제한 시간 안에 결과가 나오고, 서버 예외가 없어야 한다.
                assertTrue(f.get(20, TimeUnit.SECONDS) != null);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private void checkInGuestTo(String reservationId, String roomNumber, LocalDate checkIn) {
        Reservation reservation = new Reservation(
                reservationId, "IT-Guest", RoomType.SUPERIOR_TWIN,
                checkIn, 2, 1, "통합 테스트", GuestPreference.empty(),
                null, null, null,
                new PaymentLedger(PaymentLedger.PaymentType.PREPAID, 300_000L), null
        );
        reservationService.receiveReservations(List.of(reservation));
        reservationService.manualAssignRoom(reservationId, roomNumber);
        reservationService.processCheckIn(reservationId);
    }
}
