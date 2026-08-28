package com.rivoo.salon.domain.exception;

import com.rivoo.common.exception.RivooException;
import org.springframework.http.HttpStatus;

/**
 * Raised when the call to billing-service during salon onboarding did not create the
 * subscription.
 * <p>
 * Same split as {@link AuthServiceException}: {@link #unavailable} means billing-service itself
 * is the problem (5xx, unreachable, or an unusable response) and stays a 502; {@link #rejected}
 * means billing-service answered correctly and refused the request for a business reason (any
 * 4xx — e.g. 422 for a tenant that already has a subscription), which is not an infrastructure
 * failure and must not be flattened into 502.
 * <p>
 * Same {@code clientSafeDetail()} decision as {@link AuthServiceException}, for the same reason:
 * the only caller is the ANONYMOUS {@code POST /api/v1/salons}, so the restrictive default stays.
 */
public class BillingServiceException extends RivooException {

    private static final String UNAVAILABLE_ERROR_TYPE = "billing-service-error";
    private static final String UNAVAILABLE_ERROR_TITLE = "Billing Service Error";

    private BillingServiceException(String message, Throwable cause, String errorType,
                                    String errorTitle, HttpStatus httpStatus) {
        super(message, cause, errorType, errorTitle, httpStatus);
    }

    /** billing-service is the problem: 5xx, unreachable, or an unusable response. */
    public static BillingServiceException unavailable(String message, Throwable cause) {
        return new BillingServiceException(message, cause, UNAVAILABLE_ERROR_TYPE,
                UNAVAILABLE_ERROR_TITLE, HttpStatus.BAD_GATEWAY);
    }

    /** billing-service worked and said no: it answered 4xx for a business reason. */
    public static BillingServiceException rejected(String message, Throwable cause) {
        return new BillingServiceException(message, cause, OnboardingRejection.ERROR_TYPE,
                OnboardingRejection.ERROR_TITLE, OnboardingRejection.HTTP_STATUS);
    }
}
