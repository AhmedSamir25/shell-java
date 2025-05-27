import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Main {

    private static Path currentDir;

    public static void main(String[] args) throws Exception {
        String[] shellType = { "echo", "exit", "type", "pwd", "cd" };
        Scanner scanner = new Scanner(System.in);
        currentDir = Paths.get("").toAbsolutePath();

        while (true) {
            System.out.print("$ ");
            if (!scanner.hasNextLine()) {
                break;
            }

            String input = scanner.nextLine().trim();
            if (input.isEmpty()) continue;

            ExtractResult result = extractStreams(tokenize(input));
            List<String> partsList = result.commands();
            Streams streams = result.streams();

            if (partsList.isEmpty()) continue;

            String command = partsList.get(0);
            List<String> arguments = partsList.subList(1, partsList.size());

            if (input.equals("exit 0")) break;

            // Handle exit with any number
            if (command.equals("exit")) {
                int exitCode = 0;
                if (!arguments.isEmpty()) {
                    try {
                        exitCode = Integer.parseInt(arguments.get(0));
                    } catch (NumberFormatException e) {
                        exitCode = 0;
                    }
                }
                System.exit(exitCode);
            }

            Printer printer = streams.toPrinter(System.out, System.err);

            if (command.equals("type")) {
                handleTypeCommand(arguments, shellType, printer);
                continue;
            }

            if (command.equals("echo")) {
                String result_echo = processEchoArguments(arguments);
                printer.out.println(result_echo);
                continue;
            }

            if (command.equals("pwd")) {
                printer.out.println(currentDir.toString());
                continue;
            }

            if (command.equals("cd")) {
                handleCdCommand(arguments, printer);
                continue;
            }

            if (command.equals("cat")) {
                handleCatCommand(arguments, printer);
                continue;
            }

            Optional<Path> executablePath = findExecutableInPath(command);
            if (executablePath.isPresent()) {
                handleExternalCommand(partsList, streams);
            } else {
                printer.err.println(command + ": command not found");
            }
        }
    }

    private static void handleTypeCommand(
        List<String> arguments,
        String[] shellType,
        Printer printer
    ) {
        if (arguments.isEmpty()) {
            printer.out.println("Usage: type [command]");
            return;
        }
        String targetCommand = arguments.get(0);
        if (Arrays.asList(shellType).contains(targetCommand)) {
            printer.out.println(targetCommand + " is a shell builtin");
        } else {
            Optional<Path> path = findExecutableInPath(targetCommand);
            if (path.isPresent()) {
                printer.out.println(
                    targetCommand + " is " + path.get().toString()
                );
            } else {
                printer.out.println(targetCommand + ": not found");
            }
        }
    }

    private static void handleCdCommand(
        List<String> arguments,
        Printer printer
    ) throws IOException {
        if (arguments.isEmpty() || arguments.get(0).equals("~")) {
            String homePath = System.getenv("HOME");
            if (homePath != null) {
                currentDir = Paths.get(homePath);
            }
            return;
        }

        String pathStr = String.join(" ", arguments);
        Path targetDir = Paths.get(pathStr);

        if (!targetDir.isAbsolute()) {
            targetDir = currentDir.resolve(targetDir).normalize();
        }

        if (Files.exists(targetDir) && Files.isDirectory(targetDir)) {
            currentDir = targetDir;
        } else {
            printer.err.println(
                "cd: " + pathStr + ": No such file or directory"
            );
        }
    }

    private static void handleCatCommand(
        List<String> arguments,
        Printer printer
    ) {
        if (arguments.isEmpty()) {
            printer.err.println("cat: missing file operand");
            return;
        }

        for (String fileName : arguments) {
            Path filePath = Paths.get(fileName);
            if (!filePath.isAbsolute()) {
                filePath = currentDir.resolve(filePath);
            }

            try {
                if (!Files.exists(filePath)) {
                    printer.err.println(
                        "cat: " + fileName + ": No such file or directory"
                    );
                    continue;
                }

                String content = Files.readString(filePath);
                printer.out.print(content);
            } catch (IOException e) {
                printer.err.println("cat: " + fileName + ": " + e.getMessage());
            }
        }
    }

    private static void handleExternalCommand(
        List<String> commands,
        Streams streams
    ) {
        try {
            ProcessBuilder builder = new ProcessBuilder(commands);
            builder.directory(currentDir.toFile());
            builder.inheritIO();

            if (streams.output() != null) {
                // Ensure parent directories exist
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
            }
            if (streams.err() != null) {
                // Ensure parent directories exist
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
            }

            Process process = builder.start();
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            System.err.println(commands.get(0) + ": No such file or directory");
        }
    }

    private static Optional<Path> findExecutableInPath(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return Optional.empty();

        String[] paths = pathEnv.split(":");
        for (String dir : paths) {
            Path file = Paths.get(dir, command);
            if (Files.exists(file) && Files.isExecutable(file)) {
                return Optional.of(file);
            }
        }
        return Optional.empty();
    }

    private static String processEchoArguments(List<String> args) {
        return String.join(" ", args);
    }

    public static List<String> tokenize(String input) {
        var result = new ArrayList<String>();
        var currentToken = new StringBuilder();
        Character quote = null;
        boolean backslash = false;
        char[] charArray = input.toCharArray();

        for (int i = 0; i < charArray.length; i++) {
            char c = charArray[i];
            switch (c) {
                case ' ' -> {
                    if (backslash) {
                        currentToken.append(c);
                    } else if (quote == null) {
                        if (!currentToken.isEmpty()) {
                            result.add(currentToken.toString());
                            currentToken = new StringBuilder();
                        }
                    } else {
                        currentToken.append(c);
                    }
                }
                case '\'', '\"' -> {
                    if (backslash) {
                        currentToken.append(c);
                    } else {
                        if (quote == null) {
                            quote = c;
                        } else {
                            if (quote.equals(c)) {
                                quote = null;
                            } else {
                                currentToken.append(c);
                            }
                        }
                    }
                }
                case '\\' -> {
                    if (backslash) {
                        currentToken.append(c);
                    } else {
                        switch (quote) {
                            case '\'' -> currentToken.append(c);
                            case '"' -> {
                                switch (next(charArray, i)) {
                                    case '$', '~', '"', '\\', '\n' -> {
                                        backslash = true;
                                        continue;
                                    }
                                    default -> currentToken.append(c);
                                }
                            }
                            case null -> {
                                backslash = true;
                                continue;
                            }
                            default -> {}
                        }
                    }
                }
                default -> currentToken.append(c);
            }
            if (backslash) {
                backslash = false;
            }
        }
        if (!currentToken.isEmpty()) {
            result.add(currentToken.toString());
        }
        return result;
    }

    private static Character next(char[] charArray, int current) {
        if (current + 1 < charArray.length) {
            return charArray[current + 1];
        } else {
            return null;
        }
    }

    private static ExtractResult extractStreams(List<String> parts) {
        var newCommands = new ArrayList<String>();
        File output = null;
        File err = null;
        String lastRedirection = null;
        boolean appendOutput = false; // For output redirection
        boolean appendErr = false; // For error redirection

        for (String command : parts) {
            if (lastRedirection != null) {
                switch (lastRedirection) {
                    case ">", "1>" -> {
                        output = new File(command);
                        appendOutput = false; // Overwrite mode
                        lastRedirection = null;
                    }
                    case "2>" -> {
                        err = new File(command);
                        appendErr = false; // Overwrite mode for stderr
                        lastRedirection = null;
                    }
                    case ">>", "1>>" -> {
                        output = new File(command);
                        appendOutput = true; // Append mode
                        lastRedirection = null;
                    }
                    case "2>>" -> {
                        err = new File(command);
                        appendErr = true; // Append mode for stderr
                        lastRedirection = null;
                    }
                }
            } else {
                switch (command) {
                    case ">", "1>", "2>", ">>", "1>>", "2>>" -> {
                        lastRedirection = command;
                    }
                    default -> {
                        newCommands.add(command);
                    }
                }
            }
        }

        return new ExtractResult(
            newCommands,
            new Streams(null, output, err, appendOutput, appendErr)
        );
    }

    private static record ExtractResult(
        List<String> commands,
        Streams streams
    ) {}

    private static record Streams(
        File input,
        File output,
        File err,
        boolean appendOutput,
        boolean appendErr
    ) {
        public Printer toPrinter(
            PrintStream defaultOut,
            PrintStream defaultErr
        ) throws IOException {
            PrintStream outStream;
            if (output != null) {
                // Ensure parent directories exist
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
                // Ensure parent directories exist
                if (err.getParentFile() != null) {
                    err.getParentFile().mkdirs();
                }
                errStream = new PrintStream(
                    new FileOutputStream(err, appendErr)
                );
            } else {
                errStream = defaultErr;
            }

            return new Printer(outStream, errStream);
        }
    }

    private static record Printer(PrintStream out, PrintStream err) {}
}
