package atlas.impressao.adaptadores;

import atlas.impressao.interfaces.Printer;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.print.*;
import java.io.IOException;
import java.text.AttributedString;
import java.util.*;
import javax.print.PrintService;

public final class SystemPrinter implements Printer {
    private final String name;

    public SystemPrinter(String name) { this.name = name; }

    @Override public void print(UUID id, String content) throws IOException, UncertainPrintException {
        PrintService service = Arrays.stream(PrinterJob.lookupPrintServices())
                .filter(printer -> printer.getName().equals(name)).findFirst()
                .orElseThrow(() -> new IOException("Fila de impressão não encontrada: " + name));
        PrinterJob job = PrinterJob.getPrinterJob();
        try { job.setPrintService(service); }
        catch (PrinterException ex) { throw new IOException("O driver rejeitou a fila selecionada.",ex); }
        job.setJobName("ATLAS " + id);
        job.setPrintable((graphics,page,index) -> draw((Graphics2D)graphics,page,index,content));
        try { job.print(); }
        catch (PrinterException ex) { throw new UncertainPrintException("Confira a fila e o papel após a falha de envio.",ex); }
    }

    private static int draw(Graphics2D g, PageFormat page, int index, String content) {
        Font font = new Font(Font.MONOSPACED,Font.PLAIN,10);
        g.setFont(font);
        FontRenderContext context = g.getFontRenderContext();
        var lines = new ArrayList<java.awt.font.TextLayout>();
        float width = (float)page.getImageableWidth();
        int perPage = (int)(page.getImageableHeight()/14);
        if (width < 10 || perPage < 1) return Printable.NO_SUCH_PAGE;
        for (String line : content.split("\\R",-1)) {
            AttributedString text = new AttributedString(line.isEmpty() ? " " : line);
            text.addAttribute(TextAttribute.FONT,font);
            var iterator = text.getIterator();
            var measurer = new LineBreakMeasurer(iterator,context);
            while (measurer.getPosition() < iterator.getEndIndex()) lines.add(measurer.nextLayout(width));
        }
        int start = index * perPage;
        if (start >= lines.size()) return Printable.NO_SUCH_PAGE;
        for (int i = start; i < Math.min(lines.size(),start+perPage); i++)
            lines.get(i).draw(g,(float)page.getImageableX(),(float)page.getImageableY()+11+14*(i-start));
        return Printable.PAGE_EXISTS;
    }
}
