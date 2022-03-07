import ai.onnxruntime.*;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


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

        tokenize = wordpieceTokenizer.tokenize("[CLS] i want to make a transfer to israel [SEP]");
        System.out.println(tokenize);

        long[] ids = wordpieceTokenizer.tokenToIds(tokenize);
//        System.out.println(ids);

        long[][] idArray = new long[1][ids.length];
        idArray[0] = ids;

        String model = "/Users/davidwiner/Documents/workspace/onnx/beto.onnx";
//
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        OrtSession session;
        try {
            session = env.createSession(model, new OrtSession.SessionOptions());
            OnnxTensor tensor = OnnxTensor.createTensor(env, idArray);
            HashMap<String, OnnxTensor> input = new HashMap<String, OnnxTensor>() {
                {
                    put("input_ids", tensor);
                }
            };

            try (OrtSession.Result result = session.run(input)) {
                // manipulate the results
                float[][] perTokenVector = (float[][]) result.get(1).getValue();
                float [] vector = perTokenVector[0];
                System.out.println(vector.length);

            }
        } catch (OrtException e) {
            e.printStackTrace();
        }


    }

}
