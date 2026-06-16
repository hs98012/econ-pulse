package com.econpulse.term.application;

import com.econpulse.term.api.dto.TermCreateRequest;
import com.econpulse.term.api.dto.TermResponse;
import com.econpulse.term.api.dto.TermUpdateRequest;
import com.econpulse.term.domain.EconomicTerm;
import com.econpulse.term.infrastructure.EconomicTermRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EconomicTermService {

    private final EconomicTermRepository economicTermRepository;

    public EconomicTermService(EconomicTermRepository economicTermRepository) {
        this.economicTermRepository = economicTermRepository;
    }

    @Transactional
    public TermResponse create(TermCreateRequest request) {
        String normalizedName = normalizeName(request.name());
        validateDuplicateName(request.name(), normalizedName);

        EconomicTerm economicTerm = new EconomicTerm(
                request.name(),
                normalizedName,
                request.definition(),
                request.aliases()
        );

        return TermResponse.from(economicTermRepository.save(economicTerm));
    }

    @Transactional(readOnly = true)
    public List<TermResponse> findAll() {
        return economicTermRepository.findAll()
                .stream()
                .map(TermResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TermResponse findById(Long termId) {
        return TermResponse.from(getTerm(termId));
    }

    @Transactional(readOnly = true)
    public List<TermResponse> search(String keyword) {
        String normalizedKeyword = normalizeSearchKeyword(keyword);

        return economicTermRepository.findAll()
                .stream()
                .filter(term -> matchesKeyword(term, normalizedKeyword))
                .map(TermResponse::from)
                .toList();
    }

    @Transactional
    public TermResponse update(Long termId, TermUpdateRequest request) {
        EconomicTerm economicTerm = getTerm(termId);
        String normalizedName = normalizeName(request.name());

        if (economicTermRepository.existsByNameAndIdNot(request.name(), termId)
                || economicTermRepository.existsByNormalizedNameAndIdNot(normalizedName, termId)) {
            throw new DuplicateTermNameException();
        }

        economicTerm.update(request.name(), normalizedName, request.definition(), request.aliases());

        return TermResponse.from(economicTerm);
    }

    @Transactional
    public void delete(Long termId) {
        EconomicTerm economicTerm = getTerm(termId);
        economicTermRepository.delete(economicTerm);
    }

    private EconomicTerm getTerm(Long termId) {
        return economicTermRepository.findById(termId)
                .orElseThrow(TermNotFoundException::new);
    }

    private void validateDuplicateName(String name, String normalizedName) {
        if (economicTermRepository.existsByName(name)
                || economicTermRepository.existsByNormalizedName(normalizedName)) {
            throw new DuplicateTermNameException();
        }
    }

    private boolean matchesKeyword(EconomicTerm term, String normalizedKeyword) {
        if (term.getNormalizedName().contains(normalizedKeyword)) {
            return true;
        }

        return term.getAliases()
                .stream()
                .map(this::normalizeSearchKeyword)
                .anyMatch(alias -> alias.contains(normalizedKeyword));
    }

    private String normalizeName(String name) {
        return normalizeSearchKeyword(name);
    }

    private String normalizeSearchKeyword(String value) {
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
