package com.rivoo.staff.infrastructure.adapter.out.persistence.adapter;

import com.rivoo.staff.domain.model.EmployeeServiceAssignment;
import com.rivoo.staff.domain.port.out.EmployeeServicePersistencePort;
import com.rivoo.staff.infrastructure.adapter.out.persistence.entity.EmployeeServiceJpaEntity;
import com.rivoo.staff.infrastructure.adapter.out.persistence.entity.ServiceOfferingJpaEntity;
import com.rivoo.staff.infrastructure.adapter.out.persistence.repository.EmployeeServiceJpaRepository;
import com.rivoo.staff.infrastructure.adapter.out.persistence.repository.ServiceOfferingJpaRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmployeeServicePersistenceAdapter implements EmployeeServicePersistencePort {

    private final EmployeeServiceJpaRepository repository;
    private final ServiceOfferingJpaRepository serviceRepository;
    private final EntityManager entityManager;

    @Override
    public List<EmployeeServiceAssignment> findByEmployeeId(Long employeeId) {
        List<EmployeeServiceJpaEntity> entities = repository.findByEmployeeId(employeeId);
        if (entities.isEmpty()) return List.of();

        // Load associated services to populate service name, default duration/price
        List<Long> serviceIds = entities.stream().map(EmployeeServiceJpaEntity::getServiceId).toList();
        Map<Long, ServiceOfferingJpaEntity> serviceMap = serviceRepository.findAllById(serviceIds)
                .stream().collect(Collectors.toMap(ServiceOfferingJpaEntity::getId, s -> s));

        List<EmployeeServiceAssignment> result = new ArrayList<>();
        for (EmployeeServiceJpaEntity entity : entities) {
            ServiceOfferingJpaEntity svc = serviceMap.get(entity.getServiceId());
            if (svc == null) {
                // employee_services.service_id has an ON DELETE CASCADE FK to services(id), and the
                // application never hard-deletes a service (ServiceOfferingPersistencePort only exposes
                // save/deactivate). So this row cannot be produced by normal application flow; if it
                // shows up, referential integrity was bypassed out-of-band (e.g. a manual fix with
                // foreign_key_checks disabled, or a partial data restore). That is data corruption
                // worth surfacing, not something to swallow silently.
                log.atWarn()
                        .addKeyValue("employeeId", entity.getEmployeeId())
                        .addKeyValue("serviceId", entity.getServiceId())
                        .addKeyValue("tenantId", entity.getTenantId())
                        .log("Skipping orphaned employee_services assignment: referenced service no longer exists");
                continue;
            }
            result.add(EmployeeServiceAssignment.builder()
                    .employeeId(entity.getEmployeeId())
                    .serviceId(entity.getServiceId())
                    .tenantId(entity.getTenantId())
                    .customDuration(entity.getCustomDuration())
                    .customPrice(entity.getCustomPrice())
                    .serviceExternalId(svc.getExternalId())
                    .serviceName(svc.getName())
                    .defaultDuration(svc.getDurationMinutes())
                    .defaultPrice(svc.getPrice())
                    .build());
        }
        return result;
    }

    @Override
    public void deleteByEmployeeId(Long employeeId) {
        repository.deleteByEmployeeId(employeeId);
        entityManager.flush();
    }

    @Override
    public List<EmployeeServiceAssignment> saveAll(List<EmployeeServiceAssignment> assignments) {
        List<EmployeeServiceJpaEntity> entities = assignments.stream()
                .map(a -> EmployeeServiceJpaEntity.builder()
                        .employeeId(a.getEmployeeId())
                        .serviceId(a.getServiceId())
                        .tenantId(a.getTenantId())
                        .customDuration(a.getCustomDuration())
                        .customPrice(a.getCustomPrice())
                        .build())
                .toList();
        repository.saveAll(entities);
        // Return with service data populated
        return findByEmployeeId(assignments.getFirst().getEmployeeId());
    }
}
