package com.econpulse.popular.infrastructure.redis;

import com.econpulse.popular.application.PopularTermScore;
import com.econpulse.popular.application.port.PopularTermStore;
import com.econpulse.popular.application.port.PopularTermStoreException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Repository;

@Repository
public class RedisPopularTermStore implements PopularTermStore {

    static final long MAX_SAFE_SCORE = 9_007_199_254_740_991L;

    private static final Comparator<ScoredTerm> RESULT_ORDER = Comparator
            .comparingLong(ScoredTerm::score).reversed()
            .thenComparingLong(ScoredTerm::economicTermId);

    private final StringRedisTemplate redisTemplate;
    private final PopularTermRedisKey redisKey;
    private final PopularTermProperties properties;

    public RedisPopularTermStore(
            StringRedisTemplate redisTemplate,
            PopularTermRedisKey redisKey,
            PopularTermProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.redisKey = redisKey;
        this.properties = properties;
    }

    @Override
    public void increment(long economicTermId, LocalDate date) {
        if (economicTermId <= 0) {
            throw new IllegalArgumentException("Economic term ID must be positive.");
        }
        String key = redisKey.daily(date);
        try {
            Double score = redisTemplate.opsForZSet().incrementScore(key, Long.toString(economicTermId), 1.0);
            validateScore(score);
            Boolean expirationSet = redisTemplate.expire(key, properties.retention());
            if (!Boolean.TRUE.equals(expirationSet)) {
                throw new PopularTermStoreException(
                        PopularTermStoreException.Reason.UNAVAILABLE,
                        "Popular term retention could not be applied."
                );
            }
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public List<PopularTermScore> findTop(LocalDate date, int limit) {
        if (limit < 1 || limit > properties.maxQuerySize()) {
            throw new IllegalArgumentException("Popular term limit exceeds the configured range.");
        }
        try {
            Set<TypedTuple<String>> tuples = redisTemplate.opsForZSet()
                    .reverseRangeWithScores(redisKey.daily(date), 0, limit - 1L);
            if (tuples == null || tuples.isEmpty()) {
                return List.of();
            }
            List<ScoredTerm> terms = new ArrayList<>(tuples.size());
            for (TypedTuple<String> tuple : tuples) {
                terms.add(toScoredTerm(tuple));
            }
            terms.sort(RESULT_ORDER);
            List<PopularTermScore> result = new ArrayList<>(terms.size());
            for (int index = 0; index < terms.size(); index++) {
                ScoredTerm term = terms.get(index);
                result.add(new PopularTermScore(term.economicTermId(), term.score(), index + 1));
            }
            return List.copyOf(result);
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    private ScoredTerm toScoredTerm(TypedTuple<String> tuple) {
        String member = tuple.getValue();
        if (member == null) {
            throw invalidData("Popular term member must not be null.");
        }
        try {
            long economicTermId = Long.parseLong(member);
            if (economicTermId <= 0) {
                throw invalidData("Popular term member must be a positive economic term ID.");
            }
            return new ScoredTerm(economicTermId, validateScore(tuple.getScore()));
        } catch (NumberFormatException exception) {
            throw new PopularTermStoreException(
                    PopularTermStoreException.Reason.INVALID_DATA,
                    "Popular term member is not a valid economic term ID.",
                    exception
            );
        }
    }

    private long validateScore(Double value) {
        if (value == null || !Double.isFinite(value) || value < 0 || value != Math.rint(value)
                || value > MAX_SAFE_SCORE) {
            throw invalidData("Popular term score is not a non-negative safe integer.");
        }
        return value.longValue();
    }

    private PopularTermStoreException invalidData(String message) {
        return new PopularTermStoreException(PopularTermStoreException.Reason.INVALID_DATA, message);
    }

    private PopularTermStoreException unavailable(DataAccessException cause) {
        return new PopularTermStoreException(
                PopularTermStoreException.Reason.UNAVAILABLE,
                "Popular term store is unavailable.",
                cause
        );
    }

    private record ScoredTerm(long economicTermId, long score) {
    }
}
