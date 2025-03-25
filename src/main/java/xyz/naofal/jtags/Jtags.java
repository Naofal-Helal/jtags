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
    Path output = Path.of("tags");
    boolean absolutePaths = false;
    boolean excludeNonPublic = false;
    boolean excludeAnonymous = false;
    boolean excludeStaticField = false;
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

        case "-no-static":
          logger.config("Excluding 'file:' field in tags");
          options.excludeStaticField = true;
          break;

        case "-no-non-public":
          logger.config("Excluding non-public elements");
          options.excludeNonPublic = true;
          break;

        case "-no-anonymous":
          logger.config("Excluding anonymous classes");
          options.excludeAnonymous = true;
          break;

        case "-lib":
          logger.config("Third-party library mode");
          options.absolutePaths = true;
          options.excludeStaticField = true;
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
                              (alias for -absolute -no-non-public -no-anonymous)
          -absolute           Use absolute paths for tag locations
          -no-static          Exclude the "file:" field from tags
          -no-anonymous       Exclude anonymous classes
          -no-anonymous       Exclude anonymous classes
          -h, -help           Show this message
        """);
  }
}
