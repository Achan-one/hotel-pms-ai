package com.hotel;

import com.hotel.domain.GuestPreference;
import com.hotel.domain.Reservation;
import com.hotel.domain.Room;
import com.hotel.domain.RoomType;
import com.hotel.repository.RoomRepository;
import com.hotel.service.AiPreferenceParser;
import com.hotel.service.RoomAssigner;

import java.util.Optional;

public class Main {
    public static void main(String[] args) {
        RoomRepository repository = new RoomRepository();
        RoomAssigner assigner = new RoomAssigner(repository);
        AiPreferenceParser aiParser = new AiPreferenceParser();

        System.out.println("==================================================");
        System.out.println("🏨 호텔 PMS AI 기반 객실 배정 시뮬레이션");
        System.out.println("==================================================\n");

        // 케이스 1: 뉘앙스 한국어 요청 (어르신 동반 -> 저층, 엘베 근처 추론)
        String memo1 = "부모님이 무릎이 안 좋으셔서 계단이나 걷는 거 최대한 적었으면 좋겠습니다.";
        System.out.println("🤖 [AI 분석 중] 원문: \"" + memo1 + "\"");
        GuestPreference pref1 = aiParser.parse(memo1);
        System.out.println("=> 추출 결과: " + pref1);

        Reservation res1 = new Reservation(
                "RSV-AI-01", "이순신", RoomType.SUPERIOR_DOUBLE, 2, memo1, pref1
        );
        executeAssignment(assigner, res1);

        System.out.println();

        // 케이스 2: 일본어 복합 요청 (고층, 조용함, 안쪽 방 선호)
        String memo2 = "できれば高層階の静かな部屋を希望します。エレベーターから離れた奥の部屋が良いです。";
        System.out.println("🤖 [AI 분석 중] 원문: \"" + memo2 + "\"");
        GuestPreference pref2 = aiParser.parse(memo2);
        System.out.println("=> 추출 결과: " + pref2);

        Reservation res2 = new Reservation(
                "RSV-AI-02", "田中", RoomType.MODERATE_DOUBLE, 3, memo2, pref2
        );
        executeAssignment(assigner, res2);
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
            System.out.println("=> [배정 실패] 잔여 객실 없음");
        }
        System.out.println("--------------------------------------------------");
    }
}