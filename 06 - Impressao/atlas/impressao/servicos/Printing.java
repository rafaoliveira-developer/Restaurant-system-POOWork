package atlas.impressao.servicos;

import atlas.impressao.adaptadores.*;
import atlas.impressao.interfaces.Printer;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

public final class Printing {
    private final Printer adapter;
    public record Result(String status, String error) {}

    public Printing(Printer adapter) { this.adapter = adapter; }

    public Result submit(UUID id, String content) {
        try {
            adapter.print(id,content);
            return new Result("IMPRESSO","");
        } catch (Printer.UncertainPrintException | RuntimeException ex) {
            return new Result("INCERTO",message(ex));
        } catch (IOException ex) {
            return new Result("FALHA",message(ex));
        }
    }

    private static String message(Exception ex) {
        String text = ex.getMessage();
        if (text == null || text.isBlank()) return "Falha do adaptador de impressão.";
        return text.substring(0,Math.min(300,text.length()));
    }

    public static Printing configured(String mode, String queue, Path output) {
        return switch (mode) {
            case "simulado" -> new Printing(new SimulatedPrinter(output));
            case "sistema" -> new Printing(new SystemPrinter(queue));
            default -> throw new IllegalArgumentException("Modo de impressão inválido: " + mode);
        };
    }
}
