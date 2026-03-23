package al.naof.jtags;

import static al.naof.jtags.JtagsLogger.logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

public class Jtags {
  static class Options {
    List<String> sources = new ArrayList<>();
    Path output = Path.of(".", "tags");
    boolean absolutePaths = false;
    boolean excludeNonPublic = false;
    boolean excludeAnonymous = false;
    List<Class<? extends TagField>> fields =
        List.of(TagField.StaticTag.class, TagField.Package.class, TagField.EnclosingType.class);
    Optional<Path> extractPath = Optional.empty();
  }

  public static void main(String[] args) {
    var arguments = new ArrayDeque<>(Arrays.asList(args));

    if (arguments.size() == 0) {
      printUsage();
      System.exit(1);
    }

    Options options = new Options();
    String argument;
    while ((argument = arguments.poll()) != null) {
      switch (argument) {
        case "-h", "help", "-help", "--help":
          printUsage();
          System.exit(0);
          break;

        case "-absolute":
          logger.config("Using absolute paths");
          options.absolutePaths = true;
          break;

        case "-no-non-public":
          logger.config("Excluding non-public elements");
          options.excludeNonPublic = true;
          break;

        case "-no-anonymous":
          logger.config("Excluding anonymous classes");
          options.excludeAnonymous = true;
          break;

        case "-fields":
          String fields =
              switch (arguments.poll()) {
                case null -> {
                  printUsage();
                  logger.severe("Expected argument after " + argument);
                  System.exit(1);
                  yield null;
                }
                case String s -> s;
              };

          options.fields =
              fields
                  .chars()
                  .<Class<? extends TagField>>mapToObj(
                      it ->
                          switch (it) {
                            case 's' -> TagField.StaticTag.class;
                            case 'p' -> TagField.Package.class;
                            case 't' -> TagField.EnclosingType.class;
                            default -> {
                              printUsage();
                              logger.severe("Unknown field: " + it);
                              System.exit(1);
                              yield null;
                            }
                          })
                  .toList();

          logger.config(
              () ->
                  "Including fields: "
                      + String.join(
                          ", ", options.fields.stream().map(it -> it.getSimpleName()).toList()));
          break;

        case "-lib":
          logger.config("Third-party library mode");
          options.excludeNonPublic = true;
          options.excludeAnonymous = true;
          break;

        case "-o", "-output":
          options.output =
              switch (arguments.poll()) {
                case null -> {
                  printUsage();
                  logger.severe("Expected argument after " + argument);
                  System.exit(1);
                  yield null;
                }
                case "-" -> Path.of("-");
                case String output -> Path.of(output).toAbsolutePath();
              };
          logger.config("Writing tags to " + options.output.toString());
          break;

        case "-extract-dir":
          options.extractPath =
              switch (arguments.poll()) {
                case null -> {
                  printUsage();
                  logger.severe("Expected argument after " + argument);
                  System.exit(1);
                  yield null;
                }
                case String dir -> Optional.of(Path.of(dir).toAbsolutePath());
              };
          logger.config("Extracting JARs to " + options.extractPath.toString());
          break;

        case String arg
        when arg.startsWith("@"):
          String filename = arg.substring(1);
          if (filename.isBlank()) {
            logger.severe("Expected filename after @");
            System.exit(1);
          }
          try {
            logger.config("Reading arguments from " + filename);
            Files.readAllLines(Path.of(filename)).stream()
                .filter(it -> !it.trim().startsWith("#") && !it.isBlank())
                .flatMap(it -> Arrays.stream(it.split("#")[0].trim().split("\\s+")))
                .toList()
                .reversed()
                .forEach(arguments::addFirst);
          } catch (Exception ex) {
            logger.log(Level.SEVERE, "Could not arguments from file `" + filename + "`", ex);
            System.exit(1);
          }
          break;

        case String arg
        when arg.startsWith("-D"):
          int equals = arg.indexOf('=');
          if (equals < 0) {
            logger.severe("Invalid argument: expected -D<key>=<value>");
            System.exit(1);
          }
          String key = arg.substring(2, equals);
          String value = arg.substring(equals + 1);
          logger.config("Set system property %s=%s".formatted(key, value));
          System.setProperty(key, value);
          JtagsLogger.setLevelFromProperties();
          break;

        default:
          options.sources.add(argument);
          break;
      }
    }

    if (options.sources.isEmpty()) {
      printUsage();
      logger.severe("No source files provided");
      System.exit(1);
    }

    System.exit(run(options) ? 0 : 1);
  }

  static boolean run(Options options) {
    var tags = TagCollector.collectTags(options);
    var tagsWriter = new TagsWriter(options);
    return tagsWriter.writeTagsFile(tags);
  }

  static void printUsage() {
    System.err.println(
"""
Usage: jtags [options] <sources...>
Options:
  @<file>             Read arguments from <file>
  -o, -output <file>  Write tags to specified <file>
                      Use - for standard output. Default: tags
  -lib                Treat sources as third-party libraries
                      (alias for -no-non-public -no-anonymous)
  -no-anonymous       Exclude anonymous classes
  -no-non-public      Exclude non-public elements
  -absolute           Use absolute paths for tag locations
  -fields <fields>    Fields to include in tag entries. Default: spt
                      Avaiblable fields are:
                        s  static tag
                        p  package
                        t  enclosing type
  -extract-dir <dir>  Extract JAR and ZIP archives to <dir>
  -D<key>=<value>     Set system property, e.g. -Dlogger.level=FINE
  -h, -help           Show this message
""");
  }
}
