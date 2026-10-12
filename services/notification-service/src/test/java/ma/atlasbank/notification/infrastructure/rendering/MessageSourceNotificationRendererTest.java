package ma.atlasbank.notification.infrastructure.rendering;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import ma.atlasbank.commons.money.Money;
import ma.atlasbank.contracts.account.AccountStatus;
import ma.atlasbank.notification.domain.LocalizedLabel;
import ma.atlasbank.notification.domain.NotificationType;
import ma.atlasbank.notification.domain.RenderedMessage;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

class MessageSourceNotificationRendererTest {

    private final MessageSourceNotificationRenderer renderer = new MessageSourceNotificationRenderer(messages());

    @Test
    void rendersInTheRecipientLanguageWithLocalAmountFormatting() {
        RenderedMessage french = renderer.render(NotificationType.TRANSFER_SENT, Locale.FRENCH,
                List.of("Salma Benali", Money.of(new BigDecimal("1250.5"), "MAD"), "**** 0001", "**** 0002", "TRF-1"));

        assertThat(french.subject()).startsWith("Virement de").contains("1").contains("250,50");
        assertThat(french.body()).contains("Bonjour Salma Benali").contains("Référence : TRF-1");
    }

    @Test
    void translatesLabelsAndKeepsApostrophes() {
        RenderedMessage french = renderer.render(NotificationType.ACCOUNT_STATUS_CHANGED, Locale.FRENCH,
                List.of("Salma", "**** 0001", LocalizedLabel.of(AccountStatus.FROZEN), "Contrôle"));

        assertThat(french.body()).contains("gelé (débits bloqués)").contains("n'êtes pas à l'origine");
    }

    @Test
    void fallsBackToTheDefaultBundleAndRawCodes() {
        RenderedMessage german = renderer.render(NotificationType.TRANSACTION_REJECTED, Locale.GERMAN,
                List.of("Max", Money.of(BigDecimal.TEN, "EUR"), "TRF-2", LocalizedLabel.rejection("SOMETHING_NEW")));

        assertThat(german.subject()).isEqualTo("Transaction TRF-2 was not executed");
        assertThat(german.body()).contains("reason: SOMETHING_NEW");
    }

    @Test
    void supportsArabic() {
        RenderedMessage arabic = renderer.render(NotificationType.DEPOSIT_RECEIVED, Locale.forLanguageTag("ar"),
                List.of("سلمى", Money.of(new BigDecimal("100"), "MAD"), "**** 0001", "DEP-1"));

        assertThat(arabic.subject()).startsWith("إيداع نقدي");
        assertThat(arabic.body()).contains("DEP-1");
    }

    private static ResourceBundleMessageSource messages() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages/notifications");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        return messages;
    }
}
