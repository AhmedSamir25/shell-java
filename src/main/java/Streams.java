import java.io.*;

public record Streams(
    File input,
    File output,
    File err,
    boolean appendOutput,
    boolean appendErr
) {
    public Printer toPrinter(PrintStream defaultOut, PrintStream defaultErr)
        throws IOException {
        PrintStream outStream;
        if (output != null) {
            if (output.getParentFile() != null) {
                output.getParentFile().mkdirs();
            }
            outStream = new PrintStream(
                new FileOutputStream(output, appendOutput)
            );
        } else {
            outStream = defaultOut;
        }

        PrintStream errStream;
        if (err != null) {
            if (err.getParentFile() != null) {
                err.getParentFile().mkdirs();
            }
            errStream = new PrintStream(new FileOutputStream(err, appendErr));
        } else {
            errStream = defaultErr;
        }

        return new Printer(outStream, errStream);
    }
}
