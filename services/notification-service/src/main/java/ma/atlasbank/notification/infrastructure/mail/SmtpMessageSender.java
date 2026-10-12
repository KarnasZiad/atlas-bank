package ma.atlasbank.notification.infrastructure.mail;

import ma.atlasbank.notification.application.port.MessageSender;
import ma.atlasbank.notification.domain.RenderedMessage;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * E-mail channel over SMTP (server, credentials and TLS configured through {@code spring.mail.*}).
 */
@Component
class SmtpMessageSender implements MessageSender {

    private final JavaMailSender mailSender;
    private final MailProperties properties;

    SmtpMessageSender(JavaMailSender mailSender, MailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(String destination, RenderedMessage message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(properties.from());
        mail.setTo(destination);
        mail.setSubject(message.subject());
        mail.setText(message.body());
        mailSender.send(mail);
    }
}
