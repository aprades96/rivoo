package com.rivoo.auth.infrastructure.adapter.out.persistence.repository;

import com.rivoo.auth.infrastructure.adapter.out.persistence.entity.TenantUserMappingJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TenantUserMappingJpaRepository extends JpaRepository<TenantUserMappingJpaEntity, Long> {

    List<TenantUserMappingJpaEntity> findByTenantId(String tenantId);

    @Modifying
    @Query("UPDATE TenantUserMappingJpaEntity t SET t.active = :active WHERE t.tenantId = :tenantId")
    void updateActiveStatusByTenantId(@Param("tenantId") String tenantId, @Param("active") boolean active);
}
