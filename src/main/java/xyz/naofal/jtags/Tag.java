package xyz.naofal.jtags;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public record Tag(TagKind kind, String name, Path location, String line, List<TagField> fields)
    implements Comparable<Tag> {

  public Tag(TagKind kind, String name, Path location, String line) {
    this(kind, name, location, line, List.of());
  }

  @Override
  public int compareTo(Tag o) {
    return Comparator.<Tag, String>comparing(it -> it.name.toUpperCase())
        .thenComparing(Tag::location)
        .thenComparing(Tag::kind)
        .compare(this, o);
  }
}
