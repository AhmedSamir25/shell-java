import java.io.File;
import java.io.FileWriter;
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

            boolean isRedirect = input.contains(">") || input.contains("1>");
            boolean isErrorRedirect = input.contains("2>");
            String outputFileName = null;
            String errorFileName = null;
            String commandWithoutRedirect = input;

            if (isRedirect || isErrorRedirect) {
                String[] parts = splitCommandAndFile(input);
                commandWithoutRedirect = parts[0];
                outputFileName = parts[1];
                errorFileName = parts[2];
            }

            List<String> partsList = tokenize(commandWithoutRedirect);
            if (partsList.isEmpty()) continue;

            String command = partsList.get(0);
            List<String> arguments = partsList.subList(1, partsList.size());

            if (input.equals("exit 0")) break;

            if (command.equals("type")) {
                handleTypeCommand(arguments, shellType);
                continue;
            }

            if (command.equals("echo")) {
                String result = processEchoArguments(arguments);
                if (isRedirect && outputFileName != null) {
                    writeToFile(outputFileName, result);
                } else {
                    System.out.println(result);
                }
                continue;
            }

            if (command.equals("pwd")) {
                String pwdResult = currentDir.getAbsolutePath();
                if (isRedirect && outputFileName != null) {
                    writeToFile(outputFileName, pwdResult);
                } else {
                    System.out.println(pwdResult);
                }
                continue;
            }

            if (command.equals("cd")) {
                handleCdCommand(arguments, currentDir);
                continue;
            }

            String executablePath = findExecutableInPath(command);
            if (executablePath != null) {
                handleExternalCommand(
                    command,
                    arguments,
                    currentDir,
                    isRedirect,
                    outputFileName,
                    isErrorRedirect,
                    errorFileName
                );
            } else {
                System.out.println(command + ": command not found");
            }
        }
    }

    private static String[] splitCommandAndFile(String input) {
        String outputFile = null;
        String errorFile = null;
        String command = input;

        int errorIndex = input.indexOf("2>");
        if (errorIndex != -1) {
            command = input.substring(0, errorIndex).trim();
            errorFile = input.substring(errorIndex + 2).trim();
        }

        int outputIndex = input.indexOf("1>");
        if (outputIndex == -1) {
            outputIndex = input.indexOf(">");
            if (outputIndex != -1) {
                command = input.substring(0, outputIndex).trim();
                outputFile = input.substring(outputIndex + 1).trim();
            }
        } else {
            command = input.substring(0, outputIndex).trim();
            outputFile = input.substring(outputIndex + 2).trim();
        }

        return new String[] { command, outputFile, errorFile };
    }

    private static void handleTypeCommand(
        List<String> arguments,
        String[] shellType
    ) {
        if (arguments.isEmpty()) {
            System.out.println("Usage: type [command]");
            return;
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
    }

    private static void handleCdCommand(
        List<String> arguments,
        File currentDir
    ) throws IOException {
        if (arguments.isEmpty() || arguments.get(0).equals("~")) {
            String homePath = System.getenv("HOME");
            currentDir = new File(homePath);
            return;
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
    }

    private static void handleExternalCommand(
        String command,
        List<String> arguments,
        File currentDir,
        boolean isRedirect,
        String outputFileName,
        boolean isErrorRedirect,
        String errorFileName
    ) {
        List<String> fullCommand = new ArrayList<>();
        fullCommand.add(command);
        fullCommand.addAll(arguments);

        try {
            ProcessBuilder builder = new ProcessBuilder(fullCommand);
            builder.directory(currentDir);

            if (isRedirect || isErrorRedirect) {
                if (isRedirect && outputFileName != null) {
                    builder.redirectOutput(new File(outputFileName));
                }
                if (isErrorRedirect && errorFileName != null) {
                    builder.redirectError(new File(errorFileName));
                } else if (isRedirect && outputFileName != null) {
                    builder.redirectErrorStream(true);
                }
            } else {
                builder.redirectErrorStream(true);
            }

            Process process = builder.start();

            if (!isRedirect && !isErrorRedirect) {
                Scanner outputScanner = new Scanner(process.getInputStream());
                while (outputScanner.hasNextLine()) {
                    System.out.println(outputScanner.nextLine());
                }
            }

            process.waitFor();
        } catch (IOException | InterruptedException e) {
            System.out.println(command + ": failed to execute");
        }
    }

    private static void writeToFile(String fileName, String content) {
        try {
            FileWriter writer = new FileWriter(fileName);
            writer.write(content);
            writer.close();
        } catch (IOException e) {
            System.out.println("Error writing to file: " + e.getMessage());
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

            if (c == '\\' && i < input.length() - 1) {
                if (!inSingleQuotes) {
                    char nextChar = input.charAt(i + 1);
                    if (inDoubleQuotes) {
                        if (
                            nextChar == '"' ||
                            nextChar == '\\' ||
                            nextChar == '$' ||
                            nextChar == '`'
                        ) {
                            current.append(nextChar);
                            i++;
                            continue;
                        } else {
                            current.append(c);
                        }
                    } else {
                        current.append(nextChar);
                        i++;
                        continue;
                    }
                } else {
                    current.append(c);
                }
                continue;
            }

            if (c == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
                continue;
            }

            if (c == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
                continue;
            }

            if (c == ' ' && !inSingleQuotes && !inDoubleQuotes) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }

            current.append(c);
        }

        if (current.length() > 0) {
            tokens.add(current.toString());
        }

        return tokens;
    }
}
