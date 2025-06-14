import java.io.*;
import java.util.*;

public class PipelineHandler {

    private final Shell shell;
    private final BuiltinCommands builtinCommands;

    public PipelineHandler(Shell shell, BuiltinCommands builtinCommands) {
        this.shell = shell;
        this.builtinCommands = builtinCommands;
    }

    public void handlePipeline(List<String> allParts, Streams streams) {
        List<List<String>> commands = parseCommands(allParts);
        int n = commands.size();
        if (n == 0) return;

        try {
            if (allCommandsAreExternal(commands)) {
                handleExternalPipeline(commands);
            } else {
                handleMixedPipeline(commands, n);
            }
        } catch (Exception e) {
            System.err.println("Pipeline error: " + e.getMessage());
        }
    }

    private List<List<String>> parseCommands(List<String> allParts) {
        List<List<String>> commands = new ArrayList<>();
        List<String> current = new ArrayList<>();
        for (String part : allParts) {
            if (part.equals("|")) {
                commands.add(current);
                current = new ArrayList<>();
            } else {
                current.add(part);
            }
        }
        commands.add(current);
        return commands;
    }

    private boolean allCommandsAreExternal(List<List<String>> commands) {
        for (List<String> cmd : commands) {
            if (builtinCommands.isBuiltinCommand(cmd.get(0))) {
                return false;
            }
        }
        return true;
    }

    private void handleExternalPipeline(List<List<String>> commands)
        throws Exception {
        List<ProcessBuilder> builders = new ArrayList<>();
        for (List<String> cmd : commands) {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(shell.getCurrentDir().toFile());
            builders.add(pb);
        }

        List<Process> processes = ProcessBuilder.startPipeline(builders);
        List<Thread> errThreads = new ArrayList<>();

        for (Process p : processes) {
            Thread t = new Thread(() -> {
                try (InputStream err = p.getErrorStream()) {
                    err.transferTo(System.err);
                } catch (Exception ignored) {}
            });
            t.start();
            errThreads.add(t);
        }

        try (
            InputStream out = processes
                .get(processes.size() - 1)
                .getInputStream()
        ) {
            out.transferTo(System.out);
        }

        for (Process p : processes) {
            p.waitFor();
        }
        for (Thread t : errThreads) {
            t.join();
        }
    }

    private void handleMixedPipeline(List<List<String>> commands, int n)
        throws Exception {
        PipedInputStream[] pipeIns = new PipedInputStream[n - 1];
        PipedOutputStream[] pipeOuts = new PipedOutputStream[n - 1];

        for (int i = 0; i < n - 1; i++) {
            pipeOuts[i] = new PipedOutputStream();
            pipeIns[i] = new PipedInputStream(pipeOuts[i]);
        }

        Process[] processes = new Process[n];
        Thread[] threads = new Thread[n];
        PrintStream originalOut = System.out;
        InputStream originalIn = System.in;

        for (int i = 0; i < n; i++) {
            final int idx = i;
            boolean isBuiltin = builtinCommands.isBuiltinCommand(
                commands.get(i).get(0)
            );

            if (isBuiltin) {
                threads[i] = createBuiltinThread(
                    commands.get(i),
                    idx,
                    n,
                    pipeIns,
                    pipeOuts,
                    originalIn,
                    originalOut
                );
                threads[i].start();
            } else {
                processes[i] = createExternalProcess(
                    commands.get(i),
                    idx,
                    n,
                    pipeIns,
                    pipeOuts
                );
            }
        }

        // Wait for all threads and processes
        for (int i = 0; i < n; i++) {
            if (threads[i] != null) {
                threads[i].join();
            }
            if (processes[i] != null) {
                processes[i].waitFor();
            }
        }
    }

    private Thread createBuiltinThread(
        List<String> cmd,
        int idx,
        int n,
        PipedInputStream[] pipeIns,
        PipedOutputStream[] pipeOuts,
        InputStream originalIn,
        PrintStream originalOut
    ) {
        return new Thread(() -> {
            PrintStream prevOut = System.out;
            InputStream prevIn = System.in;
            try {
                if (idx == 0) {
                    System.setIn(originalIn);
                } else {
                    System.setIn(pipeIns[idx - 1]);
                }
                if (idx == n - 1) {
                    System.setOut(originalOut);
                } else {
                    System.setOut(new PrintStream(pipeOuts[idx], true));
                }

                builtinCommands.executeBuiltin(
                    cmd.get(0),
                    cmd.size() > 1
                        ? cmd.subList(1, cmd.size())
                        : Collections.emptyList(),
                    new Printer(System.out, System.err)
                );
                System.out.flush();

                if (idx < n - 1) {
                    pipeOuts[idx].close();
                }
            } catch (Exception e) {} finally {
                System.setOut(prevOut);
                System.setIn(prevIn);
            }
        });
    }

    private Process createExternalProcess(
        List<String> cmd,
        int idx,
        int n,
        PipedInputStream[] pipeIns,
        PipedOutputStream[] pipeOuts
    ) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(shell.getCurrentDir().toFile());

        if (idx == 0) {
            pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
        } else {
            pb.redirectInput(ProcessBuilder.Redirect.PIPE);
        }

        if (idx == n - 1) {
            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        } else {
            pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
        }

        Process process = pb.start();

        if (idx > 0) {
            Thread t = new Thread(() -> {
                try (OutputStream out = process.getOutputStream()) {
                    pipeIns[idx - 1].transferTo(out);
                } catch (Exception e) {}
            });
            t.start();
        }

        if (idx < n - 1) {
            Thread t = new Thread(() -> {
                try (InputStream in = process.getInputStream()) {
                    in.transferTo(pipeOuts[idx]);
                    pipeOuts[idx].close();
                } catch (Exception e) {}
            });
            t.start();
        }

        Thread errThread = new Thread(() -> {
            try (InputStream err = process.getErrorStream()) {
                err.transferTo(System.err);
            } catch (Exception ignored) {}
        });
        errThread.start();

        return process;
    }
}
