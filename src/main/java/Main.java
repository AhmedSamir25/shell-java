import java.util.Scanner;
import java.util.StringJoiner;
import java.util.Arrays;
import java.io.File;

public class Main {

    public static void main(String[] args) throws Exception {
        String[] shellType = {"echo", "exit","type"};
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");
            if (!scanner.hasNextLine()) {
                break;
            }

            String input = scanner.nextLine();

            if (input.equals("exit 0")) {
                break;
            }

            if (input.startsWith("type ")) {
                String[] words = input.split(" ");
                if (words.length > 1) {
                    String command = words[1];
                    if (Arrays.asList(shellType).contains(command)) {
                        System.out.println(command + " is a shell builtin");
                    } else {
                        String pathEnv = System.getenv("PATH");
                        String[] paths = pathEnv.split(":");
                        boolean found = false;

                        for (String path : paths) {
                            File file = new File(path, command);
                            if (file.exists() && file.canExecute()) {
                                System.out.println(command + " is " + file.getAbsolutePath());
                                found = true;
                                break;
                            }
                        }

                        if (!found) {
                            System.out.println(command + ": not found");
                        }
                    }
                } else {
                    System.out.println("Usage: type [command]");
                }
            } else if (input.startsWith("echo ")) {
                System.out.println(input.substring(5));
            } else {
                System.out.println(input + ": command not found");
            }
        }
    }
}
