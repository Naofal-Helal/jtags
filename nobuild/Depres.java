/* DepRes -- Dependency Resolution
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

import java.io.ByteArrayOutputStream;
import java.io.Console;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.io.Writer;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
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

/// DepRes -- Dependency Resolution
///
/// This acts as a command line tool that can be run on it's own, or as a library for your build
/// scripts:
///
/// ```java
/// // Build.java
/// import static nobuild.NoBuild.*;
///
/// import nobuild.Depres;
///
/// public class Build {
///   public static void main(String[] args) {
///     rebuildSelf(Build.class, args);
///     Depres.run();
///   }
/// }
/// ```
///
/// See {@link Depres#printUsage} or run with the `-h` flag to view available options.
///
/// Typical usage is to write your config and dependency specifiers in a file like `dependencies`
/// (the contents are exactly the command arguments!), then run Depres with `@dependencies` or `-d`:
///
/// ```
/// -lock dependencies.lock # comments are supported
/// -output third-party/jars
///
/// org.openjfx:javafx-controls:24[platform]
///
/// -scope-compile-only:
///     de.larsgrefer.sass:sass-embedded-host^4.1.0
/// ```
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

  public enum OS {
    LINUX,
    WINDOWS,
    MACOS,
  }

  static final OS systemOs =
      switch (System.getProperty("os.name")) {
        case String s when s.contains("Linux") -> OS.LINUX;
        case String s when s.contains("Windows") -> OS.WINDOWS;
        case String s when s.contains("Mac") -> OS.MACOS;
        case String s -> {
          logger.severe("Unsupported OS: " + s);
          abort(1);
          yield null;
        }
      };

  static final String DEPRES_USERNAME = "DEPRES_USERNAME";
  static final String DEPRES_PASSWORD = "DEPRES_USERNAME";

  static {
    String username = System.getenv(DEPRES_USERNAME);
    if (username != null && !username.isBlank()) {
      logger.config("Setting authentication for Maven repository");
      Authenticator.setDefault(
          new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
              return new PasswordAuthentication(
                  username, System.getenv(DEPRES_PASSWORD).toCharArray());
            }
          });
    }
  }

  public interface ArtefactCache {
    Path getPath(String group, String name, String version, String filename);
  }

  public record MavenLocal(Path mavenHome) implements ArtefactCache {
    public static MavenLocal getInstance() {
      Path path =
          Optional.ofNullable(System.getenv("MAVEN_HOME"))
              .map(Path::of)
              .orElse(Path.of(System.getProperty("user.home"), ".m2"));
      try {
        if (!Files.exists(path)) Files.createDirectories(path);
      } catch (Exception ex) {
        logger.log(Level.SEVERE, "Could not create artefact cache directory", ex);
      }
      return new MavenLocal(path);
    }

    @Override
    public Path getPath(String group, String name, String version, String filename) {
      return mavenHome.resolve("repository", group.split("\\.")).resolve(name, version, filename);
    }
  }

  static final ArtefactCache artefactCache = MavenLocal.getInstance();

  static final Pattern unversioned = Pattern.compile("([\\w.-]+):([\\w.-]+)(?:\\[(\\w+)])?");
  static final Pattern exact = Pattern.compile("([\\w.-]+):([\\w.-]+):(.+?)(?:\\[(\\w+)])?");
  static final Pattern caret = Pattern.compile("([\\w.-]+):([\\w.-]+)\\^(.+?)(?:\\[(\\w+)])?");
  static final Pattern semver = Pattern.compile("([0-9]+)\\.([0-9]+)\\.([0-9]+)");

  public static void main(String[] args) {
    try {
      doRun(args);
    } catch (AbortException ex) {
      System.exit(ex.code);
    } catch (Exception ex) {
      logger.log(Level.SEVERE, "Unexpected error", ex);
      System.exit(1);
    }
  }

  /// Runs the Depres tool with `args`.
  ///
  /// @see Depres#printUsage for available options
  /// @return `true` if dependencies were resolved successfully
  public static boolean run(String... args) {
    try {
      doRun(args);
      return true;
    } catch (AbortException ex) {
      if (ex.code == 0) return true;
      else return false;
    } catch (Exception ex) {
      logger.log(Level.SEVERE, "Unexpected error", ex);
      return false;
    }
  }

  /// Runs the Depres tool with the default options: @dependencies
  ///
  /// @return `true` if dependencies were resolved successfully
  public static boolean run() {
    return run("-d");
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
    String lockHash = null;
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

        case "-d":
          arguments.addFirst("@dependencies");
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

        // TODO: provide a mechanism to keep artefacts in cache and
        // output the classpath list instead
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
          lockHash = arguments.poll();
          if (lockHash == null) {
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
            String classifier = matcher.group(4);
            dependencies.add(
                new Dependency(
                    groupId, name, new Version.Exact(version), classifier, currentScope));
          } else if ((matcher = unversioned.matcher(argument)).matches()) {
            String groupId = matcher.group(1);
            String name = matcher.group(2);
            String classifier = matcher.group(3);
            dependencies.add(
                new Dependency(groupId, name, new Version.Exact(null), classifier, currentScope));
          } else if ((matcher = caret.matcher(argument)).matches()) {
            String groupId = matcher.group(1);
            String name = matcher.group(2);
            String version = matcher.group(3);
            String classifier = matcher.group(4);
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
      String specifiedHash = sha256Hash(specifiedDependencies);
      if (lockHash == null | !specifiedHash.equalsIgnoreCase(lockHash)) {
        dependencies = resolveDependencies(specifiedDependencies);
        writeDependencyLock(lockfile.get(), dependencies, specifiedHash);
      } else {
        dependencies = lockedDependencies;
      }
    } else {
      logger.info("Resolving dependencies...");
      dependencies = resolveDependencies(specifiedDependencies);
    }

    if (logger.isLoggable(Level.FINEST)) {
      logger.finest("Downloading dependencies: " + dependencies);
    }

    downloadArtefacts(jarTemplate, dependencies);

    if (downloadSources) {
      downloadArtefacts(sourcesTemplate, dependencies, false);
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
    final ArrayDeque<Dependency> toResolve = new ArrayDeque<>();
    final Map<String, Dependency> resolved = new HashMap<>();
    final Map<String, Dependency> unresolved = new HashMap<>();

    toResolve.addAll(dependencies);

    Dependency dep;
    while ((dep = toResolve.poll()) != null) {
      resolveDependency(toResolve, resolved, unresolved, dep);
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

  record ResolveParentsResult(Map<String, String> properties, List<Dependency> dependencies) {}

  private static void putProperties(Dependency dep, Document pom, Map<String, String> properties) {
    putProjectProperties(dep, properties);
    try {
      NodeList propertyNodes =
          (NodeList) xpath.evaluate("/project/properties/*", pom, XPathConstants.NODESET);
      for (int i = 0; i < propertyNodes.getLength(); i++) {
        Node property = propertyNodes.item(i);
        String value = property.getTextContent();
        if (value.startsWith("$")) value = resolveProperties(value, properties);
        properties.put(property.getNodeName(), value);
      }
      logger.finest(() -> "Found properties %s".formatted(properties));
    } catch (XPathExpressionException e) {
      return;
    }
  }

  private static void putProjectProperties(Dependency dep, Map<String, String> properties) {
    if (!(dep.version() instanceof Exact(var exactVersion))) {
      logger.severe("Can't resolve properties with unexact version: `%s`".formatted(dep));
      abort(1);
      return;
    }
    properties.put("project.groupId", dep.group());
    properties.put("project.artifactId", dep.name());
    properties.put("project.version", exactVersion);
  }

  static ResolveParentsResult resolveParents(Node parentNode, String currentScope) {
    if (parentNode == null) {
      return new ResolveParentsResult(new HashMap<>(), new ArrayList<>());
    }
    var parent = getDependencyInfo(parentNode, null, Map.of());
    logger.finer("Resolving parent %s...".formatted(parent));

    if (!(parent.version() instanceof Exact(var exactVersion))) {
      logger.severe("Can't resolve parent with unexact version: `%s`".formatted(parent));
      abort(1);
      return null;
    }

    Optional<Path> pomPath =
        ensureArtefact(
            parent.group(), parent.name(), exactVersion, "", pomTemplate, Level.FINE, Level.FINEST);

    try (InputStream pomIn = Files.newInputStream(pomPath.get())) {

      Document pom = documentBuilder.parse(pomIn);

      Node superParentNode = (Node) xpath.evaluate("/project/parent", pom, XPathConstants.NODE);

      var parentsResult = resolveParents(superParentNode, currentScope);
      Map<String, String> properties = parentsResult.properties();
      List<Dependency> dependencies = parentsResult.dependencies();

      putProperties(parent, pom, properties);

      NodeList depNodes =
          (NodeList)
              xpath.evaluate(
                  "/project/dependencyManagement/dependencies/dependency",
                  pom,
                  XPathConstants.NODESET);
      for (int i = 0; i < depNodes.getLength(); i++) {
        Node dep = depNodes.item(i);
        dependencies.add(getDependencyInfo(dep, currentScope, properties));
      }
      return new ResolveParentsResult(properties, dependencies);
    } catch (Exception ex) {
      logger.log(Level.SEVERE, "Error while resolving parent dependency", ex);
      abort(1);
      return null;
    }
  }

  static final Pattern propertyRegex = Pattern.compile("\\$\\{([a-zA-Z0-9._-]+?)\\}");

  private static String resolveProperties(String string, Map<String, String> properties) {
    return propertyRegex
        .matcher(string)
        .replaceAll(it -> {
          var key = it.group(1);
          var value = properties.getOrDefault(key, null);
          if (value == null) {
            logger.finest("Undefined property `%s`".formatted(key));
            return "<UNDEFINED>";
          }
          return value;
        });
  }

  static Dependency getDependencyInfo(Node node, String setScope, Map<String, String> properties) {
    try {
      String group = xpath.evaluate("groupId", node);
      if (group.startsWith("$")) group = resolveProperties(group, properties);
      String name = xpath.evaluate("artifactId", node);
      if (name.startsWith("$")) name = resolveProperties(name, properties);
      String version = xpath.evaluate("version", node);
      if (version.startsWith("$")) version = resolveProperties(version, properties);
      if (version.isBlank()) version = null;
      String classifier = xpath.evaluate("classifier", node);
      if (classifier.startsWith("$")) classifier = resolveProperties(classifier, properties);
      if (classifier.isBlank()) classifier = null;
      return new Dependency(group, name, new Exact(version), classifier, setScope);
    } catch (Exception ex) {
      logger.log(Level.SEVERE, "Malformed dependency XML node %s".formatted(node), ex);
      abort(1);
      return null;
    }
  }

  static void resolveDependency(
      final ArrayDeque<Dependency> toResolve,
      final Map<String, Dependency> resolved,
      final Map<String, Dependency> unresolved,
      Dependency dep) {
    if (resolved.containsKey(dep.id())) {
      // TODO: ensure compatible version
      return;
    }
    if (unresolved.containsKey(dep.id())) {
      // TODO: ensure compatible version
      var depFromBom = unresolved.remove(dep.id());
      if (dep.version() instanceof Exact(var exactVersion) && exactVersion == null) {
        // replace with versioned dependency from a parent or BOM and continue
        dep = depFromBom;
      }
    }
    if (dep.version() instanceof Exact(var exactVersion) && exactVersion == null) {
      logger.severe(
          "Can't resolve dependency without version: `%s`. Specifiy a BOM before this to provide the version, or specifiy the version explicitly."
              .formatted(dep));
      abort(1);
      return;
    }
    logger.fine("Resolving " + dep.toString());

    Exact resolvedVersion =
        switch (dep.version) {
          case Exact(var version) -> new Exact(version);

          case Caret(int major) -> {
            final String metadataUrl =
                mavenCentral
                    + metadataURLTemplate.formatted(
                        String.join("/", dep.group().split("\\.")), dep.name());
            try (InputStream in =
                URI.create(metadataUrl).toURL().openConnection().getInputStream(); ) {
              Document metadata = documentBuilder.parse(in);
              NodeList versionNodes = metadata.getElementsByTagName("version");
              List<String> versions = new ArrayList<>();
              for (int i = 0; i < versionNodes.getLength(); ++i) {
                versions.add(versionNodes.item(i).getTextContent());
              }
              logger.finest(() -> "Available versions: " + versions);
              String version =
                  versions.stream()
                      .filter(
                          it -> it.matches("^" + String.valueOf(major) + "\\.([0-9]+)\\.([0-9]+)"))
                      .toList()
                      .getLast();
              yield new Exact(version);
            } catch (NoSuchElementException _) {
              logger.severe("Requested version is not available for dependency " + dep);
              abort(1);
              yield null;
            } catch (Exception ex) {
              ex.printStackTrace();
              abort(1);
              yield null;
            }
          }
        };

    Optional<Path> pomPath =
        ensureArtefact(
            dep.group(),
            dep.name(),
            resolvedVersion.version(),
            "",
            pomTemplate,
            Level.FINE,
            Level.FINEST);
    if (pomPath.isEmpty() && dep.getClassifier().isEmpty()) {
      logger.severe("Requested version is not available for dependency " + dep);
      abort(1);
    }

    logger.finer("Resolved " + dep + " => " + resolvedVersion);

    URL classifiedURL = null;
    try {
      if (dep.getClassifier().isPresent()) {
        var classifier = dep.classifier().equals("platform") ? "linux" : dep.classifier();
        classifiedURL =
            URI.create(
                    (mavenCentral + artefactURLTemplate + jarTemplate)
                        .formatted(
                            String.join("/", dep.group().split("\\.")),
                            dep.name(),
                            resolvedVersion.version(),
                            "-" + classifier))
                .toURL();

        logger.finest("classified url = %s".formatted(classifiedURL));
      }
    } catch (Exception ex) {
      logger.severe(ex.toString());
      abort(1);
    }

    boolean hasClassifier = classifiedURL != null && remoteResourceExists(classifiedURL);
    logger.finest(
        "does %s have classifier = %s".formatted(dep.toString(), String.valueOf(hasClassifier)));

    try (InputStream pomIn = Files.newInputStream(pomPath.get())) {

      Document pom = documentBuilder.parse(pomIn);

      Node parent = (Node) xpath.evaluate("/project/parent", pom, XPathConstants.NODE);

      var parentsResult = resolveParents(parent, dep.scope());
      Map<String, String> properties = parentsResult.properties();
      parentsResult.dependencies().stream()
          .filter(it -> !resolved.containsKey(it.id()))
          .filter(it -> !unresolved.containsKey(it.id())) // TODO: ensure compatible version
          .forEach(it -> unresolved.put(it.id(), it));

      NodeList bomDependencies =
          (NodeList)
              xpath.evaluate(
                  "/project/dependencyManagement/dependencies/dependency",
                  pom,
                  XPathConstants.NODESET);
      if (bomDependencies.getLength() > 0) {
        logger.finer("%s is a BOM".formatted(dep));
        putProperties(dep, pom, properties);
        for (int i = 0; i < bomDependencies.getLength(); ++i) {
          Node subDepNode = bomDependencies.item(i);
          Dependency subDep = getDependencyInfo(subDepNode, dep.scope(), properties);
          if (resolved.containsKey(subDep.id())) continue; // TODO: ensure compatible version
          logger.finest(
              "Putting %s:%s as lazily resolved dependency"
                  .formatted(subDep.id(), subDep.version()));
          unresolved.put(subDep.id(), subDep);
        }

        URL jarURL;
        try {
          String classifierString =
              dep.classifier() == null || dep.classifier().isEmpty()
                  ? ""
                  : ("-" + dep.classifier());
          jarURL =
              URI.create(
                      (mavenCentral + artefactURLTemplate + jarTemplate)
                          .formatted(
                              String.join("/", dep.group().split("\\.")),
                              dep.name(),
                              resolvedVersion.version(),
                              classifierString))
                  .toURL();
        } catch (Exception ex) {
          logger.severe(ex.toString());
          abort(1);
          return;
        }
        if (!remoteResourceExists(jarURL)) {
          logger.finest(
              "Resource %s doesn't exist, treating %s as BOM only".formatted(jarURL, dep));
          return;
        }
      }

      resolved.put(
          dep.id(),
          new Dependency(
              dep.group(),
              dep.name(),
              resolvedVersion,
              hasClassifier ? dep.classifier() : null,
              dep.scope()));

      NodeList subDependencies =
          (NodeList)
              xpath.evaluate(
                  "/project/dependencies/dependency[not(scope) or scope='compile']",
                  pom,
                  XPathConstants.NODESET);

      for (int i = 0; i < subDependencies.getLength(); ++i) {
        Node subDepNode = subDependencies.item(i);

        if ("true".equals(xpath.evaluate("optional", subDepNode))) continue;

        var subDep = getDependencyInfo(subDepNode, dep.scope(), properties);
        if (!(subDep.version() instanceof Exact(String exactVersion))) {
          throw new AssertionError("Attempted to resolve dependency with unexact version");
        }

        toResolve.addLast(
            new Dependency(
                subDep.group(),
                subDep.name(),
                switch (dep.version) {
                  case Exact _ -> subDep.version();
                  case Caret _ -> {
                    Matcher matcher;
                    if ((matcher = semver.matcher(exactVersion)).matches() == false) {
                      yield subDep.version();
                    } else {
                      int major = Integer.parseInt(matcher.group(1));
                      yield new Caret(major);
                    }
                  }
                },
                // TODO: support reading actual platforms/architectures from the
                // <profiles> in POM
                dep.classifier(),
                dep.scope()));
      }

    } catch (Exception ex) {
      ex.printStackTrace();
      abort(1);
    }
  }

  static void writeDependencyLock(Path lockfile, List<Dependency> dependencies, String hash) {
    dependencies = new ArrayList<>(dependencies);
    dependencies.sort(
        Comparator.<Dependency, String>comparing(it -> it.scope())
            .thenComparing(Comparator.naturalOrder()));
    String currentScope = null;
    try (Writer out = new FileWriter(lockfile.toFile())) {
      out.write("# Depres dependency lock file\n--locked ");
      out.write(hash);
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

  public sealed interface Version extends Serializable {
    public record Exact(String version) implements Version {
      public String toString() {
        if (version == null) return "<unversioned>";
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
      String group, String name, Version version, String classifier, String scope)
      implements Comparable<Dependency>, Serializable {
    public Dependency(String group, String name, Version version) {
      this(group, name, version, null, "implementation");
    }

    public Optional<String> getClassifier() {
      return Optional.ofNullable(classifier);
    }

    public String id() {
      return group + ":" + name;
    }

    public String toString() {
      return group
          + ":"
          + name
          + version
          + getClassifier().map(it -> "[%s]".formatted(it)).orElse("");
    }

    public int compareTo(Dependency other) {
      return Comparator.comparing(Dependency::group)
          .thenComparing(Dependency::name)
          .compare(this, other);
    }
  }

  public static String mavenCentral = "https://repo1.maven.org/maven2/";
  public static String artefactURLTemplate = "%1$s/%2$s/%3$s/";
  public static String metadataURLTemplate = "%1$s/%2$s/maven-metadata.xml";
  public static String jarTemplate = "%2$s-%3$s%4$s.jar";
  public static String sourcesTemplate = "%2$s-%3$s-sources.jar";
  public static String pomTemplate = "%2$s-%3$s.pom";

  static String[] getClassifiers(Dependency dependency) {
    return dependency
        .getClassifier()
        .map(it ->
            switch (it) {
              case "platform" -> downloadForPlatforms;
              case String cls -> new String[] {cls};
            })
        .orElse(new String[] {""});
  }

  static void downloadArtefacts(String filenameTemplate, List<Dependency> dependencies) {
    downloadArtefacts(filenameTemplate, dependencies, true);
  }

  static void downloadArtefacts(
      String filenameTemplate, List<Dependency> dependencies, boolean failFast) {
    for (Dependency dependency : dependencies) {
      if (!(dependency.version() instanceof Exact(String exactVersion))) {
        throw new AssertionError("Attempted to download dependency with unexact version");
      }
      for (String classifier : getClassifiers(dependency)) {
        var path =
            ensureArtefact(
                dependency.group(),
                dependency.name(),
                exactVersion,
                classifier,
                filenameTemplate,
                Level.INFO,
                failFast ? Level.SEVERE : Level.WARNING);
        if (path.isEmpty()) {
          if (failFast) abort(1);
        }
      }
    }
  }

  static Optional<Path> ensureArtefact(
      String group,
      String name,
      String version,
      String classifier,
      String filenameTemplate,
      Level downloadLogLevel,
      Level failLogLevel) {
    String classifierString = classifier.isEmpty() ? "" : ("-" + classifier);
    String filename = filenameTemplate.formatted(group, name, version, classifierString);
    Path artefactPath = artefactCache.getPath(group, name, version, filename);
    if (Files.exists(artefactPath)) return Optional.of(artefactPath);

    String url =
        (mavenCentral + artefactURLTemplate + filenameTemplate)
            .formatted(String.join("/", group.split("\\.")), name, version, classifierString);
    logger.log(downloadLogLevel, "Downloading %s ...".formatted(filename));
    try {
      downloadArtefact(url, artefactPath);
    } catch (IOException ex) {
      logger.log(failLogLevel, "Could not download " + url, ex);
      return Optional.empty();
    }
    return Optional.of(artefactPath);
  }

  /** Downloads file from {@code url} to {@code destination} */
  static void downloadArtefact(String url, Path destination) throws IOException {
    try (InputStream urlStream = URI.create(url).toURL().openStream(); ) {
      var temp = destination.getParent().resolve(destination.getFileName() + ".part");
      urlStream.transferTo(new FileOutputStream(temp.toFile()));
      Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE);
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
              jarTemplate.formatted(
                  null, dep.name(), exactVersion, classifier.isEmpty() ? "" : ("-" + classifier));
          Path link =
              switch (dep.scope()) {
                case "implementation" -> destination.resolve(jarName);
                case String scope -> {
                  Files.createDirectories(destination.resolve(scope));
                  yield destination.resolve(scope, jarName);
                }
              };
          Path target = artefactCache.getPath(dep.group(), dep.name(), exactVersion, jarName);
          linkFile(target, link);
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
        String jarName = sourcesTemplate.formatted(null, dep.name(), exactVersion);
        Path link = destination.resolve(jarName);
        Path target = artefactCache.getPath(dep.group(), dep.name(), exactVersion, jarName);
        linkFile(target, link);
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
        if (Optional.ofNullable(System.console()).map(Console::isTerminal).orElse(false)) {
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

  static void linkFile(Path source, Path destination) {
    try {
      switch (systemOs) {
        case LINUX, MACOS -> {
          if (!Files.exists(destination) || !Files.readSymbolicLink(destination).equals(source)) {
            Files.createSymbolicLink(destination, source);
          }
        }
        case WINDOWS -> {
          if (!Files.exists(destination)) {
            Files.copy(source, destination);
          }
        }
      }
    } catch (IOException e) {
      logger.log(Level.SEVERE, "Could not link file", e);
      abort(1);
    }
  }

  static String sha256Hash(Object o) {
    try (var baos = new ByteArrayOutputStream();
        var oos = new ObjectOutputStream(baos); ) {
      oos.writeObject(o);
      byte[] hash = MessageDigest.getInstance("SHA-256").digest(baos.toByteArray());
      return HexFormat.of().formatHex(hash);
    } catch (Exception ex) {
      logger.log(Level.SEVERE, "Could not compute SHA-256 for object", ex);
      return null;
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
  -d                  Default option. equivalent to @dependencies
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
    @Serial static final long serialVersionUID = 0xcafe08a85bd7160eL;

    final int code;

    public AbortException(int code) {
      this.code = code;
    }
  }

  static void abort(int code) {
    throw new AbortException(code);
  }
}
