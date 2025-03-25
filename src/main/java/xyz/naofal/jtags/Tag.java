package xyz.naofal.jtags;

public record Tag(TagKind kind, String name, String location, String line, boolean isStatic)
    implements Comparable<Tag> {

  public Tag(TagKind kind, String name, String location, String line) {
    this(kind, name, location, line, false);
  }

  @Override
  public int compareTo(Tag o) {
    return String.CASE_INSENSITIVE_ORDER.compare(name, o.name());
  }
}
