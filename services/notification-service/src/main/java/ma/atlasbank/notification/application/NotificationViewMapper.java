package ma.atlasbank.notification.application;

import ma.atlasbank.notification.application.view.NotificationView;
import ma.atlasbank.notification.domain.Notification;
import org.mapstruct.Mapper;

@Mapper
public interface NotificationViewMapper {

    NotificationView toView(Notification notification);
}
