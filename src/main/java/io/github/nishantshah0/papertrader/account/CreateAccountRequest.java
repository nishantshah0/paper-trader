package io.github.nishantshah0.papertrader.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(
        @NotBlank
        @Size(min = 3, max = 32)
        @Pattern(regexp = "[a-z0-9_]+", message = "lowercase letters, digits and underscores only")
        String username) {
}
