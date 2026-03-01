package com.rivoo.staff.infrastructure.adapter.out.persistence.adapter;

import com.rivoo.staff.domain.model.EmployeeServiceAssignment;
import com.rivoo.staff.domain.port.out.EmployeeServicePersistencePort;
import com.rivoo.staff.infrastructure.adapter.out.persistence.entity.EmployeeServiceJpaEntity;
import com.rivoo.staff.infrastructure.adapter.out.persistence.entity.ServiceOfferingJpaEntity;
import com.rivoo.staff.infrastructure.adapter.out.persistence.repository.EmployeeServiceJpaRepository;
import com.rivoo.staff.infrastructure.adapter.out.persistence.repository.ServiceOfferingJpaRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
            result.add(EmployeeServiceAssignment.builder()
                    .employeeId(entity.getEmployeeId())
                    .serviceId(entity.getServiceId())
                    .tenantId(entity.getTenantId())
                    .customDuration(entity.getCustomDuration())
                    .customPrice(entity.getCustomPrice())
                    .serviceExternalId(svc != null ? svc.getExternalId() : null)
                    .serviceName(svc != null ? svc.getName() : null)
                    .defaultDuration(svc != null ? svc.getDurationMinutes() : 0)
                    .defaultPrice(svc != null ? svc.getPrice() : null)
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
