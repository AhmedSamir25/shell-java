import java.io.File;
import java.util.*;

public class TabCompletion {

    private final Shell shell;
    private static final String[] SHELL_COMMANDS = {
        "echo",
        "exit",
        "type",
        "pwd",
        "cd",
        "history",
    };

    public TabCompletion(Shell shell) {
        this.shell = shell;
    }

    public String handleTabCompletion(String currentInput) {
        String[] tokens = currentInput.trim().split("\\s+");
        if (tokens.length == 0 || currentInput.trim().isEmpty()) {
            System.out.print("\u0007");
            return currentInput;
        }

        String lastToken = tokens[tokens.length - 1];
        String[] paths = shell.getPathEnv().split(":");

        Set<String> matchesSet = new LinkedHashSet<>();

        // Add shell commands
        for (String cmd : SHELL_COMMANDS) {
            if (cmd.startsWith(lastToken)) {
                matchesSet.add(cmd);
            }
        }

        // Add executables from PATH
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
            return completeWithSingle(currentInput, lastToken, matches.get(0));
        } else if (matches.size() > 1) {
            return handleMultipleMatches(currentInput, lastToken, matches);
        } else {
            System.out.print("\u0007");
            return currentInput;
        }
    }

    private String completeWithSingle(
        String currentInput,
        String lastToken,
        String completion
    ) {
        String prefix = "";
        int lastTokenIndex = currentInput.lastIndexOf(lastToken);
        if (lastTokenIndex > 0) {
            prefix = currentInput.substring(0, lastTokenIndex);
            if (!prefix.endsWith(" ")) prefix += " ";
        }
        return prefix + completion + " ";
    }

    private String handleMultipleMatches(
        String currentInput,
        String lastToken,
        List<String> matches
    ) {
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
            return currentInput;
        }
    }

    private String longestCommonPrefix(List<String> strings) {
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
}
