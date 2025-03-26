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
import java.util.Arrays;
import java.util.IntSummaryStatistics;
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
    public static boolean runTests(Class<?> testClass) {
      IntSummaryStatistics stats =
          Arrays.stream(testClass.getDeclaredMethods())
              .filter(it -> it.isAnnotationPresent(Test.class))
              .map(
                  it -> {
                    try {
                      System.err.println(
                          "╭" + center(" Test " + it.getName() + " ", 48, '─') + "╮");
                      it.setAccessible(true);
                      it.invoke(null);
                      System.err.println(
                          "╰" + center(" " + it.getName() + ": SUCCESS ", 48, '─') + "╯");
                      System.err.println();
                      return 1;
                    } catch (IllegalAccessException ex) {
                      System.err.println(
                          "╰"
                              + center(" ERROR: could not run " + it.getName() + " ", 48, '─')
                              + "╯");
                      System.err.println(ex.toString());
                      System.err.println();
                      return 0;
                    } catch (InvocationTargetException ex) {
                      System.err.println(
                          "├"
                              + center(
                                  " " + ex.getTargetException().getClass().getSimpleName() + " ",
                                  48,
                                  '─')
                              + "┤");
                      Optional.ofNullable(ex.getTargetException().getMessage())
                          .orElse("")
                          .lines()
                          .forEach(line -> System.err.println("│ " + line));
                      System.err.println(
                          "╰" + center(" " + it.getName() + ": FAIL ", 48, '─') + "╯");
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
     * @return Exit status code
     */
    public static int runJava(String mainClass, String... args) {
      return runJava(new String[0], new String[0], mainClass, args);
    }

    /**
     * Runs a Java class
     *
     * @param additionalClassPaths Additional class paths to pass to the compiler
     * @param javaArguments Arguments to pass to the java binary
     * @param mainClass The fully qualified class name, e.g. {@code com.example.MyClass$MySubClass}
     * @param args Arguments to pass to the main method
     * @return Exit status code
     */
    public static int runJava(
        String[] additionalClassPaths, String[] javaArguments, String mainClass, String... args) {
      String pathSeparator = System.getProperty("path.separator");

      String classPaths =
          String.join(
              pathSeparator,
              Stream.concat(Stream.of(javaClassPath), Arrays.stream(additionalClassPaths))
                  .toArray(String[]::new));

      Stream<String> commandLineStream =
          Stream.of(
                  Stream.of(javaBin.toString(), "-cp", classPaths),
                  Arrays.stream(javaArguments),
                  Stream.of(mainClass),
                  Arrays.stream(args))
              .flatMap(it -> it);

      return command(commandLineStream.toArray(String[]::new));
    }

    /**
     * Runs a shell command. May throw an {@code IOException}
     *
     * @return Exit status code
     * @throws Exception
     */
    public static int command(String... command) {
      ProcessBuilder pb =
          new ProcessBuilder(command)
              .redirectInput(Redirect.INHERIT)
              .redirectOutput(Redirect.INHERIT)
              .redirectError(Redirect.INHERIT);
      Process process;
      try {
        process = pb.start();
        process.waitFor();
        return process.exitValue();
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
  }
}
