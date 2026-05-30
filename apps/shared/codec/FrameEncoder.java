package shared.codec;

import java.io.DataOutputStream;
import java.io.IOException;

public class FrameEncoder {
  public static void writeFrame(DataOutputStream out, int type, byte[] body) throws IOException {
    out.writeByte(type);
    out.writeInt(body.length);
    out.write(body);
    out.flush();
  }
}
