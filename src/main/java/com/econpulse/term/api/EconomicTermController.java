package com.econpulse.term.api;

import com.econpulse.term.api.dto.TermCreateRequest;
import com.econpulse.term.api.dto.PageResponse;
import com.econpulse.term.api.dto.TermDetailResponse;
import com.econpulse.term.api.dto.TermSummaryResponse;
import com.econpulse.term.api.dto.TermUpdateRequest;
import com.econpulse.term.application.EconomicTermService;
import com.econpulse.global.error.BusinessException;
import com.econpulse.global.error.ErrorCode;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/terms")
public class EconomicTermController {

    private final EconomicTermService economicTermService;

    public EconomicTermController(EconomicTermService economicTermService) {
        this.economicTermService = economicTermService;
    }

    @PostMapping
    public ResponseEntity<TermDetailResponse> create(@Valid @RequestBody TermCreateRequest request) {
        TermDetailResponse response = economicTermService.create(request);
        return ResponseEntity
                .created(URI.create("/api/v1/terms/" + response.id()))
                .body(response);
    }

    @GetMapping
    public ResponseEntity<PageResponse<TermSummaryResponse>> find(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        validatePageRequest(page, size);
        return ResponseEntity.ok(economicTermService.find(query, page, size));
    }

    @GetMapping("/{termId}")
    public ResponseEntity<TermDetailResponse> findById(@PathVariable Long termId) {
        return ResponseEntity.ok(economicTermService.findById(termId));
    }

    @PutMapping("/{termId}")
    public ResponseEntity<TermDetailResponse> update(
            @PathVariable Long termId,
            @Valid @RequestBody TermUpdateRequest request
    ) {
        return ResponseEntity.ok(economicTermService.update(termId, request));
    }

    @DeleteMapping("/{termId}")
    public ResponseEntity<Void> delete(@PathVariable Long termId) {
        economicTermService.delete(termId);
        return ResponseEntity.noContent().build();
    }

    private void validatePageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }
}
