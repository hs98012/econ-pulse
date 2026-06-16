package com.econpulse.term.api;

import com.econpulse.term.api.dto.TermCreateRequest;
import com.econpulse.term.api.dto.TermResponse;
import com.econpulse.term.api.dto.TermUpdateRequest;
import com.econpulse.term.application.EconomicTermService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.List;
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
@RequestMapping("/api/terms")
public class EconomicTermController {

    private final EconomicTermService economicTermService;

    public EconomicTermController(EconomicTermService economicTermService) {
        this.economicTermService = economicTermService;
    }

    @PostMapping
    public ResponseEntity<TermResponse> create(@Valid @RequestBody TermCreateRequest request) {
        TermResponse response = economicTermService.create(request);
        return ResponseEntity
                .created(URI.create("/api/terms/" + response.id()))
                .body(response);
    }

    @GetMapping
    public ResponseEntity<List<TermResponse>> findAll() {
        return ResponseEntity.ok(economicTermService.findAll());
    }

    @GetMapping("/{termId}")
    public ResponseEntity<TermResponse> findById(@PathVariable Long termId) {
        return ResponseEntity.ok(economicTermService.findById(termId));
    }

    @GetMapping("/search")
    public ResponseEntity<List<TermResponse>> search(@RequestParam @NotBlank String keyword) {
        return ResponseEntity.ok(economicTermService.search(keyword));
    }

    @PutMapping("/{termId}")
    public ResponseEntity<TermResponse> update(
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
}
