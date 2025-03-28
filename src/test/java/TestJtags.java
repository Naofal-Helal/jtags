import static notest.Test.Util.*;

import java.io.IOException;
import notest.Test;

class TestJtags {
  public static final String Jtags = "xyz.naofal.jtags.Jtags";

  public static void main(String[] args) {
    runTests(TestJtags.class, args);
  }

  @Test
  static void basicExample() throws IOException {
    var process =
        runJava(
            new String[] {"-Dlogger.level=CONFIG"},
            Jtags,
            "-o",
            "-",
            "src/test/java/examples/BasicExample.java");

    var tags = readAllLines(process.inputReader());

    getSnapshot().assertEquals(tags);
  }
}
