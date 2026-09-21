package com.hotel.service;

import com.hotel.domain.Reservation;
import com.hotel.service.dto.AssignmentAlert;

import java.util.Collections;
import java.util.List;

public class BatchAssignmentResult {

    public record FailedAssignmentItem(Reservation reservation, String reason) {}

    private final List<Reservation> successfulAssignments;
    private final List<FailedAssignmentItem> failedAssignments;
    private final List<AssignmentAlert> hardRequestAlerts;

    public BatchAssignmentResult(List<Reservation> successfulAssignments,
                                 List<FailedAssignmentItem> failedAssignments,
                                 List<AssignmentAlert> hardRequestAlerts) {
        this.successfulAssignments = Collections.unmodifiableList(successfulAssignments);
        this.failedAssignments = Collections.unmodifiableList(failedAssignments);
        this.hardRequestAlerts = (hardRequestAlerts != null)
                ? Collections.unmodifiableList(hardRequestAlerts)
                : List.of();
    }

    public BatchAssignmentResult(List<Reservation> successfulAssignments, List<FailedAssignmentItem> failedAssignments) {
        this(successfulAssignments, failedAssignments, List.of());
    }

    public List<Reservation> getSuccessfulAssignments() {
        return successfulAssignments;
    }

    public List<FailedAssignmentItem> getFailedAssignments() {
        return failedAssignments;
    }

    public List<AssignmentAlert> getHardRequestAlerts() {
        return hardRequestAlerts;
    }

    public List<Reservation> getFailedReservations() {
        return failedAssignments.stream().map(FailedAssignmentItem::reservation).toList();
    }

    public int getTotalCount() {
        return successfulAssignments.size() + failedAssignments.size();
    }

    public int getSuccessCount() {
        return successfulAssignments.size();
    }

    public int getFailureCount() {
        return failedAssignments.size();
    }

    public int getAlertCount() {
        return hardRequestAlerts.size();
    }

    public String toSummaryString() {
        return String.format("""
            ==================================================
            📊 [배치 배정 결과 요약]
            - 총 요청 건수: %d건
            - 배정 성공: %d건 (⚠️ 하드 리퀘스트 주의 요망: %d건)
            - 배정 실패(만실 등): %d건
            ==================================================""",
                getTotalCount(), getSuccessCount(), getAlertCount(), getFailureCount());
    }
}