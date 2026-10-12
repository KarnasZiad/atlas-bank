package ma.atlasbank.notification.infrastructure.mail;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param from sender address of customer e-mails
 */
@Validated
@ConfigurationProperties("atlas.notifications.mail")
public record MailProperties(@NotBlank @Email String from) {
}
