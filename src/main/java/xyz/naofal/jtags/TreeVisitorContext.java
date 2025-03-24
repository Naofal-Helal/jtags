package xyz.naofal.jtags;

import static xyz.naofal.jtags.JtagsLogger.logger;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.Trees;
import java.io.BufferedReader;
import java.nio.file.Paths;
import java.util.Optional;

public class TreeVisitorContext {
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
    long offset = getOffsetInSource(compilationUnitTree, node);
    try (var reader = new BufferedReader(compilationUnitTree.getSourceFile().openReader(true))) {
      long skipped = 0;
      while (true) {
        String line = reader.readLine();
        if (line == null) throw new RuntimeException("Reached the end of the stream");
        skipped += line.length() + 1;
        if (skipped >= offset) return line;
      }
    } catch (Exception e) {
      logger.severe(e.toString());
      logger.finer("Node that caused the exception:\n" + node.toString().indent(4));
      System.exit(1);
      return null;
    }
  }

  private long getOffsetInSource(CompilationUnitTree compilationUnitTree, Tree node) {
    assert compilationUnitTree != null;
    return switch (node) {
      case ClassTree t -> {
        ModifiersTree modifiers = t.getModifiers();
        if (modifiers == null
            || (modifiers.getAnnotations().isEmpty() && modifiers.getFlags().isEmpty())) {
          yield sourcePositions.getStartPosition(compilationUnitTree, t);
        } else {
          yield sourcePositions.getEndPosition(compilationUnitTree, t.getModifiers());
        }
      }
      case MethodTree t ->
          sourcePositions.getEndPosition(
              compilationUnitTree,
              Optional.<Tree>ofNullable(t.getReturnType())
                  .or(() -> Optional.ofNullable(t.getModifiers()))
                  .orElse(t));
      default -> sourcePositions.getStartPosition(compilationUnitTree, node);
    };
  }
}
