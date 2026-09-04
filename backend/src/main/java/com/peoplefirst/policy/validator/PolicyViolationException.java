package com.peoplefirst.policy.validator;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class PolicyViolationException extends RuntimeException {

    public PolicyViolationException(String message) {
        super(message);
    }
}
