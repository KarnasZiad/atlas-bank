package ma.atlasbank.customer.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Justification recorded with a suspension or a closure (kept in the audit trail).
 */
public record StatusChangeCommand(@NotBlank @Size(max = 500) String reason) {
}
