/*
 * NoBuild system
 *
 * @version 0.1
 * @since 1.8
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
package nobuild;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * NoBuild system
 *
 * <h2>Quick Guide</h2>
 *
 * A simple build script:
 *
 * <pre>
 * // Build.java
 * import static nobuild.Nobuild.*;
 *
 * public class Build {
 *   public static void main(String[] args) {
 *     rebuildSelf(Build.class, args);
 *     compileJava("Hello.java");
 *     runJava("Hello");
 *   }
 * }
 * </pre>
 *
 * Which can be run as follows:
 *
 * <pre>
 * $ javac -d build/ Build.java
 * $ java -cp build/ Build
 * </pre>
 *
 * Note that by using {@link NoBuild#rebuildSelf}, the build script {@code Build} will automatically
 * rebuild itself if {@code Build.java} is modified.
 *
 * @author Naofal Helal
 * @version 0.1
 * @since 1.8
 */
public class NoBuild {
  public static Logger logger = Logger.getLogger("logger");
  public static Handler loggingHandler = new NoBuildLogHandler();
  public static JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

  public static Path javaHome = Paths.get(System.getProperty("java.home"));
  public static String javaBin = javaHome.resolve("bin", "java").toString();
  public static String javacBin = javaHome.resolve("bin", "javac").toString();

  /** The class path used by the java executable */
  public static String javaClassPath = System.getProperty("java.class.path");

  /**
   * Path to the build script class file, or {@code null} if {@link #rebuildSelf} hasn't been
   * called.
   *
   * <p>Will be used as the default output directory for compilation.
   */
  public static String buildClassPath = null;

  /** Returns the class path of the build script */
  public static String defaultBuildClassPath(Class<?> buildClass) {
    URL buildClassUrl = buildClass.getResource(buildClass.getName() + ".class");
    return Paths.get(buildClassUrl.getPath()).getParent().toString();
  }

  static {
    logger.setUseParentHandlers(false);
    logger.addHandler(loggingHandler);
    String level;
    if ((level = System.getProperty("logger.level")) != null) {
      logger.setLevel(Level.parse(level));
    }
  }

  public static class NoBuildLogHandler extends Handler {

    @Override
    public void close() {
      flush();
    }

    @Override
    public void flush() {
      System.err.flush();
    }

    @Override
    public void publish(LogRecord record) {
      if (record.getMessage() != null && !record.getMessage().isEmpty()) {
        System.err.printf("[%s] %s%n", record.getLevel().getName(), record.getMessage());
      }
      if (record.getThrown() != null) {
        System.err.println(indent(record.getThrown().toString(), 4));
      }
    }
  }

  /**
   * Rebuilds the build script if necessary
   *
   * @param buildClass The main class of the build script
   * @param args arguments from the main method
   */
  public static void rebuildSelf(Class<?> buildClass, String[] args) {
    rebuildSelf(buildClass, args, new String[0]);
  }

  /**
   * Rebuilds the build script if necessary
   *
   * @param buildClass The main class of the build script
   * @param args Arguments from the main method
   * @param additionalSources Additional sources to watch and compile
   */
  public static void rebuildSelf(Class<?> buildClass, String[] args, String... additionalSources) {
    String buildSource = buildClass.getName() + ".java";
    buildClassPath = defaultBuildClassPath(buildClass);

    String[] sourcePaths =
        Stream.concat(Stream.of(buildSource), Arrays.stream(additionalSources))
            .toArray(String[]::new);

    if (!classNeedsRebuild(buildClass.getName(), sourcePaths)) return;

    logger.info(String.format("Recompiling %s...", buildSource));

    if (!compileJava(buildClassPath, sourcePaths)) {
      logger.severe("Compilation failed");
      System.exit(1);
    }

    int status = runJava(buildClass.getName(), args);
    System.exit(status);
  }

  /**
   * Runs a Java class
   *
   * @param mainClass The fully qualified class name, e.g. {@code com.example.MyClass$MySubClass}
   * @return Exit status code
   */
  public static int runJava(String mainClass) {
    return runJava(mainClass, new String[0]);
  }

  /**
   * Runs a Java class
   *
   * @param mainClass The fully qualified class name, e.g. {@code com.example.MyClass$MySubClass}
   * @param args Arguments to pass to the main method
   * @return Exit status code
   */
  public static int runJava(String mainClass, String... args) {
    return runJava(new String[0], mainClass, args);
  }

  /**
   * Runs a Java class
   *
   * @param additionalClassPaths Additional class paths to pass to the compiler
   * @param mainClass The fully qualified class name, e.g. {@code com.example.MyClass$MySubClass}
   * @param args Arguments to pass to the main method
   * @return Exit status code
   */
  public static int runJava(String[] additionalClassPaths, String mainClass, String... args) {
    String pathSeparator = System.getProperty("path.separator");

    String classPaths =
        String.join(
            pathSeparator,
            Stream.concat(Stream.of(javaClassPath), Arrays.stream(additionalClassPaths))
                .toArray(String[]::new));

    Stream<String> commandLineStream =
        Stream.concat(
            Stream.of(javaBin.toString(), "-cp", classPaths, mainClass), Arrays.stream(args));

    return command(commandLineStream.toArray(String[]::new));
  }

  /**
   * Compiles java sources
   *
   * @return {@code true} if all the sources compiled successfully
   */
  public static boolean compileJava(String... sourcePaths) {
    return compileJava(new String[0], buildClassPath, sourcePaths);
  }

  /**
   * Compiles java sources
   *
   * @return {@code true} if all the sources compiled successfully
   */
  public static boolean compileJava(String[] additionalClassPaths, String... sourcePaths) {
    return compileJava(additionalClassPaths, buildClassPath, sourcePaths);
  }

  /**
   * Compiles java sources
   *
   * @return {@code true} if all the sources compiled successfully
   */
  public static boolean compileJava(String classOutputPath, String... sourcePaths) {
    return compileJava(new String[0], classOutputPath, sourcePaths);
  }

  /**
   * Compiles java sources
   *
   * @return {@code true} if all the sources compiled successfully
   */
  public static boolean compileJava(
      String[] additionalClassPaths, String classOutputPath, String... sourcePaths) {
    try (StandardJavaFileManager fileManager =
        compiler.getStandardFileManager(null, null, null); ) {

      Iterable<? extends JavaFileObject> compilationUnits =
          fileManager.getJavaFileObjects(sourcePaths);

      List<String> compilerOptions = new ArrayList<>();
      compilerOptions.add("-d");
      compilerOptions.add(classOutputPath);

      if (additionalClassPaths.length > 0) {
        compilerOptions.add("-cp");
        compilerOptions.add(String.join(File.pathSeparator, additionalClassPaths));
      }

      return compiler
          .getTask(null, fileManager, null, compilerOptions, null, compilationUnits)
          .call();

    } catch (Exception ex) {
      logger.log(Level.SEVERE, "Exception occurred while attempting to compile:", ex);
      return false;
    }
  }

  /**
   * Runs a shell command
   *
   * @return Exit status code
   */
  public static int command(String... command) {
    try {
      return commandThrows(command);
    } catch (IOException ex) {
      logger.log(Level.SEVERE, "Error running command:", ex);
      return 0xbad_cafe;
    }
  }

  /**
   * Runs a shell command. May throw an {@code IOException}
   *
   * @return Exit status code
   * @throws Exception
   */
  public static int commandThrows(String... command) throws IOException {
    ProcessBuilder pb =
        new ProcessBuilder(command)
            .redirectOutput(Redirect.INHERIT)
            .redirectError(Redirect.INHERIT);
    Process process;
    try {
      process = pb.start();
      process.waitFor();
      return process.exitValue();
    } catch (InterruptedException ex) {
      logger.log(Level.SEVERE, "Command Interrupted");
      logger.log(Level.FINEST, "", ex.toString());
      return 0xbad_cafe;
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
            .getPathMatcher(String.join("", "glob:", cwd.toString(), File.separator, globPattern));
    try (@SuppressWarnings("unused")
        Stream<Path> paths =
            Files.find(
                cwd, Integer.MAX_VALUE, (path, basicFileAttributes) -> pathMatcher.matches(path))) {
      return paths.map(Path::toString).toArray(String[]::new);
    } catch (IOException e) {
      return new String[0];
    }
  }

  /** Checks if the {@code targetPath} needs to be rebuilt from {@code sourcePaths} */
  public static boolean needsRebuild(String targetPath, String... sourcePaths) {
    long targetLastModified = new File(targetPath).lastModified();
    return targetLastModified == 0
        || Arrays.stream(sourcePaths)
            .anyMatch(sourcePath -> new File(sourcePath).lastModified() > targetLastModified);
  }

  /** Checks if the {@code targetPath} needs to be rebuilt from {@code sourcePaths} */
  public static boolean needsRebuild(Path targetPath, Path... sourcePaths) {
    long targetLastModified = targetPath.toFile().lastModified();
    return targetLastModified == 0
        || Arrays.stream(sourcePaths)
            .anyMatch(sourcePath -> sourcePath.toFile().lastModified() > targetLastModified);
  }

  /**
   * Checks if the class {@code className} needs to be rebuilt from {@code sourcePaths}
   *
   * @param className The fully qualified class name
   */
  public static boolean classNeedsRebuild(String className, String... sourcePaths) {
    return classNeedsRebuild(buildClassPath, className, sourcePaths);
  }

  /**
   * Checks if the class {@code className} needs to be rebuilt from {@code sourcePaths}
   *
   * @param classPath Path to look for the class in
   * @param className The fully qualified class name
   */
  public static boolean classNeedsRebuild(
      String classPath, String className, String... sourcePaths) {
    String targetClass =
        String.join(
            "", classPath, File.separator, className.replaceAll("\\.", File.separator), ".class");
    return needsRebuild(targetClass, sourcePaths);
  }

  /** Indents lines in {@code str} with {@code n} spaces */
  public static String indent(String str, int n) {
    if (str.isEmpty()) {
      return "";
    } else {
      StringBuilder sb = new StringBuilder(n);
      for (int i = 0; i < n; i++) {
        sb.append(" ");
      }
      String spaces = sb.toString();
      return Arrays.stream(str.split("\n"))
          .map(line -> spaces + line)
          .collect(Collectors.joining("\n", "", "\n"));
    }
  }

  /** Transfers bytes from IO streams {@code in} to {@code out} */
  public static long transferTo(InputStream in, OutputStream out) throws IOException {
    Objects.requireNonNull(out, "out");
    long transferred = 0L;
    byte[] buffer = new byte[16384];

    int read;
    while ((read = in.read(buffer, 0, 16384)) >= 0) {
      out.write(buffer, 0, read);
      if (transferred < Long.MAX_VALUE) {
        try {
          transferred = Math.addExact(transferred, (long) read);
        } catch (ArithmeticException ex) {
          transferred = Long.MAX_VALUE;
        }
      }
    }

    return transferred;
  }

  /** Downloads file from {@code url} to {@code destination} */
  public static boolean downloadArtefact(String url, Path destination) {
    try {
      InputStream urlStream = URI.create(url).toURL().openStream();

      Files.createDirectories(destination.getParent());
      transferTo(urlStream, new FileOutputStream(destination.toFile()));

      return true;
    } catch (IOException ex) {
      logger.log(Level.SEVERE, "Failed to download artefact", ex);
      return false;
    }
  }

  /** Downloads file from {@code url} to {@code destination} */
  public static boolean downloadArtefact(URL url, Path destination) {
    try {
      InputStream urlStream = url.openStream();

      Files.createDirectories(destination.getParent());
      transferTo(urlStream, new FileOutputStream(destination.toFile()));

      return true;
    } catch (IOException ex) {
      logger.log(Level.SEVERE, "Failed to download artefact", ex);
      return false;
    }
  }

  /** Describes a Java dependency */
  public static record Dependency(String group, String name, String version) {}

  /** URL template for maven central dependencies */
  public static String mavenCentral = "https://repo1.maven.org/maven2/%1$s/%2$s/%3$s/%2$s-%3$s.jar";

  /**
   * Downloads {@code dependencies} from {@code repository}
   *
   * @param repository URL template for a repository, template arguments are provided:
   *     <ul>
   *       <li>{@code %1$s}: group id, delimeted with slashes
   *       <li>{@code %2$s}: artefact name
   *       <li>{@code %3$s}: artefact version
   *     </ul>
   *     See {@link NoBuild#mavenCentral}
   * @param destination Path to download artefacts to
   * @param dependencies Dependencies to download
   * @return {@code true} if all dependencies were downloaded successfully
   */
  public static boolean downloadDependencies(
      String repository, Path destination, Dependency... dependencies) {
    boolean success = true;
    for (Dependency dependency : dependencies) {
      Path jarPath = destination.resolve(dependency.name() + ".jar");
      String artefact =
          "%s:%s-%s".formatted(dependency.group(), dependency.name(), dependency.version());
      String url =
          repository.formatted(
              String.join("/", dependency.group().split("\\.")),
              dependency.name(),
              dependency.version());
      if (!needsRebuild(jarPath)) continue;
      logger.info("Downloading %s ...".formatted(artefact));
      if (!downloadArtefact(url, jarPath)) {
        logger.severe("Could not download " + artefact);
        success = false;
      }
    }
    return success;
  }
}
