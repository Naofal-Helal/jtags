package xyz.naofal.jtags;

import static xyz.naofal.jtags.JtagsLogger.logger;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.PackageTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePathScanner;
import java.util.List;
import java.util.PriorityQueue;
import javax.lang.model.element.Modifier;

public class TreeVisitor extends TreePathScanner<Void, TreeVisitorContext> {
  public final PriorityQueue<Tag> tags = new PriorityQueue<>();

  @Override
  public Void visitCompilationUnit(CompilationUnitTree node, TreeVisitorContext p) {
    p.compilationUnitTree = node;

    logger.fine(() -> "Collecting tags in file: " + p.getLocation());

    return super.visitCompilationUnit(node, p);
  }

  @Override
  public Void visitPackage(PackageTree node, TreeVisitorContext p) {
    Tag tag =
        new Tag(
            TagKind.PACKAGE, node.getPackageName().toString(), p.getLocation(), p.getLine(node));

    logger.finer(() -> "Package: " + tag);

    tags.add(tag);

    return null;
  }

  @Override
  public Void visitClass(ClassTree node, TreeVisitorContext p) {
    Tag tag =
        new Tag(
            switch (node.getKind()) {
              case CLASS -> TagKind.CLASS;
              case RECORD -> TagKind.RECORD;
              case INTERFACE -> TagKind.INTERFACE;
              case ENUM -> TagKind.ENUM;
              case ANNOTATION_TYPE -> TagKind.ANNOTATION;
              default -> {
                logger.warning("Unknown class kind " + node.getKind());
                yield TagKind.CLASS;
              }
            },
            node.getSimpleName().toString(),
            p.getLocation(),
            p.getLine(node),
            node.getModifiers().getFlags().contains(Modifier.STATIC));

    logger.finer(() -> "Type: " + tag);

    tags.add(tag);

    return super.visitClass(node, p);
  }

  @Override
  public Void visitMethod(MethodTree node, TreeVisitorContext p) {
    Tag tag =
        new Tag(
            switch (node.getKind()) {
              case METHOD -> TagKind.METHOD;
              default -> {
                logger.warning("Unknown method kind " + node.getKind());
                yield TagKind.METHOD;
              }
            },
            node.getReturnType() != null
                ? node.getName().toString()
                : ((ClassTree) getCurrentPath().getParentPath().getLeaf())
                    .getSimpleName()
                    .toString(),
            p.getLocation(),
            p.getLine(node),
            node.getModifiers().getFlags().contains(Modifier.STATIC));

    logger.finer(() -> "Method: " + tag);

    tags.add(tag);

    return super.visitMethod(node, p);
  }

  @Override
  public Void visitVariable(VariableTree node, TreeVisitorContext p) {
    Tree parent = getCurrentPath().getParentPath().getLeaf();

    if (!(parent instanceof ClassTree enclosingType)) {
      return null;
    }

    Tag tag =
        new Tag(
            enclosingType.getKind() == Tree.Kind.ENUM
                    && node.getModifiers()
                        .getFlags()
                        .containsAll(List.of(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL))
                ? TagKind.ENUM_CONSTANT
                : TagKind.FIELD,
            node.getName().toString(),
            p.getLocation(),
            p.getLine(node),
            node.getModifiers().getFlags().contains(Modifier.STATIC));

    logger.finer(() -> "Variable: " + tag);

    tags.add(tag);

    return null;
  }
}
