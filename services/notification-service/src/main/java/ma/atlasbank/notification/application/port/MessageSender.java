package ma.atlasbank.notification.application.port;

import ma.atlasbank.notification.domain.RenderedMessage;

/**
 * Delivery channel. A failure is thrown so that the triggering event is retried.
 */
public interface MessageSender {

    void send(String destination, RenderedMessage message);
}
