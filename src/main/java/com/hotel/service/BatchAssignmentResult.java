package com.hotel.service;

import com.hotel.domain.Reservation;

import java.util.Collections;
import java.util.List;

public class BatchAssignmentResult {

    public record FailedAssignmentItem(Reservation reservation, String reason) {}

    private final List<Reservation> successfulAssignments;
    private final List<FailedAssignmentItem> failedAssignments;

    public BatchAssignmentResult(List<Reservation> successfulAssignments, List<FailedAssignmentItem> failedAssignments) {
        this.successfulAssignments = Collections.unmodifiableList(successfulAssignments);
        this.failedAssignments = Collections.unmodifiableList(failedAssignments);
    }

    public List<Reservation> getSuccessfulAssignments() {
        return successfulAssignments;
    }

    public List<FailedAssignmentItem> getFailedAssignments() {
        return failedAssignments;
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

    public String toSummaryString() {
        return String.format("""
            ==================================================
            📊 [배치 배정 결과 요약]
            - 총 요청 건수: %d건
            - 배정 성공: %d건
            - 배정 실패(만실 등): %d건
            ==================================================""",
                getTotalCount(), getSuccessCount(), getFailureCount());
    }
}