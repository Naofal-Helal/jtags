package xyz.naofal.jtags;

import java.nio.file.Path;
import java.util.Comparator;

public record Tag(TagKind kind, String name, Path location, String line, boolean isStatic)
    implements Comparable<Tag> {

  public Tag(TagKind kind, String name, Path location, String line) {
    this(kind, name, location, line, false);
  }

  @Override
  public int compareTo(Tag o) {
    return Comparator.comparing(Tag::name, String.CASE_INSENSITIVE_ORDER)
        .thenComparing(Tag::location)
        .thenComparing(Tag::kind)
        .compare(this, o);
  }
}
