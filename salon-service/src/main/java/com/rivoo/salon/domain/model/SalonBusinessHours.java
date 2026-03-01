package com.rivoo.salon.domain.model;

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

    public void validate() {
        if (dayOfWeek < 1 || dayOfWeek > 7) {
            throw new IllegalArgumentException("dayOfWeek must be 1-7, got: " + dayOfWeek);
        }
        if (open) {
            if (openTime == null || closeTime == null) {
                throw new IllegalArgumentException("Open days must have openTime and closeTime");
            }
            if (!closeTime.isAfter(openTime)) {
                throw new IllegalArgumentException("closeTime must be after openTime");
            }
            if (breakStartTime != null && breakEndTime != null && !breakEndTime.isAfter(breakStartTime)) {
                throw new IllegalArgumentException("breakEndTime must be after breakStartTime");
            }
        }
    }
}
