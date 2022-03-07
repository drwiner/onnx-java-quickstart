public interface IVocabulary {

    /**
     * Returns the token corresponding to the given index.
     *
     * @param index the index
     * @return the token corresponding to the given index
     */
    String getToken(long index);

    /**
     * Check if the vocabulary contains a token.
     *
     * @param token String token to be checked
     * @return whether this vocabulary contains the token
     */
    boolean contains(String token);

    /**
     * Returns the index of the given token.
     *
     * @param token the token
     * @return the index of the given token.
     */
    long getIndex(String token);

    /**
     * Returns the size of the {@link Vocabulary}.
     *
     * @return the size of the {@link Vocabulary}
     */
    long size();
}