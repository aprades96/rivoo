package com.rivoo.salon.domain.port.in;

import com.rivoo.salon.application.dto.SalonPublicResponse;
import com.rivoo.salon.application.dto.SalonResponse;

public interface GetSalonUseCase {

    SalonResponse getByTenantId(String tenantId);

    SalonResponse getBySlug(String slug);

    SalonPublicResponse getPublicBySlug(String slug);
}
