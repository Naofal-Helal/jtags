package xyz.naofal.jtags;

import static xyz.naofal.jtags.JtagsLogger.logger;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
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
            ? tag.location().toAbsolutePath().toString()
            : tag.location().toString());
    writer.write("\t/^");
    writer.write(tag.line().substring(0, Math.min(tag.line().length(), MAX_PATTERN_LENGTH)));
    writer.write("$/;\"\t");
    writer.write(
        switch (tag.kind()) {
          case PACKAGE -> 'P';
          case CLASS -> 'C';
          case RECORD -> 'R';
          case INTERFACE -> 'I';
          case ANNOTATION -> 'A';
          case ENUM -> 'E';
          case FIELD -> 'f';
          case ENUM_CONSTANT -> 'e';
          case METHOD -> 'm';
        });
    if (tag.isStatic()) {
      writer.write("\tfile:");
    }

    writer.write('\n');
  }
}
