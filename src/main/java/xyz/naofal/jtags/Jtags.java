package xyz.naofal.jtags;

import static xyz.naofal.jtags.JtagsLogger.logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Jtags {
  static class Options {
    List<String> sources = new ArrayList<>();
    boolean absolutePaths = false;
    Path output = Paths.get("tags");
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

        case "-o", "-output":
          options.output =
              switch (arguments.poll()) {
                case null -> {
                  logger.severe("Expected argument after " + argument);
                  printUsage();
                  System.exit(1);
                  yield null;
                }
                case String output -> Paths.get(output);
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
          -absolute           Use absolute paths for tag locations
          -o, -output <file>  Write tags to specified <file>
          -h, -help           Show this message
        """);
  }
}
