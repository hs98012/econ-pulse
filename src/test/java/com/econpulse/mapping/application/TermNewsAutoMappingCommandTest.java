package com.econpulse.mapping.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class TermNewsAutoMappingCommandTest {

    @Test
    void deduplicatesSortsAndDefensivelyCopiesIds() {
        List<Long> ids = new ArrayList<>(List.of(3L, 1L, 3L, 2L));

        TermNewsAutoMappingCommand command = new TermNewsAutoMappingCommand(ids);
        ids.add(4L);

        assertThat(command.newsArticleIds()).containsExactly(1L, 2L, 3L);
        assertThatThrownBy(() -> command.newsArticleIds().add(5L))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void createsSingleNewsCommand() {
        assertThat(TermNewsAutoMappingCommand.single(7L).newsArticleIds()).containsExactly(7L);
    }

    @Test
    void rejectsNullEmptyAndNonPositiveIds() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TermNewsAutoMappingCommand(null));
        assertThatIllegalArgumentException().isThrownBy(() -> new TermNewsAutoMappingCommand(List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new TermNewsAutoMappingCommand(List.of(0L)));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new TermNewsAutoMappingCommand(java.util.Arrays.asList(1L, null))
        );
    }

    @Test
    void rejectsMoreThanOneHundredUniqueNewsIds() {
        List<Long> ids = LongStream.rangeClosed(1, 101).boxed().toList();

        assertThatIllegalArgumentException().isThrownBy(() -> new TermNewsAutoMappingCommand(ids));
    }
}
