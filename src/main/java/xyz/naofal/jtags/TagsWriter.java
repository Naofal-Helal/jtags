package xyz.naofal.jtags;

import static xyz.naofal.jtags.JtagsLogger.logger;
import static xyz.naofal.jtags.TagField.EnclosingType;
import static xyz.naofal.jtags.TagField.Package;
import static xyz.naofal.jtags.TagField.StaticTag;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Path;
import java.util.PriorityQueue;
import xyz.naofal.jtags.Jtags.Options;

public record TagsWriter(Options options) {
  private static final int MAX_PATTERN_LENGTH = 96;

  public boolean writeTagsFile(PriorityQueue<Tag> tags) {

    try (var outputStream = new FileOutputStream(options().output.toFile());
        var writer = new OutputStreamWriter(outputStream); ) {

      writer.write(
          """
          !_TAG_FILE_ENCODING\tutf-8\t
          !_TAG_FILE_SORTED\t2\t/0=unsorted, 1=sorted, 2=foldcase/
          """);

      Tag tag;
      while ((tag = tags.poll()) != null) {
        writeTag(writer, tag);
      }

      writer.flush();
    } catch (IOException ex) {
      logger.severe(ex.toString());
      return false;
    }

    return true;
  }

  private void writeTag(Writer writer, Tag tag) throws IOException {
    writer.write(tag.name());
    writer.write('\t');
    writer.write(
        options().absolutePaths
            ? tag.location().toString()
            : Path.of(".").toAbsolutePath().relativize(tag.location()).toString());
    writer.write("\t/^");
    writer.write(tag.line().substring(0, Math.min(tag.line().length(), MAX_PATTERN_LENGTH)));
    writer.write("$/;\"\t");
    writer.write(
        switch (tag.kind()) {
          case PACKAGE -> "Pkg";
          case CLASS -> "Cls";
          case RECORD -> "Rcrd";
          case INTERFACE -> "Intf";
          case ANNOTATION -> "Anno";
          case ENUM -> "Enum";
          case FIELD -> "fld";
          case ENUM_CONSTANT -> "enum";
          case METHOD -> "mthd";
        });

    for (TagField field : tag.fields()) {
      writer.write('\t');
      writer.write(
          switch (field) {
            case StaticTag() -> "file:";
            case Package(var p) -> "package:" + p;
            case EnclosingType(var t, var k) ->
                k.name().toLowerCase() + ":" + (t.isEmpty() ? "(Anonymous)" : t);
          });
    }

    writer.write('\n');
  }
}
