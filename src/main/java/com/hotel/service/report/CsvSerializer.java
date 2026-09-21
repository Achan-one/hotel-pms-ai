package com.hotel.service.report;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * RFC 4180 규격을 준수하는 순수 Java CSV 직렬화 엔진.
 * - 필드 내 쉼표(,), 큰따옴표("), 개행 문자(\r, \n) 포함 시 큰따옴표 이스케이프 지원
 * - 표준 CRLF(\r\n) 개행 포맷 유지
 */
public final class CsvSerializer {

    private static final String CRLF = "\r\n";
    private static final String DELIMITER = ",";

    private CsvSerializer() {
        // 인스턴스화 방지
    }

    /**
     * DTO 목록을 지정된 헤더와 매핑 함수에 따라 RFC 4180 CSV 문자열로 변환합니다.
     *
     * @param headers        CSV 헤더 배열
     * @param rowMapperList  각 열에 대응하는 데이터 추출 매핑 함수 목록
     * @param dataList       직렬화할 DTO 데이터 리스트
     * @param <T>            데이터 모델 타입
     * @return 표준 CSV 형식의 문자열
     */
    public static <T> String serialize(List<String> headers,
                                       List<Function<T, Object>> rowMapperList,
                                       List<T> dataList) {
        Objects.requireNonNull(headers, "headers는 null일 수 없습니다.");
        Objects.requireNonNull(rowMapperList, "rowMapperList는 null일 수 없습니다.");

        if (headers.size() != rowMapperList.size()) {
            throw new IllegalArgumentException(String.format(
                    "헤더 컬럼 수(%d)와 데이터 매퍼 수(%d)가 일치하지 않습니다.",
                    headers.size(), rowMapperList.size()
            ));
        }

        StringBuilder sb = new StringBuilder();

        // 1. 헤더 렌더링
        for (int i = 0; i < headers.size(); i++) {
            sb.append(escapeCsvField(headers.get(i)));
            if (i < headers.size() - 1) {
                sb.append(DELIMITER);
            }
        }
        sb.append(CRLF);

        // 2. 데이터 행 렌더링
        if (dataList != null && !dataList.isEmpty()) {
            for (T item : dataList) {
                for (int i = 0; i < rowMapperList.size(); i++) {
                    Object value = rowMapperList.get(i).apply(item);
                    sb.append(escapeCsvField(value));
                    if (i < rowMapperList.size() - 1) {
                        sb.append(DELIMITER);
                    }
                }
                sb.append(CRLF);
            }
        }

        return sb.toString();
    }

    /**
     * 개별 필드 값을 RFC 4180 규칙에 따라 이스케이프합니다.
     */
    public static String escapeCsvField(Object value) {
        if (value == null) {
            return "";
        }

        String text = String.valueOf(value);
        boolean containsSpecialChar = text.contains(",") || text.contains("\"")
                || text.contains("\n") || text.contains("\r");

        if (!containsSpecialChar) {
            return text;
        }

        // 큰따옴표(")는 2개("")로 치환하고 필드 전체를 큰따옴표로 감쌈
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}