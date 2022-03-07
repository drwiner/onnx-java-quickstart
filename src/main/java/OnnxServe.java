import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;


public class OnnxServe {

    public static void main(String[] args) throws IOException {

        /*
         * Load vocab
         */

        Vocabulary vocab = Vocabulary.builder()
                        .addFromTextFile(Paths.get("./src/main/java/vocab.txt"))
                        .optUnknownToken("[UNK]")
                        .build();

        WordpieceTokenizer wordpieceTokenizer = new WordpieceTokenizer(vocab, "[UNK]", 200);
        List<String> tokenize = wordpieceTokenizer.tokenize("holaesta es uncabeza de mi piensa!!");
        System.out.println(tokenize);

        tokenize = wordpieceTokenizer.tokenize("I want to make a transfer to israel");
        System.out.println(tokenize);

        String model = "/Users/davidwiner/Documents/workspace/onnx/beto.onnx";
//
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        try {
            OrtSession session = env.createSession(model, new OrtSession.SessionOptions());
        } catch (OrtException e) {
            e.printStackTrace();
        }

    }
}
