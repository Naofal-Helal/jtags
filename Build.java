import static nobuild.NoBuild.*;

import java.nio.file.Paths;

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

    runJava(classPaths, mainClass, args);
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
