package com.econpulse.term.application;

import com.econpulse.term.api.dto.TermCreateRequest;
import com.econpulse.term.api.dto.PageResponse;
import com.econpulse.term.api.dto.TermDetailResponse;
import com.econpulse.term.api.dto.TermSummaryResponse;
import com.econpulse.term.api.dto.TermUpdateRequest;
import com.econpulse.term.domain.EconomicTerm;
import com.econpulse.term.domain.EconomicTermAlias;
import com.econpulse.term.domain.TermNormalizer;
import com.econpulse.term.domain.TermStatus;
import com.econpulse.term.infrastructure.EconomicTermAliasRepository;
import com.econpulse.term.infrastructure.EconomicTermRepository;
import com.econpulse.mapping.infrastructure.TermNewsMappingRepository;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EconomicTermService {

    private final EconomicTermRepository economicTermRepository;
    private final EconomicTermAliasRepository economicTermAliasRepository;
    private final TermNewsMappingRepository termNewsMappingRepository;

    public EconomicTermService(
            EconomicTermRepository economicTermRepository,
            EconomicTermAliasRepository economicTermAliasRepository,
            TermNewsMappingRepository termNewsMappingRepository
    ) {
        this.economicTermRepository = economicTermRepository;
        this.economicTermAliasRepository = economicTermAliasRepository;
        this.termNewsMappingRepository = termNewsMappingRepository;
    }

    @Transactional
    public TermDetailResponse create(TermCreateRequest request) {
        String name = normalizeDisplayValue(request.name());
        String normalizedName = TermNormalizer.normalize(name);
        validateDuplicateName(normalizedName);

        EconomicTerm economicTerm = new EconomicTerm(
                name,
                normalizedName,
                request.definition().trim(),
                buildAliases(request.aliases(), normalizedName)
        );
        validateDuplicateAliases(economicTerm, null);

        try {
            EconomicTerm savedTerm = economicTermRepository.saveAndFlush(economicTerm);
            return TermDetailResponse.from(savedTerm, 0);
        } catch (DataIntegrityViolationException exception) {
            throw translateDuplicateException(exception);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<TermSummaryResponse> find(String query, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("name").ascending().and(Sort.by("id").ascending()));
        String normalizedQuery = query == null || query.isBlank() ? null : TermNormalizer.normalize(query);

        if (normalizedQuery == null) {
            return PageResponse.from(economicTermRepository.findAllByStatusWithAliases(TermStatus.ACTIVE, pageable)
                    .map(TermSummaryResponse::from));
        }

        return PageResponse.from(economicTermRepository.searchByNormalizedNameOrAlias(
                        normalizedQuery,
                        TermStatus.ACTIVE,
                        pageable
                )
                .map(TermSummaryResponse::from));
    }

    @Transactional(readOnly = true)
    public TermDetailResponse findById(Long termId) {
        EconomicTerm economicTerm = getActiveTerm(termId);
        long latestNewsCount = termNewsMappingRepository.countByEconomicTermId(termId);
        return TermDetailResponse.from(economicTerm, latestNewsCount);
    }

    @Transactional
    public TermDetailResponse update(Long termId, TermUpdateRequest request) {
        EconomicTerm economicTerm = getActiveTerm(termId);
        String name = normalizeDisplayValue(request.name());
        String normalizedName = TermNormalizer.normalize(name);

        if (economicTermRepository.existsByNormalizedNameAndIdNot(normalizedName, termId)) {
            throw new DuplicateTermNameException();
        }

        economicTerm.update(name, normalizedName, request.definition().trim(), buildAliases(request.aliases(), normalizedName));
        validateDuplicateAliases(economicTerm, termId);

        try {
            economicTermRepository.flush();
            long latestNewsCount = termNewsMappingRepository.countByEconomicTermId(termId);
            return TermDetailResponse.from(economicTerm, latestNewsCount);
        } catch (DataIntegrityViolationException exception) {
            throw translateDuplicateException(exception);
        }
    }

    @Transactional
    public void delete(Long termId) {
        economicTermRepository.findById(termId)
                .ifPresent(EconomicTerm::deactivate);
    }

    private EconomicTerm getActiveTerm(Long termId) {
        return economicTermRepository.findByIdAndStatus(termId, TermStatus.ACTIVE)
                .orElseThrow(TermNotFoundException::new);
    }

    private void validateDuplicateName(String normalizedName) {
        if (economicTermRepository.existsByNormalizedName(normalizedName)) {
            throw new DuplicateTermNameException();
        }
    }

    private void validateDuplicateAliases(EconomicTerm economicTerm, Long termId) {
        for (EconomicTermAlias alias : economicTerm.getAliases()) {
            boolean exists = termId == null
                    ? economicTermAliasRepository.existsByNormalizedAlias(alias.getNormalizedAlias())
                    : economicTermAliasRepository.existsByNormalizedAliasAndEconomicTermIdNot(
                            alias.getNormalizedAlias(),
                            termId
                    );
            if (exists) {
                throw new DuplicateTermAliasException();
            }
        }
    }

    private RuntimeException translateDuplicateException(DataIntegrityViolationException exception) {
        String message = exception.getMostSpecificCause().getMessage();
        if (message != null && message.contains("economic_term_aliases")) {
            return new DuplicateTermAliasException();
        }
        return new DuplicateTermNameException();
    }

    private List<EconomicTermAlias> buildAliases(List<String> aliases, String normalizedName) {
        Map<String, String> uniqueAliases = new LinkedHashMap<>();
        aliases.stream()
                .map(TermNormalizer::normalize)
                .filter(alias -> !alias.equals(normalizedName))
                .forEach(alias -> uniqueAliases.putIfAbsent(alias, alias));

        return uniqueAliases.values()
                .stream()
                .map(alias -> new EconomicTermAlias(alias, alias))
                .toList();
    }

    private String normalizeDisplayValue(String value) {
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
                .replaceAll("\\s+", " ");
    }
}
