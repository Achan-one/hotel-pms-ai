package com.hotel.domain;

public class Room {
    private final String roomNumber;
    private final int floor;
    private final RoomType roomType;
    private final boolean nearElevator;
    private final boolean connerRoom;

    private boolean assigned;

    public Room(String roomNumber, int floor, RoomType roomType, boolean nearElevator, boolean connerRoom) {
        this.roomNumber = roomNumber;
        this.floor = floor;
        this.roomType = roomType;
        this.nearElevator = nearElevator;
        this.connerRoom = connerRoom;
        this.assigned = false;
    }

    public void assign() {
        if (this.assigned) {
            System.out.println("이미 배정된 객실입니다: " + roomNumber);
            return;
        }
        this.assigned = true;
    }

    public void release() {
        if (!this.assigned) {
            System.out.println("Assign이 아직 이루어지지 않은 객실입니다.");
            return;
        }
        this.assigned = false;
    }

    // 외부에 열어주는 public Getter
    public String getRoomNumber() { return roomNumber; }
    public int getFloor() { return floor; }
    public RoomType getRoomType() { return roomType; }
    public boolean isNearElevator() { return nearElevator; }
    public boolean isConnerRoom() { return connerRoom; }
    public boolean isAssigned() { return assigned; }

    @Override
    public String toString() {
        return String.format("[%s호 | %2d층 | %-10s | 엘베:%s | 코너:%s | %s]",
                roomNumber, floor, roomType.getDescription(),
                nearElevator ? "O" : "X",
                connerRoom ? "O" : "X",
                assigned ? "배정완료" : "공실"
        );
    }
}