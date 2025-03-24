package xyz.naofal.jtags;

import static xyz.naofal.jtags.JtagsLogger.logger;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.PackageTree;
import com.sun.source.util.SimpleTreeVisitor;
import java.util.PriorityQueue;

public class TreeVisitor extends SimpleTreeVisitor<Void, TreeVisitorContext> {
  public PriorityQueue<Tag> tags = new PriorityQueue<>();

  @Override
  public Void visitCompilationUnit(CompilationUnitTree node, TreeVisitorContext p) {
    p.compilationUnitTree = node;

    logger.finer("Collecting tags in file: " + p.getLocation());

    PackageTree packageTree = node.getPackage();
    if (packageTree != null) {
      packageTree.accept(this, p);
    }

    node.getTypeDecls().forEach(it -> it.accept(this, p));

    return null;
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
            p.getLine(node));

    logger.finer(() -> "Type: " + tag);

    tags.add(tag);

    node.getMembers().forEach(it -> it.accept(this, p));

    return null;
  }

  @Override
  public Void visitMethod(MethodTree node, TreeVisitorContext p) {
    Tag tag = new Tag(TagKind.METHOD, node.getName().toString(), p.getLocation(), p.getLine(node));
    logger.fine(() -> "Method: " + tag);
    tags.add(tag);
    return null;
  }
}
