package xyz.naofal.jtags;

import static xyz.naofal.jtags.JtagsLogger.logger;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Jtags {
  static class Options {
    List<String> sources = new ArrayList<>();
    Path output = Path.of(".", "tags");
    boolean absolutePaths = false;
    boolean excludeNonPublic = false;
    boolean excludeAnonymous = false;
    List<Class<? extends TagField>> fields =
        List.of(TagField.StaticTag.class, TagField.Package.class, TagField.EnclosingType.class);
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
                  logger.severe("Expected argument after " + argument);
                  printUsage();
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
                          (Class<? extends TagField>)
                              switch (it) {
                                case 's' -> TagField.StaticTag.class;
                                case 'p' -> TagField.Package.class;
                                case 't' -> TagField.EnclosingType.class;
                                default -> {
                                  logger.severe("Unknown field: " + it);
                                  printUsage();
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
                  logger.severe("Expected argument after " + argument);
                  printUsage();
                  System.exit(1);
                  yield null;
                }
                case String output -> Path.of(output);
              };
          logger.config("Writing tags to " + options.output.toString());
          break;

        default:
          options.sources.add(argument);
          break;
      }
    }

    if (options.sources.isEmpty()) {
      logger.severe("No source files provided");
      printUsage();
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
  -o, -output <file>  Write tags to specified <file>
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
  -h, -help           Show this message
""");
  }
}
