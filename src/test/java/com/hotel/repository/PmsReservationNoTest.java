package com.hotel.repository;

import com.hotel.domain.BookingChannelInfo;
import com.hotel.domain.BreakfastOption;
import com.hotel.domain.GuestPreference;
import com.hotel.domain.PaymentLedger;
import com.hotel.domain.TagPreference;
import com.hotel.service.dto.ReservationSearchCondition;
import com.hotel.domain.PmsReservationNumber;
import com.hotel.domain.Reservation;
import com.hotel.domain.RoomType;
import com.hotel.repository.memory.InMemoryReservationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class PmsReservationNoTest {

    private static final Pattern FORMAT = Pattern.compile("PMS-\\d{6}-[2-9A-HJ-NP-Z]{8}");

    @Autowired
    private ReservationRepository repository;

    @AfterEach
    void cleanUp() {
        repository.clear();
    }

    private Reservation reservation(String id, String guest) {
        return new Reservation(id, guest, RoomType.SUPERIOR_TWIN, LocalDate.of(2027, 9, 1), 2, null, GuestPreference.empty());
    }

    @Test
    @DisplayName("[PMS 번호] 형식은 PMS-yymmdd-8자리이고, 헷갈리는 글자(0, O, 1, I)는 쓰지 않는다")
    void formatAndAlphabet() {
        String number = PmsReservationNumber.generate(LocalDate.of(2026, 9, 20));

        assertTrue(FORMAT.matcher(number).matches(), number);
        assertTrue(number.startsWith("PMS-260920-"));
    }

    @Test
    @DisplayName("[PMS 번호] 많이 만들어도 겹치지 않는다")
    void generatedNumbersAreUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 20_000; i++) {
            assertTrue(seen.add(PmsReservationNumber.generate()), "중복 번호 발생");
        }
    }

    @Test
    @DisplayName("[PMS 번호] 저장할 때 발급되어 도메인 객체에도 채워지고, 다시 읽어도 같다")
    void issuedOnSaveAndStable() {
        Reservation r = reservation("OTA-1", "Tanaka");
        assertNull(r.getPmsReservationNo(), "저장 전에는 번호가 없다");

        repository.save(r);

        assertNotNull(r.getPmsReservationNo());
        assertTrue(FORMAT.matcher(r.getPmsReservationNo()).matches());
        Reservation loaded = repository.findById("OTA-1").orElseThrow();
        assertEquals(r.getPmsReservationNo(), loaded.getPmsReservationNo());

        // 수정해서 다시 저장해도 번호는 그대로다.
        loaded.updateOperationalDetails("변경", null, null, null);
        repository.save(loaded);
        assertEquals(r.getPmsReservationNo(), repository.findById("OTA-1").orElseThrow().getPmsReservationNo());
    }

    @Test
    @DisplayName("[PMS 번호] 같은 OTA 예약 ID가 다시 들어와 덮어써져도 번호는 바뀌지 않는다")
    void resendKeepsTheNumber() {
        Reservation first = reservation("OTA-2", "Suzuki");
        repository.save(first);
        String issued = first.getPmsReservationNo();

        Reservation resent = reservation("OTA-2", "Suzuki (재전송)");
        repository.save(resent);

        assertEquals(issued, resent.getPmsReservationNo());
        assertEquals(issued, repository.findById("OTA-2").orElseThrow().getPmsReservationNo());
        assertEquals(1, repository.count());
    }

    @Test
    @DisplayName("[PMS 번호] 서로 다른 예약은 서로 다른 번호를 받는다 (saveAll 포함)")
    void differentReservationsGetDifferentNumbers() {
        Reservation a = reservation("OTA-3", "A");
        Reservation b = reservation("OTA-4", "B");

        repository.saveAll(List.of(a, b));

        assertNotNull(a.getPmsReservationNo());
        assertNotNull(b.getPmsReservationNo());
        assertNotEquals(a.getPmsReservationNo(), b.getPmsReservationNo());
    }

    @Test
    @DisplayName("[PMS 번호] 인메모리 저장소도 같은 규칙으로 발급하고 재저장 시 유지한다")
    void inMemoryRepositoryFollowsTheSameRule() {
        InMemoryReservationRepository memory = new InMemoryReservationRepository();
        Reservation first = reservation("OTA-5", "A");
        memory.save(first);
        String issued = first.getPmsReservationNo();
        assertNotNull(issued);

        Reservation resent = reservation("OTA-5", "A2");
        memory.save(resent);

        assertEquals(issued, resent.getPmsReservationNo());
    }

    private Reservation otaReservation(String id, String channelNo) {
        return new Reservation(id, "Tanaka", RoomType.SUPERIOR_TWIN, LocalDate.of(2027, 9, 1), 2, 1,
                null, GuestPreference.empty(), TagPreference.empty(),
                new BookingChannelInfo(BookingChannelInfo.ChannelType.AGODA, channelNo, "플랜"),
                BreakfastOption.none(), new PaymentLedger(PaymentLedger.PaymentType.PREPAID, 0), null);
    }

    private List<String> searchIds(ReservationRepository repo, String query) {
        ReservationSearchCondition condition = new ReservationSearchCondition(query, null, null, null, null, null, null, null, null, null);
        return repo.search(condition).stream().map(Reservation::getReservationId).toList();
    }

    @Test
    @DisplayName("[예약번호 검색] 한 검색어로 PMS 예약 번호, 기존 예약 ID, OTA 예약번호를 모두 찾을 수 있다")
    void searchFindsByPmsNumberReservationIdAndOtaNumber() {
        Reservation a = otaReservation("RSV-A-001", "AGODA-778899");
        Reservation b = otaReservation("RSV-B-002", "AGODA-112233");
        repository.saveAll(List.of(a, b));

        // 기존 예약 ID로 (이전과 똑같이 동작)
        assertEquals(List.of("RSV-A-001"), searchIds(repository, "RSV-A"));
        // OTA 예약번호로
        assertEquals(List.of("RSV-B-002"), searchIds(repository, "112233"));
        // PMS 예약 번호로 (대소문자 무시, 일부만 입력해도)
        assertEquals(List.of("RSV-A-001"), searchIds(repository, a.getPmsReservationNo().toLowerCase()));
        assertEquals(List.of("RSV-B-002"), searchIds(repository, b.getPmsReservationNo().substring(11)));
    }

    @Test
    @DisplayName("[예약번호 검색] 인메모리 저장소도 같은 세 가지 번호로 찾는다")
    void inMemorySearchMatchesAllThreeNumbers() {
        InMemoryReservationRepository memory = new InMemoryReservationRepository();
        Reservation a = otaReservation("RSV-A-001", "AGODA-778899");
        memory.save(a);

        assertEquals(List.of("RSV-A-001"), searchIds(memory, "778899"));
        assertEquals(List.of("RSV-A-001"), searchIds(memory, a.getPmsReservationNo()));
        assertEquals(List.of("RSV-A-001"), searchIds(memory, "rsv-a"));
        assertTrue(searchIds(memory, "없는번호").isEmpty());
    }
}
