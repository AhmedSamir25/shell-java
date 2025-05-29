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
                            // مسح السطر الحالي وإعادة كتابة النص المكتمل
                            System.out.print("\r$ " + completed);
                            tempString = completed;
                        }
                    } else if (c == '\b' || c == 127) { // Backspace
                        if (tempString.length() > 0) {
                            System.out.print("\b \b");
                            tempString = tempString.substring(0, tempString.length() - 1);
                        }
                    } else if (c >= 32 && c <= 126) { // الأحرف المرئية فقط
                        System.out.print(c);
                        tempString += c;
                    } else if (c == 3) { // Ctrl+C
                        System.out.println();
                        break;
                    } else if (c == 4) { // Ctrl+D
                        running = false;
                        break;
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("خطأ في تهيئة Terminal: " + e.getMessage());
        } finally {
            // إعادة تعيين Terminal إلى الوضع العادي
            try {
                ProcessBuilder resetBuilder = new ProcessBuilder(
                        "/bin/sh", "-c", "stty echo icanon < /dev/tty");
                resetBuilder.directory(new File("").getCanonicalFile());
                Process resetProcess = resetBuilder.start();
                resetProcess.waitFor();
            } catch (Exception e) {
                // تجاهل الأخطاء عند إعادة التعيين
            }
        }
    }

    private static String handleTabCompletion(String currentInput) {
        String[] tokens = currentInput.trim().split("\\s+");
        if (tokens.length == 0 || currentInput.trim().isEmpty()) {
            System.out.print("\u0007"); // Bell sound
            return currentInput;
        }

        String lastToken = tokens[tokens.length - 1];
        String[] paths = pathEnv.split(":");

        // استخدام Set لإزالة التكرارات
        Set<String> matchesSet = new LinkedHashSet<>();

        // البحث في الأوامر المدمجة
        for (String cmd : SHELL_COMMANDS) {
            if (cmd.startsWith(lastToken)) {
                matchesSet.add(cmd);
            }
        }

        // البحث في ملفات PATH
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

        // إكمال مباشر لو فيه تطابق واحد
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

        // عرض الخيارات لو فيه أكتر من تطابق
//        else if (matches.size() > 1) {
//            System.out.println();
//            System.out.println("الخيارات المتاحة:");
//            for (String match : matches) {
//                System.out.print(match + "  ");
//            }
//            System.out.println();
//            System.out.print("$ " + currentInput); // إعادة عرض السطر
//        }
        else {
            System.out.print("\u0007"); // Bell sound
        }

        return currentInput;
    }


    private static void parseCommand(String input) {
        try {
            ExtractResult result = extractStreams(tokenize(input));
            List<String> partsList = result.commands();
            Streams streams = result.streams();

            if (partsList.isEmpty()) return;

            String command = partsList.get(0);
            List<String> arguments = partsList.subList(1, partsList.size());

            if (input.equals("exit 0")) {
                running = false;
                return;
            }

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
            builder.inheritIO();

            if (streams.output() != null) {
                if (streams.output().getParentFile() != null) {
                    streams.output().getParentFile().mkdirs();
                }

                if (streams.appendOutput()) {
                    builder.redirectOutput(ProcessBuilder.Redirect.appendTo(streams.output()));
                } else {
                    builder.redirectOutput(streams.output());
                }
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
            }

            Process process = builder.start();
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            System.err.println(commands.get(0) + ": No such file or directory");
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