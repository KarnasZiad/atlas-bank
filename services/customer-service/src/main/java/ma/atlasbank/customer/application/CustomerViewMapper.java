package ma.atlasbank.customer.application;

import java.util.Locale;
import ma.atlasbank.customer.application.view.CustomerSummaryView;
import ma.atlasbank.customer.application.view.CustomerView;
import ma.atlasbank.customer.domain.Customer;
import ma.atlasbank.customer.domain.NationalId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface CustomerViewMapper {

    CustomerView toView(Customer customer);

    @Mapping(target = "fullName", expression = "java(customer.fullName())")
    CustomerSummaryView toSummary(Customer customer);

    /** Personal data leaves the service masked, whatever the caller's role. */
    default String maskNationalId(NationalId nationalId) {
        return nationalId.masked();
    }

    default String languageTag(Locale language) {
        return language.toLanguageTag();
    }
}
