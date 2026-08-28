package com.rivoo.salon.domain.exception;

import com.rivoo.common.exception.RivooException;
import org.springframework.http.HttpStatus;

/**
 * No {@code clientSafeDetail()} override, deliberately: {@code SalonPublicSnapshotLoader} raises it
 * on the ANONYMOUS {@code GET /api/v1/salons/public/{slug}}, where the message echoes the
 * requested slug.
 * <p>
 * This changes nothing today: {@code SalonExceptionHandler#handleSalonNotFound} has its own
 * dedicated mapping and publishes {@code getMessage()} regardless of this method — necessarily so,
 * because appointment-service's {@code SalonServiceAdapter} keys on the {@code type} of that exact
 * response ({@code RivooErrorTypes.SALON_NOT_FOUND}). Leaving the restrictive default here means
 * that if the dedicated handler is ever removed, this exception falls back to the generic detail
 * instead of silently starting to echo slugs through {@code GlobalExceptionHandler}.
 */
public class SalonNotFoundException extends RivooException {

    public SalonNotFoundException(String identifier) {
        super("Salon not found: " + identifier, "salon-not-found", "Salon Not Found", HttpStatus.NOT_FOUND);
    }
}
