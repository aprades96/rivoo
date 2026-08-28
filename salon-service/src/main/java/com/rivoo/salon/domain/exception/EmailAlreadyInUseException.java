package com.rivoo.salon.domain.exception;

import com.rivoo.common.exception.RivooException;
import org.springframework.http.HttpStatus;

public class EmailAlreadyInUseException extends RivooException {

    public EmailAlreadyInUseException(String email) {
        super("Email already in use: " + email, "email-already-in-use", "Email Already In Use", HttpStatus.CONFLICT);
    }

    /**
     * Publishes the message even though the only throw site ({@code OnboardingSagaService}) is the
     * ANONYMOUS {@code POST /api/v1/salons} — the one exception to the rule this method encodes.
     * <p>
     * That makes the endpoint an account-enumeration oracle: anyone can probe an address and learn
     * from the 409 whether it is already registered. This is NOT an oversight and must not be
     * "fixed" as part of a leak sweep. Hiding it degrades registration for legitimate users, who
     * would be told only that something went wrong and not that they already have an account, so
     * the trade-off is a product decision the product owner is deciding separately. Keeping the
     * override here (rather than relying on {@code SalonExceptionHandler#handleEmailAlreadyInUse},
     * which independently publishes the same message today) makes the decision explicit and
     * reviewable in one place instead of implicit in an advice.
     * <p>
     * When that decision lands: if the answer is "hide it", delete this override — the generic
     * detail then applies automatically and no handler change is needed.
     */
    @Override
    public String clientSafeDetail() {
        return getMessage();
    }
}
