import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The default implementation of Vocabulary. */
public class Vocabulary implements IVocabulary {

    private Map<String, TokenInfo> tokens;
    private List<String> indexToToken;
    private Set<String> reservedTokens;
    private String unknownToken;

    /**
     * Creates a {@code DefaultVocabulary} object with the given list of tokens.
     *
     * @param tokens the {@link List} of tokens to build the vocabulary with
     */
    public Vocabulary(List<String> tokens) {
        this(builder().add(tokens));
    }

    /**
     * Creates a {@code DefaultVocabulary} object with a {@link Builder}.
     *
     * @param builder the {@link Builder} to build the vocabulary with
     */
    public Vocabulary(Builder builder) {
        tokens = new ConcurrentHashMap<>();
        reservedTokens = builder.reservedTokens;
        unknownToken = builder.unknownToken;
        if (unknownToken != null) {
            reservedTokens.add(unknownToken);
        }
        for (List<String> sentence : builder.sentences) {
            for (String token : sentence) {
                addToken(token);
            }
        }
        // Preserve order in vocab file, add reservedTokens after original vocab
        for (String token : reservedTokens) {
            addToken(token);
        }

        boolean pruned = pruneTokens(builder.minFrequency, builder.maxTokens);
        if (pruned) {
            initializeIndexToTokenReplacingIndices();
        } else {
            initializeIndexToTokenKeepingIndices();
        }
    }

    private void addToken(String token) {
        int index = tokens.size();
        tokens.compute(
                token,
                (k, v) -> {
                    if (v == null) {
                        v = new TokenInfo();
                        // Set index only when adding a new token
                        v.index = index;
                    }

                    // Update the frequency for both old and new tokens
                    if (reservedTokens.contains(k)) {
                        v.frequency = Integer.MAX_VALUE;
                    } else if (v.frequency < Integer.MAX_VALUE) {
                        ++v.frequency;
                    }
                    return v;
                });
    }

    /**
     * Removes tokens from {@code tokens} based on the arguments.
     *
     * @param minFrequency a minimum frequency where all tokens below it are pruned. -1 for no
     *     minFrequency.
     * @param maxSize a maximum number of tokens where only the maxSize most frequent are kept. -1
     *     for no maxSize
     * @return returns true if pruning occurred
     */
    private boolean pruneTokens(int minFrequency, int maxSize) {
        boolean pruned = false;
        // Prune tokens below min frequency
        if (minFrequency > 1) {
            for (Map.Entry<String, TokenInfo> token : tokens.entrySet()) {
                if (token.getValue().frequency < minFrequency) {
                    tokens.remove(token.getKey());
                }
            }
            pruned = true;
        }

        // Prune number of tokens to maxSize
        if (maxSize > 0 && tokens.size() > maxSize) {
            tokens.entrySet()
                    .stream()
                    .sorted(
                            Map.Entry.comparingByValue(
                                    Comparator.comparingInt(
                                            tokenInfo ->
                                                    -tokenInfo.frequency))) // most frequent first
                    .skip(maxSize)
                    .forEach(token -> tokens.remove(token.getKey()));
            pruned = true;
        }
        return pruned;
    }

    /**
     * Initializes indexToToken using the indices in tokens.
     *
     * <p>This is used when not pruning to preserve the order tokens were given to this vocabulary.
     */
    private void initializeIndexToTokenKeepingIndices() {
        indexToToken = Arrays.asList(new String[tokens.size()]);
        for (Map.Entry<String, TokenInfo> token : tokens.entrySet()) {
            indexToToken.set(Math.toIntExact(token.getValue().index), token.getKey());
        }
    }

    /**
     * Initializes indexToToken while replacing the indices in tokens.
     *
     * <p>When pruning, there will be unused indices. So, this will redo the indexing to be fully
     * compact without indexing gaps. The order of the original indices is preserved.
     */
    private void initializeIndexToTokenReplacingIndices() {
        indexToToken =
                tokens.entrySet()
                        .stream()
                        .sorted(Comparator.comparingLong(token -> token.getValue().index))
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toList());
        for (int i = 0; i < indexToToken.size(); i++) {
            tokens.get(indexToToken.get(i)).index = i;
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean contains(String token) {
        return tokens.containsKey(token);
    }

    /** {@inheritDoc} */
    @Override
    public String getToken(long index) {
        if (index < 0 || index >= indexToToken.size()) {
            return unknownToken;
        }
        return indexToToken.get((int) index);
    }

    /** {@inheritDoc} */
    @Override
    public long getIndex(String token) {
        if (tokens.containsKey(token)) {
            return tokens.get(token).index;
        }

        if (unknownToken != null) {
            return tokens.get(unknownToken).index;
        }

        throw new IllegalStateException(
                "Unexpected token in getIndex. Define an unknownToken for the vocabulary to enable support for unknown tokens.");
    }

    /** {@inheritDoc} */
    @Override
    public long size() {
        return tokens.size();
    }

    /**
     * Creates a new builder to build a {@code DefaultVocabulary}.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /** Builder class that is used to build the {@link Vocabulary}. */
    public static final class Builder {

        List<List<String>> sentences = new ArrayList<>();
        Set<String> reservedTokens = new HashSet<>();
        int minFrequency = -1;
        int maxTokens = -1;
        String unknownToken;

        private Builder() {}

        /**
         * Sets the optional parameter that specifies the minimum frequency to consider a token to
         * be part of the {@link Vocabulary}. Defaults to no minimum.
         *
         * @param minFrequency the minimum frequency to consider a token to be part of the {@link
         *     Vocabulary} or -1 for no minimum
         * @return this {@code VocabularyBuilder}
         */
        public Builder optMinFrequency(int minFrequency) {
            this.minFrequency = minFrequency;
            return this;
        }

        /**
         * Sets the optional limit on the size of the vocabulary.
         *
         * <p>The size includes the reservedTokens. If the number of added tokens exceeds the
         * maxToken limit, it keeps the most frequent tokens.
         *
         * @param maxTokens the maximum number of tokens or -1 for no maximum
         * @return this {@link Builder}
         */
        public Builder optMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        /**
         * Sets the optional parameter that specifies the unknown token's string value with
         * "&gt;unk&lt;".
         *
         * @return this {@code VocabularyBuilder}
         */
        public Builder optUnknownToken() {
            return optUnknownToken("<unk>");
        }

        /**
         * Sets the optional parameter that specifies the unknown token's string value.
         *
         * @param unknownToken the string value of the unknown token
         * @return this {@code VocabularyBuilder}
         */
        public Builder optUnknownToken(String unknownToken) {
            this.unknownToken = unknownToken;
            return this;
        }

        /**
         * Sets the optional parameter that sets the list of reserved tokens.
         *
         * @param reservedTokens the list of reserved tokens
         * @return this {@code VocabularyBuilder}
         */
        public Builder optReservedTokens(Collection<String> reservedTokens) {
            this.reservedTokens.addAll(reservedTokens);
            return this;
        }

        /**
         * Adds the given sentence to the {@link Vocabulary}.
         *
         * @param sentence the sentence to be added
         * @return this {@code VocabularyBuilder}
         */
        public Builder add(List<String> sentence) {
            this.sentences.add(sentence);
            return this;
        }

        /**
         * Adds the given list of sentences to the {@link Vocabulary}.
         *
         * @param sentences the list of sentences to be added
         * @return this {@code VocabularyBuilder}
         */
        public Builder addAll(List<List<String>> sentences) {
            this.sentences.addAll(sentences);
            return this;
        }

        /**
         * Adds a text vocabulary to the {@link Vocabulary}.
         *
         * <pre>
         *   Example text file(vocab.txt):
         *   token1
         *   token2
         *   token3
         *   will be mapped to index of 0 1 2
         * </pre>
         *
         * @param path the path to the text file
         * @return this {@code VocabularyBuilder}
         * @throws IOException if failed to read vocabulary file
         */
        public Builder addFromTextFile(Path path) throws IOException {
            add(readLines(path, true));
            return this;
        }

        /**
         * Adds a text vocabulary to the {@link Vocabulary}.
         *
         * @param url the text file url
         * @return this {@code VocabularyBuilder}
         * @throws IOException if failed to read vocabulary file
         */
        public Builder addFromTextFile(URL url) throws IOException {
            try (InputStream is = url.openStream()) {
                add(readLines(is, true));
            }
            return this;
        }

        /**
         * Reads all lines from a file.
         *
         * @param file the file to be read
         * @return all lines in the file
         * @throws IOException if read file failed
         */
        public static List<String> readLines(Path file) throws IOException {
            return readLines(file, false);
        }

        /**
         * Reads all lines from a file.
         *
         * @param file the file to be read
         * @param trim true if you want to trim the line and exclude empty lines
         * @return all lines in the file
         * @throws IOException if read file failed
         */
        public static List<String> readLines(Path file, boolean trim) throws IOException {
            if (Files.notExists(file)) {
                return Collections.emptyList();
            }
            try (InputStream is = new BufferedInputStream(Files.newInputStream(file))) {
                return readLines(is, trim);
            }
        }

        /**
         * Reads all lines from the specified InputStream.
         *
         * @param is the InputStream to read
         * @return all lines from the input
         */
        public static List<String> readLines(InputStream is) {
            return readLines(is, false);
        }

        /**
         * Reads all lines from the specified InputStream.
         *
         * @param is the InputStream to read
         * @param trim true if you want to trim the line and exclude empty lines
         * @return all lines from the input
         */
        public static List<String> readLines(InputStream is, boolean trim) {
            List<String> list = new ArrayList<>();
            try (Scanner scanner =
                         new Scanner(is, StandardCharsets.UTF_8.name()).useDelimiter("\\n|\\r\\n")) {
                while (scanner.hasNext()) {
                    String line = scanner.next();
                    if (trim) {
                        line = line.trim();
                        if (line.isEmpty()) {
                            continue;
                        }
                    }
                    list.add(line);
                }
            }
            return list;
        }

        /**
         * Adds a customized vocabulary to the {@link Vocabulary}.
         *
         * @param url the text file url
         * @param lambda the function to parse the vocabulary file
         * @return this {@code VocabularyBuilder}
         */
        public Builder addFromCustomizedFile(URL url, Function<URL, List<String>> lambda) {
            return add(lambda.apply(url));
        }

        /**
         * Builds the {@link Vocabulary} object with the set arguments.
         *
         * @return the {@link Vocabulary} object built
         */
        public Vocabulary build() {
            if (maxTokens > 0 && maxTokens < reservedTokens.size()) {
                throw new IllegalArgumentException(
                        "The vocabulary maxTokens can not be smaller than the number of reserved tokens");
            }
            return new Vocabulary(this);
        }
    }

    /**
     * {@code TokenInfo} represents the information stored in the {@link Vocabulary} about a
     * given token.
     */
    private static final class TokenInfo {
        int frequency;
        long index = -1;
    }
}