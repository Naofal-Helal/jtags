package xyz.naofal.jtags;

import static xyz.naofal.jtags.JtagsLogger.logger;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Trees;
import java.util.AbstractQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import xyz.naofal.jtags.Jtags.Options;

public class TagCollector {

  static JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

  public static AbstractQueue<Tag> collectTags(Options options) {
    try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {

      Iterable<? extends JavaFileObject> compilationUnits =
          fileManager.getJavaFileObjects(options.sources.toArray(String[]::new));

      logger.info("Parsing sources...");
      JavacTask task =
          (JavacTask) compiler.getTask(null, fileManager, null, null, null, compilationUnits);
      Iterable<? extends CompilationUnitTree> trees = task.parse();

      logger.info("Collecting tags...");

      Trees treeUtils = Trees.instance(task);
      AbstractQueue<Tag> tags = new PriorityBlockingQueue<>();

      TreeVisitor treeVisitor = new TreeVisitor(options, tags);
      for (CompilationUnitTree compilationUnitTree : trees) {
              TreeVisitorContext context = new TreeVisitorContext(compilationUnitTree, treeUtils);
              treeVisitor.scan(compilationUnitTree, context);
      }

      return tags;

    } catch (Exception ex) {
      logger.severe(ex.toString());
      System.exit(1);
      return null;
    }
  }
}
