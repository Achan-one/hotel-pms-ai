package com.hotel.service;

import com.hotel.domain.Reservation;

import java.util.Collections;
import java.util.List;

public class BatchAssignmentResult {

    private final List<Reservation> successfulAssignments;
    private final List<Reservation> failedAssignments;

    public BatchAssignmentResult(List<Reservation> successfulAssignments, List<Reservation> failedAssignments) {
        this.successfulAssignments = Collections.unmodifiableList(successfulAssignments);
        this.failedAssignments = Collections.unmodifiableList(failedAssignments);
    }

    public List<Reservation> getSuccessfulAssignments() {
        return successfulAssignments;
    }

    public List<Reservation> getFailedAssignments() {
        return failedAssignments;
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

    @Override
    public String toString() {
        return String.format("BatchAssignmentResult[총 %d건 | 성공: %d건 | 실패(만실 등): %d건]",
                getTotalCount(), getSuccessCount(), getFailureCount());
    }
}