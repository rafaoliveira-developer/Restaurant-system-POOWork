package atlas.impressao.adaptadores;

import atlas.impressao.interfaces.Printer;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

public final class SimulatedPrinter implements Printer {
    private final Path directory;

    public SimulatedPrinter(Path directory) { this.directory = directory; }

    @Override public void print(UUID id, String content) throws IOException, UncertainPrintException {
        Files.createDirectories(directory);
        Path output = directory.resolve(id + ".txt");
        Path temporary = Files.createTempFile(directory,id.toString(),".tmp");
        try {
            Files.writeString(temporary,content);
            try { Files.move(temporary,output); }
            catch (FileAlreadyExistsException ex) {
                throw new UncertainPrintException("Já existe um comprovante com este identificador.",ex);
            }
        } finally { Files.deleteIfExists(temporary); }
    }
}
