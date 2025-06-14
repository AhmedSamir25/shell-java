import java.util.List;

public class CommandExecutor {

    private final Shell shell;
    private final BuiltinCommands builtinCommands;
    private final ExternalCommands externalCommands;
    private final PipelineHandler pipelineHandler;
    private final CommandParser commandParser;

    public CommandExecutor(Shell shell) {
        this.shell = shell;
        this.builtinCommands = new BuiltinCommands(shell);
        this.externalCommands = new ExternalCommands(shell);
        this.pipelineHandler = new PipelineHandler(shell, builtinCommands);
        this.commandParser = new CommandParser();
    }

    public void executeCommand(String input) {
        try {
            shell.getCommandHistory().add(input);

            ExtractResult result = commandParser.extractStreams(
                commandParser.tokenize(input)
            );
            List<String> partsList = result.commands();
            Streams streams = result.streams();

            if (partsList.isEmpty()) return;

            if (partsList.contains("|")) {
                pipelineHandler.handlePipeline(partsList, streams);
                return;
            }

            String command = partsList.get(0);
            List<String> arguments = partsList.subList(1, partsList.size());

            if (handleExitCommand(input, arguments)) return;

            Printer printer = streams.toPrinter(System.out, System.err);

            if (builtinCommands.isBuiltinCommand(command)) {
                builtinCommands.executeBuiltin(command, arguments, printer);
            } else {
                externalCommands.executeExternal(partsList, streams);
            }
        } catch (Exception e) {
            System.err.println("Command execution error: " + e.getMessage());
        }
    }

    private boolean handleExitCommand(String input, List<String> arguments) {
        if (input.equals("exit 0")) {
            shell.setRunning(false);
            return true;
        }

        if (arguments.size() > 0 && arguments.get(0).equals("exit")) {
            int exitCode = 0;
            if (arguments.size() > 1) {
                try {
                    exitCode = Integer.parseInt(arguments.get(1));
                } catch (NumberFormatException e) {
                    exitCode = 0;
                }
            }
            shell.setRunning(false);
            System.exit(exitCode);
            return true;
        }
        return false;
    }
}
