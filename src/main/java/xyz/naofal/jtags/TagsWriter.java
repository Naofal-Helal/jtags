package xyz.naofal.jtags;

import static xyz.naofal.jtags.JtagsLogger.logger;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.PriorityQueue;

public class TagsWriter {
  private static final int MAX_PATTERN_LENGTH = 96;

  public static void writeTagsFile(PriorityQueue<Tag> tags, OutputStream outputStream) {
    var writer = new OutputStreamWriter(outputStream);
    try {
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
    }
  }

  private static void writeTag(Writer writer, Tag tag) throws IOException {
    writer.write(tag.name());
    writer.write('\t');
    writer.write(tag.location());
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
