package com.hotel.service.dto;

import java.util.List;
import java.util.function.Function;

/**
 * 목록 조회 결과 한 페이지. page는 0부터 시작한다.
 */
public record PageResult<T>(List<T> items, int page, int size, long total) {

    public static final int DEFAULT_SIZE = 50;
    public static final int MAX_SIZE = 500;

    public static int normalizeSize(Integer size) {
        if (size == null || size <= 0) return DEFAULT_SIZE;
        return Math.min(size, MAX_SIZE);
    }

    public static int normalizePage(Integer page) {
        return (page == null || page < 0) ? 0 : page;
    }

    /**
     * 이미 메모리에 올라온 전체 목록에서 한 페이지를 잘라낸다.
     */
    public static <T> PageResult<T> slice(List<T> all, int page, int size) {
        long from = (long) page * size;
        if (from >= all.size()) {
            return new PageResult<>(List.of(), page, size, all.size());
        }
        int end = (int) Math.min(from + size, all.size());
        return new PageResult<>(List.copyOf(all.subList((int) from, end)), page, size, all.size());
    }

    public <R> PageResult<R> map(Function<T, R> mapper) {
        return new PageResult<>(items.stream().map(mapper).toList(), page, size, total);
    }
}
