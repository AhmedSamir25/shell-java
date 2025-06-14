import java.io.*;
import java.util.List;

public class InputHandler {

    private final Shell shell;
    private final TabCompletion tabCompletion;

    public InputHandler(Shell shell) {
        this.shell = shell;
        this.tabCompletion = new TabCompletion(shell);
    }

    public String readCommand(BufferedReader reader) throws IOException {
        String tempString = "";
        System.out.print("$ ");
        shell.setHistoryIndex(-1);

        while (true) {
            char c = (char) reader.read();

            if (c == 27) { // ESC sequence
                char next = (char) reader.read();
                if (next == '[') {
                    char arrow = (char) reader.read();
                    if (arrow == 'A') { // Up arrow
                        tempString = handleUpArrow(tempString);
                        continue;
                    } else if (arrow == 'B') { // Down arrow
                        tempString = handleDownArrow(tempString);
                        continue;
                    }
                }
            }

            if (c == '\n' || c == '\r') {
                System.out.print('\n');
                return tempString;
            } else if (c == '\t') {
                String completed = tabCompletion.handleTabCompletion(
                    tempString
                );
                if (!completed.equals(tempString)) {
                    System.out.print("\r$ " + completed);
                    tempString = completed;
                }
            } else if (c == '\b' || c == 127) { // Backspace
                if (tempString.length() > 0) {
                    System.out.print("\b \b");
                    tempString = tempString.substring(
                        0,
                        tempString.length() - 1
                    );
                }
            } else if (c >= 32 && c <= 126) { // Printable characters
                System.out.print(c);
                tempString += c;
            } else if (c == 3) { // Ctrl+C
                System.out.println();
                return "";
            } else if (c == 4) { // Ctrl+D
                shell.setRunning(false);
                return null;
            }
        }
    }

    private String handleUpArrow(String tempString) {
        System.out.print("\r$ " + " ".repeat(tempString.length()) + "\r$ ");

        List<String> history = shell.getCommandHistory();
        if (!history.isEmpty()) {
            int historyIndex = shell.getHistoryIndex();
            if (historyIndex == -1) {
                historyIndex = history.size() - 1;
            } else {
                historyIndex = Math.max(0, historyIndex - 1);
            }
            shell.setHistoryIndex(historyIndex);
            tempString = history.get(historyIndex);
            System.out.print(tempString);
        }
        return tempString;
    }

    private String handleDownArrow(String tempString) {
        System.out.print("\r$ " + " ".repeat(tempString.length()) + "\r$ ");

        List<String> history = shell.getCommandHistory();
        if (!history.isEmpty() && shell.getHistoryIndex() != -1) {
            int historyIndex = shell.getHistoryIndex() + 1;
            if (historyIndex >= history.size()) {
                tempString = "";
                historyIndex = -1;
            } else {
                tempString = history.get(historyIndex);
            }
            shell.setHistoryIndex(historyIndex);
        }
        System.out.print(tempString);
        return tempString;
    }
}
