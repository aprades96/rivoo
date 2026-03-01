package com.rivoo.staff.application;

import com.rivoo.staff.application.dto.CreateServiceOfferingRequest;
import com.rivoo.staff.application.dto.ServiceOfferingInternalResponse;
import com.rivoo.staff.application.dto.ServiceOfferingResponse;
import com.rivoo.staff.application.dto.UpdateServiceOfferingRequest;
import com.rivoo.staff.domain.exception.DuplicateServiceNameException;
import com.rivoo.staff.domain.exception.ServiceOfferingNotFoundException;
import com.rivoo.staff.domain.model.ServiceOffering;
import com.rivoo.staff.domain.port.in.ManageServiceOfferingUseCase;
import com.rivoo.staff.domain.port.out.ServiceOfferingPersistencePort;
import com.rivoo.staff.infrastructure.mapper.ServiceOfferingDtoMapper;
import com.rivoo.common.util.ExternalIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceOfferingService implements ManageServiceOfferingUseCase {

    private final ServiceOfferingPersistencePort serviceOfferingPersistencePort;
    private final ServiceOfferingDtoMapper mapper;

    @Override
    @Transactional
    public ServiceOfferingResponse create(String tenantId, CreateServiceOfferingRequest request) {
        if (serviceOfferingPersistencePort.existsByNameAndTenantId(request.name(), tenantId)) {
            throw new DuplicateServiceNameException(request.name());
        }

        ServiceOffering service = ServiceOffering.builder()
                .externalId(ExternalIdGenerator.generate("svc"))
                .tenantId(tenantId)
                .name(request.name())
                .description(request.description())
                .durationMinutes(request.durationMinutes())
                .price(request.price())
                .currency(request.currency() != null ? request.currency() : "EUR")
                .active(true)
                .build();

        ServiceOffering saved = serviceOfferingPersistencePort.save(service);
        log.info("Service offering created: externalId={}, tenantId={}", saved.getExternalId(), tenantId);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public ServiceOfferingResponse update(String tenantId, String externalId, UpdateServiceOfferingRequest request) {
        ServiceOffering service = serviceOfferingPersistencePort.findByExternalId(externalId)
                .orElseThrow(() -> new ServiceOfferingNotFoundException(externalId));

        if (request.name() != null) {
            if (!request.name().equals(service.getName())
                    && serviceOfferingPersistencePort.existsByNameAndTenantId(request.name(), tenantId)) {
                throw new DuplicateServiceNameException(request.name());
            }
            service.setName(request.name());
        }
        if (request.description() != null) service.setDescription(request.description());
        if (request.durationMinutes() != null) service.setDurationMinutes(request.durationMinutes());
        if (request.price() != null) service.setPrice(request.price());
        if (request.currency() != null) service.setCurrency(request.currency());

        ServiceOffering updated = serviceOfferingPersistencePort.save(service);
        log.info("Service offering updated: externalId={}, tenantId={}", externalId, tenantId);
        return mapper.toResponse(updated);
    }

    @Override
    @Transactional
    public void deactivate(String tenantId, String externalId) {
        ServiceOffering service = serviceOfferingPersistencePort.findByExternalId(externalId)
                .orElseThrow(() -> new ServiceOfferingNotFoundException(externalId));

        service.setActive(false);
        serviceOfferingPersistencePort.save(service);
        log.info("Service offering deactivated: externalId={}, tenantId={}", externalId, tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ServiceOfferingResponse> list(Pageable pageable) {
        return serviceOfferingPersistencePort.findAllActive(pageable).map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ServiceOfferingInternalResponse getInternal(String tenantId, String serviceExternalId) {
        ServiceOffering service = serviceOfferingPersistencePort.findByExternalId(serviceExternalId)
                .orElseThrow(() -> new ServiceOfferingNotFoundException(serviceExternalId));
        return mapper.toInternalResponse(service);
    }
}
