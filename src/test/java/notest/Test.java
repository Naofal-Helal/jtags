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
import java.io.Reader;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IntSummaryStatistics;
import java.util.List;
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

    /** Runs all tests in {@code testClass} */
    public static void runTests(Class<?> testClass, String[] args) {
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
                    } catch (InvocationTargetException ex) {
                      System.err.println(
                          boxMiddle(ex.getTargetException().getClass().getSimpleName(), 50));
                      System.err.println(
                          boxLeft(
                              Optional.ofNullable(ex.getTargetException().getMessage())
                                  .orElse("")));
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

    public static record Diff(List<String> diff) {

      public static Diff diff(Reader readerA, Reader readerB) throws IOException {
        List<String> diff = new ArrayList<>();

        BufferedReader
            bufReaderA = readerA instanceof BufferedReader ra ? ra : new BufferedReader(readerA),
            bufReaderB = readerB instanceof BufferedReader rb ? rb : new BufferedReader(readerB);

        String a = bufReaderA.readLine(), b = bufReaderB.readLine();
        boolean different = false;
        while (a != null || b != null) {
          if (a == null || b == null || (a != null && !a.equals(b))) {
            different = true;
            if (a != null) {
              diff.add("-" + a);
            }
            if (b != null) {
              diff.add("+" + b);
            }
          } else {
            diff.add(" " + a);
          }

          a = bufReaderA.readLine();
          b = bufReaderB.readLine();
        }

        if (different) {
          diff.sort(
              new Comparator<String>() {
                public int compare(String s1, String s2) {
                  char a = s1.charAt(0), b = s2.charAt(0);
                  if (a == ' ' || b == ' ') return 0;
                  return b - a;
                }
              });
        } else {
          diff = List.of();
        }

        return new Diff(diff);
      }

      public void assertEquals() {
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
