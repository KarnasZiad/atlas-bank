package ma.atlasbank.platform.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Open to customers and staff. Resource ownership is then enforced by the application layer
 * through {@link ma.atlasbank.commons.security.Requester#canAccessCustomer(java.util.UUID)}.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasAnyRole('CUSTOMER', 'EMPLOYEE')")
public @interface CustomerOrStaff {
}
