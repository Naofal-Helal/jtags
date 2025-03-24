package xyz.naofal.jtags;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class JtagsLogger {
  public static Logger logger = Logger.getLogger("logger");
  public static Handler loggingHandler = new LogHandler();

  static {
    logger.setUseParentHandlers(false);
    logger.addHandler(loggingHandler);
    String level;
    if ((level = System.getProperty("logger.level")) != null) {
      logger.setLevel(Level.parse(level));
    }
  }

  public static class LogHandler extends Handler {
    @Override
    public void close() {
      flush();
    }

    @Override
    public void flush() {
      System.err.flush();
    }

    public static Map<Level, Integer> colors =
        new HashMap<Level, Integer>() {
          {
            put(Level.FINEST, 5);
            put(Level.FINE, 4);
            put(Level.FINER, 6);
            put(Level.INFO, 2);
            put(Level.WARNING, 3);
            put(Level.SEVERE, 1);
          }
        };

    @Override
    public void publish(LogRecord record) {
      if (record.getMessage() != null && !record.getMessage().isEmpty()) {
        // if (System.console().isTerminal()) {
        int color = colors.get(record.getLevel());
        System.err.printf(
            "\u001b[38;5;%dm[%s]\u001b[0m %s%n",
            color, record.getLevel().getName(), record.getMessage());
        // } else {
        //   System.err.printf("[%s] %s%n", record.getLevel().getName(), record.getMessage());
        // }
      }
      if (record.getThrown() != null) {
        System.err.println(record.getThrown().toString().indent(4));
      }
    }
  }
}
