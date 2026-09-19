package com.hotel;

import com.hotel.domain.Room;
import com.hotel.domain.RoomType;
import com.hotel.repository.RoomRepository;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        RoomRepository repository = new RoomRepository();
        List<Room> allRooms = repository.findAll();

        System.out.println("=== 200개 객실 초기화 검증 ===");
        System.out.println("총 로딩된 객실 수: " + allRooms.size() + "개");

        // 1. 최고층 코너룸 확인
        repository.findByRoomNumber("1220")
                .ifPresent(room -> System.out.println("최고층 코너룸: " + room));

        // 2. 저층 엘베 인접룸 확인
        repository.findByRoomNumber("0309")
                .ifPresent(room -> System.out.println("저층 엘베앞: " + room));

        // 3. 타입별 방 개수 집계
        for (RoomType type : RoomType.values()) {
            long count = allRooms.stream()
                    .filter(r -> r.getRoomType() == type)
                    .count();
            System.out.printf("%-18s: %d개%n", type.getDescription(), count);
        }
    }
}