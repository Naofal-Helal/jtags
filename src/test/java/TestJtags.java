import static notest.Test.Util.*;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import notest.Test;

class TestJtags {
  public static final String Jtags = "xyz.naofal.jtags.Jtags";

  public static void main(String[] args) {
    runTests(TestJtags.class, args);
  }

  @Test
  static void basicExample() throws IOException {
    Path file = Files.createTempFile("jtags", null);
    var tags =
        runJava(
                new String[] {"-Dlogger.level=CONFIG"},
                Jtags,
                "-o",
                "-",
                "src/test/java/examples/BasicExample.java")
            .inputReader();

    Diff.diff(
            tags,
            new FileReader(
                Path.of("src", "test", "java", "snapshots", "BasicExample.basicExample.1")
                    .toFile()))
        .assertEquals();
  }
}
