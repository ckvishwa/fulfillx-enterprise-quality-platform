package com.fulfillx.authservice.auth;

import com.fulfillx.authservice.user.UserStatus;

/**
 * Thrown only after the password has already been verified — see
 * {@link AuthenticationService#login}. Revealing the account status at
 * that point is safe UX, not an enumeration risk, because the caller has
 * already proven they know valid credentials.
 */
public class AccountNotActiveException extends RuntimeException {

    private final UserStatus status;

    public AccountNotActiveException(UserStatus status) {
        super("Account is " + status.name().toLowerCase() + ".");
        this.status = status;
    }

    public UserStatus getStatus() {
        return status;
    }
}
