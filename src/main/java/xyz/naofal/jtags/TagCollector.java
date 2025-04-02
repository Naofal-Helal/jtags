package xyz.naofal.jtags;

import static xyz.naofal.jtags.JtagsLogger.logger;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.nio.file.Path;
import java.util.AbstractQueue;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import xyz.naofal.jtags.Jtags.Options;

public class TagCollector {

  static JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

  public static AbstractQueue<Tag> collectTags(Options options) {
    try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {

      String[] sources = resolveSources(options);
      logger.finest(() -> "Collecting tags from sources: " + Arrays.toString(sources));

      Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjects(sources);

      JavacTask task =
          (JavacTask) compiler.getTask(null, fileManager, null, null, null, compilationUnits);
      Iterable<? extends CompilationUnitTree> trees = task.parse();

      TreeVisitor treeVisitor = new TreeVisitor(options);
      TreeVisitorContext context = new TreeVisitorContext(Trees.instance(task));

      for (CompilationUnitTree compilationUnitTree : trees) {
        treeVisitor.scan(compilationUnitTree, context);
      }

      return treeVisitor.tags;

    } catch (IOException ex) {
      logger.severe(ex.toString());
      System.exit(1);
      return null;
    }
  }

  static String[] resolveSources(Options options) {
    return options.sources.stream()
        .flatMap(
            path ->
                switch (path) {
                  case String _ when path.endsWith(".java") -> Stream.of(path);
                  case String _ when path.endsWith(".jar") || path.endsWith(".zip") -> {
                    if (options.extractPath.isEmpty()) {
                      logger.severe("Archive file specified, but no -extract-dir");
                      System.exit(1);
                    }
                    Path archivePath = Path.of(path);
                    String archiveName = archivePath.getFileName().toString();
                    yield ArchiveExtractor.extractArchive(
                        archivePath,
                        options
                            .extractPath
                            .get()
                            .resolve(archiveName.substring(0, archiveName.lastIndexOf('.'))),
                        "^.*\\.java$")
                        .stream();
                  }
                  default -> {
                    logger.warning("Ignoring unsupported source: " + path);
                    yield Stream.of();
                  }
                })
        .filter(Objects::nonNull)
        // TODO: support package-info and module-info
        .filter(it -> !it.endsWith("package-info.java") && !it.endsWith("module-info.java"))
        .toArray(String[]::new);
  }
}
