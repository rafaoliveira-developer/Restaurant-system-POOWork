package atlas.impressao.interfaces;

import java.io.IOException;
import java.util.UUID;

public interface Printer {
    void print(UUID id, String content) throws IOException, UncertainPrintException;

    final class UncertainPrintException extends Exception {
        public UncertainPrintException(String message, Throwable cause) { super(message,cause); }
        public UncertainPrintException(String message) { super(message); }
    }
}
