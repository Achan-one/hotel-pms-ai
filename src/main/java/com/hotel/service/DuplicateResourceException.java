package com.hotel.service;

/**
 * 이미 존재하는 리소스를 다시 만들려 할 때 던진다. API에서는 409로 응답한다.
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
