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
}
