package com.rivoo.appointment.domain.model;

import java.util.EnumSet;
import java.util.Set;

public enum AppointmentStatus {
    PENDING,
    CONFIRMED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    NO_SHOW;

    private static final Set<AppointmentStatus> TERMINAL = EnumSet.of(COMPLETED, CANCELLED, NO_SHOW);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public boolean canTransitionTo(AppointmentStatus target) {
        return switch (this) {
            case PENDING -> target == CONFIRMED || target == CANCELLED;
            case CONFIRMED -> target == IN_PROGRESS || target == CANCELLED || target == NO_SHOW;
            case IN_PROGRESS -> target == COMPLETED;
            case COMPLETED, CANCELLED, NO_SHOW -> false;
        };
    }
}
