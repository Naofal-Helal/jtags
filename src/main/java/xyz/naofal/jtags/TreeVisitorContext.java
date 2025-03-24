package xyz.naofal.jtags;

import static xyz.naofal.jtags.JtagsLogger.logger;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.Trees;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Paths;

public class TreeVisitorContext {
  private static final int MAX_LINE_LENGTH = 4096;

  final Trees trees;
  final SourcePositions sourcePositions;
  public CompilationUnitTree compilationUnitTree;

  public TreeVisitorContext(Trees trees) {
    this.trees = trees;
    sourcePositions = trees.getSourcePositions();
  }

  public String getLocation() {
    assert compilationUnitTree != null;
    return Paths.get(".")
        .toAbsolutePath()
        .relativize(Paths.get(compilationUnitTree.getSourceFile().toUri()).toAbsolutePath())
        .toString();
  }

  public String getLine(Tree node) {
    assert compilationUnitTree != null;
    long offset = sourcePositions.getStartPosition(compilationUnitTree, node);
    try (var reader = new BufferedReader(compilationUnitTree.getSourceFile().openReader(true))) {
      long skipped = 0;
      while (skipped <= offset) {
        reader.mark(MAX_LINE_LENGTH);
        String line = reader.readLine();
        if (line == null) break;
        int lineLength = line.length();
        if (lineLength >= MAX_LINE_LENGTH) {
          logger.severe("Line length exceeded");
          System.exit(1);
        }
        skipped += lineLength + 1;
      }
      reader.reset();
      return reader.readLine();
    } catch (IOException e) {
      logger.severe(e.toString());
      System.exit(1);
      return null;
    }
  }
}
