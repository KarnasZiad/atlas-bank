package ma.atlasbank.account.infrastructure.persistence;

import java.util.UUID;
import ma.atlasbank.account.application.port.PostingReceiptRepository;
import ma.atlasbank.account.domain.PostingReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

interface JpaPostingReceiptRepository extends JpaRepository<PostingReceipt, UUID>, PostingReceiptRepository {
}
