import java.io.File;
import java.io.IOException;
import java.util.*;

public class Main {

    public static void main(String[] args) throws Exception {
        String[] shellType = {"echo", "exit", "type"};
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");
            if (!scanner.hasNextLine()) {
                break;
            }

            String input = scanner.nextLine().trim();
            if (input.isEmpty()) continue;

            String[] parts = input.split(" ");
            String command = parts[0];
            String[] arguments = Arrays.copyOfRange(parts, 1, parts.length);

            if (input.equals("exit 0")) {
                break;
            }

            if (command.equals("type")) {
                if (arguments.length == 0) {
                    System.out.println("Usage: type [command]");
                    continue;
                }
                String targetCommand = arguments[0];
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
                System.out.println(String.join(" ", arguments));
                continue;
            }

            String executablePath = findExecutableInPath(command);
            if (executablePath != null) {
                List<String> fullCommand = new ArrayList<>();
                fullCommand.add(executablePath);
                fullCommand.addAll(Arrays.asList(arguments));

                try {
                    ProcessBuilder builder = new ProcessBuilder(fullCommand);
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
}
