package ma.atlasbank.contracts.payment;

/**
 * Signature of a payment order by the transaction service.
 *
 * @param keyId identifies the signing key, so that keys can be rotated while orders signed with the
 *              previous key are still in flight
 * @param value Base64 Ed25519 signature of {@link TransactionRequested#signedContent()}
 */
public record OrderSignature(String keyId, String value) {
}
