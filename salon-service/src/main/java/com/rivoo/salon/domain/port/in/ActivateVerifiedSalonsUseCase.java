package com.rivoo.salon.domain.port.in;

/**
 * Promotes the salons whose owner has confirmed their email address to publicly visible.
 * <p>
 * A salon registered through {@code POST /api/v1/salons} stays {@code ONBOARDING} - and therefore
 * absent from every public surface - until its owner proves the address is theirs. This is the step
 * that ends that wait, and it must run without anyone touching anything by hand.
 */
public interface ActivateVerifiedSalonsUseCase {

    /**
     * @return how many salons were promoted to {@code ACTIVE} on this pass
     */
    int activateVerifiedOwners();
}
