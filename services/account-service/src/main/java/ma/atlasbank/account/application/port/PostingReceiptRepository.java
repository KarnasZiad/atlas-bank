package ma.atlasbank.account.application.port;

import java.util.UUID;
import ma.atlasbank.account.domain.PostingReceipt;

public interface PostingReceiptRepository {

    PostingReceipt save(PostingReceipt receipt);

    boolean existsById(UUID transactionId);
}
