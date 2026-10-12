package ma.atlasbank.account.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StatusChangeCommand(@NotBlank @Size(max = 500) String reason) {
}
