package com.rivoo.billing.domain.model;

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
public class PlanLimit {

    private Long id;
    private Long planId;
    private String limitKey;
    private int limitValue;
}
