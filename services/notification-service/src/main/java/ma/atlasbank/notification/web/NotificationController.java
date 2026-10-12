package ma.atlasbank.notification.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import ma.atlasbank.commons.security.Requester;
import ma.atlasbank.notification.application.NotificationQueryService;
import ma.atlasbank.notification.application.view.NotificationView;
import ma.atlasbank.platform.security.CustomerOrStaff;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/api/v1/notifications", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Notifications", description = "Messages sent to customers")
class NotificationController {

    private final NotificationQueryService queries;

    @CustomerOrStaff
    @GetMapping
    @Operation(summary = "Notification history", description = "Customers get their own notifications.")
    PagedModel<NotificationView> list(
            @Parameter(description = "Staff only: restrict to one customer")
            @RequestParam(required = false) UUID customerId,
            @ParameterObject @PageableDefault(sort = "sentAt", direction = Sort.Direction.DESC) Pageable pageable,
            Requester requester) {
        return new PagedModel<>(queries.list(customerId, requester, pageable));
    }
}
