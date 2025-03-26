import static notest.Test.Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import notest.Test;

class TestJtags {
  public static final String Jtags = "xyz.naofal.jtags.Jtags";

  public static void main(String[] args) {
    System.exit(Util.runTests(TestJtags.class) ? 0 : 1);
  }

  @Test
  static void basicExample() throws IOException {
    Path file = Files.createTempFile("jtags", null);
    Util.runJava(
        new String[0],
        new String[] {"-Dlogger.level=CONFIG"},
        Jtags,
        "-o",
        file.toString(),
        "src/test/java/examples/BasicExample.java");
    assert Files.readString(file)
            .trim()
            .equals(
                """
                ABC
                """
                    .trim())
        : "Unexpected Result:\n" + Files.readString(file);
  }
}
