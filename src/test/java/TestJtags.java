import static notest.Test.Util.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
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
            "src/test/resources/BasicExample.java");

    var tags = readAllLines(process.inputReader());

    getSnapshot().assertEquals(tags);
  }

  static void downloadOpenjdkSources() throws IOException {
    Path destination = Path.of("src", "test", "resources", "downloads");
    if (Files.exists(destination.resolve("openjdk-src"))) return;

    URL openjdkSrcZipURL =
        URI.create("https://github.com/openjdk/jdk24u/archive/refs/tags/jdk-24+36.zip").toURL();
    Path openjdkSrcZip = destination.resolve("jdk-24+36.zip");

    if (!Files.exists(openjdkSrcZip)) {
      System.err.println("Downloading openjdk sources jdk-24+36.zip (~118MB)...");
      openjdkSrcZipURL
          .openConnection()
          .getInputStream()
          .transferTo(Files.newOutputStream(openjdkSrcZip));
    }

    System.err.println("Extracting openjdk sources...");

    Files.createDirectories(destination.resolve("openjdk-src"));

    try (InputStream in = Files.newInputStream(openjdkSrcZip);
        ZipInputStream zin = new ZipInputStream(in)) {
      ZipEntry entry;
      while ((entry = zin.getNextEntry()) != null) {
        if (!entry.getName().startsWith("jdk24u-jdk-24-36/src/j")) {
          zin.closeEntry();
          entry = zin.getNextEntry();
          continue;
        }
        Path entryPath =
            destination.resolve(
                "openjdk-src", entry.getName().substring("jdk24u-jdk-24-36/src/".length()));
        if (entry.isDirectory()) {
          Files.createDirectories(entryPath);
        } else {
          var bytes = zin.readAllBytes();
          Files.write(entryPath, bytes);
        }
        zin.closeEntry();
      }
    }
  }

  @Test
  static void openjdkSources() throws IOException {
    downloadOpenjdkSources();

    // var process =
    //     runJava(
    //         new String[] {"-Dlogger.level=CONFIG"},
    //         Jtags,
    //         Stream.concat(Stream.of("-o", "-"),
    // Arrays.stream(glob("src/test/resources/downloads/**/.java")))
    //             .toArray(String[]::new));

    // var tags = readAllLines(process.inputReader());

    // getSnapshot().assertEquals(tags);
  }
}
