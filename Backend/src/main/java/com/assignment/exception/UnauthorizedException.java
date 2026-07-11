package com.assignment.exception;

public class UnauthorizedException extends CustomException {
    public UnauthorizedException(String message) {
        super(message, 401);
    }
}
