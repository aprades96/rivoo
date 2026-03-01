package com.rivoo.staff.domain.model;

import java.math.BigDecimal;

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
public class EmployeeServiceAssignment {

    private Long employeeId;
    private Long serviceId;
    private String tenantId;
    private Integer customDuration;
    private BigDecimal customPrice;

    // These are set from the associated ServiceOffering for convenience
    private String serviceExternalId;
    private String serviceName;
    private int defaultDuration;
    private BigDecimal defaultPrice;

    public int getEffectiveDuration() {
        return customDuration != null ? customDuration : defaultDuration;
    }

    public BigDecimal getEffectivePrice() {
        return customPrice != null ? customPrice : defaultPrice;
    }
}
