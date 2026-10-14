package ma.atlasbank.contracts.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import ma.atlasbank.contracts.payment.TransactionRequested.Approval;
import ma.atlasbank.contracts.payment.TransactionRequested.Initiator;
import ma.atlasbank.contracts.payment.TransactionRequested.InitiatorType;
import org.junit.jupiter.api.Test;

class OrderSignaturesTest {

    private static final KeyPair KEYS = generate();
    private static final Map<String, PublicKey> TRUSTED = Map.of("k1", KEYS.getPublic());

    @Test
    void acceptsAnOrderSignedWithATrustedKey() {
        TransactionRequested signed = OrderSignatures.sign(transfer("250.00", "Rent"), "k1", KEYS.getPrivate());

        assertThat(OrderSignatures.isAuthentic(signed, TRUSTED)).isTrue();
    }

    @Test
    void rejectsUnsignedOrdersAndUnknownKeys() {
        TransactionRequested unsigned = transfer("250.00", "Rent");
        TransactionRequested signedByAnotherKey = OrderSignatures.sign(unsigned, "k2", generate().getPrivate());

        assertThat(OrderSignatures.isAuthentic(unsigned, TRUSTED)).isFalse();
        assertThat(OrderSignatures.isAuthentic(signedByAnotherKey, TRUSTED)).isFalse();
        assertThat(OrderSignatures.isAuthentic(signedByAnotherKey.withSignature(
                new OrderSignature("k1", signedByAnotherKey.signature().value())), TRUSTED)).isFalse();
    }

    @Test
    void detectsAnyTamperingWithWhatTheLedgerActsOn() {
        TransactionRequested signed = OrderSignatures.sign(transfer("250.00", "Rent"), "k1", KEYS.getPrivate());

        TransactionRequested moreMoney = new TransactionRequested(signed.eventId(), signed.occurredAt(),
                signed.transactionId(), signed.reference(), signed.kind(), signed.sourceAccountNumber(),
                signed.targetAccountNumber(), new BigDecimal("2500.00"), signed.currency(), signed.description(),
                signed.initiator(), signed.approval(), signed.signature());
        TransactionRequested posingAsStaff = new TransactionRequested(signed.eventId(), signed.occurredAt(),
                signed.transactionId(), signed.reference(), signed.kind(), signed.sourceAccountNumber(),
                signed.targetAccountNumber(), signed.amount(), signed.currency(), signed.description(),
                new Initiator("customer-1", "salma", null, InitiatorType.EMPLOYEE), signed.approval(), signed.signature());
        TransactionRequested forgedApproval = new TransactionRequested(signed.eventId(), signed.occurredAt(),
                signed.transactionId(), signed.reference(), signed.kind(), signed.sourceAccountNumber(),
                signed.targetAccountNumber(), signed.amount(), signed.currency(), signed.description(),
                signed.initiator(), new Approval("agent-2", "agent.youssef"), signed.signature());

        assertThat(OrderSignatures.isAuthentic(moreMoney, TRUSTED)).isFalse();
        assertThat(OrderSignatures.isAuthentic(posingAsStaff, TRUSTED)).isFalse();
        assertThat(OrderSignatures.isAuthentic(forgedApproval, TRUSTED)).isFalse();
    }

    @Test
    void ignoresTheAmountScaleAndTheEventMetadata() {
        TransactionRequested signed = OrderSignatures.sign(transfer("250.00", "Rent"), "k1", KEYS.getPrivate());
        TransactionRequested redelivered = new TransactionRequested(UUID.randomUUID(), Instant.now(),
                signed.transactionId(), signed.reference(), signed.kind(), signed.sourceAccountNumber(),
                signed.targetAccountNumber(), new BigDecimal("250"), signed.currency(), signed.description(),
                signed.initiator(), signed.approval(), signed.signature());

        assertThat(OrderSignatures.isAuthentic(redelivered, TRUSTED)).isTrue();
    }

    @Test
    void encodesFieldsUnambiguously() {
        // A separator typed in the free-text description cannot pass for the start of the next field.
        assertThat(transfer("250.00", "Rent;6:agent1").signedContent()).contains("13:Rent;6:agent1;");
        assertThat(transfer("250.00", null).signedContent()).isNotEqualTo(transfer("250.00", "").signedContent());
    }

    @Test
    void readsKeysEncodedAsBase64Der() {
        String publicKey = java.util.Base64.getEncoder().encodeToString(KEYS.getPublic().getEncoded());
        String privateKey = java.util.Base64.getEncoder().encodeToString(KEYS.getPrivate().getEncoded());

        TransactionRequested signed = OrderSignatures.sign(transfer("1.00", null), "k1", OrderSignatures.privateKey(privateKey));

        assertThat(OrderSignatures.isAuthentic(signed, Map.of("k1", OrderSignatures.publicKey(publicKey)))).isTrue();
    }

    private static final UUID TRANSACTION_ID = UUID.fromString("0192a8b4-6f2e-7c3a-9b1d-2e4f6a8c0d1e");

    private static TransactionRequested transfer(String amount, String description) {
        return new TransactionRequested(UUID.randomUUID(), Instant.parse("2026-10-13T10:00:00Z"), TRANSACTION_ID,
                "TRF-20261013-ABCDEFGH", TransactionKind.TRANSFER, "MA6400000000000000000000001", "MA6400000000000000000000002",
                new BigDecimal(amount), "MAD", description,
                new Initiator("customer-1", "salma", UUID.fromString("0192a8b4-0000-7000-8000-000000000001"),
                        InitiatorType.CUSTOMER),
                null, null);
    }

    private static KeyPair generate() {
        try {
            return KeyPairGenerator.getInstance(OrderSignatures.ALGORITHM).generateKeyPair();
        } catch (java.security.NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException(unavailable);
        }
    }
}
