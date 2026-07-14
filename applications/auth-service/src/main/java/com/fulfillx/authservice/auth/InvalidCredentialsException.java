package com.fulfillx.authservice.auth;

/**
 * Thrown for both "no such user" and "wrong password" — deliberately
 * indistinguishable to callers so login responses don't leak which emails
 * are registered (user-enumeration hardening).
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password.");
    }
}
