import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Shell {

    private Path currentDir;
    private boolean running = true;
    private final String pathEnv = System.getenv("PATH");
    private final List<String> commandHistory = new ArrayList<>();
    private int historyIndex = -1;
    private final CommandExecutor commandExecutor;
    private final InputHandler inputHandler;

    public Shell() {
        this.currentDir = Paths.get("").toAbsolutePath();
        this.commandExecutor = new CommandExecutor(this);
        this.inputHandler = new InputHandler(this);
    }

    public void run() throws Exception {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                "/bin/sh",
                "-c",
                "stty -echo -icanon min 1 < /dev/tty"
            );
            processBuilder.directory(new File("").getCanonicalFile());
            Process rawMode = processBuilder.start();
            rawMode.waitFor();

            final BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in)
            );

            while (running) {
                String command = inputHandler.readCommand(reader);
                if (command != null && !command.trim().isEmpty()) {
                    commandExecutor.executeCommand(command.trim());
                }
            }
        } catch (Exception e) {
            System.err.println(
                "Configuration error Terminal: " + e.getMessage()
            );
        } finally {
            resetTerminal();
        }
    }

    private void resetTerminal() {
        try {
            ProcessBuilder resetBuilder = new ProcessBuilder(
                "/bin/sh",
                "-c",
                "stty echo icanon < /dev/tty"
            );
            resetBuilder.directory(new File("").getCanonicalFile());
            Process resetProcess = resetBuilder.start();
            resetProcess.waitFor();
        } catch (Exception e) {}
    }

    // Getters and setters
    public Path getCurrentDir() {
        return currentDir;
    }

    public void setCurrentDir(Path currentDir) {
        this.currentDir = currentDir;
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public String getPathEnv() {
        return pathEnv;
    }

    public List<String> getCommandHistory() {
        return commandHistory;
    }

    public int getHistoryIndex() {
        return historyIndex;
    }

    public void setHistoryIndex(int historyIndex) {
        this.historyIndex = historyIndex;
    }
}
