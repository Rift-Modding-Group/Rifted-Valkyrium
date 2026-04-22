package org.valkyrienskies.mod.common.util.names;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Generates names from a noun list
 */
public class NounListNameGenerator implements NameGenerator {

    private static final int NOUN_LIST_LENGTH = 6801;
    private static final int DEFAULT_NOUNS_PER_NAME = 3;
    private final List<String> nouns = new ArrayList<>(NOUN_LIST_LENGTH);

    private static final NounListNameGenerator instance = new NounListNameGenerator();

    private NounListNameGenerator() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            Objects.requireNonNull(getClass().getClassLoader()
                .getResourceAsStream("assets/valkyrienskies/nounlist.txt"))))) {
            String line;
            while ((line = reader.readLine()) != null) {
                nouns.add(line);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load noun list", e);
        }
    }

    @Override
    public String generateName() {
        return this.generateName(DEFAULT_NOUNS_PER_NAME);
    }

    public String generateName(int numberOfNouns) {
        return ThreadLocalRandom.current()
            .ints(numberOfNouns, 0, nouns.size())
            .mapToObj(nouns::get)
            .collect(Collectors.joining("-"));
    }

    public static NounListNameGenerator getInstance() {
        return instance;
    }
}
