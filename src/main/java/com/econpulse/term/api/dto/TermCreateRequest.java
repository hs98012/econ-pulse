package com.econpulse.term.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record TermCreateRequest(
        @NotBlank
        @Size(max = 100)
        String name,

        @NotBlank
        String definition,

        @NotNull
        List<@NotBlank @Size(max = 100) String> aliases
) {
}
