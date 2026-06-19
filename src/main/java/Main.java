import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {
    static String currentDir = System.getProperty("user.dir");
    static List<Job> jobs = new ArrayList<>();

    static class Job {
        int number;
        long pid;
        String command;
        Process process;

        Job(int number, long pid, String command, Process process) {
            this.number = number;
            this.pid = pid;
            this.command = command;
            this.process = process;
        }

        boolean isRunning() {
            return process.isAlive();
        }
    }

    private static int nextJobNumber() {
        List<Integer> used = new ArrayList<>();
        for (Job job : jobs) used.add(job.number);
        int n = 1;
        while (used.contains(n)) n++;
        return n;
    }

    private static void reapJobs() {
        int last = jobs.size() - 1;
        List<Job> toRemove = new ArrayList<>();
        for (int i = 0; i < jobs.size(); i++) {
            Job job = jobs.get(i);
            if (!job.isRunning()) {
                char marker = (i == last) ? '+' : (i == last - 1) ? '-' : ' ';
                System.out.println("[" + job.number + "]" + marker + "  "
                        + String.format("%-24s", "Done") + job.command);
                toRemove.add(job);
            }
        }
        jobs.removeAll(toRemove);
    }

    private static void listJobs() {
        int last = jobs.size() - 1;
        List<Job> toRemove = new ArrayList<>();
        for (int i = 0; i < jobs.size(); i++) {
            Job job = jobs.get(i);
            char marker = (i == last) ? '+' : (i == last - 1) ? '-' : ' ';
            if (job.isRunning()) {
                System.out.println("[" + job.number + "]" + marker + "  "
                        + String.format("%-24s", "Running") + job.command + " &");
            } else {
                System.out.println("[" + job.number + "]" + marker + "  "
                        + String.format("%-24s", "Done") + job.command);
                toRemove.add(job);
            }
        }
        jobs.removeAll(toRemove);
    }

    private static boolean isBuiltin(String cmd) {
        return cmd.equals("echo") || cmd.equals("pwd") || cmd.equals("cd")
                || cmd.equals("type") || cmd.equals("exit") || cmd.equals("jobs");
    }

    // run a builtin with given stdin/stdout streams
    private static void runBuiltin(List<String> seg, InputStream in, OutputStream out) throws Exception {
        String cmd = seg.get(0);
        List<String> args = seg.subList(1, seg.size());
        PrintStream ps = new PrintStream(out, true);

        switch (cmd) {
            case "echo":
                ps.println(String.join(" ", args));
                break;
            case "pwd":
                ps.println(currentDir);
                break;
            case "type":
                if (!args.isEmpty()) {
                    String target = args.get(0);
                    if (isBuiltin(target)) {
                        ps.println(target + " is a shell builtin");
                    } else {
                        String found = findInPath(target);
                        ps.println(found != null ? target + " is " + found : target + ": not found");
                    }
                }
                break;
            case "cd":
                if (!args.isEmpty()) handleCd(args.get(0));
                break;
            case "jobs":
                // redirect jobs output to ps
                PrintStream oldOut = System.out;
                System.setOut(ps);
                listJobs();
                System.setOut(oldOut);
                break;
        }
    }

    private static void runPipeline(List<String> tokens) throws Exception {
    List<List<String>> segments = new ArrayList<>();
    List<String> current = new ArrayList<>();
    for (String t : tokens) {
        if (t.equals("|")) {
            if (!current.isEmpty()) segments.add(current);
            current = new ArrayList<>();
        } else {
            current.add(t);
        }
    }
    if (!current.isEmpty()) segments.add(current);
    if (segments.size() < 2) return;

    // check if any segment is a builtin
    boolean hasBuiltin = false;
    for (List<String> seg : segments) {
        if (isBuiltin(seg.get(0))) { hasBuiltin = true; break; }
    }

    if (!hasBuiltin) {
        // all external: use OS-level startPipeline
        List<ProcessBuilder> builders = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            List<String> seg = segments.get(i);
            String cmd = seg.get(0);
            if (findInPath(cmd) == null) {
                System.err.println(cmd + ": command not found");
                return;
            }
            ProcessBuilder pb = new ProcessBuilder(seg);
            pb.directory(new File(currentDir));
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            if (i == 0) pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
            if (i == segments.size() - 1) pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            builders.add(pb);
        }
        List<Process> processes = ProcessBuilder.startPipeline(builders);
        for (Process p : processes) p.waitFor();
        return;
    }

    // has builtins: manual piped threading
    int n = segments.size();
    PipedOutputStream[] pipeOuts = new PipedOutputStream[n - 1];
    PipedInputStream[] pipeIns = new PipedInputStream[n - 1];
    for (int i = 0; i < n - 1; i++) {
        pipeOuts[i] = new PipedOutputStream();
        pipeIns[i] = new PipedInputStream(pipeOuts[i], 65536);
    }

    List<Thread> threads = new ArrayList<>();
    List<Process> processes = new ArrayList<>();

    for (int i = 0; i < n; i++) {
        List<String> seg = segments.get(i);
        String cmd = seg.get(0);
        InputStream segIn = (i == 0) ? System.in : pipeIns[i - 1];
        OutputStream segOut = (i == n - 1) ? System.out : pipeOuts[i];

        if (isBuiltin(cmd)) {
            final List<String> s = seg;
            final InputStream si = segIn;
            final OutputStream so = segOut;
            Thread t = new Thread(() -> {
                try {
                    runBuiltin(s, si, so);
                    if (so != System.out) so.close();
                } catch (Exception e) { e.printStackTrace(); }
            });
            threads.add(t);
        } else {
            if (findInPath(cmd) == null) {
                System.err.println(cmd + ": command not found");
                return;
            }
            ProcessBuilder pb = new ProcessBuilder(seg);
            pb.directory(new File(currentDir));
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            Process proc = pb.start();
            processes.add(proc);

            if (i > 0) {
                final InputStream src = segIn;
                final OutputStream dst = proc.getOutputStream();
                Thread feeder = new Thread(() -> {
                    try { src.transferTo(dst); dst.close(); } catch (Exception ignored) {}
                });
                feeder.setDaemon(true);
                threads.add(feeder);
            }

            final InputStream src = proc.getInputStream();
            final OutputStream dst = segOut;
            Thread collector = new Thread(() -> {
                try {
                    src.transferTo(dst);
                    if (dst != System.out) dst.close();
                } catch (Exception ignored) {}
            });
            collector.setDaemon(true);
            threads.add(collector);
        }
    }

    for (Thread t : threads) t.start();
    for (Process p : processes) p.waitFor();
    for (Thread t : threads) t.join();
}

    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);

        while (true) {
            reapJobs();
            System.out.print("$ ");
            String input = sc.nextLine();

            List<String> tokens = parseInput(input);
            if (tokens.isEmpty()) continue;

            if (tokens.contains("|")) {
                runPipeline(tokens);
                continue;
            }

            boolean background = false;
            if (!tokens.isEmpty() && tokens.get(tokens.size() - 1).equals("&")) {
                background = true;
                tokens.remove(tokens.size() - 1);
            }

            if (tokens.isEmpty()) continue;

            String redirectStdout = null;
            String redirectStderr = null;
            boolean appendStdout = false;
            boolean appendStderr = false;
            List<String> cleanTokens = new ArrayList<>();

            for (int i = 0; i < tokens.size(); i++) {
                String t = tokens.get(i);
                if ((t.equals(">>") || t.equals("1>>")) && i + 1 < tokens.size()) {
                    redirectStdout = tokens.get(++i);
                    appendStdout = true;
                } else if ((t.equals(">") || t.equals("1>")) && i + 1 < tokens.size()) {
                    redirectStdout = tokens.get(++i);
                    appendStdout = false;
                } else if (t.equals("2>>") && i + 1 < tokens.size()) {
                    redirectStderr = tokens.get(++i);
                    appendStderr = true;
                } else if (t.equals("2>") && i + 1 < tokens.size()) {
                    redirectStderr = tokens.get(++i);
                    appendStderr = false;
                } else {
                    cleanTokens.add(t);
                }
            }

            if (cleanTokens.isEmpty()) continue;

            String command = cleanTokens.get(0);
            List<String> arguments = cleanTokens.subList(1, cleanTokens.size());

            if (command.equals("exit")) {
                break;
            } else if (command.equals("echo")) {
                writeOutput(String.join(" ", arguments) + "\n", redirectStdout, appendStdout);
                createFileIfRedirected(redirectStderr, appendStderr);
            } else if (command.equals("pwd")) {
                writeOutput(currentDir + "\n", redirectStdout, appendStdout);
                createFileIfRedirected(redirectStderr, appendStderr);
            } else if (command.equals("cd")) {
                if (!arguments.isEmpty()) handleCd(arguments.get(0));
            } else if (command.equals("jobs")) {
                listJobs();
            } else if (command.equals("type")) {
                if (!arguments.isEmpty()) {
                    String target = arguments.get(0);
                    String result;
                    if (isBuiltin(target)) {
                        result = target + " is a shell builtin\n";
                    } else {
                        String foundPath = findInPath(target);
                        result = foundPath != null
                                ? target + " is " + foundPath + "\n"
                                : target + ": not found\n";
                    }
                    writeOutput(result, redirectStdout, appendStdout);
                    createFileIfRedirected(redirectStderr, appendStderr);
                }
            } else {
                runExternalCommand(command, arguments, redirectStdout, appendStdout,
                        redirectStderr, appendStderr, background, input.trim());
            }
        }
    }

    private static void writeOutput(String output, String redirectFile, boolean append) throws Exception {
        if (redirectFile != null) {
            try (FileOutputStream fos = new FileOutputStream(redirectFile, append)) {
                fos.write(output.getBytes());
            }
        } else {
            System.out.print(output);
        }
    }

    private static void createFileIfRedirected(String redirectFile, boolean append) throws Exception {
        if (redirectFile != null) new FileOutputStream(redirectFile, append).close();
    }

    private static List<String> parseInput(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder curr = new StringBuilder();
        int i = 0;

        while (i < input.length()) {
            char c = input.charAt(i);
            if (c == '\\') {
                i++;
                if (i < input.length()) { curr.append(input.charAt(i)); i++; }
            } else if (c == '\'') {
                i++;
                while (i < input.length() && input.charAt(i) != '\'') { curr.append(input.charAt(i)); i++; }
                i++;
            } else if (c == '"') {
                i++;
                while (i < input.length() && input.charAt(i) != '"') {
                    if (input.charAt(i) == '\\' && i + 1 < input.length()) {
                        char next = input.charAt(i + 1);
                        if (next == '"' || next == '\\') { curr.append(next); i += 2; }
                        else { curr.append('\\'); i++; }
                    } else { curr.append(input.charAt(i)); i++; }
                }
                i++;
            } else if (c == '|') {
                if (curr.length() > 0) { tokens.add(curr.toString()); curr.setLength(0); }
                tokens.add("|");
                i++;
            } else if (c == ' ' || c == '\t') {
                if (curr.length() > 0) { tokens.add(curr.toString()); curr.setLength(0); }
                i++;
            } else {
                curr.append(c); i++;
            }
        }
        if (curr.length() > 0) tokens.add(curr.toString());
        return tokens;
    }

    private static void handleCd(String path) {
        if (path.equals("~")) {
            path = System.getenv("HOME");
            if (path == null) { System.out.println("cd: HOME not set"); return; }
        }
        File dir = new File(path);
        if (!dir.isAbsolute()) dir = new File(currentDir, path);
        try {
            String resolved = dir.getCanonicalPath();
            File resolvedDir = new File(resolved);
            if (resolvedDir.exists() && resolvedDir.isDirectory()) currentDir = resolved;
            else System.out.println("cd: " + path + ": No such file or directory");
        } catch (Exception e) {
            System.out.println("cd: " + path + ": No such file or directory");
        }
    }

    private static void runExternalCommand(String command, List<String> arguments,
            String redirectStdout, boolean appendStdout,
            String redirectStderr, boolean appendStderr,
            boolean background, String originalInput) {
        if (findInPath(command) == null) {
            System.out.println(command + ": command not found");
            return;
        }
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add(command);
            cmd.addAll(arguments);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(new File(currentDir));

            if (redirectStdout != null) {
                File f = new File(redirectStdout);
                pb.redirectOutput(appendStdout ? ProcessBuilder.Redirect.appendTo(f) : ProcessBuilder.Redirect.to(f));
            } else pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);

            if (redirectStderr != null) {
                File f = new File(redirectStderr);
                pb.redirectError(appendStderr ? ProcessBuilder.Redirect.appendTo(f) : ProcessBuilder.Redirect.to(f));
            } else pb.redirectError(ProcessBuilder.Redirect.INHERIT);

            Process process = pb.start();

            if (background) {
                int jobNumber = nextJobNumber();
                long pid = process.pid();
                String jobCommand = originalInput.replaceAll("\\s*&\\s*$", "").trim();
                jobs.add(new Job(jobNumber, pid, jobCommand, process));
                System.out.println("[" + jobNumber + "] " + pid);
            } else {
                process.waitFor();
            }
        } catch (Exception e) {
            System.out.println(command + ": command not found");
        }
    }

    private static String findInPath(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return null;
        for (String dir : pathEnv.split(File.pathSeparator)) {
            File file = new File(dir, command);
            if (file.exists() && file.canExecute()) return file.getAbsolutePath();
        }
        return null;
    }
}