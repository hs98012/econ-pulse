package com.econpulse.mapping.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class TermNewsMatcher {

    private static final BigDecimal TITLE_EXACT_SCORE = new BigDecimal("1.0000");
    private static final BigDecimal SUMMARY_EXACT_SCORE = new BigDecimal("0.9000");
    private static final BigDecimal TITLE_ALIAS_SCORE = new BigDecimal("0.8000");
    private static final BigDecimal SUMMARY_ALIAS_SCORE = new BigDecimal("0.7000");

    private static final Comparator<TermMatchCandidate> CANDIDATE_ORDER =
            Comparator.comparingInt(TermNewsMatcher::matchTypePriority).reversed()
                    .thenComparing(
                            TermNewsMatcher::fieldPriority,
                            Comparator.reverseOrder()
                    )
                    .thenComparing(
                            candidate -> codePointLength(candidate.matchedText()),
                            Comparator.reverseOrder()
                    )
                    .thenComparing(TermMatchCandidate::matchedText);

    public Optional<TermMatchCandidate> match(TermMatchTarget target, NewsMatchContent content) {
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }

        List<TermMatchCandidate> candidates = new ArrayList<>();
        addMatches(candidates, target, content, target.name(), MatchType.EXACT_NAME);
        target.aliases().stream()
                .filter(TermNewsMatcher::isEligibleAlias)
                .forEach(alias -> addMatches(candidates, target, content, alias, MatchType.ALIAS));

        return candidates.stream().sorted(CANDIDATE_ORDER).findFirst();
    }

    private static void addMatches(
            List<TermMatchCandidate> candidates,
            TermMatchTarget target,
            NewsMatchContent content,
            String expression,
            MatchType matchType
    ) {
        if (contains(content.title(), expression)) {
            candidates.add(candidate(target, content, expression, matchType, MatchedField.TITLE));
        }
        if (contains(content.summary(), expression)) {
            candidates.add(candidate(target, content, expression, matchType, MatchedField.SUMMARY));
        }
    }

    private static TermMatchCandidate candidate(
            TermMatchTarget target,
            NewsMatchContent content,
            String expression,
            MatchType matchType,
            MatchedField field
    ) {
        return new TermMatchCandidate(
                target.termId(),
                content.newsArticleId(),
                matchType,
                score(matchType, field),
                expression,
                field
        );
    }

    private static BigDecimal score(MatchType matchType, MatchedField field) {
        if (matchType == MatchType.EXACT_NAME) {
            return field == MatchedField.TITLE ? TITLE_EXACT_SCORE : SUMMARY_EXACT_SCORE;
        }
        return field == MatchedField.TITLE ? TITLE_ALIAS_SCORE : SUMMARY_ALIAS_SCORE;
    }

    private static boolean contains(String content, String expression) {
        int fromIndex = 0;
        while (fromIndex <= content.length() - expression.length()) {
            int matchIndex = content.indexOf(expression, fromIndex);
            if (matchIndex < 0) {
                return false;
            }
            if (!requiresAsciiTokenBoundary(expression)
                    || hasAsciiTokenBoundary(content, matchIndex, expression.length())) {
                return true;
            }
            fromIndex = matchIndex + 1;
        }
        return false;
    }

    private static boolean requiresAsciiTokenBoundary(String expression) {
        return expression.codePoints()
                .allMatch(codePoint -> codePoint == ' ' || isAsciiLetterOrDigit(codePoint));
    }

    private static boolean hasAsciiTokenBoundary(String content, int index, int length) {
        boolean startsAtBoundary = index == 0 || !isAsciiLetterOrDigit(content.charAt(index - 1));
        int endIndex = index + length;
        boolean endsAtBoundary = endIndex == content.length()
                || !isAsciiLetterOrDigit(content.charAt(endIndex));
        return startsAtBoundary && endsAtBoundary;
    }

    private static boolean isAsciiLetterOrDigit(int value) {
        return value >= 'a' && value <= 'z'
                || value >= '0' && value <= '9';
    }

    private static boolean isEligibleAlias(String alias) {
        return codePointLength(alias) >= 2;
    }

    private static int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }

    private static int matchTypePriority(TermMatchCandidate candidate) {
        return candidate.matchType() == MatchType.EXACT_NAME ? 2 : 1;
    }

    private static int fieldPriority(TermMatchCandidate candidate) {
        return candidate.matchedField() == MatchedField.TITLE ? 2 : 1;
    }
}
