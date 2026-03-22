package com.rivoo.billing.domain.port.in;

import com.rivoo.billing.application.dto.PlanResponse;

import java.util.List;

public interface ListPlansUseCase {

    List<PlanResponse> listActivePlans();
}
