import static nobuild.NoBuild.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
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

    String mainClass = "al.naof.jtags.Jtags";
    String[] sourcePaths = glob("src/main/java/**.java");

    var subcommand = arguments.pop();
    switch (subcommand) {
      case "build":
        buildJtags(mainClass, sourcePaths);
        break;

      case "run":
        buildJtags(mainClass, sourcePaths);

        Map<Boolean, List<String>> argPartition =
            arguments.stream().collect(Collectors.partitioningBy(it -> it.startsWith("-D")));

        runJava()
            .javaArguments(argPartition.get(true).toArray(String[]::new))
            .run(mainClass, argPartition.get(false).toArray(String[]::new));
        break;

      case "package":
        clean();
        buildJtags(mainClass, sourcePaths);
        packageJar(Optional.ofNullable(arguments.poll()).orElse("Jtags.jar"));
        break;

      case "test":
        buildJtags(mainClass, sourcePaths);
        runTests(arguments.toArray(String[]::new));
        break;

      default:
        logger.severe("Unknown subcommand: " + subcommand);
        printUsage();
        System.exit(1);
        break;
    }
  }

  static void buildJtags(String mainClass, String[] sourcePaths) {
    if (!classNeedsRebuild(mainClass, sourcePaths)) {
      return;
    }

    logger.info("Compiling %s...".formatted(program));
    if (!compileJava(sourcePaths)) {
      System.exit(1);
    }
  }

  static void clean() {
    command("rm", "-rf", buildClassPath + "/al");
  }

  static void packageJar(String jarfile) {
    String jarfileTmp = jarfile + ".tmp";
    command("jar", "cfe", jarfileTmp, "al.naof.jtags.Jtags", "-C", buildClassPath, "al/naof/jtags");

    // Make jar file executable by hashbang
    String hashbang = "#!/usr/bin/env -S java -jar\n";
    try (FileInputStream in = new FileInputStream(jarfileTmp);
        FileOutputStream out = new FileOutputStream(jarfile); ) {
      out.write(hashbang.getBytes());
      in.transferTo(out);
      new File(jarfile).setExecutable(true);
      Files.delete(Path.of(jarfileTmp));
    } catch (Exception ex) {
      logger.log(Level.SEVERE, "Problem during file IO with " + jarfile, ex);
    }
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

    System.exit(runJava().javaArguments("-enableassertions").run(mainClass, args));
  }
}
