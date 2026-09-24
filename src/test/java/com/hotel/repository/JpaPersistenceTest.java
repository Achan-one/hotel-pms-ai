package com.hotel.repository;

import com.hotel.domain.GuestPreference;
import com.hotel.domain.Reservation;
import com.hotel.domain.ReservationStatus;
import com.hotel.domain.RoomType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class JpaPersistenceTest {

    @Autowired
    private ReservationRepository reservationRepository;

    @Test
    @DisplayName("[영속성] 예약을 저장하고 영속성 컨텍스트 분리 후에도 DB에서 온전한 도메인 객체로 복원되어야 한다")
    void saveAndFind_DomainMappingIntegrity() {
        String rsvId = "RSV-DB-VERIFY-001";
        Reservation rsv = new Reservation(
                rsvId, "홍길동", RoomType.SUPERIOR_TWIN,
                LocalDate.of(2026, 9, 20), 3, "도쿄타워 전망 희망", GuestPreference.empty()
        );
        rsv.assignRoom("0501");

        // DB에 저장
        reservationRepository.save(rsv);

        // 다시 조회하여 도메인 불변 원장 및 운영 상태 복원 검증
        Optional<Reservation> found = reservationRepository.findById(rsvId);
        assertTrue(found.isPresent());
        Reservation entityToDomain = found.get();

        assertEquals("홍길동", entityToDomain.getGuestName());
        assertEquals(RoomType.SUPERIOR_TWIN, entityToDomain.getBookedRoomType());
        assertEquals("0501", entityToDomain.getAssignedRoomNumber());
        assertEquals(ReservationStatus.ASSIGNED, entityToDomain.getStatus());
        assertEquals("도쿄타워 전망 희망", entityToDomain.getRawRequestText());

        // 정리
        reservationRepository.deleteById(rsvId);
    }
}