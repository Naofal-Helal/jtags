/*
 * NoTest - Simple testing utilities
 *
 * MIT License
 *
 * Copyright (c) 2025 Naofal Helal
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package notest;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Marks a method as a test method */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Test {

  /** Utility methods for performing tests */
  public static class Util {
    public static Path javaHome = Paths.get(System.getProperty("java.home"));
    public static String javaClassPath = System.getProperty("java.class.path");
    public static String javaBin = javaHome.resolve("bin", "java").toString();
    public static Path snapshotsPath = Path.of("src", "test", "java", "snapshots");

    private static class Options {
      boolean updateSnapshots = false;
    }

    private static Options options = new Options();

    /** Runs all tests in {@code testClass} */
    public static void runTests(Class<?> testClass, String[] args) {
      var arguments = new ArrayDeque<String>(Arrays.asList(args));

      switch (arguments.poll()) {
        case "-u", "-update-snapshots":
          options.updateSnapshots = true;
          break;
        case null, default:
          break;
      }

      System.exit(doRunTests(testClass) ? 0 : 1);
    }

    /** Runs all tests in {@code testClass} */
    public static boolean doRunTests(Class<?> testClass) {
      IntSummaryStatistics stats =
          Arrays.stream(testClass.getDeclaredMethods())
              .filter(it -> it.isAnnotationPresent(Test.class))
              .map(
                  it -> {
                    try {
                      System.err.println(boxTop("Test " + it.getName(), 50));
                      it.setAccessible(true);
                      it.invoke(null);
                      System.err.println(boxBottom(it.getName() + ": SUCCESS", 50));
                      System.err.println();
                      return 1;
                    } catch (IllegalAccessException ex) {
                      System.err.println(boxBottom("ERROR: could not run " + it.getName(), 50));
                      System.err.println(ex.toString());
                      System.err.println();
                      return 0;
                    } catch (InvocationTargetException invocationException) {
                      var ex = invocationException.getTargetException();
                      System.err.println(boxMiddle(ex.getClass().getSimpleName(), 50));
                      System.err.println(boxLeft(Optional.ofNullable(ex.getMessage()).orElse("")));
                      if (!(ex instanceof AssertionError)) {
                        ex.printStackTrace();
                      }
                      System.err.println(boxBottom(it.getName() + ": FAIL", 50));
                      System.err.println();
                      return 0;
                    }
                  })
              .collect(Collectors.summarizingInt(it -> it));

      System.err.printf(
          "Ran %d tests; %d Succeeded, %d Failed%n"
              .formatted(stats.getCount(), stats.getSum(), stats.getCount() - stats.getSum()));

      return stats.getCount() == stats.getSum();
    }

    /**
     * Runs a Java class
     *
     * @param mainClass The fully qualified class name, e.g. {@code com.example.MyClass$MySubClass}
     * @param args Arguments to pass to the main method
     * @return A {@link Process} representing the started process
     */
    public static Process runJava(String mainClass, String... args) {
      return runJava(new String[0], mainClass, args);
    }

    /**
     * Runs a Java class
     *
     * @param additionalClassPaths Additional class paths to pass to the compiler
     * @param javaArguments Arguments to pass to the java binary
     * @param mainClass The fully qualified class name, e.g. {@code com.example.MyClass$MySubClass}
     * @param args Arguments to pass to the main method
     * @return A {@link Process} representing the started process
     */
    public static Process runJava(String[] javaArguments, String mainClass, String... args) {
      Stream<String> commandLineStream =
          Stream.of(
                  Stream.of(javaBin),
                  Arrays.stream(javaArguments),
                  Stream.of(mainClass),
                  Arrays.stream(args))
              .flatMap(it -> it);

      ProcessBuilder pb =
          new ProcessBuilder(commandLineStream.toArray(String[]::new))
              .redirectInput(Redirect.PIPE)
              .redirectOutput(Redirect.PIPE)
              .redirectError(Redirect.PIPE);

      try {
        return pb.start();
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    }

    /**
     * Returns file paths that match a {@code globPattern}
     *
     * @see java.nio.file.FileSystem#getPathMatcher(String) getPathMatcher
     */
    public static String[] glob(String globPattern) {
      Path cwd = Paths.get(".");
      PathMatcher pathMatcher =
          FileSystems.getDefault()
              .getPathMatcher(
                  String.join("", "glob:", cwd.toString(), File.separator, globPattern));
      try (@SuppressWarnings("unused")
          Stream<Path> paths =
              Files.find(
                  cwd,
                  Integer.MAX_VALUE,
                  (path, basicFileAttributes) -> pathMatcher.matches(path))) {
        return paths.map(Path::toString).toArray(String[]::new);
      } catch (IOException ex) {
        throw new RuntimeException("Error during glob pattern matching", ex);
      }
    }

    /** Representation of changes between two string sequences */
    public static record Diff(List<String> diff) {

      /** Creates a {@code Diff} representing changes from {@code a} to {@code b} */
      public static Diff diff(List<String> a, List<String> b) {
        if (a.equals(b)) return new Diff(List.of());

        if (a.isEmpty()) a = List.of("");
        if (b.isEmpty()) b = List.of("");

        int diffSize = 0;
        // longest common subsequence algorithm
        int[][] dp = new int[a.size() + 1][b.size() + 1];
        for (int i = 1; i <= a.size(); i++) {
          for (int j = 1; j <= b.size(); j++) {
            if (a.get(i - 1).equals(b.get(j - 1))) {
              dp[i][j] = dp[i - 1][j - 1] + 1;
            } else {
              dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
            }
          }
        }

        int lcs = dp[a.size()][b.size()];
        if (lcs == a.size()) return new Diff(List.of());

        List<String> diff = new ArrayList<>(a.size());
        int i = a.size(), j = b.size();
        while (true) {
          if (i > 0 && j > 0 && a.get(i - 1).equals(b.get(j - 1))) {
            diff.add(" " + a.get(i - 1));
            i--;
            j--;
          } else if (j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j])) {
            if (j > 0) diff.add("+" + b.get(j - 1));
            j--;
          } else if (i > 0 && (j == 0 || dp[i][j - 1] < dp[i - 1][j])) {
            if (i > 0) diff.add("-" + a.get(i - 1));
            i--;
          } else {
            break;
          }
        }

        return new Diff(diff.reversed());
      }

      /** Asserts that the two sequences are the same */
      public void assertEqual() {
        if (diff.isEmpty()) return;

        throw new AssertionError(
            "Result differs from expected. Diff:\n"
                + String.join(
                    "\n",
                    diff.stream()
                        .map(
                            it ->
                                switch (it.charAt(0)) {
                                  case ' ' -> it;
                                  case '-' ->
                                      "\u001b[38;5;1m%s\u001b[0m"
                                          .formatted(it)
                                          .replace(' ', '·')
                                          .replace('\t', '⇥')
                                          .replaceAll("·+", "\u001b[38;5;8m$0\u001b[38;5;1m")
                                          .replaceAll("⇥+", "\u001b[38;5;8m$0\u001b[38;5;1m");
                                  case '+' ->
                                      "\u001b[38;5;2m%s\u001b[0m"
                                          .formatted(it)
                                          .replace(' ', '·')
                                          .replace('\t', '⇥')
                                          .replaceAll("·+", "\u001b[38;5;8m$0\u001b[38;5;2m")
                                          .replaceAll("⇥+", "\u001b[38;5;8m$0\u001b[38;5;2m");
                                  default -> throw new IllegalArgumentException();
                                })
                        .toList()));
      }
    }

    /** Reads all lines from {@code reader} */
    public static List<String> readAllLines(BufferedReader reader) throws IOException {
      var lines = new ArrayList<String>();
      String line;
      while ((line = reader.readLine()) != null) lines.add(line);
      return lines;
    }

    public static record Snapshot(Path path) {
      public void assertEquals(List<String> result) throws IOException {
        var lines = Files.readAllLines(path);
        if (options.updateSnapshots && !lines.equals(result)) {
          Files.write(path, result);
          return;
        }
        Diff.diff(lines, result).assertEqual();
      }
    }

    private static Map<String, Integer> snapshotCount = new HashMap<>();

    public static Snapshot getSnapshot() throws IOException {
      var stackFrame = new Throwable().getStackTrace()[1];
      var className = stackFrame.getClassName();
      var methodName = stackFrame.getMethodName();
      var prefix = className + "." + methodName;
      var snapshotNumber = snapshotCount.compute(prefix, (k, v) -> v == null ? 1 : ++v);
      Path path = snapshotsPath.resolve(prefix + "." + snapshotNumber);
      if (!snapshotsPath.toFile().exists()) snapshotsPath.toFile().mkdirs();
      if (!path.toFile().exists()) Files.write(path, List.of());
      return new Snapshot(path);
    }

    /**
     * Pads string to center.
     *
     * <p>From <a href="https://stackoverflow.com/a/8155547">stackoverflow.com/a/8155547</a>
     */
    private static String center(String s, int size, char pad) {
      if (s == null || size <= s.length()) return s;

      StringBuilder sb = new StringBuilder(size);
      for (int i = 0; i < (size - s.length()) / 2; i++) {
        sb.append(pad);
      }
      sb.append(s);
      while (sb.length() < size) {
        sb.append(pad);
      }
      return sb.toString();
    }

    private static String boxTop(String string, int width) {
      return "╭" + center(" " + string + " ", width - 2, '─') + "╮";
    }

    private static String boxMiddle(String string, int width) {
      return "├" + center(" " + string + " ", width - 2, '─') + "┤";
    }

    private static String boxBottom(String string, int width) {
      return "╰" + center(" " + string + " ", width - 2, '─') + "╯";
    }

    private static String boxLeft(String string) {
      return string;
      // return String.join("\n", string.lines().map(it -> "│ " + it).toList());
    }
  }
}
