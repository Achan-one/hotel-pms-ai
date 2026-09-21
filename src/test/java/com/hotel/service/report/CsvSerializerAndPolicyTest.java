package com.hotel.service.report;

import com.hotel.domain.ReservationStatus;
import com.hotel.service.dto.ReservationSearchCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class CsvSerializerAndPolicyTest {

    record SampleItem(String id, String memo, int amount) {}

    @Test
    @DisplayName("[CSV 직렬화] 특수문자(쉼표, 따옴표, 개행)가 포함된 필드는 RFC 4180에 맞게 이스케이프되어야 한다")
    void serialize_EscapesSpecialCharactersCorrectly() {
        List<String> headers = List.of("ID", "MEMO", "AMOUNT");
        List<Function<SampleItem, Object>> mappers = List.of(
                SampleItem::id,
                SampleItem::memo,
                SampleItem::amount
        );

        SampleItem item1 = new SampleItem("1", "Hello, World", 1000);
        SampleItem item2 = new SampleItem("2", "He said \"Hi\"", 2000);
        SampleItem item3 = new SampleItem("3", "Line1\nLine2", 3000);

        String csv = CsvSerializer.serialize(headers, mappers, List.of(item1, item2, item3));

        assertTrue(csv.contains("\"Hello, World\""), "쉼표 포함 문자열은 큰따옴표로 감싸져야 합니다.");
        assertTrue(csv.contains("\"He said \"\"Hi\"\"\""), "큰따옴표는 이스케이프(\"\") 처리되어야 합니다.");
        assertTrue(csv.contains("\"Line1\nLine2\""), "개행 포함 문자열은 큰따옴표로 감싸져야 합니다.");
    }

    @Test
    @DisplayName("[ReportPolicy] 조건이 전혀 없는 빈 검색 조건 전달 시 전체 덤프 방어로 예외가 발생해야 한다")
    void validateExportCondition_EmptyCondition_ThrowsException() {
        ReservationSearchCondition emptyCondition = new ReservationSearchCondition(
                null, null, null, null, null, null, null
        );

        assertThrows(IllegalArgumentException.class, () ->
                ReportPolicy.validateExportCondition(emptyCondition));

        assertThrows(IllegalArgumentException.class, () ->
                ReportPolicy.validateExportCondition(null));
    }

    @Test
    @DisplayName("[ReportPolicy] 31일을 초과하는 기간 지정 시 예외가 발생해야 한다")
    void validateExportCondition_ExceedingMaxDays_ThrowsException() {
        LocalDate today = LocalDate.of(2026, 9, 21);
        ReservationSearchCondition validCondition = new ReservationSearchCondition(
                null, null, today, 31, null, null, null
        );
        assertDoesNotThrow(() -> ReportPolicy.validateExportCondition(validCondition));

        ReservationSearchCondition invalidCondition = new ReservationSearchCondition(
                null, null, today, 32, null, null, null
        );
        assertThrows(IllegalArgumentException.class, () ->
                ReportPolicy.validateExportCondition(invalidCondition));
    }
}