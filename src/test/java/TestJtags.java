import static notest.Test.Util.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
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

    Diff.diff(
            tags,
            readAllLines(new BufferedReader(new FileReader(
                Path.of("src", "test", "java", "snapshots", "BasicExample.basicExample.1")
                    .toFile()))))
        .assertEqual();
  }
}
