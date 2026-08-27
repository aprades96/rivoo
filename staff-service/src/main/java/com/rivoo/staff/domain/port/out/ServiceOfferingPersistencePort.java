package com.rivoo.staff.domain.port.out;

import com.rivoo.staff.domain.model.ServiceOffering;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ServiceOfferingPersistencePort {

    ServiceOffering save(ServiceOffering service);

    Optional<ServiceOffering> findByExternalId(String externalId);

    Page<ServiceOffering> findAllActive(Pageable pageable);

    boolean existsByNameAndTenantId(String name, String tenantId);

    List<ServiceOffering> findAllActiveByTenantId(String tenantId);
}
