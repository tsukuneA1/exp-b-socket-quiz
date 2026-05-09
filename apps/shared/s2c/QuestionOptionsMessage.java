package apps.shared.s2c;

import apps.shared.codec.InvalidMessageException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public record QuestionOptionsMessage(List<String> options) implements ServerMessage {
    public QuestionOptionsMessage {
        if (options == null || options.isEmpty())
            throw new InvalidMessageException("QUESTION_OPTIONS must have at least one option");
    }

    public static QuestionOptionsMessage parse(byte[] body) {
        if (body.length < 1)
            throw new InvalidMessageException("QUESTION_OPTIONS body too short");
        int numOptions = body[0] & 0xFF;
        List<String> options = new ArrayList<>(numOptions);
        int i = 1;
        for (int n = 0; n < numOptions; n++) {
            if (i + 2 > body.length)
                throw new InvalidMessageException("QUESTION_OPTIONS body truncated at option " + n);
            int len = ((body[i] & 0xFF) << 8) | (body[i + 1] & 0xFF);
            i += 2;
            if (i + len > body.length)
                throw new InvalidMessageException("QUESTION_OPTIONS option " + n + " length exceeds body");
            options.add(new String(body, i, len, StandardCharsets.UTF_8));
            i += len;
        }
        return new QuestionOptionsMessage(options);
    }

    public byte[] toBytes() {
        List<byte[]> encoded = new ArrayList<>(options.size());
        int totalLen = 1;
        for (String opt : options) {
            byte[] b = opt.getBytes(StandardCharsets.UTF_8);
            encoded.add(b);
            totalLen += 2 + b.length;
        }
        byte[] body = new byte[totalLen];
        body[0] = (byte) options.size();
        int i = 1;
        for (byte[] b : encoded) {
            body[i]     = (byte) (b.length >> 8);
            body[i + 1] = (byte) (b.length);
            i += 2;
            System.arraycopy(b, 0, body, i, b.length);
            i += b.length;
        }
        return body;
    }
}
