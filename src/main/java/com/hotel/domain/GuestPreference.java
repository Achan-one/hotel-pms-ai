package com.hotel.domain;

public class GuestPreference {

    public enum FloorPref { HIGH, LOW, NONE }
    public enum ElevatorPref { NEAR, AWAY, NONE }
    public enum CornerPref { PREFER, AVOID, NONE }

    private final FloorPref floorPref;
    private final ElevatorPref elevatorPref;
    private final CornerPref cornerPref;
    private final boolean preferQuiet; // 조용한 방 선호

    public GuestPreference(FloorPref floorPref, ElevatorPref elevatorPref, CornerPref cornerPref, boolean preferQuiet) {
        this.floorPref = (floorPref != null) ? floorPref : FloorPref.NONE;
        this.elevatorPref = (elevatorPref != null) ? elevatorPref : ElevatorPref.NONE;
        this.cornerPref = (cornerPref != null) ? cornerPref : CornerPref.NONE;
        this.preferQuiet = preferQuiet;
    }

    public static GuestPreference empty() {
        return new GuestPreference(FloorPref.NONE, ElevatorPref.NONE, CornerPref.NONE, false);
    }

    // 외부에 열어주는 public Getter
    public FloorPref getFloorPref() { return floorPref; }
    public ElevatorPref getElevatorPref() { return elevatorPref; }
    public CornerPref getCornerPref() { return cornerPref; }
    public boolean isPreferQuiet() { return preferQuiet; }

    @Override
    public String toString() {
        return String.format("[선호도 | 층:%s | EV:%s | 코너:%s | 조용함:%s]",
                floorPref, elevatorPref, cornerPref, preferQuiet ? "O" : "X");
    }
}