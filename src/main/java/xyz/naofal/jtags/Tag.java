package xyz.naofal.jtags;

public record Tag(TagKind kind, String name, String location, String line)
    implements Comparable<Tag> {

  @Override
  public int compareTo(Tag o) {
    return String.CASE_INSENSITIVE_ORDER.compare(name, o.name());
  }
}
