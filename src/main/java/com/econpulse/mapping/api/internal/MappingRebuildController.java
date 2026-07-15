package com.econpulse.mapping.api.internal;

import com.econpulse.mapping.application.TermNewsAutoMappingResult;
import com.econpulse.mapping.application.TermNewsAutoMappingService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/api/v1/mappings")
@ConditionalOnProperty(name = "econpulse.internal.mapping-rebuild.enabled", havingValue = "true")
public class MappingRebuildController {

    private final TermNewsAutoMappingService autoMappingService;

    public MappingRebuildController(TermNewsAutoMappingService autoMappingService) {
        this.autoMappingService = autoMappingService;
    }

    @PostMapping("/rebuild")
    public ResponseEntity<MappingRebuildResponse> rebuild(
            @Valid @RequestBody MappingRebuildRequest request
    ) {
        TermNewsAutoMappingResult result = autoMappingService.map(request.toCommand());
        return ResponseEntity.ok(MappingRebuildResponse.from(result));
    }
}
