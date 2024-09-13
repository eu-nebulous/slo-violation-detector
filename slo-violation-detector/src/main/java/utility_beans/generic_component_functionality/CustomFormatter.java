package utility_beans.generic_component_functionality;

import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

public class CustomFormatter extends Formatter {
    String format = "%1$te %1$tb %1$tH:%1$tM %4$s %n";
    @Override
    public String format(LogRecord record) {
        return String.format(format, record.getMillis(), record.getLoggerName(), record.getLevel().getName(), record.getMessage());
    }
}
