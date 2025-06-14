import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class BuiltinCommands {

    private final Shell shell;
    private static final String[] SHELL_COMMANDS = {
        "echo",
        "exit",
        "type",
        "pwd",
        "cd",
        "history",
        "cat",
    };

    public BuiltinCommands(Shell shell) {
        this.shell = shell;
    }

    public boolean isBuiltinCommand(String command) {
        return Arrays.asList(SHELL_COMMANDS).contains(command);
    }

    public void executeBuiltin(
        String command,
        List<String> arguments,
        Printer printer
    ) {
        switch (command) {
            case "type" -> handleTypeCommand(arguments, printer);
            case "echo" -> handleEchoCommand(arguments, printer);
            case "pwd" -> handlePwdCommand(printer);
            case "cd" -> handleCdCommand(arguments, printer);
            case "cat" -> handleCatCommand(arguments, printer);
            case "history" -> handleHistoryCommand(arguments, printer);
        }
    }

    private void handleTypeCommand(List<String> arguments, Printer printer) {
        if (arguments.isEmpty()) {
            printer.out().println("Usage: type [command]");
            return;
        }
        String targetCommand = arguments.get(0);
        if (Arrays.asList(SHELL_COMMANDS).contains(targetCommand)) {
            printer.out().println(targetCommand + " is a shell builtin");
        } else {
            Optional<Path> path = findExecutableInPath(targetCommand);
            if (path.isPresent()) {
                printer
                    .out()
                    .println(targetCommand + " is " + path.get().toString());
            } else {
                printer.out().println(targetCommand + ": not found");
            }
        }
    }

    private void handleEchoCommand(List<String> arguments, Printer printer) {
        String result = String.join(" ", arguments);
        printer.out().println(result);
    }

    private void handlePwdCommand(Printer printer) {
        printer.out().println(shell.getCurrentDir().toString());
    }

    private void handleCdCommand(List<String> arguments, Printer printer) {
        try {
            if (arguments.isEmpty() || arguments.get(0).equals("~")) {
                String homePath = System.getenv("HOME");
                if (homePath != null) {
                    shell.setCurrentDir(Paths.get(homePath));
                }
                return;
            }

            String pathStr = String.join(" ", arguments);
            Path targetDir = Paths.get(pathStr);

            if (!targetDir.isAbsolute()) {
                targetDir = shell
                    .getCurrentDir()
                    .resolve(targetDir)
                    .normalize();
            }

            if (Files.exists(targetDir) && Files.isDirectory(targetDir)) {
                shell.setCurrentDir(targetDir);
            } else {
                printer
                    .err()
                    .println("cd: " + pathStr + ": No such file or directory");
            }
        } catch (Exception e) {
            printer.err().println("cd: " + e.getMessage());
        }
    }

    private void handleCatCommand(List<String> arguments, Printer printer) {
        if (arguments.isEmpty()) {
            printer.err().println("cat: missing file operand");
            return;
        }

        for (String fileName : arguments) {
            Path filePath = Paths.get(fileName);
            if (!filePath.isAbsolute()) {
                filePath = shell.getCurrentDir().resolve(filePath);
            }

            try {
                if (!Files.exists(filePath)) {
                    printer
                        .err()
                        .println(
                            "cat: " + fileName + ": No such file or directory"
                        );
                    continue;
                }

                String content = Files.readString(filePath);
                printer.out().print(content);
            } catch (IOException e) {
                printer
                    .err()
                    .println("cat: " + fileName + ": " + e.getMessage());
            }
        }
    }

    private void handleHistoryCommand(List<String> arguments, Printer printer) {
        int limit = shell.getCommandHistory().size();

        if (!arguments.isEmpty()) {
            try {
                limit = Integer.parseInt(arguments.get(0));
            } catch (NumberFormatException e) {
                printer.err().println("history: numeric argument required");
                return;
            }
        }

        int start = Math.max(0, shell.getCommandHistory().size() - limit);
        for (int i = start; i < shell.getCommandHistory().size(); i++) {
            printer
                .out()
                .printf("%5d  %s%n", i + 1, shell.getCommandHistory().get(i));
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
