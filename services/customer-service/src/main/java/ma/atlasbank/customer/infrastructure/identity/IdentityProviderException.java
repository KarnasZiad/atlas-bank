package ma.atlasbank.customer.infrastructure.identity;

/**
 * The identity provider rejected or could not complete an operation. Raised from an outbox
 * listener, it leaves the publication incomplete so that it is retried.
 */
class IdentityProviderException extends RuntimeException {

    IdentityProviderException(String message) {
        super(message);
    }
}
