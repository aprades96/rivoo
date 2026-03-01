package com.rivoo.client.infrastructure.adapter.out.persistence.adapter;

import com.rivoo.client.domain.model.Client;
import com.rivoo.client.domain.port.out.ClientPersistencePort;
import com.rivoo.client.infrastructure.adapter.out.persistence.entity.ClientJpaEntity;
import com.rivoo.client.infrastructure.adapter.out.persistence.repository.ClientJpaRepository;
import com.rivoo.client.infrastructure.mapper.ClientPersistenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ClientPersistenceAdapter implements ClientPersistencePort {

    private final ClientJpaRepository repository;
    private final ClientPersistenceMapper mapper;

    @Override
    public Client save(Client client) {
        ClientJpaEntity entity = mapper.toJpaEntity(client);
        ClientJpaEntity saved = repository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Client> findByExternalId(String externalId) {
        return repository.findByExternalId(externalId).map(mapper::toDomain);
    }

    @Override
    public Optional<Client> findByTenantIdAndEmail(String tenantId, String email) {
        return repository.findByTenantIdAndEmail(tenantId, email).map(mapper::toDomain);
    }

    @Override
    public Optional<Client> findByTenantIdAndPhone(String tenantId, String phone) {
        return repository.findByTenantIdAndPhone(tenantId, phone).map(mapper::toDomain);
    }

    @Override
    public Optional<Client> findByExternalIdAndTenantId(String externalId, String tenantId) {
        return repository.findByExternalIdAndTenantId(externalId, tenantId).map(mapper::toDomain);
    }

    @Override
    public Page<Client> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(mapper::toDomain);
    }

    @Override
    public boolean existsByTenantIdAndEmail(String tenantId, String email) {
        return repository.existsByTenantIdAndEmail(tenantId, email);
    }
}
