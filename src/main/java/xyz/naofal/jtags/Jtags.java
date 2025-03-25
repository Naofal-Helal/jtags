package xyz.naofal.jtags;

import static xyz.naofal.jtags.JtagsLogger.logger;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Jtags {
  static class Options {
    List<String> sources = new ArrayList<>();
    boolean absolutePaths = false;
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
        case "-absolute":
          logger.config("Using absolute paths");
          options.absolutePaths = true;
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
    try {
      tagsWriter.writeTagsFile(tags, new FileOutputStream("tags"));
    } catch (FileNotFoundException ex) {
      logger.severe(ex.toString());
      return false;
    }

    return true;
  }

  static void printUsage() {
    System.err.println(
        """
        Usage: jtags [options] <sources...>
        Options:
          -absolute   Use absolute paths for tag locations
        """);
  }
}
