import static nobuild.NoBuild.*;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class Build {
  static final String program = "jtags";

  static void printUsage() {
    System.err.println(
        """
        Usage: java Build <subcommand> [args...]
        Subcommands:
          build          Build %1$s
          run [args...]  Run %1$s with arguments
          package        Package %1$s to a JAR file
            [file]         Output JAR filename [Default: Jtags.jar]
          test           Run %1$s tests
        """
            .formatted(program));
  }

  public static void main(String[] args) {
    rebuildSelf(Build.class, args);

    var arguments = new ArrayDeque<>(Arrays.asList(args));

    if (arguments.size() == 0) {
      printUsage();
      System.exit(1);
    }

    String mainClass = "xyz.naofal.jtags.Jtags";
    String[] sourcePaths = glob("src/main/java/**.java");
    String[] classPaths = {};

    var subcommand = arguments.pop();
    switch (subcommand) {
      case "build":
        buildJtags(mainClass, sourcePaths, classPaths);
        break;

      case "run":
        buildJtags(mainClass, sourcePaths, classPaths);

        Map<Boolean, List<String>> argPartition =
            arguments.stream().collect(Collectors.partitioningBy(it -> it.startsWith("-D")));
        runJava(
            classPaths,
            argPartition.get(true).toArray(String[]::new),
            mainClass,
            argPartition.get(false).toArray(String[]::new));
        break;

      case "package":
        buildJtags(mainClass, sourcePaths, classPaths);
        packageJar(Optional.ofNullable(arguments.poll()));
        break;

      case "test":
        buildJtags(mainClass, sourcePaths, classPaths);
        runTests(arguments.toArray(String[]::new));
        break;

      default:
        logger.severe("Unknown subcommand: " + subcommand);
        printUsage();
        System.exit(1);
        break;
    }
  }

  static void buildJtags(String mainClass, String[] sourcePaths, String[] classPaths) {
    if (!classNeedsRebuild(mainClass, sourcePaths)) {
      return;
    }

    logger.info("Compiling %s...".formatted(program));
    if (!compileJava(classPaths, sourcePaths)) {
      System.exit(1);
    }
  }

  static void packageJar(Optional<String> jarfile) {
    command(
        "jar",
        "cfe",
        jarfile.orElse("Jtags.jar"),
        "xyz.naofal.jtags.Jtags",
        "-C",
        buildClassPath,
        ".");
  }

  static void runTests(String[] args) {
    String mainClass = "TestJtags";
    String[] sourcePaths = glob("src/test/java/**.java");

    if (classNeedsRebuild(mainClass, sourcePaths)) {
      logger.info("Compiling %s...".formatted(mainClass));
      if (!compileJava(sourcePaths)) {
        System.exit(1);
      }
    }

    System.exit(runJava(new String[0], new String[] {"-enableassertions"}, mainClass, args));
  }
}
