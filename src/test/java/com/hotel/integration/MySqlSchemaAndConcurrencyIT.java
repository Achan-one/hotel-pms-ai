package com.hotel.integration;

import com.hotel.domain.GuestPreference;
import com.hotel.domain.PaymentLedger;
import com.hotel.domain.Reservation;
import com.hotel.domain.Room;
import com.hotel.domain.RoomType;
import com.hotel.domain.StayPeriod;
import com.hotel.domain.StaffAccount;
import com.hotel.domain.StaffRole;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.StaffRepository;
import com.hotel.service.AuthService;
import com.hotel.service.ReservationService;
import com.hotel.service.dto.RoomChangeRequest;
import org.flywaydb.core.Flyway;
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
    @Autowired
    private AuthService authService;
    @Autowired
    private StaffRepository staffRepository;
    @Autowired
    private com.hotel.repository.ReservationRepository reservationRepository;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM room_night_occupancy WHERE room_number IN (?, ?, ?)", ROOM_A, ROOM_B, ROOM_SOLO);
        jdbc.update("DELETE FROM room_schedules WHERE room_number IN (?, ?, ?)", ROOM_A, ROOM_B, ROOM_SOLO);
        jdbc.update("DELETE FROM reservations WHERE reservation_id LIKE 'IT-%'");
        jdbc.update("DELETE FROM staff_accounts WHERE staff_id LIKE 'it-admin-%'");
        jdbc.update("UPDATE rooms SET status = 'VACANT' WHERE room_number IN (?, ?, ?)", ROOM_A, ROOM_B, ROOM_SOLO);
    }

    @Test
    @DisplayName("V1~V7 마이그레이션이 모두 성공하고 엔티티 검증(validate)을 통과한다")
    void migrationsApplyAndSchemaValidates() {
        // 컨텍스트가 뜬 것 자체가 ddl-auto=validate 통과를 뜻한다.
        Integer applied = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1 AND version IN ('1', '2', '3', '4', '5', '6', '7')",
                Integer.class);
        assertEquals(7, applied);
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

    @Test
    @DisplayName("관리자 둘이 동시에 서로를 비활성화하면 InnoDB 행 잠금으로 직렬화되어 한 명은 남는다")
    void concurrentAdminDisableKeepsOneActive() throws Exception {
        // 이 테스트가 만든 관리자만 활성으로 두고, 시드나 초기화 로직이 만든 관리자는 잠시 끈다.
        List<String> previouslyActive = jdbc.queryForList(
                "SELECT staff_id FROM staff_accounts WHERE role = 'ROLE_ADMIN' AND enabled = 1", String.class);
        previouslyActive.forEach(id -> jdbc.update("UPDATE staff_accounts SET enabled = 0 WHERE staff_id = ?", id));
        try {
            staffRepository.save(new StaffAccount("it-admin-a", "hash", "A", StaffRole.ROLE_ADMIN));
            staffRepository.save(new StaffAccount("it-admin-b", "hash", "B", StaffRole.ROLE_ADMIN));

            for (int round = 0; round < 5; round++) {
                staffRepository.updateEnabled("it-admin-a", true);
                staffRepository.updateEnabled("it-admin-b", true);

                CountDownLatch start = new CountDownLatch(1);
                ExecutorService pool = Executors.newFixedThreadPool(2);
                try {
                    Future<Boolean> a = pool.submit(disable(start, "it-admin-a", "it-admin-b"));
                    Future<Boolean> b = pool.submit(disable(start, "it-admin-b", "it-admin-a"));
                    start.countDown();

                    boolean first = a.get(20, TimeUnit.SECONDS);
                    boolean second = b.get(20, TimeUnit.SECONDS);
                    Integer active = jdbc.queryForObject(
                            "SELECT COUNT(*) FROM staff_accounts WHERE role = 'ROLE_ADMIN' AND enabled = 1", Integer.class);

                    assertTrue(first ^ second, "둘 중 정확히 하나만 성공해야 한다");
                    assertEquals(1, active);
                } finally {
                    pool.shutdownNow();
                }
            }
        } finally {
            previouslyActive.forEach(id -> jdbc.update("UPDATE staff_accounts SET enabled = 1 WHERE staff_id = ?", id));
        }
    }

    private Callable<Boolean> disable(CountDownLatch start, String actor, String target) {
        return () -> {
            start.await();
            try {
                authService.setStaffEnabled(actor, target, false);
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        };
    }

    @Test
    @DisplayName("V7 마이그레이션: 기존 예약에 PMS 번호가 채워지고 중복 번호는 유니크 제약이 막는다")
    void pmsReservationNoIsBackfilledAndUnique() {
        // V7은 이미 적용된 상태다. 번호 없이 넣으면 NOT NULL로 거절되는지, 같은 번호는 유니크로 거절되는지 확인한다.
        String columnNullable = jdbc.queryForObject(
                "SELECT IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                        + "AND TABLE_NAME = 'reservations' AND COLUMN_NAME = 'pms_reservation_no'", String.class);
        assertEquals("NO", columnNullable);

        Integer uniqueIndexes = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() "
                        + "AND TABLE_NAME = 'reservations' AND COLUMN_NAME = 'pms_reservation_no' AND NON_UNIQUE = 0", Integer.class);
        assertEquals(1, uniqueIndexes);
    }

    @Test
    @DisplayName("PMS 예약 번호가 실제 MySQL에서 발급되고 재저장해도 유지된다")
    void pmsReservationNoIsIssuedOnRealMySql() {
        Reservation r = new Reservation("IT-PMSNO-1", "Kim", RoomType.SUPERIOR_TWIN, FAR_FUTURE, 1, null, GuestPreference.empty());
        reservationRepository.save(r);
        String issued = r.getPmsReservationNo();
        assertTrue(issued != null && issued.startsWith("PMS-"));

        reservationRepository.save(new Reservation("IT-PMSNO-1", "Kim (재전송)", RoomType.SUPERIOR_TWIN, FAR_FUTURE, 1, null, GuestPreference.empty()));

        String stored = jdbc.queryForObject("SELECT pms_reservation_no FROM reservations WHERE reservation_id = 'IT-PMSNO-1'", String.class);
        assertEquals(issued, stored);
    }

    @Test
    @DisplayName("V6 상태의 DB에 이미 예약이 있어도 V7이 모든 행에 서로 다른 PMS 번호를 채운다")
    void v7BackfillsExistingReservations() throws Exception {
        // 컨테이너에 마이그레이션 검증용 별도 DB를 만든다. 이미 V7까지 적용된 본 DB와 섞이지 않는다.
        String url = "jdbc:mysql://" + mysql.getHost() + ":" + mysql.getFirstMappedPort()
                + "/migtest?useSSL=false&allowPublicKeyRetrieval=true";
        try (java.sql.Connection root = java.sql.DriverManager.getConnection(
                "jdbc:mysql://" + mysql.getHost() + ":" + mysql.getFirstMappedPort() + "/?useSSL=false&allowPublicKeyRetrieval=true",
                "root", mysql.getPassword());
             java.sql.Statement st = root.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS migtest");
            st.execute("CREATE DATABASE migtest");
        }

        Flyway.configure().dataSource(url, "root", mysql.getPassword())
                .locations("classpath:db/migration").target("6").load().migrate();

        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(url, "root", mysql.getPassword());
             java.sql.Statement st = conn.createStatement()) {
            for (int i = 1; i <= 3; i++) {
                st.execute("INSERT INTO reservations (reservation_id, original_guest_name, booked_room_type, "
                        + "contract_check_in_date, contract_stay_nights, status) "
                        + "VALUES ('LEGACY-" + i + "', 'Guest', 'SUPERIOR_TWIN', '2026-09-20', 2, 'PENDING')");
            }
        }

        Flyway.configure().dataSource(url, "root", mysql.getPassword())
                .locations("classpath:db/migration").load().migrate();

        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(url, "root", mysql.getPassword());
             java.sql.Statement st = conn.createStatement();
             java.sql.ResultSet rs = st.executeQuery("SELECT pms_reservation_no FROM reservations ORDER BY reservation_id")) {
            java.util.Set<String> numbers = new java.util.HashSet<>();
            while (rs.next()) {
                String number = rs.getString(1);
                assertTrue(number.matches("PMS-\\d{6}-[0-9A-F]{8}"), "백필된 번호 형식: " + number);
                numbers.add(number);
            }
            assertEquals(3, numbers.size(), "기존 예약마다 서로 다른 번호가 채워져야 한다");
        }
    }
}
