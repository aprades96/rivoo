package com.rivoo.salon.domain.exception;

import com.rivoo.common.exception.RivooException;
import org.springframework.http.HttpStatus;

/**
 * Raised when the call to auth-service during salon onboarding did not produce a Keycloak user.
 * <p>
 * The two factories below encode the distinction the HTTP status has to carry: {@link
 * #unavailable} means auth-service itself is the problem (it answered 5xx, was unreachable, or
 * its response was unusable) and maps to 502 — a genuine "the upstream is broken" signal that
 * should page an operator and that the caller may retry. {@link #rejected} means the exact
 * opposite: auth-service answered correctly and refused the request for a business reason (any
 * 4xx), which is not an infrastructure failure and must not be flattened into 502 — a 502 there
 * both hides the real reason from the caller and raises a false infrastructure alarm.
 */
public class AuthServiceException extends RivooException {

    private static final String UNAVAILABLE_ERROR_TYPE = "auth-service-error";
    private static final String UNAVAILABLE_ERROR_TITLE = "Auth Service Error";

    private AuthServiceException(String message, Throwable cause, String errorType,
                                 String errorTitle, HttpStatus httpStatus) {
        super(message, cause, errorType, errorTitle, httpStatus);
    }

    /** auth-service is the problem: 5xx, unreachable, or an unusable response. */
    public static AuthServiceException unavailable(String message, Throwable cause) {
        return new AuthServiceException(message, cause, UNAVAILABLE_ERROR_TYPE,
                UNAVAILABLE_ERROR_TITLE, HttpStatus.BAD_GATEWAY);
    }

    /** auth-service worked and said no: it answered 4xx for a business reason. */
    public static AuthServiceException rejected(String message, Throwable cause) {
        return new AuthServiceException(message, cause, OnboardingRejection.ERROR_TYPE,
                OnboardingRejection.ERROR_TITLE, OnboardingRejection.HTTP_STATUS);
    }
}
