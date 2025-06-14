import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

public class ExternalCommands {

    private final Shell shell;

    public ExternalCommands(Shell shell) {
        this.shell = shell;
    }

    public void executeExternal(List<String> commands, Streams streams) {
        Optional<Path> executablePath = findExecutableInPath(commands.get(0));
        if (executablePath.isPresent()) {
            handleExternalCommand(commands, streams);
        } else {
            System.err.println(commands.get(0) + ": command not found");
        }
    }

    private void handleExternalCommand(List<String> commands, Streams streams) {
        try {
            ProcessBuilder builder = new ProcessBuilder(commands);
            builder.directory(shell.getCurrentDir().toFile());

            setupRedirection(builder, streams);

            Process process = builder.start();
            handleProcessOutput(process, streams);
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            System.err.println(commands.get(0) + ": No such file or directory");
        }
    }

    private void setupRedirection(ProcessBuilder builder, Streams streams) {
        if (streams.output() != null) {
            if (streams.output().getParentFile() != null) {
                streams.output().getParentFile().mkdirs();
            }

            if (streams.appendOutput()) {
                builder.redirectOutput(
                    ProcessBuilder.Redirect.appendTo(streams.output())
                );
            } else {
                builder.redirectOutput(streams.output());
            }
        } else {
            builder.redirectOutput(ProcessBuilder.Redirect.PIPE);
        }

        if (streams.err() != null) {
            if (streams.err().getParentFile() != null) {
                streams.err().getParentFile().mkdirs();
            }

            if (streams.appendErr()) {
                builder.redirectError(
                    ProcessBuilder.Redirect.appendTo(streams.err())
                );
            } else {
                builder.redirectError(streams.err());
            }
        } else {
            builder.redirectError(ProcessBuilder.Redirect.PIPE);
        }
    }

    private void handleProcessOutput(Process process, Streams streams)
        throws IOException {
        if (streams.output() == null || streams.err() == null) {
            try (InputStream in = process.getInputStream()) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    System.out.write(buffer, 0, len);
                }
            }
            try (InputStream err = process.getErrorStream()) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = err.read(buffer)) != -1) {
                    System.err.write(buffer, 0, len);
                }
            }
        }
    }

    private Optional<Path> findExecutableInPath(String command) {
        if (shell.getPathEnv() == null) return Optional.empty();

        String[] paths = shell.getPathEnv().split(":");
        for (String dir : paths) {
            Path file = Paths.get(dir, command);
            if (Files.exists(file) && Files.isExecutable(file)) {
                return Optional.of(file);
            }
        }
        return Optional.empty();
    }
}
