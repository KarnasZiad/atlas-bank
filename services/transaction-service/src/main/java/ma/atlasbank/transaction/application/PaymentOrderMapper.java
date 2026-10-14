package ma.atlasbank.transaction.application;

import java.util.Currency;
import ma.atlasbank.transaction.application.view.PaymentOrderView;
import ma.atlasbank.transaction.domain.PaymentOrder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface PaymentOrderMapper {

    @Mapping(target = "amount", expression = "java(order.money().amount())")
    @Mapping(target = "initiatedBy", source = "initiator.username")
    @Mapping(target = "initiatorType", source = "initiator.type")
    @Mapping(target = "reviewedBy", source = "reviewer.username")
    PaymentOrderView toView(PaymentOrder order);

    default String currencyCode(Currency currency) {
        return currency.getCurrencyCode();
    }
}
