package ma.atlasbank.customer.application;

import java.util.Locale;
import ma.atlasbank.commons.security.Requester;
import ma.atlasbank.customer.application.view.CustomerSummaryView;
import ma.atlasbank.customer.application.view.CustomerView;
import ma.atlasbank.customer.domain.Customer;
import ma.atlasbank.customer.domain.NationalId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface CustomerViewMapper {

    CustomerView toView(Customer customer);

    /**
     * Customers read their own file without the bank's internal notes: a suspension reason may refer
     * to an ongoing investigation, and employee logins are not theirs to know.
     */
    default CustomerView toViewFor(Customer customer, Requester requester) {
        CustomerView view = toView(customer);
        if (requester.isStaff()) {
            return view;
        }
        return new CustomerView(view.id(), view.firstName(), view.lastName(), view.email(), view.phoneNumber(),
                view.dateOfBirth(), view.nationalId(), view.address(), view.preferredLanguage(), view.status(), null,
                view.identityProvisioned(), null, null, view.verifiedAt(), view.createdAt(), view.lastModifiedAt());
    }

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
