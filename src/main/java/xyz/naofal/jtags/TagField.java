package xyz.naofal.jtags;

public sealed interface TagField {
  public record StaticTag() implements TagField {}

  public record Package(String p) implements TagField {}

  public record EnclosingType(String type, TagKind kind) implements TagField {}
}
