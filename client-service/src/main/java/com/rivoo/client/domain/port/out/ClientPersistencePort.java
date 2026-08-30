package com.rivoo.client.domain.port.out;

import com.rivoo.client.domain.model.Client;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface ClientPersistencePort {

    Client save(Client client);

    Optional<Client> findByExternalId(String externalId);

    Optional<Client> findByTenantIdAndEmail(String tenantId, String email);

    Optional<Client> findByTenantIdAndPhone(String tenantId, String phone);

    Optional<Client> findByExternalIdAndTenantId(String externalId, String tenantId);

    Page<Client> findAll(String search, Pageable pageable);

    boolean existsByTenantIdAndEmail(String tenantId, String email);
}
