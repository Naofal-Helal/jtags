package xyz.naofal.jtags;

import static xyz.naofal.jtags.JtagsLogger.logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ArchiveExtractor {
  static List<String> extractArchive(Path archive, Path destination, String pattern) {
    logger.info("Extracting " + archive.toString() + "...");
    List<String> extractedPaths = new ArrayList<>();
    Pattern compiledPattern = Pattern.compile(pattern);
    try (InputStream in = new FileInputStream(archive.toFile());
        ZipInputStream zis = new ZipInputStream(in); ) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        logger.finest("Extracting " + entry.getName() + "...");
        if (!compiledPattern.matcher(entry.getName()).matches()) {
          zis.closeEntry();
          entry = zis.getNextEntry();
          continue;
        }
        Path entryPath = destination.resolve(entry.getName());
        Files.createDirectories(entryPath.getParent());
        logger.finest(() -> "Extracting " + entryPath.toString() + "...");
        var bytes = zis.readAllBytes();
        Files.write(entryPath, bytes);
        extractedPaths.add(entryPath.toString());
        zis.closeEntry();
      }
      return extractedPaths;
    } catch (IOException ex) {
      logger.severe(ex.toString());
      return List.of();
    }
  }
}
