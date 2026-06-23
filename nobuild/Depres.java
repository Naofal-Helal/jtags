/*
 * Depres - simple Java dependency resolver
 *
 * @version 0.1
 *
 * MIT License
 *
 * Copyright (c) 2026 Naofal Helal
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

import static nobuild.Depres.Version.Caret;
import static nobuild.Depres.Version.Exact;

import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.*;
import javax.xml.xpath.*;
import org.w3c.dom.*;
import org.w3c.dom.Document;

public class Depres {
  public static Logger logger = Logger.getLogger("depres");
  public static Handler loggingHandler = new DepresLogHandler();

  static {
    logger.setUseParentHandlers(false);
    logger.addHandler(loggingHandler);
    logger.setLevel(Level.INFO);
    String level;
    if ((level = System.getProperty("depres.level")) != null) {
      logger.setLevel(Level.parse(level));
    }
  }

  static final String DEPRES_CACHE = "DEPRES_HOME";
  static final Path cachePath =
      Optional.ofNullable(System.getenv(DEPRES_CACHE))
          .map(Path::of)
          .orElse(
              switch (System.getProperty("os.name")) {
                case "Linux" -> Path.of(System.getProperty("user.home"), ".cache", "depres");
                case String os -> throw new UnsupportedOperationException("Unsupported OS: " + os);
              });
  static final Path jarsPath = cachePath.resolve("jars");
  static final Path srcsPath = cachePath.resolve("src-jars");
  static final Path docsPath = cachePath.resolve("doc-jars");

  static final Pattern exact = Pattern.compile("([\\w.-]+):([\\w.-]+):(.+?)(?:\\[(\\w+)])?");
  static final Pattern caret = Pattern.compile("([\\w.-]+):([\\w.-]+)\\^(.+?)(?:\\[(\\w+)])?");
  static final Pattern semver = Pattern.compile("([0-9]+)\\.([0-9]+)\\.([0-9]+)");

  public static void main(String[] args) {
    try {
      doRun(args);
    } catch (AbortException ex) {
      System.exit(ex.code);
    }
  }

  public static boolean run(String... args) {
    try {
      doRun(args);
      return true;
    } catch (AbortException ex) {
      if (ex.code == 0) return true;
      else return false;
    }
  }

  static String[] downloadForPlatforms = {
    switch (System.getProperty("os.name")) {
      case String os when os.startsWith("Linux") -> "linux";
      case String os when os.startsWith("Windows") -> "win";
      case String os when os.startsWith("Mac") -> "mac";
      case String os -> throw new UnsupportedOperationException("Unknown os: " + os);
    }
  };

  public static void doRun(String[] args) {
    final Instant startTimestamp = Instant.now();
    ArrayDeque<String> arguments = new ArrayDeque<>(Arrays.asList(args));
    final List<Dependency> specifiedDependencies = new ArrayList<>();
    final List<Dependency> lockedDependencies = new ArrayList<>();
    List<Dependency> dependencies = specifiedDependencies;
    Optional<Path> lockfile = Optional.empty();
    int lockHash = 0;
    Optional<Path> outputDir = Optional.empty();
    Optional<Path> outputSrcsDir = Optional.empty();
    boolean downloadSources = false;
    String currentScope = "implementation";

    if (arguments.isEmpty()) {
      printUsage();
      abort(1);
    }

    while (!arguments.isEmpty()) {
      final String argument = arguments.pop();
      switch (argument) {
        case String _
        when argument.startsWith("@"):
          String filename = argument.substring(1);
          try {
            Files.readAllLines(Path.of(filename)).stream()
                .filter(it -> !it.trim().startsWith("#") && !it.isBlank())
                .flatMap(it -> Arrays.stream(it.split("#")[0].trim().split("\\s+")))
                .toList()
                .reversed()
                .forEach(arguments::addFirst);
          } catch (Exception ex) {
            logger.log(Level.SEVERE, "Could not read file " + filename + "\n", ex);
            abort(1);
          }
          break;

        case "-l", "-lock":
          lockfile =
              switch (arguments.poll()) {
                case null -> {
                  logger.severe("Expected argument after " + argument);
                  abort(1);
                  yield null;
                }
                case String path -> Optional.of(Path.of(path));
              };

          if (Files.exists(lockfile.get())) {
            arguments.addFirst("-scope-%s:".formatted(currentScope));
            arguments.addFirst("@" + lockfile.get().toString());
          }
          break;

        case "-o", "-output":
          outputDir =
              switch (arguments.poll()) {
                case null -> {
                  logger.severe("Expected argument after " + argument);
                  abort(1);
                  yield null;
                }
                case String path -> Optional.of(Path.of(path));
              };
          break;

        case "-output-srcs":
          outputSrcsDir =
              switch (arguments.poll()) {
                case null -> {
                  logger.severe("Expected argument after " + argument);
                  abort(1);
                  yield null;
                }
                case String path -> Optional.of(Path.of(path));
              };
          break;

        case "-sources":
          downloadSources = true;
          break;

        case "-platforms":
          downloadForPlatforms =
              switch (arguments.poll()) {
                case null -> {
                  logger.severe("Expected argument after " + argument);
                  abort(1);
                  yield null;
                }
                case "all" -> new String[] {"linux", "mac", "win"};
                case String oses -> oses.split(",");
              };
          break;

        case String _
        when argument.startsWith("-scope-"):
          currentScope = argument.substring("-scope-".length(), argument.length() - ":".length());
          break;

        case "--locked":
          dependencies = lockedDependencies;
          try {
            lockHash =
                switch (arguments.poll()) {
                  case null -> throw new IllegalArgumentException();
                  case String hashCode -> Integer.parseUnsignedInt(hashCode, 16);
                };
          } catch (Exception _) {
            logger.severe("Malformed lock file");
            abort(1);
          }
          break;

        case "--unlocked":
          dependencies = specifiedDependencies;
          break;

        default:
          // Read dependency specifiers
          Matcher matcher;
          if ((matcher = exact.matcher(argument)).matches()) {
            String groupId = matcher.group(1);
            String name = matcher.group(2);
            String version = matcher.group(3);
            Optional<String> classifier = Optional.ofNullable(matcher.group(4));
            dependencies.add(
                new Dependency(
                    groupId, name, new Version.Exact(version), classifier, currentScope));

          } else if ((matcher = caret.matcher(argument)).matches()) {
            String groupId = matcher.group(1);
            String name = matcher.group(2);
            String version = matcher.group(3);
            Optional<String> classifier = Optional.ofNullable(matcher.group(4));
            if ((matcher = semver.matcher(version)).matches() == false) {
              logger.severe(
                  "Caret can only be used with semver versions in the form MAJOR.MINOR.PATCH: "
                      + argument);
              abort(1);
            }
            int major = Integer.parseInt(matcher.group(1));
            dependencies.add(
                new Dependency(groupId, name, new Version.Caret(major), classifier, currentScope));

          } else {
            logger.severe("Invalid dependency specifier: " + argument);
            abort(1);
          }
      }
    }

    logger.config("Specified dependencies:");
    for (var dep : specifiedDependencies) {
      logger.config("[%s] %s".formatted(dep.scope(), dep.toString()));
    }

    if (outputDir.isEmpty()) {
      logger.warning("No output directory specified; only caching dependencies");
    }

    if (lockfile.isPresent()) {
      int specifiedHash = specifiedDependencies.hashCode();
      if (lockHash != specifiedHash) {
        dependencies = resolveDependencies(specifiedDependencies);
        writeDependencyLock(lockfile.get(), dependencies, specifiedHash);
      } else {
        dependencies = lockedDependencies;
      }
    } else {
      dependencies = resolveDependencies(specifiedDependencies);
    }

    logger.finest("Downloading dependencies: " + dependencies);

    downloadArtefacts(mavenCentral + artefactURLTemplate + jarURLTemplate, jarsPath, dependencies);

    if (downloadSources) {
      downloadArtefacts(
          mavenCentral + artefactURLTemplate + srcsURLTemplate, srcsPath, dependencies);
    }

    if (outputDir.isPresent()) {
      linkDependencies(dependencies, outputDir.get());
    }

    if (outputSrcsDir.isPresent()) {
      linkSources(dependencies, outputSrcsDir.get());
    }

    logger.info(
        "Depres done in " + Duration.between(startTimestamp, Instant.now()).toMillis() + "ms");
  }

  static DocumentBuilder documentBuilder;
  static XPath xpath = XPathFactory.newInstance().newXPath();

  static {
    try {
      documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
    } catch (Exception ex) {
      logger.severe(ex.toString());
      abort(1);
    }
  }

  static List<Dependency> resolveDependencies(List<Dependency> dependencies) {
    final Map<String, Dependency> resolved = new HashMap<>();

    for (var dep : dependencies) {
      resolveDependency(resolved, dep);
    }

    return resolved.values().stream().toList();
  }

  // https://www.rgagnon.com/javadetails/java-0059.html
  static boolean remoteResourceExists(URL url) {
    try {
      HttpURLConnection.setFollowRedirects(false);
      HttpURLConnection con = (HttpURLConnection) url.openConnection();
      con.setRequestMethod("HEAD");
      return (con.getResponseCode() == HttpURLConnection.HTTP_OK);
    } catch (Exception ex) {
      ex.printStackTrace();
      return false;
    }
  }

  static void resolveDependency(final Map<String, Dependency> resolved, Dependency dep) {
    if (resolved.containsKey(dep.id())) {
      // TODO: ensure compatible version
      return;
    }
    logger.fine("Resolving " + dep.toString());
    final String artefactURL =
        mavenCentral
            + artefactURLTemplate.formatted(String.join("/", dep.group().split("\\.")), dep.name());
    try (InputStream in =
        URI.create(artefactURL + metadataURLTemplate).toURL().openConnection().getInputStream(); ) {
      Document metadata = documentBuilder.parse(in);
      NodeList versionNodes = metadata.getElementsByTagName("version");
      List<String> versions = new ArrayList<>();
      for (int i = 0; i < versionNodes.getLength(); ++i) {
        versions.add(versionNodes.item(i).getTextContent());
      }
      // logger.finest(() -> "Available versions: " + versions);
      Exact resolvedVersion =
          switch (dep.version) {
            case Exact(var version) -> {
              if (versions.contains(version)) {
                yield new Exact(version);
              } else {
                logger.severe("Requested version is not available for dependency " + dep);
                abort(1);
                yield null;
              }
            }

            case Caret(int major) -> {
              try {
                String version =
                    versions.stream()
                        .filter(
                            it ->
                                it.matches("^" + String.valueOf(major) + "\\.([0-9]+)\\.([0-9]+)"))
                        .toList()
                        .getLast();
                yield new Exact(version);
              } catch (NoSuchElementException _) {
                logger.severe("Requested version is not available for dependency " + dep);
                abort(1);
                yield null;
              }
            }
          };

      logger.finer("Resolved " + dep + " => " + resolvedVersion);

      URL linuxJarURL = null;
      try {
        linuxJarURL =
            URI.create(
                    artefactURL
                        + jarURLTemplate.formatted(
                            null, dep.name(), resolvedVersion.version(), "-linux"))
                .toURL();
      } catch (Exception ex) {
        logger.severe(ex.toString());
        abort(1);
      }

      boolean isPlatformSpecific = remoteResourceExists(linuxJarURL);
      logger.info(
          "%s is platform specefic = %s"
              .formatted(dep.toString(), String.valueOf(isPlatformSpecific)));

      resolved.put(
          dep.id(),
          new Dependency(
              dep.group(),
              dep.name(),
              resolvedVersion,
              isPlatformSpecific ? dep.classifier() : Optional.empty(),
              dep.scope()));

      try (InputStream pomIn =
          URI.create(
                  artefactURL
                      + pomURLTemplate.formatted(null, dep.name(), resolvedVersion.version()))
              .toURL()
              .openConnection()
              .getInputStream(); ) {

        Document pom = documentBuilder.parse(pomIn);
        NodeList subDependencies =
            (NodeList)
                xpath.evaluate(
                    "/project/dependencies/dependency[not(scope) or scope='compile']",
                    pom,
                    XPathConstants.NODESET);

        for (int i = 0; i < subDependencies.getLength(); ++i) {
          Node subDep = subDependencies.item(i);

          if ("true".equals(xpath.evaluate("optional", subDep))) continue;

          String group = xpath.evaluate("groupId", subDep);
          String name = xpath.evaluate("artifactId", subDep);
          String version = xpath.evaluate("version", subDep);
          resolveDependency(
              resolved,
              new Dependency(
                  group,
                  name,
                  switch (dep.version) {
                    case Exact _ -> new Exact(version);
                    case Caret _ -> {
                      Matcher matcher;
                      if ((matcher = semver.matcher(version)).matches() == false) {
                        yield new Exact(version);
                      } else {
                        int major = Integer.parseInt(matcher.group(1));
                        yield new Caret(major);
                      }
                    }
                  },
                  dep.classifier(),
                  dep.scope()));
        }

      } catch (Exception ex) {
        ex.printStackTrace();
        abort(1);
      }

    } catch (Exception ex) {
      ex.printStackTrace();
      abort(1);
    }
  }

  static void writeDependencyLock(Path lockfile, List<Dependency> dependencies, int hashCode) {
    dependencies = new ArrayList<>(dependencies);
    dependencies.sort(
        Comparator.<Dependency, String>comparing(it -> it.scope())
            .thenComparing(Comparator.naturalOrder()));
    String currentScope = null;
    try (Writer out = new FileWriter(lockfile.toFile())) {
      out.write("# Depres dependency lock file\n--locked ");
      out.write(Integer.toUnsignedString(hashCode, 16));
      out.write('\n');
      for (var dep : dependencies) {
        if (!dep.scope().equals(currentScope)) {
          currentScope = dep.scope();
          out.write("-scope-");
          out.write(currentScope);
          out.write(":\n");
        }
        out.write("  ");
        out.write(dep.toString());
        out.write('\n');
      }
      out.write("--unlocked\n");
    } catch (Exception ex) {
      logger.severe(ex.toString());
      abort(1);
    }
  }

  public sealed interface Version {
    public record Exact(String version) implements Version {
      public String toString() {
        return ":" + version;
      }
    }

    public record Caret(int majorVersion) implements Version {
      public String toString() {
        return "^" + majorVersion + ".0.0";
      }
    }
  }

  /** Describes a Java dependency */
  public static record Dependency(
      String group, String name, Version version, Optional<String> classifier, String scope)
      implements Comparable<Dependency> {
    public Dependency(String group, String name, Version version) {
      this(group, name, version, Optional.empty(), "implementation");
    }

    public String id() {
      return group + ":" + name;
    }

    public String toString() {
      return group + ":" + name + version + classifier.map(it -> "[%s]".formatted(it)).orElse("");
    }

    public int compareTo(Dependency other) {
      return Comparator.comparing(Dependency::group)
          .thenComparing(Dependency::name)
          .compare(this, other);
    }
  }

  public static String mavenCentral = "https://repo1.maven.org/maven2/";
  public static String artefactURLTemplate = "%1$s/%2$s/";
  public static String metadataURLTemplate = "maven-metadata.xml";
  public static String jarURLTemplate = "%3$s/%2$s-%3$s%4$s.jar";
  public static String srcsURLTemplate = "%3$s/%2$s-%3$s-sources.jar";
  public static String pomURLTemplate = "%3$s/%2$s-%3$s.pom";

  static String[] getClassifiers(Dependency dependency) {
    return dependency
        .classifier()
        .map(
            it ->
                switch (it) {
                  case "platform" -> downloadForPlatforms;
                  case String cls -> new String[] {cls};
                })
        .orElse(new String[] {""});
  }

  static void downloadArtefacts(
      String urlTemplate, Path destination, List<Dependency> dependencies) {
    destination.toFile().mkdirs();
    for (Dependency dependency : dependencies) {
      if (!(dependency.version() instanceof Exact(String exactVersion))) {
        throw new AssertionError("Attempted to download dependency with unexact version");
      }
      for (String classifier : getClassifiers(dependency)) {
        String url =
            urlTemplate.formatted(
                String.join("/", dependency.group().split("\\.")),
                dependency.name(),
                exactVersion,
                classifier.isEmpty() ? "" : ("-" + classifier));
        String[] segments = url.split("/");
        String filename = segments[segments.length - 1];
        Path artefactPath = destination.resolve(filename);
        if (Files.exists(artefactPath)) continue;
        logger.info("Downloading %s ...".formatted(filename));
        if (!downloadArtefact(url, artefactPath)) {
          logger.severe("Could not download " + url);
          abort(1);
        }
      }
    }
  }

  /** Downloads file from {@code url} to {@code destination} */
  static boolean downloadArtefact(String url, Path destination) {
    try (InputStream urlStream = URI.create(url).toURL().openStream(); ) {
      urlStream.transferTo(new FileOutputStream(destination.toFile()));
      return true;
    } catch (IOException ex) {
      logger.log(Level.SEVERE, "Failed to download artefact", ex);
      return false;
    }
  }

  static void linkDependencies(List<Dependency> dependencies, Path destination) {
    try {
      Files.createDirectories(destination);
      for (Dependency dep : dependencies) {
        if (!(dep.version() instanceof Exact(String exactVersion))) {
          throw new AssertionError("Attempted to download dependency with unexact version");
        }
        for (String classifier : getClassifiers(dep)) {
          String jarName =
              dep.name()
                  + "-"
                  + exactVersion
                  + (classifier.isEmpty() ? "" : ("-" + classifier))
                  + ".jar";
          Path link =
              switch (dep.scope()) {
                case "implementation" -> destination.resolve(jarName);
                case String scope -> {
                  Files.createDirectories(destination.resolve(scope));
                  yield destination.resolve(scope, jarName);
                }
              };
          Path target = jarsPath.resolve(jarName);
          if (Files.exists(link) && Files.readSymbolicLink(link).equals(target)) continue;
          Files.createSymbolicLink(link, target);
        }
      }
    } catch (IOException ex) {
      logger.severe(ex.toString());
    }
  }

  static void linkSources(List<Dependency> dependencies, Path destination) {
    try {
      Files.createDirectories(destination);
      for (Dependency dep : dependencies) {
        if (!(dep.version() instanceof Exact(String exactVersion))) {
          throw new AssertionError("Attempted to download dependency with unexact version");
        }
        String jarName = dep.name() + "-" + exactVersion + "-sources.jar";
        Path link = destination.resolve(jarName);
        Path target = srcsPath.resolve(jarName);
        if (Files.exists(link) && Files.readSymbolicLink(link).equals(target)) continue;
        Files.createSymbolicLink(link, target);
      }
    } catch (IOException ex) {
      logger.severe(ex.toString());
    }
  }

  public static class DepresLogHandler extends Handler {

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
        if (System.console().isTerminal()) {
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

  static void printUsage() {
    System.err.println(
        """
Usage: depres <argument...>
Arguments:
  <dependency>        Dependency in the form <group>:<name><operator><version>
                      where <operator> is ":" to match exactly, or "^" to match
                      the major version. Optionally, a classifier may be
                      specified in square brackets "[...]" after the <version>.
                      The special classifier "[platform]" is replaced with the
                      value of -platforms.
  @<argument-file>    Read arguments from <argument-file>
  -l, -lock <file>    Output resolved dependencies to <file>
  -o, -output <dir>   Place JARs in <dir>
  -scope-<name>:      Group subsequent dependencies in the scope <name>.
                      Default: -scope-implementation:
  -platforms <names>  Download platform specific jars for comma separated
                      <names>. Use "all" for "linux,mac,win". Defaults to only
                      the current platform.
  -output-srcs <dir>  Place source JARs in <dir>
  -sources            Also download sources
""");
  }

  static class AbortException extends RuntimeException {
    @java.io.Serial static final long serialVersionUID = 0xcafe08a85bd7160eL;

    final int code;

    public AbortException(int code) {
      this.code = code;
    }
  }

  static void abort(int code) {
    throw new AbortException(code);
  }
}
