package com.rivoo.staff.domain.port.in;

import com.rivoo.staff.application.dto.CreateServiceOfferingRequest;
import com.rivoo.staff.application.dto.ServiceOfferingInternalResponse;
import com.rivoo.staff.application.dto.ServiceOfferingResponse;
import com.rivoo.staff.application.dto.UpdateServiceOfferingRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ManageServiceOfferingUseCase {

    ServiceOfferingResponse create(String tenantId, CreateServiceOfferingRequest request);

    ServiceOfferingResponse update(String tenantId, String externalId, UpdateServiceOfferingRequest request);

    void deactivate(String tenantId, String externalId);

    Page<ServiceOfferingResponse> list(Pageable pageable);

    ServiceOfferingInternalResponse getInternal(String tenantId, String serviceExternalId);
}
