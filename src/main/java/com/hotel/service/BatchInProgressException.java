package com.hotel.service;

/**
 * 일괄 배정이나 해제가 진행 중인 동안 예약을 바꾸려 할 때 던진다. API에서는 423(Locked)으로 응답한다.
 */
public class BatchInProgressException extends RuntimeException {

    public BatchInProgressException(String message) {
        super(message);
    }
}
