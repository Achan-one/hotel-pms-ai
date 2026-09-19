package com.hotel;

import com.hotel.domain.GuestPreference;
import com.hotel.domain.GuestPreference.CornerPref;
import com.hotel.domain.GuestPreference.ElevatorPref;
import com.hotel.domain.GuestPreference.FloorPref;
import com.hotel.domain.Reservation;
import com.hotel.domain.Room;
import com.hotel.domain.RoomType;
import com.hotel.repository.RoomRepository;
import com.hotel.service.RoomAssigner;

import java.util.Optional;

public class Main {
    public static void main(String[] args) {
        RoomRepository repository = new RoomRepository();
        RoomAssigner assigner = new RoomAssigner(repository);

        System.out.println("==================================================");
        System.out.println("🏨 호텔 PMS 객실 배정 엔진 시뮬레이션");
        System.out.println("총 등록 객실 수: " + repository.findAll().size() + "실 (정상 기준: 191실)");
        System.out.println("==================================================\n");

        // 손님 1: 4박 연박 + 고층/코너/조용한 방 선호 (모더레이트 더블)
        GuestPreference longStayPref = new GuestPreference(
                FloorPref.HIGH, ElevatorPref.AWAY, CornerPref.PREFER, true
        );
        Reservation resLongStay = new Reservation(
                "RSV-101", "장기투숙객(4박)", RoomType.MODERATE_DOUBLE,
                4, "조용하고 높은 코너 방 부탁합니다.", longStayPref
        );

        // 손님 2: 1박 단박 + 저층/엘리베이터 앞 선호 (슈페리얼 더블)
        GuestPreference mobilityPref = new GuestPreference(
                FloorPref.LOW, ElevatorPref.NEAR, CornerPref.NONE, false
        );
        Reservation resShortStay = new Reservation(
                "RSV-102", "단기투숙객(1박)", RoomType.SUPERIOR_DOUBLE,
                1, "무릎이 안 좋아 엘베 앞 낮은 층 원해요.", mobilityPref
        );

        // 손님 3: 선호도 없는 기본 1박 예약 (슈페리얼 트윈)
        Reservation resDefault = new Reservation(
                "RSV-103", "일반투숙객(1박)", RoomType.SUPERIOR_TWIN,
                1, "", GuestPreference.empty()
        );

        // 배정 파이프라인 가동
        executeAssignment(assigner, resLongStay);
        executeAssignment(assigner, resShortStay);
        executeAssignment(assigner, resDefault);

        System.out.println("\n[배정 완료 후 객실 상태 요약]");
        long assignedCount = repository.findAll().stream().filter(Room::isAssigned).count();
        System.out.println("총 배정 완료 객실: " + assignedCount + "실 / 공실: " + (191 - assignedCount) + "실");
    }

    private static void executeAssignment(RoomAssigner assigner, Reservation res) {
        Optional<Room> assignedRoomOpt = assigner.assign(res);

        System.out.println("--------------------------------------------------");
        System.out.println("요청: " + res);
        if (assignedRoomOpt.isPresent()) {
            Room room = assignedRoomOpt.get();
            System.out.printf("=> [배정 확정] %s호 | %d층 | %s | 엘베:%s | 코너:%s%n",
                    room.getRoomNumber(),
                    room.getFloor(),
                    room.getRoomType().getDescription(),
                    room.isNearElevator() ? "인접" : "이격",
                    room.isCorner() ? "코너" : "일반"
            );
        } else {
            System.out.println("=> [배정 실패] 조건에 맞는 잔여 객실 없음");
        }
    }
}