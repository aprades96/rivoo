package com.rivoo.salon.domain.exception;

import com.rivoo.common.exception.RivooException;
import org.springframework.http.HttpStatus;

/**
 * No {@code clientSafeDetail()} override, deliberately: the only throw site is
 * {@code OnboardingSagaService}, i.e. the ANONYMOUS {@code POST /api/v1/salons}.
 * <p>
 * As with {@code SalonNotFoundException}, this changes nothing today —
 * {@code SalonExceptionHandler#handleSlugAlreadyExists} publishes {@code getMessage()} through its
 * own dedicated mapping, and the slug in that message is one the caller just submitted. The
 * restrictive default here is the fallback if that handler ever goes away.
 */
public class SlugAlreadyExistsException extends RivooException {

    public SlugAlreadyExistsException(String slug) {
        super("Slug already exists: " + slug, "slug-already-exists", "Slug Already Exists", HttpStatus.CONFLICT);
    }
}
