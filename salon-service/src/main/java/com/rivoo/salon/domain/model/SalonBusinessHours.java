package com.rivoo.salon.domain.model;

import com.rivoo.common.exception.BusinessValidationException;

import java.time.LocalTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalonBusinessHours {

    private Long id;
    private Long salonId;
    private int dayOfWeek; // 1=Mon...7=Sun (ISO 8601)
    private boolean open;
    private LocalTime openTime;
    private LocalTime closeTime;
    private LocalTime breakStartTime;
    private LocalTime breakEndTime;

    /**
     * Reached only from {@code PUT /api/v1/salons/me/business-hours}, {@code hasRole('SALON_OWNER')},
     * so every message below is published to the caller via
     * {@link BusinessValidationException#clientSafe(String)}: each one describes the schedule the
     * owner just submitted for their own salon, names nothing else, and is the only thing telling
     * them which row to fix.
     */
    public void validate() {
        if (dayOfWeek < 1 || dayOfWeek > 7) {
            throw BusinessValidationException.clientSafe("dayOfWeek must be 1-7, got: " + dayOfWeek);
        }
        if (open) {
            if (openTime == null || closeTime == null) {
                throw BusinessValidationException.clientSafe("Open days must have openTime and closeTime");
            }
            if (!closeTime.isAfter(openTime)) {
                throw BusinessValidationException.clientSafe("closeTime must be after openTime");
            }
            if (breakStartTime != null && breakEndTime != null && !breakEndTime.isAfter(breakStartTime)) {
                throw BusinessValidationException.clientSafe("breakEndTime must be after breakStartTime");
            }
        }
    }
}
