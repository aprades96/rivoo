package com.rivoo.client.infrastructure.mapper;

import com.rivoo.client.domain.model.Client;
import com.rivoo.client.infrastructure.adapter.out.persistence.entity.ClientJpaEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ClientPersistenceMapper {

    ClientJpaEntity toJpaEntity(Client client);

    Client toDomain(ClientJpaEntity entity);
}
