package com.oodesigns.cas.domain.service;

import com.oodesigns.cas.domain.value.BackupCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BackupCodeGenerator}.
 */
class BackupCodeGeneratorTest {

    private static final String CODE_PATTERN = "^[0-9A-Z]{4}-[0-9A-Z]{4}-[0-9A-Z]{4}-[0-9A-Z]{4}$";

    /** Symbols deliberately excluded to avoid transcription errors. */
    private static final String AMBIGUOUS_SYMBOLS = "IO01";

    private static SecureRandom seededRandom(final long seed) throws NoSuchAlgorithmException {
        final SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
        random.setSeed(seed);
        return random;
    }

    // ---------------------------------------------------------------- format

    @Test
    void generateProducesCodeInRequiredFormat() {
        final BackupCode code = new BackupCodeGenerator().generate();
        assertTrue(code.getCode().matches(CODE_PATTERN),
            "Expected XXXX-XXXX-XXXX-XXXX but was: " + code.getCode());
        assertEquals(19, code.length());
        assertEquals(16, code.normalized().length());
    }

    @Test
    void generatedCodesNeverContainAmbiguousSymbols() {
        final BackupCodeGenerator generator = new BackupCodeGenerator();
        for (int i = 0; i < 500; i++) {
            final String normalized = generator.generate().normalized();
            for (final char ambiguous : AMBIGUOUS_SYMBOLS.toCharArray()) {
                assertEquals(-1, normalized.indexOf(ambiguous),
                    "Code must not contain ambiguous symbol '" + ambiguous + "': " + normalized);
            }
        }
    }

    @Test
    void generatorUsesFullAlphabetOverManyDraws() {
        // Sanity check that no symbol is unreachable (e.g. an off-by-one in the index bound)
        final BackupCodeGenerator generator = new BackupCodeGenerator();
        final Set<Character> observed = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            for (final char symbol : generator.generate().normalized().toCharArray()) {
                observed.add(symbol);
            }
        }
        assertEquals(32, observed.size(), "Expected all 32 alphabet symbols to be reachable");
    }

    // ---------------------------------------------------------------- batches

    @Test
    void generateBatchReturnsDefaultSizeOfDistinctCodes() {
        final List<BackupCode> codes = new BackupCodeGenerator().generateBatch();
        assertEquals(BackupCodeGenerator.DEFAULT_BATCH_SIZE, codes.size());
        assertEquals(codes.size(), new HashSet<>(codes).size(), "Backup codes must be distinct");
        codes.forEach(code -> assertTrue(code.getCode().matches(CODE_PATTERN)));
    }

    @ParameterizedTest(name = "batch of {0}")
    @ValueSource(ints = {1, 2, 10, 16})
    void generateBatchHonoursRequestedSize(final int count) {
        final List<BackupCode> codes = new BackupCodeGenerator().generateBatch(count);
        assertEquals(count, codes.size());
        assertEquals(count, new HashSet<>(codes).size(), "Backup codes must be distinct");
    }

    @Test
    void generateBatchReturnsImmutableList() {
        final List<BackupCode> codes = new BackupCodeGenerator().generateBatch(2);
        assertThrows(UnsupportedOperationException.class, () -> codes.add(codes.getFirst()));
    }

    @ParameterizedTest(name = "invalid batch size {0}")
    @ValueSource(ints = {0, -1, 17, 100})
    void generateBatchRejectsSizesOutsideAllowedRange(final int count) {
        final BackupCodeGenerator generator = new BackupCodeGenerator();
        final IllegalArgumentException thrown =
            assertThrows(IllegalArgumentException.class, () -> generator.generateBatch(count));
        assertTrue(thrown.getMessage().contains(String.valueOf(count)));
    }

    // ---------------------------------------------------------------- randomness source

    @Test
    void constructorRejectsNullRandom() {
        assertThrows(NullPointerException.class, () -> new BackupCodeGenerator(null));
    }

    @Test
    void seededGeneratorIsReproducible() throws NoSuchAlgorithmException {
        final List<BackupCode> first = new BackupCodeGenerator(seededRandom(42L)).generateBatch(5);
        final List<BackupCode> second = new BackupCodeGenerator(seededRandom(42L)).generateBatch(5);
        assertEquals(first, second, "Same seed must yield the same codes (proves SecureRandom is the only entropy source)");
    }

    @Test
    void differentSeedsProduceDifferentCodes() throws NoSuchAlgorithmException {
        final List<BackupCode> first = new BackupCodeGenerator(seededRandom(1L)).generateBatch(5);
        final List<BackupCode> second = new BackupCodeGenerator(seededRandom(2L)).generateBatch(5);
        assertNotEquals(first, second);
    }

    @Test
    void independentGeneratorsDoNotCollide() {
        // 80 bits of entropy per code: two independent batches must not overlap
        final Set<BackupCode> all = new HashSet<>(new BackupCodeGenerator().generateBatch(16));
        all.addAll(new BackupCodeGenerator().generateBatch(16));
        assertEquals(32, all.size());
    }

    @Test
    void generateBatchRedrawsWhenARandomDuplicateOccurs() {
        // Force a collision: the first two codes drawn are identical, so the batch must
        // discard the duplicate and draw again rather than return a short or repeated list.
        final int symbolsPerCode = 16;
        final SecureRandom collidingRandom = new SecureRandom() {
            private int draws;

            @Override
            public int nextInt(final int bound) {
                draws++;
                // First two codes are all-'A'; everything after is all-'B'
                return draws <= symbolsPerCode * 2 ? 0 : 1;
            }
        };

        final List<BackupCode> codes = new BackupCodeGenerator(collidingRandom).generateBatch(2);

        assertEquals(2, codes.size());
        assertEquals(2, new HashSet<>(codes).size(), "Duplicate must have been rejected and redrawn");
        assertEquals("AAAA-AAAA-AAAA-AAAA", codes.getFirst().getCode());
        assertEquals("BBBB-BBBB-BBBB-BBBB", codes.get(1).getCode());
    }
}


