package xyz.naofal.jtags;

import static xyz.naofal.jtags.JtagsLogger.logger;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.util.PriorityQueue;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import xyz.naofal.jtags.Jtags.Options;

public class TagCollector {

  static JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

  public static PriorityQueue<Tag> collectTags(Options options) {
    try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {

      Iterable<? extends JavaFileObject> compilationUnits =
          fileManager.getJavaFileObjects(options.sources.toArray(String[]::new));

      JavacTask task =
          (JavacTask) compiler.getTask(null, fileManager, null, null, null, compilationUnits);
      Iterable<? extends CompilationUnitTree> trees = task.parse();
      TreeVisitorContext context = new TreeVisitorContext(Trees.instance(task));
      TreeVisitor treeVisitor = new TreeVisitor();
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
}
