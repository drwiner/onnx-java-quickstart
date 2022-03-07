import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class WordpieceTokenizer  {

    private String unknown;
    private int maxInputChars;
    private Vocabulary vocabulary;

    /**
     * Creates an instance of {@code WordpieceTokenizer}.
     *
     * @param vocabulary a {@code DefaultVocabulary} used for wordpiece tokenization
     * @param unknown String that represent unknown token
     * @param maxInputChars maximum number of input characters
     */
    public WordpieceTokenizer(Vocabulary vocabulary, String unknown, int maxInputChars) {
        this.unknown = unknown;
        this.maxInputChars = maxInputChars;
        this.vocabulary = vocabulary;
    }

    public long[] tokenToIds(List<String> tokens){
        long[] idArray = new long[tokens.size()];
        for (int i=0; i< tokens.size(); i++){
            idArray[i] = vocabulary.getIndex(tokens.get(i));
        }
        return idArray;
    }

    public List<String> tokenize(String sentence) {
        StringBuilder sb = new StringBuilder();
        List<String> subTokens = new ArrayList<>();
        List<String> outputTokens = new ArrayList<>();
        List<String> strings = Arrays.asList(sentence.trim().split(" "));
        for (String token : strings) {
            char[] chars = token.toCharArray();
            if (chars.length > maxInputChars) {
                outputTokens.add(unknown);
                continue;
            }
            boolean isBad = false;
            int start = 0;
            subTokens.clear();
            String currentSubString = null;
            while (start < chars.length) {
                int end = chars.length;
                while (start < end) {
                    sb.setLength(0);
                    sb.append(token, start, end);
                    if (start > 0) {
                        sb.insert(0, "##");
                    }
                    String subString = sb.toString();
                    if (vocabulary.contains(subString)) {
                        currentSubString = subString;
                        break;
                    } else {
                        currentSubString = null;
                    }
                    end--;
                }
                if (currentSubString == null) {
                    isBad = true;
                    break;
                }
                subTokens.add(currentSubString);
                if (subTokens.size() > maxInputChars) {
                    throw new IllegalStateException("Too many subTokens for: '" + sentence + '\'');
                }
                start = end;
            }
            if (isBad) {
                outputTokens.add(unknown);
            } else {
                outputTokens.addAll(subTokens);
            }
        }
        return outputTokens;
    }
}
