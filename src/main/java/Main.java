import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Main {

    private static Path currentDir;
    private static final String[] SHELL_COMMANDS = { "echo", "exit", "type", "pwd", "cd", };
    private static boolean running = true;
    static String pathEnv = System.getenv("PATH");
    public static void main(String[] args) throws Exception {
        currentDir = Paths.get("").toAbsolutePath();

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "/bin/sh", "-c", "stty -echo -icanon min 1 < /dev/tty");
            processBuilder.directory(new File("").getCanonicalFile());
            Process rawMode = processBuilder.start();
            rawMode.waitFor();

            final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

            while (running) {
                String tempString = "";
                System.out.print("$ ");

                while (true) {
                    char c = (char) reader.read();

                    if (c == '\n' || c == '\r') {
                        System.out.print('\n');
                        if (!tempString.trim().isEmpty()) {
                            parseCommand(tempString.trim());
                        }
                        break;
                    } else if (c == '\t') {
                        String completed = handleTabCompletion(tempString);
                        if (!completed.equals(tempString)) {
                            System.out.print("\r$ " + completed);
                            tempString = completed;
                        }
                    } else if (c == '\b' || c == 127) {
                        if (tempString.length() > 0) {
                            System.out.print("\b \b");
                            tempString = tempString.substring(0, tempString.length() - 1);
                        }
                    } else if (c >= 32 && c <= 126) {
                        System.out.print(c);
                        tempString += c;
                    } else if (c == 3) {
                        System.out.println();
                        break;
                    } else if (c == 4) {
                        running = false;
                        break;
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("خطأ في تهيئة Terminal: " + e.getMessage());
        } finally {
            try {
                ProcessBuilder resetBuilder = new ProcessBuilder(
                        "/bin/sh", "-c", "stty echo icanon < /dev/tty");
                resetBuilder.directory(new File("").getCanonicalFile());
                Process resetProcess = resetBuilder.start();
                resetProcess.waitFor();
            } catch (Exception e) {
            }
        }
    }

    private static String handleTabCompletion(String currentInput) {
        String[] tokens = currentInput.trim().split("\\s+");
        if (tokens.length == 0 || currentInput.trim().isEmpty()) {
            System.out.print("\u0007");
            return currentInput;
        }

        String lastToken = tokens[tokens.length - 1];
        String[] paths = pathEnv.split(":");

        Set<String> matchesSet = new LinkedHashSet<>();

        for (String cmd : SHELL_COMMANDS) {
            if (cmd.startsWith(lastToken)) {
                matchesSet.add(cmd);
            }
        }

        for (String path : paths) {
            File dir = new File(path);
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.getName().startsWith(lastToken)) {
                            matchesSet.add(file.getName());
                        }
                    }
                }
            }
        }

        List<String> matches = new ArrayList<>(matchesSet);
        Collections.sort(matches);
        if (matches.size() == 1) {
            String completion = matches.get(0);
            String prefix = "";
            int lastTokenIndex = currentInput.lastIndexOf(lastToken);
            if (lastTokenIndex > 0) {
                prefix = currentInput.substring(0, lastTokenIndex);
                if (!prefix.endsWith(" ")) prefix += " ";
            }
            return prefix + completion + " ";
        }

        else if (matches.size() > 1) {
            String lcp = longestCommonPrefix(matches);
            if (!lcp.equals(lastToken)) {
                String prefix = "";
                int lastTokenIndex = currentInput.lastIndexOf(lastToken);
                if (lastTokenIndex > 0) {
                    prefix = currentInput.substring(0, lastTokenIndex);
                    if (!prefix.endsWith(" ")) prefix += " ";
                }
                return prefix + lcp;
            } else {
                System.out.print("\u0007");
                System.out.println();
                for (String match : matches) {
                    System.out.print(match + "  ");
                }
                System.out.println();
                System.out.print("$ " + currentInput);
            }
        }

        else {
            System.out.print("\u0007");
        }

        return currentInput;
    }

    private static String longestCommonPrefix(List<String> strings) {
        if (strings == null || strings.isEmpty()) return "";

        String prefix = strings.get(0);
        for (int i = 1; i < strings.size(); i++) {
            while (!strings.get(i).startsWith(prefix)) {
                prefix = prefix.substring(0, prefix.length() - 1);
                if (prefix.isEmpty()) return "";
            }
        }
        return prefix;
    }

    private static void parseCommand(String input) {
        try {
            ExtractResult result = extractStreams(tokenize(input));
            List<String> partsList = result.commands();
            Streams streams = result.streams();

            if (partsList.isEmpty()) return;

            if (partsList.contains("|")) {
                handlePipeline(partsList, streams);
                return;
            }

            String command = partsList.get(0);
            List<String> arguments = partsList.subList(1, partsList.size());

            if (input.equals("exit 0")) {
                running = false;
                return;
            }

            if (command.equals("exit")) {
                int exitCode = 0;
                if (!arguments.isEmpty()) {
                    try {
                        exitCode = Integer.parseInt(arguments.get(0));
                    } catch (NumberFormatException e) {
                        exitCode = 0;
                    }
                }
                running = false;
                System.exit(exitCode);
            }

            Printer printer = streams.toPrinter(System.out, System.err);

            if (command.equals("type")) {
                handleTypeCommand(arguments, printer);
                return;
            }

            if (command.equals("echo")) {
                String result_echo = processEchoArguments(arguments);
                printer.out.println(result_echo);
                return;
            }

            if (command.equals("pwd")) {
                printer.out.println(currentDir.toString());
                return;
            }

            if (command.equals("cd")) {
                handleCdCommand(arguments, printer);
                return;
            }

            if (command.equals("cat")) {
                handleCatCommand(arguments, printer);
                return;
            }

            Optional<Path> executablePath = findExecutableInPath(command);
            if (executablePath.isPresent()) {
                handleExternalCommand(partsList, streams);
            } else {
                printer.err.println(command + ": command not found");
            }

        } catch (Exception e) {
            System.err.println("خطأ في تنفيذ الأمر: " + e.getMessage());
        }
    }

    private static void handleTypeCommand(List<String> arguments, Printer printer) {
        if (arguments.isEmpty()) {
            printer.out.println("Usage: type [command]");
            return;
        }
        String targetCommand = arguments.get(0);
        if (Arrays.asList(SHELL_COMMANDS).contains(targetCommand)) {
            printer.out.println(targetCommand + " is a shell builtin");
        } else {
            Optional<Path> path = findExecutableInPath(targetCommand);
            if (path.isPresent()) {
                printer.out.println(targetCommand + " is " + path.get().toString());
            } else {
                printer.out.println(targetCommand + ": not found");
            }
        }
    }

    private static void handleCdCommand(List<String> arguments, Printer printer) throws IOException {
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
            printer.err.println("cd: " + pathStr + ": No such file or directory");
        }
    }

    private static void handleCatCommand(List<String> arguments, Printer printer) {
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
                    printer.err.println("cat: " + fileName + ": No such file or directory");
                    continue;
                }

                String content = Files.readString(filePath);
                printer.out.print(content);
            } catch (IOException e) {
                printer.err.println("cat: " + fileName + ": " + e.getMessage());
            }
        }
    }

    private static void handleExternalCommand(List<String> commands, Streams streams) {
        try {
            ProcessBuilder builder = new ProcessBuilder(commands);
            builder.directory(currentDir.toFile());

            if (streams.output() != null) {
                if (streams.output().getParentFile() != null) {
                    streams.output().getParentFile().mkdirs();
                }

                if (streams.appendOutput()) {
                    builder.redirectOutput(ProcessBuilder.Redirect.appendTo(streams.output()));
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
                    builder.redirectError(ProcessBuilder.Redirect.appendTo(streams.err()));
                } else {
                    builder.redirectError(streams.err());
                }
            } else {
                builder.redirectError(ProcessBuilder.Redirect.PIPE);
            }

            Process process = builder.start();

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

            process.waitFor();
        } catch (IOException | InterruptedException e) {
            System.err.println(commands.get(0) + ": No such file or directory");
        }
    }

    private static void handlePipeline(List<String> allParts, Streams streams) {
        List<List<String>> commands = new ArrayList<>();
        List<String> current = new ArrayList<>();
        for (String part : allParts) {
            if (part.equals("|")) {
                commands.add(current);
                current = new ArrayList<>();
            } else {
                current.add(part);
            }
        }
        commands.add(current);

        if (commands.size() == 2) {
            List<String> cmd1 = commands.get(0);
            List<String> cmd2 = commands.get(1);
            boolean isBuiltin1 = Arrays.asList(SHELL_COMMANDS).contains(cmd1.get(0));
            boolean isBuiltin2 = Arrays.asList(SHELL_COMMANDS).contains(cmd2.get(0));
            PrintStream originalOut = System.out;
            PrintStream originalErr = System.err;
            InputStream originalIn = System.in;

            try {
                if (isBuiltin1 && isBuiltin2) {
                    PipedOutputStream pipeOut = new PipedOutputStream();
                    PipedInputStream pipeIn = new PipedInputStream(pipeOut);

                    Thread t1 = new Thread(() -> {
                        try {
                            System.setOut(new PrintStream(pipeOut, true));
                            runBuiltin(cmd1, new Printer(System.out, System.err));
                            System.out.flush();
                            pipeOut.close();
                        } catch (Exception ignored) {
                        } finally {
                            System.setOut(originalOut);
                        }
                    });

                    Thread t2 = new Thread(() -> {
                        try {
                            System.setIn(pipeIn);
                            runBuiltin(cmd2, new Printer(originalOut, originalErr));
                        } catch (Exception ignored) {
                        } finally {
                            System.setIn(originalIn);
                        }
                    });

                    t1.start();
                    t2.start();
                    t1.join();
                    t2.join();
                    return;
                } else if (isBuiltin1) {
                    PipedOutputStream pipeOut = new PipedOutputStream();
                    PipedInputStream pipeIn = new PipedInputStream(pipeOut);

                    Thread t1 = new Thread(() -> {
                        try {
                            System.setOut(new PrintStream(pipeOut, true));
                            runBuiltin(cmd1, new Printer(System.out, System.err));
                            System.out.flush();
                            pipeOut.close();
                        } catch (Exception ignored) {
                        } finally {
                            System.setOut(originalOut);
                        }
                    });

                    t1.start();

                    ProcessBuilder pb2 = new ProcessBuilder(cmd2);
                    pb2.directory(currentDir.toFile());
                    pb2.redirectInput(ProcessBuilder.Redirect.PIPE);
                    Process p2 = pb2.start();

                    Thread pipeToProcess = new Thread(() -> {
                        try (OutputStream out = p2.getOutputStream()) {
                            pipeIn.transferTo(out);
                        } catch (Exception ignored) {}
                    });
                    pipeToProcess.start();

                    Thread p2ErrThread = new Thread(() -> {
                        try (InputStream err = p2.getErrorStream()) {
                            err.transferTo(System.err);
                        } catch (Exception ignored) {}
                    });
                    p2ErrThread.start();

                    try (InputStream out = p2.getInputStream()) {
                        out.transferTo(System.out);
                    }

                    t1.join();
                    pipeToProcess.join();
                    p2.waitFor();
                    p2ErrThread.join();
                    return;
                } else if (isBuiltin2) {
                    ProcessBuilder pb1 = new ProcessBuilder(cmd1);
                    pb1.directory(currentDir.toFile());
                    pb1.redirectOutput(ProcessBuilder.Redirect.PIPE);
                    Process p1 = pb1.start();

                    PipedOutputStream pipeOut = new PipedOutputStream();
                    PipedInputStream pipeIn = new PipedInputStream(pipeOut);

                    Thread processToPipe = new Thread(() -> {
                        try (InputStream in = p1.getInputStream()) {
                            in.transferTo(pipeOut);
                            pipeOut.close();
                        } catch (Exception ignored) {}
                    });
                    processToPipe.start();

                    Thread p1ErrThread = new Thread(() -> {
                        try (InputStream err = p1.getErrorStream()) {
                            err.transferTo(System.err);
                        } catch (Exception ignored) {}
                    });
                    p1ErrThread.start();

                    Thread t2 = new Thread(() -> {
                        try {
                            System.setIn(pipeIn);
                            runBuiltin(cmd2, new Printer(originalOut, originalErr));
                        } catch (Exception ignored) {
                        } finally {
                            System.setIn(originalIn);
                        }
                    });
                    t2.start();

                    processToPipe.join();
                    t2.join();
                    p1.waitFor();
                    p1ErrThread.join();
                    return;
                } else {
                    ProcessBuilder pb1 = new ProcessBuilder(cmd1);
                    pb1.directory(currentDir.toFile());
                    ProcessBuilder pb2 = new ProcessBuilder(cmd2);
                    pb2.directory(currentDir.toFile());
                    List<Process> processes = ProcessBuilder.startPipeline(List.of(pb1, pb2));
                    Process p1 = processes.get(0);
                    Process p2 = processes.get(1);

                    Thread p1ErrThread = new Thread(() -> {
                        try (InputStream err = p1.getErrorStream()) {
                            err.transferTo(System.err);
                        } catch (Exception ignored) {}
                    });
                    p1ErrThread.start();

                    Thread p2ErrThread = new Thread(() -> {
                        try (InputStream err = p2.getErrorStream()) {
                            err.transferTo(System.err);
                        } catch (Exception ignored) {}
                    });
                    p2ErrThread.start();

                    try (InputStream out = p2.getInputStream()) {
                        out.transferTo(System.out);
                    }

                    p1ErrThread.join();
                    p2ErrThread.join();
                    p1.waitFor();
                    p2.waitFor();
                    return;
                }
            } catch (Exception e) {
                System.err.println("Pipeline error: " + e.getMessage());
            }
        } else {
        }
    }

    private static void runBuiltin(List<String> cmd, Printer printer) {
        String command = cmd.get(0);
        List<String> arguments = cmd.size() > 1 ? cmd.subList(1, cmd.size()) : Collections.emptyList();

        if (command.equals("type")) {
            handleTypeCommand(arguments, printer);
        } else if (command.equals("echo")) {
            String result_echo = processEchoArguments(arguments);
            printer.out.println(result_echo);
        } else if (command.equals("pwd")) {
            printer.out.println(currentDir.toString());
        } else if (command.equals("cd")) {
            try {
                handleCdCommand(arguments, printer);
            } catch (IOException e) {
                printer.err.println("cd: " + e.getMessage());
            }
        } else if (command.equals("cat")) {
            handleCatCommand(arguments, printer);
        }
    }

    private static Optional<Path> findExecutableInPath(String command) {

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
                case '|' -> {
                    if (backslash) {
                        currentToken.append(c);
                    } else if (quote == null) {
                        if (!currentToken.isEmpty()) {
                            result.add(currentToken.toString());
                            currentToken = new StringBuilder();
                        }
                        result.add("|");
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
        boolean appendOutput = false;
        boolean appendErr = false;

        for (String command : parts) {
            if (lastRedirection != null) {
                switch (lastRedirection) {
                    case ">", "1>" -> {
                        output = new File(command);
                        appendOutput = false;
                        lastRedirection = null;
                    }
                    case "2>" -> {
                        err = new File(command);
                        appendErr = false;
                        lastRedirection = null;
                    }
                    case ">>", "1>>" -> {
                        output = new File(command);
                        appendOutput = true;
                        lastRedirection = null;
                    }
                    case "2>>" -> {
                        err = new File(command);
                        appendErr = true;
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

    private static record ExtractResult(List<String> commands, Streams streams) {}

    private static record Streams(
            File input,
            File output,
            File err,
            boolean appendOutput,
            boolean appendErr
    ) {
        public Printer toPrinter(PrintStream defaultOut, PrintStream defaultErr) throws IOException {
            PrintStream outStream;
            if (output != null) {
                if (output.getParentFile() != null) {
                    output.getParentFile().mkdirs();
                }
                outStream = new PrintStream(new FileOutputStream(output, appendOutput));
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

    private static record Printer(PrintStream out, PrintStream err) {}
}