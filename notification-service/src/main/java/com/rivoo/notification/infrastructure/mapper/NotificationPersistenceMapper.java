package com.rivoo.notification.infrastructure.mapper;

import com.rivoo.notification.domain.model.Notification;
import com.rivoo.notification.infrastructure.adapter.out.persistence.entity.NotificationLogJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NotificationPersistenceMapper {

    @Mapping(target = "channel", expression = "java(notification.getChannel() != null ? notification.getChannel().name() : null)")
    @Mapping(target = "type", expression = "java(notification.getType() != null ? notification.getType().name() : null)")
    @Mapping(target = "status", expression = "java(notification.getStatus() != null ? notification.getStatus().name() : null)")
    NotificationLogJpaEntity toJpaEntity(Notification notification);

    @Mapping(target = "channel", expression = "java(entity.getChannel() != null ? com.rivoo.notification.domain.model.NotificationChannel.valueOf(entity.getChannel()) : null)")
    @Mapping(target = "type", expression = "java(entity.getType() != null ? com.rivoo.notification.domain.model.NotificationType.valueOf(entity.getType()) : null)")
    @Mapping(target = "status", expression = "java(entity.getStatus() != null ? com.rivoo.notification.domain.model.NotificationStatus.valueOf(entity.getStatus()) : null)")
    Notification toDomain(NotificationLogJpaEntity entity);
}
