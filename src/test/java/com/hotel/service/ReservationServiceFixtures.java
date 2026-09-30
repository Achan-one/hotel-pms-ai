package com.hotel.service;

import com.hotel.domain.QuotaPolicy;
import com.hotel.repository.ReservationRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.TagRepository;
import com.hotel.repository.memory.InMemoryCityLedgerRepository;
import com.hotel.repository.memory.InMemoryTagRepository;

/**
 * 인메모리 저장소로 ReservationService를 조립하는 테스트 전용 팩토리.
 * 운영 코드의 생성자는 하나뿐이라, 테스트가 필요한 기본값은 여기서 채운다.
 */
final class ReservationServiceFixtures {

    private ReservationServiceFixtures() {
    }

    /** AI 파서는 키가 없어 항상 빈 결과를 돌려주는 기본 파서를 쓴다. */
    static ReservationService inMemory(ReservationRepository reservations, RoomRepository rooms) {
        TagRepository tags = new InMemoryTagRepository();
        return inMemory(reservations, rooms, new AiPreferenceParser(tags, null, null), tags, new QuotaPolicy());
    }

    static ReservationService inMemory(ReservationRepository reservations,
                                       RoomRepository rooms,
                                       AiPreferenceParser aiParser,
                                       TagRepository tags,
                                       QuotaPolicy quota) {
        return new ReservationService(reservations, rooms, aiParser, tags, quota, new InMemoryCityLedgerRepository());
    }
}
