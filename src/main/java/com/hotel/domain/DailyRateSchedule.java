package com.hotel.domain;

import java.time.LocalDate;
import java.util.*;

public class DailyRateSchedule {

    private final Map<LocalDate, Long> dailyRates;

    public DailyRateSchedule(Map<LocalDate, Long> rates) {
        this.dailyRates = new LinkedHashMap<>();
        if (rates != null) {
            rates.forEach((date, rate) -> this.dailyRates.put(date, Math.max(0L, rate)));
        }
    }

    public static DailyRateSchedule createDefault(LocalDate checkInDate, int stayNights, long dailyRate) {
        Map<LocalDate, Long> map = new LinkedHashMap<>();
        for (int i = 0; i < stayNights; i++) {
            map.put(checkInDate.plusDays(i), Math.max(0L, dailyRate));
        }
        return new DailyRateSchedule(map);
    }

    public static DailyRateSchedule empty() {
        return new DailyRateSchedule(Collections.emptyMap());
    }

    public long getRateForDate(LocalDate date) {
        return dailyRates.getOrDefault(date, 0L);
    }

    public void updateRate(LocalDate date, long rate) {
        this.dailyRates.put(date, Math.max(0L, rate));
    }

    public Map<LocalDate, Long> getDailyRates() {
        return Collections.unmodifiableMap(dailyRates);
    }

    public long calculateTotalRate() {
        return dailyRates.values().stream().mapToLong(Long::longValue).sum();
    }
}