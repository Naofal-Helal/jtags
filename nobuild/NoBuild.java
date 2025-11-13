/* NoBuild system
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
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/// # NoBuild system
///
/// ## Quick Guide
///
/// A simple build script:
///
/// ```java
/// // Build.java
/// import static nobuild.Nobuild.*;
///
/// public class Build {
///   public static void main(String[] args) {
///     rebuildSelf(Build.class, args);
///     compileJava("Hello.java");
///     runJava("Hello");
///   }
/// }
/// ```
///
/// Which can be run as follows:
///
/// ```console
/// $ javac -d build/ Build.java
/// $ java -cp build/ Build
/// ```
///
/// or:
///
/// ```console
/// $ java -cp build/ Build.java
/// ```
///
/// Note that by using [rebuildSelf][#rebuildSelf], the build script `Build` will automatically
/// rebuild itself if `Build.java` is modified.
///
/// @author Naofal Helal
/// @version 0.2
/// @since 23
////
public class NoBuild {
  public static Logger logger = Logger.getLogger("logger");
  public static Handler loggingHandler = new NoBuildLogHandler();
  public static JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

  public static Path javaHome = Path.of(System.getProperty("java.home"));
  public static String javaBin = javaHome.resolve("bin", "java").toString();
  public static String javacBin = javaHome.resolve("bin", "javac").toString();

  /// The class path used by the java executable.
  public static String javaClassPath = System.getProperty("java.class.path");

  /// Path to the build script class file, or `null` if [rebuildSelf][#rebuildSelf] hasn't been
  /// called.
  ///
  /// Will be used as the default output directory for compilation.
  public static String buildClassPath = null;

  /// Returns the class path of the build script.
  public static String defaultBuildClassPath(Class<?> buildClass) {
    URL buildClassUrl = buildClass.getResource(buildClass.getSimpleName() + ".class");
    return Optional.ofNullable(Path.of(buildClassUrl.getPath()).getParent())
        .orElseGet(
            () -> {
              Path path = Path.of(".", "build");
              boolean isPathInClasspath =
                  Arrays.stream(javaClassPath.split(File.pathSeparator))
                      .anyMatch(
                          it -> {
                            try {
                              return Files.isSameFile(Path.of(it), path);
                            } catch (IOException e) {
                              return false;
                            }
                          });
              if (!isPathInClasspath) {
                logger.warning(
                    "Using %s as default classpath. Make sure to include with -cp %1$s"
                        .formatted(path));
              }
              return path;
            })
        .toString();
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

    public static Map<Level, Integer> colors =
        new HashMap<Level, Integer>() {
          {
            put(Level.FINEST, 5);
            put(Level.FINE, 4);
            put(Level.FINER, 6);
            put(Level.CONFIG, 4);
            put(Level.INFO, 2);
            put(Level.WARNING, 3);
            put(Level.SEVERE, 1);
          }
        };

    @Override
    public void publish(LogRecord record) {
      if (record.getMessage() != null && !record.getMessage().isEmpty()) {
        if (Optional.ofNullable(System.console()).map(it -> it.isTerminal()).orElse(false)) {
          int color = colors.get(record.getLevel());
          System.err.printf(
              "\u001b[38;5;%dm[%s]\u001b[0m %s%n",
              color, record.getLevel().getName(), record.getMessage());
        } else {
          System.err.printf("[%s] %s%n", record.getLevel().getName(), record.getMessage());
        }
      }
      if (record.getThrown() != null) {
        System.err.println(record.getThrown().toString().indent(4));
      }
    }
  }

  /// Rebuilds the build script if necessary.
  ///
  /// @param buildClass The main class of the build script
  /// @param args arguments from the main method
  public static void rebuildSelf(Class<?> buildClass, String[] args) {
    rebuildSelf(buildClass, args, new String[0]);
  }

  /// Rebuilds the build script if necessary.
  ///
  /// @param buildClass The main class of the build script
  /// @param args Arguments from the main method
  /// @param additionalSources Additional sources to watch and compile
  public static void rebuildSelf(Class<?> buildClass, String[] args, String... additionalSources) {
    String buildSource = buildClass.getName().replaceAll("\\.", File.separator) + ".java";
    buildClassPath = defaultBuildClassPath(buildClass);

    String[] sourcePaths =
        Stream.concat(Stream.of(buildSource), Arrays.stream(additionalSources))
            .toArray(String[]::new);

    if (!classNeedsRebuild(buildClass.getName(), sourcePaths)) return;

    logger.info("Recompiling %s...".formatted(buildSource));

    if (!compileJava(buildClassPath, sourcePaths)) {
      logger.severe("Compilation failed");
      System.exit(1);
    }

    int status = runJava(buildClass.getName(), args);
    System.exit(status);
  }

  /// Runs a Java class.
  ///
  /// @param mainClass The fully qualified class name, e.g. `com.example.MyClass$MySubClass`
  /// @return Exit status code
  public static int runJava(String mainClass) {
    return runJava(mainClass, new String[0]);
  }

  /// Runs a Java class.
  ///
  /// @param mainClass The fully qualified class name, e.g. `com.example.MyClass$MySubClass`
  /// @param args Arguments to pass to the main method
  /// @return Exit status code
  public static int runJava(String mainClass, String... args) {
    return runJava(new String[0], mainClass, args);
  }

  /// Runs a Java class.
  ///
  /// @param additionalClassPaths Additional class paths to pass to the compiler
  /// @param mainClass The fully qualified class name, e.g. `com.example.MyClass$MySubClass`
  /// @param args Arguments to pass to the main method
  /// @return Exit status code
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

  /// Runs a Java class.
  ///
  /// @param additionalClassPaths Additional class paths to pass to the compiler
  /// @param javaArguments Arguments to pass to the java binary
  /// @param mainClass The fully qualified class name, e.g. `com.example.MyClass$MySubClass`
  /// @param args Arguments to pass to the main method
  /// @return Exit status code
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

  /// Compiles java sources.
  ///
  /// @return `true` if all the sources compiled successfully
  public static boolean compileJava(String... sourcePaths) {
    return compileJava(new String[0], buildClassPath, sourcePaths);
  }

  /// Compiles java sources.
  ///
  /// @return `true` if all the sources compiled successfully
  public static boolean compileJava(String[] additionalClassPaths, String... sourcePaths) {
    return compileJava(additionalClassPaths, buildClassPath, sourcePaths);
  }

  /// Compiles java sources.
  ///
  /// @return `true` if all the sources compiled successfully
  public static boolean compileJava(String classOutputPath, String... sourcePaths) {
    return compileJava(new String[0], classOutputPath, sourcePaths);
  }

  /// Compiles java sources.
  ///
  /// @return `true` if all the sources compiled successfully
  public static boolean compileJava(
      String[] additionalClassPaths, String classOutputPath, String... sourcePaths) {
    try (StandardJavaFileManager fileManager =
        compiler.getStandardFileManager(null, null, null); ) {

      Iterable<? extends JavaFileObject> compilationUnits =
          fileManager.getJavaFileObjects(sourcePaths);

      List<String> compilerOptions = new ArrayList<>();
      compilerOptions.add("-Xlint:all");
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

  /// Runs a shell command.
  ///
  /// @return Exit status code
  public static int command(String... command) {
    try {
      return commandThrows(command);
    } catch (IOException ex) {
      logger.log(Level.SEVERE, "Error running command:", ex);
      return 0xbad_cafe;
    }
  }

  /// Runs a shell command. May throw an `IOException`.
  ///
  /// @return Exit status code
  /// @throws Exception
  public static int commandThrows(String... command) throws IOException {
    ProcessBuilder pb = new ProcessBuilder(command).inheritIO();
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

  /// Returns file paths that match a `globPattern`.
  ///
  /// @see java.nio.file.FileSystem#getPathMatcher(String) getPathMatcher
  public static String[] glob(String globPattern) {
    Path cwd = Path.of(".");
    PathMatcher pathMatcher =
        FileSystems.getDefault()
            .getPathMatcher(String.join("", "glob:", cwd.toString(), File.separator, globPattern));
    try (Stream<Path> paths =
        Files.find(
            cwd, Integer.MAX_VALUE, (path, basicFileAttributes) -> pathMatcher.matches(path))) {
      return paths.map(Path::toString).toArray(String[]::new);
    } catch (IOException e) {
      return new String[0];
    }
  }

  /// Checks if the `targetPath` needs to be rebuilt from `sourcePaths`.
  public static boolean needsRebuild(String targetPath, String... sourcePaths) {
    long targetLastModified = new File(targetPath).lastModified();
    return targetLastModified == 0
        || Arrays.stream(sourcePaths)
            .anyMatch(sourcePath -> new File(sourcePath).lastModified() > targetLastModified);
  }

  /// Checks if the `targetPath` needs to be rebuilt from `sourcePaths`.
  public static boolean needsRebuild(Path targetPath, Path... sourcePaths) {
    long targetLastModified = targetPath.toFile().lastModified();
    return targetLastModified == 0
        || Arrays.stream(sourcePaths)
            .anyMatch(sourcePath -> sourcePath.toFile().lastModified() > targetLastModified);
  }

  /// Checks if the class `className` needs to be rebuilt from `sourcePaths`.
  ///
  /// @param className The fully qualified class name
  public static boolean classNeedsRebuild(String className, String... sourcePaths) {
    return classNeedsRebuild(buildClassPath, className, sourcePaths);
  }

  /// Checks if the class `className` needs to be rebuilt from `sourcePaths`.
  ///
  /// @param classPath Path to look for the class in
  /// @param className The fully qualified class name
  public static boolean classNeedsRebuild(
      String classPath, String className, String... sourcePaths) {
    String targetClass =
        String.join(
            "", classPath, File.separator, className.replaceAll("\\.", File.separator), ".class");
    return needsRebuild(targetClass, sourcePaths);
  }

  /// Downloads file from `url` to `destination`.
  public static boolean downloadArtefact(String url, Path destination) {
    try {
      InputStream urlStream = URI.create(url).toURL().openStream();

      Files.createDirectories(destination.getParent());
      urlStream.transferTo(new FileOutputStream(destination.toFile()));

      return true;
    } catch (IOException ex) {
      logger.log(Level.SEVERE, "Failed to download artefact", ex);
      return false;
    }
  }

  /// Downloads file from `url` to `destination`.
  public static boolean downloadArtefact(URL url, Path destination) {
    try {
      InputStream urlStream = url.openStream();

      Files.createDirectories(destination.getParent());
      urlStream.transferTo(new FileOutputStream(destination.toFile()));

      return true;
    } catch (IOException ex) {
      logger.log(Level.SEVERE, "Failed to download artefact", ex);
      return false;
    }
  }

  /// Describes a Java dependency.
  public static record Dependency(String group, String name, String version) {}

  /// URL template for maven central dependencies.
  public static String mavenCentral = "https://repo1.maven.org/maven2/%1$s/%2$s/%3$s/%2$s-%3$s.jar";

  /// Downloads `dependencies` from `repository`.
  ///
  /// @param repository URL template for a repository, template arguments are provided:
  ///     - `%1$s`: group id, delimeted with slashes
  ///     - `%2$s`: artefact name
  ///     - `%3$s`: artefact version
  ///     See [mavenCentral][NoBuild#mavenCentral]
  /// @param destination Path to download artefacts to
  /// @param dependencies Dependencies to download
  /// @return `true` if all dependencies were downloaded successfully
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
