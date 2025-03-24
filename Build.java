import static nobuild.NoBuild.*;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Collectors;

public class Build {
  static final String program = "jtags";

  public static void main(String[] args) {
    rebuildSelf(Build.class, args);

    String mainClass = "xyz.naofal.jtags.Jtags";
    String[] sourcePaths = glob("src/main/java/**.java");
    String[] classPaths = glob("third-party/jars/*.jar");

    if (classNeedsRebuild(mainClass, sourcePaths)) {
      ensureDependencies();
      logger.info("Compiling %s...".formatted(program));
      compileJava(classPaths, sourcePaths);
    }

    var argumentPartitions =
        Arrays.stream(args).collect(Collectors.partitioningBy(it -> it.startsWith("-D")));
    String[] javaArguments = argumentPartitions.get(true).toArray(String[]::new);
    String[] programArguments = argumentPartitions.get(false).toArray(String[]::new);

    runJava(classPaths, javaArguments, mainClass, programArguments);
  }

  private static void ensureDependencies() {
    Dependency[] dependencies = {
      new Dependency("com.h2database", "h2", "2.3.232"),
    };

    if (!downloadDependencies(mavenCentral, Paths.get("third-party", "jars"), dependencies)) {
      logger.severe("Could not download all dependencies");
      System.exit(1);
    }
  }
}
