import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class Main {

    private static File currentDir;

    public static void main(String[] args) throws Exception {
        String[] shellType = { "echo", "exit", "type", "pwd", "cd", "cat" };
        Scanner scanner = new Scanner(System.in);
        currentDir = new File(System.getProperty("user.dir"));

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

                if (isErrorRedirect && errorFileName != null) {
                    readToFile(errorFileName);
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

                if (isErrorRedirect && errorFileName != null) {}
                continue;
            }

            if (command.equals("cd")) {
                handleCdCommand(arguments);
                continue;
            }

            String executablePath = findExecutableInPath(command);
            if (executablePath != null) {
                handleExternalCommand(
                    command,
                    arguments,
                    isRedirect,
                    outputFileName,
                    isErrorRedirect,
                    errorFileName
                );
            } else {
                String errorMsg = command + ": command not found";
                if (isErrorRedirect && errorFileName != null) {
                    writeToFile(errorFileName, errorMsg);
                } else {
                    System.err.println(errorMsg);
                }
            }
        }
    }

    private static String[] splitCommandAndFile(String input) {
        String outputFile = null;
        String errorFile = null;
        String command = input;

        // Handle 2> first (stderr redirection)
        int errorIndex = input.indexOf("2>");
        if (errorIndex != -1) {
            String beforeError = input.substring(0, errorIndex).trim();
            String afterError = input.substring(errorIndex + 2).trim();

            // Extract error file name (first word after 2>)
            String[] errorParts = afterError.split("\\s+", 2);
            errorFile = errorParts[0];

            // Reconstruct command without 2> part
            command = beforeError;
            if (errorParts.length > 1) {
                command += " " + errorParts[1];
            }
        }

        // Handle 1> or > (stdout redirection)
        int outputIndex = command.indexOf("1>");
        if (outputIndex == -1) {
            outputIndex = command.indexOf(">");
            if (outputIndex != -1) {
                String beforeOutput = command.substring(0, outputIndex).trim();
                String afterOutput = command.substring(outputIndex + 1).trim();

                // Extract output file name (first word after >)
                String[] outputParts = afterOutput.split("\\s+", 2);
                outputFile = outputParts[0];

                // Reconstruct command without > part
                command = beforeOutput;
                if (outputParts.length > 1) {
                    command += " " + outputParts[1];
                }
            }
        } else {
            String beforeOutput = command.substring(0, outputIndex).trim();
            String afterOutput = command.substring(outputIndex + 2).trim();

            // Extract output file name (first word after 1>)
            String[] outputParts = afterOutput.split("\\s+", 2);
            outputFile = outputParts[0];

            // Reconstruct command without 1> part
            command = beforeOutput;
            if (outputParts.length > 1) {
                command += " " + outputParts[1];
            }
        }

        return new String[] { command.trim(), outputFile, errorFile };
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

    private static void handleCdCommand(List<String> arguments)
        throws IOException {
        if (arguments.isEmpty() || arguments.get(0).equals("~")) {
            String homePath = System.getenv("HOME");
            if (homePath != null) {
                currentDir = new File(homePath);
            }
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
            System.err.println("cd: " + path + ": No such file or directory");
        }
    }

    private static void handleExternalCommand(
        String command,
        List<String> arguments,
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

            // Handle stdout redirection
            if (isRedirect && outputFileName != null) {
                builder.redirectOutput(new File(outputFileName));
            }

            // Handle stderr redirection
            if (isErrorRedirect && errorFileName != null) {
                builder.redirectError(new File(errorFileName));
            }

            // Only redirect error stream to output if we're not doing explicit redirection
            if (!isRedirect && !isErrorRedirect) {
                builder.redirectErrorStream(true);
            }

            Process process = builder.start();

            // Read stdout if not redirected
            if (!isRedirect) {
                Scanner outputScanner = new Scanner(process.getInputStream());
                while (outputScanner.hasNextLine()) {
                    System.out.println(outputScanner.nextLine());
                }
                outputScanner.close();
            }

            // Read stderr if not redirected but stdout is redirected
            if (!isErrorRedirect && isRedirect) {
                Scanner errorScanner = new Scanner(process.getErrorStream());
                while (errorScanner.hasNextLine()) {
                    String errorLine = errorScanner.nextLine();
                    // Transform error message format
                    errorLine = transformErrorMessage(command, errorLine);
                    System.err.println(errorLine);
                }
                errorScanner.close();
            }

            // Read and transform stderr if redirected
            if (isErrorRedirect && errorFileName != null && !isRedirect) {
                Scanner errorScanner = new Scanner(process.getErrorStream());
                StringBuilder errorContent = new StringBuilder();
                while (errorScanner.hasNextLine()) {
                    String errorLine = errorScanner.nextLine();
                    // Transform error message format
                    errorLine = transformErrorMessage(command, errorLine);
                    if (errorContent.length() > 0) {
                        errorContent.append("\n");
                    }
                    errorContent.append(errorLine);
                }
                errorScanner.close();

                if (errorContent.length() > 0) {
                    writeToFile(errorFileName, errorContent.toString());
                }
            }

            process.waitFor();
        } catch (IOException | InterruptedException e) {
            String errorMsg = command + ": No such file or directory";
            if (isErrorRedirect && errorFileName != null) {
                writeToFile(errorFileName, errorMsg);
            } else {
                System.err.println(errorMsg);
            }
        }
    }

    private static String transformErrorMessage(
        String command,
        String errorLine
    ) {
        // Transform "ls: cannot access 'filename': No such file or directory"
        // to "ls: filename: No such file or directory"
        if (errorLine.contains("cannot access")) {
            String pattern = command + ": cannot access '([^']+)': (.+)";
            if (errorLine.matches(pattern)) {
                String filename = errorLine.replaceAll(pattern, "$1");
                String errorType = errorLine.replaceAll(pattern, "$2");
                return command + ": " + filename + ": " + errorType;
            }
        }
        return errorLine;
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

    private static void readToFile(String fileName) {
        try {
            System.out.println("this is file name" + fileName);
            File myObj = new File(fileName);
            Scanner myReader = new Scanner(myObj);
            while (myReader.hasNextLine()) {
                String data = myReader.nextLine();
                System.out.println(data);
            }
            myReader.close();
        } catch (FileNotFoundException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
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
