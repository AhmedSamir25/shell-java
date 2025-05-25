import java.io.File;
import java.io.IOException;
import java.util.*;

public class Main {

    public static void main(String[] args) throws Exception {
        String[] shellType = { "echo", "exit", "type", "pwd", "cd" };
        Scanner scanner = new Scanner(System.in);
        File currentDir = new File(System.getProperty("user.dir"));

        while (true) {
            System.out.print("$ ");
            if (!scanner.hasNextLine()) {
                break;
            }

            String input = scanner.nextLine().trim();
            if (input.isEmpty()) continue;

            List<String> partsList = tokenize(input);
            if (partsList.isEmpty()) continue;

            String command = partsList.get(0);
            List<String> arguments = partsList.subList(1, partsList.size());

            if (input.equals("exit 0")) break;

            if (command.equals("type")) {
                if (arguments.isEmpty()) {
                    System.out.println("Usage: type [command]");
                    continue;
                }
                String targetCommand = arguments.get(0);
                if (Arrays.asList(shellType).contains(targetCommand)) {
                    System.out.println(targetCommand + " is a shell builtin");
                } else {
                    String path = findExecutableInPath(targetCommand);
                    if (path != null) {
                        System.out.println(targetCommand + " is " + path);
                    } else {
                        System.out.println(targetCommand + ": not found");
                    }
                }
                continue;
            }

            if (command.equals("echo")) {
                String result = processEchoArguments(arguments);
                System.out.println(result);
                continue;
            }

            if (command.equals("pwd")) {
                System.out.println(currentDir.getAbsolutePath());
                continue;
            }

            if (command.equals("cd")) {
                if (arguments.isEmpty() || arguments.get(0).equals("~")) {
                    String homePath = System.getenv("HOME");
                    currentDir = new File(homePath);
                    continue;
                }

                String path = String.join(" ", arguments);
                File targetDir = new File(path);

                if (!targetDir.isAbsolute()) {
                    targetDir = new File(currentDir, path);
                }

                if (targetDir.exists() && targetDir.isDirectory()) {
                    currentDir = targetDir.getCanonicalFile();
                } else {
                    System.out.println(path + ": No such file or directory");
                }
                continue;
            }

            String executablePath = findExecutableInPath(command);
            if (executablePath != null) {
                List<String> fullCommand = new ArrayList<>();
                fullCommand.add(command);
                fullCommand.addAll(arguments);

                try {
                    ProcessBuilder builder = new ProcessBuilder(fullCommand);
                    builder.directory(currentDir);
                    builder.redirectErrorStream(true);
                    Process process = builder.start();

                    Scanner outputScanner = new Scanner(process.getInputStream());
                    while (outputScanner.hasNextLine()) {
                        System.out.println(outputScanner.nextLine());
                    }
                    process.waitFor();
                } catch (IOException | InterruptedException e) {
                    System.out.println(command + ": failed to execute");
                }
            } else {
                System.out.println(command + ": command not found");
            }
        }
    }

    private static String findExecutableInPath(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return null;

        String[] paths = pathEnv.split(":");
        for (String dir : paths) {
            File file = new File(dir, command);
            if (file.exists() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }
        return null;
    }

    private static String processEchoArguments(List<String> args) {
        StringBuilder result = new StringBuilder();
        boolean first = true;

        for (String arg : args) {
            if (!first) {
                result.append(" ");
            }
            first = false;

            if ((arg.startsWith("'") && arg.endsWith("'")) ||
                    (arg.startsWith("\"") && arg.endsWith("\""))) {
                arg = arg.substring(1, arg.length() - 1);
            }

            result.append(arg);
        }

        return result.toString();
    }

    public static List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
                continue;
            } else if (c == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
                continue;
            }

            if (c == ' ' && !inSingleQuotes && !inDoubleQuotes) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                while (i + 1 < input.length() && input.charAt(i + 1) == ' ') {
                    i++;
                }
            } else {
                current.append(c);
            }
        }

        if (current.length() > 0) {
            tokens.add(current.toString());
        }

        return tokens;
    }
}