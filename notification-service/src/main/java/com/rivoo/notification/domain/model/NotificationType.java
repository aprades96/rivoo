package com.rivoo.notification.domain.model;

public enum NotificationType {
    WELCOME,
    /**
     * Someone tried to register a salon with an address that already has an account. Deliberately
     * NOT reused from WELCOME: the WELCOME body claims "tu salon esta activo", which for this
     * recipient is false — nothing was created for them. The registration endpoint answers
     * identically whether or not the address existed, so this mail is the ONLY place the two cases
     * differ, and it has to say the true thing.
     */
    REGISTRATION_ATTEMPT_EXISTING_ACCOUNT,
    APPOINTMENT_CONFIRMATION,
    APPOINTMENT_REMINDER,
    APPOINTMENT_CANCELLATION,
    PAYMENT_FAILED,
    SUBSCRIPTION_CANCELED
}
