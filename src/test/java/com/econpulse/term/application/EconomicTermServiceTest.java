package com.econpulse.term.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.econpulse.support.AbstractIntegrationTest;
import com.econpulse.global.api.PageResponse;
import com.econpulse.term.api.dto.TermCreateRequest;
import com.econpulse.term.api.dto.TermDetailResponse;
import com.econpulse.term.api.dto.TermSummaryResponse;
import com.econpulse.term.api.dto.TermUpdateRequest;
import com.econpulse.term.domain.EconomicTerm;
import com.econpulse.term.domain.TermStatus;
import com.econpulse.term.infrastructure.EconomicTermAliasRepository;
import com.econpulse.term.infrastructure.EconomicTermRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;

@SpringBootTest
class EconomicTermServiceTest extends AbstractIntegrationTest {

    private final EconomicTermService economicTermService;
    private final EconomicTermRepository economicTermRepository;
    private final EconomicTermAliasRepository economicTermAliasRepository;

    @Autowired
    EconomicTermServiceTest(
            EconomicTermService economicTermService,
            EconomicTermRepository economicTermRepository,
            EconomicTermAliasRepository economicTermAliasRepository
    ) {
        this.economicTermService = economicTermService;
        this.economicTermRepository = economicTermRepository;
        this.economicTermAliasRepository = economicTermAliasRepository;
    }

    @BeforeEach
    void setUp() {
        economicTermAliasRepository.deleteAll();
        economicTermRepository.deleteAll();
    }

    @Test
    void createsTerm() {
        TermDetailResponse response = economicTermService.create(new TermCreateRequest(
                "기준금리",
                "중앙은행 기준 금리",
                List.of("정책금리")
        ));

        assertThat(response.id()).isNotNull();
        assertThat(response.name()).isEqualTo("기준금리");
        assertThat(response.aliases()).containsExactly("정책금리");
    }

    @Test
    void normalizesName() {
        economicTermService.create(new TermCreateRequest(" ＧＤＰ  성장 ", "정의", List.of()));

        EconomicTerm savedTerm = economicTermRepository.findByNormalizedName("gdp 성장").orElseThrow();
        assertThat(savedTerm.getName()).isEqualTo("GDP 성장");
    }

    @Test
    void normalizesAliasesAndRemovesDuplicates() {
        TermDetailResponse response = economicTermService.create(new TermCreateRequest(
                "기준금리",
                "정의",
                List.of(" 정책　금리 ", "정책 금리", "기준금리")
        ));

        assertThat(response.aliases()).containsExactly("정책 금리");
    }

    @Test
    void rejectsDuplicateNormalizedName() {
        economicTermService.create(new TermCreateRequest("GDP", "정의", List.of()));

        assertThatThrownBy(() -> economicTermService.create(new TermCreateRequest("ｇｄｐ", "정의", List.of())))
                .isInstanceOf(DuplicateTermNameException.class);
    }

    @Test
    void failsWhenTermDoesNotExist() {
        assertThatThrownBy(() -> economicTermService.findById(999L))
                .isInstanceOf(TermNotFoundException.class);
    }

    @Test
    void updatesTerm() {
        TermDetailResponse created = economicTermService.create(new TermCreateRequest("GDP", "정의", List.of("국내총생산")));

        TermDetailResponse updated = economicTermService.update(
                created.id(),
                new TermUpdateRequest("국내총생산", "새 정의", List.of("gdp"))
        );

        assertThat(updated.name()).isEqualTo("국내총생산");
        assertThat(updated.definition()).isEqualTo("새 정의");
        assertThat(updated.aliases()).containsExactly("gdp");
    }

    @Test
    void rejectsDuplicateNameDuringUpdate() {
        TermDetailResponse first = economicTermService.create(new TermCreateRequest("GDP", "정의", List.of()));
        economicTermService.create(new TermCreateRequest("CPI", "정의", List.of()));

        assertThatThrownBy(() -> economicTermService.update(first.id(), new TermUpdateRequest("cpi", "정의", List.of())))
                .isInstanceOf(DuplicateTermNameException.class);
    }

    @Test
    void deleteChangesStatusToInactive() {
        TermDetailResponse created = economicTermService.create(new TermCreateRequest("GDP", "정의", List.of()));

        economicTermService.delete(created.id());

        EconomicTerm deletedTerm = economicTermRepository.findById(created.id()).orElseThrow();
        assertThat(deletedTerm.getStatus()).isEqualTo(TermStatus.INACTIVE);
    }

    @Test
    void inactiveTermIsNotReturned() {
        TermDetailResponse created = economicTermService.create(new TermCreateRequest("GDP", "정의", List.of()));
        economicTermService.delete(created.id());

        PageResponse<TermSummaryResponse> response = economicTermService.find(null, 0, 20);

        assertThat(response.content()).isEmpty();
        assertThatThrownBy(() -> economicTermService.findById(created.id()))
                .isInstanceOf(TermNotFoundException.class);
    }
}
