package com.econpulse.mapping.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class TermNewsMatcherTest {

    private final TermNewsMatcher matcher = new TermNewsMatcher();

    @ParameterizedTest
    @MethodSource("exactNameVariants")
    void matchesNormalizedExactName(String name, String title) {
        TermMatchCandidate candidate = match(name, List.of("국내총생산"), title, null);

        assertThat(candidate.matchType()).isEqualTo(MatchType.EXACT_NAME);
        assertThat(candidate.matchedField()).isEqualTo(MatchedField.TITLE);
        assertThat(candidate.matchedText()).isEqualTo("gdp 성장률");
        assertThat(candidate.confidenceScore()).isEqualByComparingTo("1.0000");
    }

    static Stream<Arguments> exactNameVariants() {
        return Stream.of(
                Arguments.of("GDP 성장률", "올해 GDP 성장률 전망"),
                Arguments.of("gdp 성장률", "올해 GDP 성장률 전망"),
                Arguments.of("　ＧＤＰ 성장률　", "올해 GDP 성장률 전망"),
                Arguments.of("GDP   성장률", "올해 GDP 성장률 전망")
        );
    }

    @Test
    void matchesExactNameInSummary() {
        TermMatchCandidate candidate = match("기준금리", List.of(), "시장 전망", "기준금리가 인상됐다");

        assertCandidate(candidate, MatchType.EXACT_NAME, MatchedField.SUMMARY, "기준금리", "0.9000");
    }

    @Test
    void titleExactWinsOverSummaryExact() {
        TermMatchCandidate candidate = match("기준금리", List.of(), "기준금리 전망", "기준금리 인상");

        assertThat(candidate.matchedField()).isEqualTo(MatchedField.TITLE);
    }

    @Test
    void exactNameWinsOverEveryAliasRegardlessOfField() {
        TermMatchCandidate candidate = match("기준금리", List.of("정책금리"), "정책금리 발표", "기준금리 결정");

        assertCandidate(candidate, MatchType.EXACT_NAME, MatchedField.SUMMARY, "기준금리", "0.9000");
    }

    @Test
    void matchesAliasInTitle() {
        TermMatchCandidate candidate = match("소비자물가지수", List.of("CPI"), "CPI 상승", null);

        assertCandidate(candidate, MatchType.ALIAS, MatchedField.TITLE, "cpi", "0.8000");
    }

    @Test
    void matchesAliasInSummary() {
        TermMatchCandidate candidate = match("소비자물가지수", List.of("물가지수"), "경제 전망", "물가지수 상승");

        assertCandidate(candidate, MatchType.ALIAS, MatchedField.SUMMARY, "물가지수", "0.7000");
    }

    @Test
    void titleAliasWinsOverSummaryAlias() {
        TermMatchCandidate candidate = match(
                "통화정책",
                List.of("정책금리", "기준 금리"),
                "정책금리 발표",
                "기준 금리 결정"
        );

        assertThat(candidate.matchedText()).isEqualTo("정책금리");
        assertThat(candidate.matchedField()).isEqualTo(MatchedField.TITLE);
    }

    @Test
    void longerAliasWinsWithinSameClassification() {
        TermMatchCandidate candidate = match("통화정책", List.of("금리", "기준 금리"), "기준 금리 인상", null);

        assertThat(candidate.matchedText()).isEqualTo("기준 금리");
    }

    @Test
    void aliasOrderAndDuplicatesDoNotChangeResult() {
        TermMatchCandidate first = match("통화정책", List.of("금리", "기준 금리", "금리"), "기준 금리", null);
        TermMatchCandidate second = match("통화정책", List.of("기준 금리", "금리"), "기준 금리", null);

        assertThat(first).isEqualTo(second);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GDP 성장률", "GDP는 상승", "2026 GDP 전망", "(GDP) 전망"})
    void matchesAsciiExpressionAtTokenBoundary(String title) {
        assertThat(matchOptional("국내총생산", List.of("GDP"), title, null)).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"myGDPvalue 전망", "Scorpio 지수"})
    void doesNotMatchAsciiAliasInsideAsciiWord(String title) {
        assertThat(matchOptional("국내총생산", List.of("GDP", "CPI"), title, null)).isEmpty();
    }

    @Test
    void matchesIndependentNumericTokenButNotLongerNumber() {
        assertThat(matchOptional("기준연도", List.of("2026"), "2026 전망", null)).isPresent();
        assertThat(matchOptional("기준연도", List.of("2026"), "120260 전망", null)).isEmpty();
    }

    @Test
    void mixedKoreanAsciiExpressionUsesSubstringPolicy() {
        assertThat(matchOptional("경제지표", List.of("K-지표"), "새 K-지표가 발표됐다", null)).isPresent();
    }

    @Test
    void ignoresOneCodePointAliases() {
        assertThat(matchOptional("경제지표", List.of("금", "A", "1"), "금 A 1", null)).isEmpty();
    }

    @Test
    void normalLengthAliasesStillMatch() {
        assertThat(matchOptional("경제지표", List.of("금리", "AI", "10"), "금리 AI 10", null)).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void blankSummaryDoesNotPreventTitleMatch(String summary) {
        assertThat(matchOptional("기준금리", List.of(), "기준금리", summary)).isPresent();
    }

    @Test
    void nullSummaryDoesNotPreventTitleMatch() {
        assertThat(matchOptional("기준금리", List.of(), "기준금리", null)).isPresent();
    }

    @Test
    void returnsEmptyWhenNoExpressionMatches() {
        assertThat(matchOptional("기준금리", List.of(), "환율 전망", null)).isEmpty();
    }

    @Test
    void targetNormalizesAndDefensivelyCopiesAliases() {
        List<String> aliases = new ArrayList<>(List.of(" 금리 ", "금리", "기준금리", " "));
        TermMatchTarget target = new TermMatchTarget(1L, " 기준금리 ", aliases);
        aliases.add("정책금리");

        assertThat(target.aliases()).containsExactly("금리");
        assertThatThrownBy(() -> target.aliases().add("변경"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void repeatedExecutionIsDeterministic() {
        TermMatchTarget target = new TermMatchTarget(1L, "통화정책", List.of("금리", "기준 금리"));
        NewsMatchContent content = new NewsMatchContent(2L, "기준 금리 발표", "금리 전망");

        assertThat(matcher.match(target, content)).isEqualTo(matcher.match(target, content));
    }

    @Test
    void lexicalTieBreakerIsStableAcrossAliasOrder() {
        List<String> aliases = new ArrayList<>(List.of("물가", "금리"));
        TermMatchCandidate first = match("경제", aliases, "물가와 금리", null);
        Collections.reverse(aliases);
        TermMatchCandidate second = match("경제", aliases, "물가와 금리", null);

        assertThat(first).isEqualTo(second);
        assertThat(first.matchedText()).isEqualTo("금리");
    }

    @Test
    void allScoresHaveScaleFourAndStayInRange() {
        List<TermMatchCandidate> candidates = List.of(
                match("기준금리", List.of(), "기준금리", null),
                match("기준금리", List.of(), "전망", "기준금리"),
                match("기준금리", List.of("정책금리"), "정책금리", null),
                match("기준금리", List.of("정책금리"), "전망", "정책금리")
        );

        assertThat(candidates).allSatisfy(candidate -> {
            assertThat(candidate.confidenceScore().scale()).isEqualTo(4);
            assertThat(candidate.confidenceScore()).isBetween(
                    new BigDecimal("0.0000"),
                    new BigDecimal("1.0000")
            );
        });
    }

    @Test
    void rejectsInvalidInputModels() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TermMatchTarget(0L, "용어", List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new TermMatchTarget(1L, " ", List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new TermMatchTarget(1L, "용어", null));
        assertThatIllegalArgumentException().isThrownBy(() -> new NewsMatchContent(0L, "제목", null));
        assertThatIllegalArgumentException().isThrownBy(() -> new NewsMatchContent(1L, " ", null));
    }

    private TermMatchCandidate match(String name, List<String> aliases, String title, String summary) {
        return matchOptional(name, aliases, title, summary).orElseThrow();
    }

    private Optional<TermMatchCandidate> matchOptional(
            String name,
            List<String> aliases,
            String title,
            String summary
    ) {
        return matcher.match(
                new TermMatchTarget(1L, name, aliases),
                new NewsMatchContent(2L, title, summary)
        );
    }

    private static void assertCandidate(
            TermMatchCandidate candidate,
            MatchType matchType,
            MatchedField field,
            String text,
            String score
    ) {
        assertThat(candidate.economicTermId()).isEqualTo(1L);
        assertThat(candidate.newsArticleId()).isEqualTo(2L);
        assertThat(candidate.matchType()).isEqualTo(matchType);
        assertThat(candidate.matchedField()).isEqualTo(field);
        assertThat(candidate.matchedText()).isEqualTo(text);
        assertThat(candidate.confidenceScore()).isEqualByComparingTo(score);
    }
}
