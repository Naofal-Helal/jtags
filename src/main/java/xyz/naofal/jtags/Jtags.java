package xyz.naofal.jtags;

import static xyz.naofal.jtags.JtagsLogger.logger;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayDeque;
import java.util.Arrays;

public class Jtags {
  static class Options {
    String[] sources;
  }

  public static void main(String[] args) {
    var arguments = new ArrayDeque<>(Arrays.asList(args));

    if (arguments.size() == 0) {
      printUsage();
      System.exit(1);
    }

    Options options = new Options();
    options.sources = args;

    System.exit(run(options) ? 0 : 1);
  }

  static boolean run(Options options) {
    var tags = TagCollector.collectTags(options);

    try {
      TagsWriter.writeTagsFile(tags, new FileOutputStream("tags"));
    } catch (FileNotFoundException ex) {
      logger.severe(ex.toString());
      return false;
    }

    return true;
  }

  static void printUsage() {
    System.err.println(
        """
        USAGE: jtags [options] <sources...>
        """);
  }
}
