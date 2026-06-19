import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {
    static String currentDir = System.getProperty("user.dir");

    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");
            String input = sc.nextLine();

            List<String> tokens = parseInput(input);
            if (tokens.isEmpty()) continue;

            String redirectStdout = null;
            String redirectStderr = null;
            boolean appendStdout = false;
            List<String> cleanTokens = new ArrayList<>();

            for (int i = 0; i < tokens.size(); i++) {
                String t = tokens.get(i);
                if ((t.equals(">>") || t.equals("1>>")) && i + 1 < tokens.size()) {
                    redirectStdout = tokens.get(++i);
                    appendStdout = true;
                } else if ((t.equals(">") || t.equals("1>")) && i + 1 < tokens.size()) {
                    redirectStdout = tokens.get(++i);
                    appendStdout = false;
                } else if (t.equals("2>") && i + 1 < tokens.size()) {
                    redirectStderr = tokens.get(++i);
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
                String output = String.join(" ", arguments) + "\n";
                writeOutput(output, redirectStdout, appendStdout);
                createFileIfRedirected(redirectStderr);
            } else if (command.equals("pwd")) {
                writeOutput(currentDir + "\n", redirectStdout, appendStdout);
                createFileIfRedirected(redirectStderr);
            } else if (command.equals("cd")) {
                if (!arguments.isEmpty()) {
                    handleCd(arguments.get(0));
                }
            } else if (command.equals("type")) {
                if (!arguments.isEmpty()) {
                    String target = arguments.get(0);
                    String result;
                    if (target.equals("echo") || target.equals("exit") || target.equals("type")
                            || target.equals("pwd") || target.equals("cd")) {
                        result = target + " is a shell builtin\n";
                    } else {
                        String foundPath = findInPath(target);
                        if (foundPath != null) {
                            result = target + " is " + foundPath + "\n";
                        } else {
                            result = target + ": not found\n";
                        }
                    }
                    writeOutput(result, redirectStdout, appendStdout);
                    createFileIfRedirected(redirectStderr);
                }
            } else {
                runExternalCommand(command, arguments, redirectStdout, appendStdout, redirectStderr);
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

    private static void createFileIfRedirected(String redirectFile) throws Exception {
        if (redirectFile != null) {
            new FileOutputStream(redirectFile).close();
        }
    }

    private static List<String> parseInput(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int i = 0;

        while (i < input.length()) {
            char c = input.charAt(i);

            if (c == '\\') {
                i++;
                if (i < input.length()) {
                    current.append(input.charAt(i));
                    i++;
                }
            } else if (c == '\'') {
                i++;
                while (i < input.length() && input.charAt(i) != '\'') {
                    current.append(input.charAt(i));
                    i++;
                }
                i++;
            } else if (c == '"') {
                i++;
                while (i < input.length() && input.charAt(i) != '"') {
                    if (input.charAt(i) == '\\' && i + 1 < input.length()) {
                        char next = input.charAt(i + 1);
                        if (next == '"' || next == '\\') {
                            current.append(next);
                            i += 2;
                        } else {
                            current.append('\\');
                            i++;
                        }
                    } else {
                        current.append(input.charAt(i));
                        i++;
                    }
                }
                i++;
            } else if (c == ' ' || c == '\t') {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                i++;
            } else {
                current.append(c);
                i++;
            }
        }

        if (current.length() > 0) {
            tokens.add(current.toString());
        }

        return tokens;
    }

    private static void handleCd(String path) {
        if (path.equals("~")) {
            path = System.getenv("HOME");
            if (path == null) {
                System.out.println("cd: HOME not set");
                return;
            }
        }

        File dir = new File(path);
        if (!dir.isAbsolute()) {
            dir = new File(currentDir, path);
        }

        try {
            String resolved = dir.getCanonicalPath();
            File resolvedDir = new File(resolved);
            if (resolvedDir.exists() && resolvedDir.isDirectory()) {
                currentDir = resolved;
            } else {
                System.out.println("cd: " + path + ": No such file or directory");
            }
        } catch (Exception e) {
            System.out.println("cd: " + path + ": No such file or directory");
        }
    }

    private static void runExternalCommand(String command, List<String> arguments,
            String redirectStdout, boolean appendStdout, String redirectStderr) {
        String commandPath = findInPath(command);
        if (commandPath == null) {
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
                File outFile = new File(redirectStdout);
                if (appendStdout) {
                    pb.redirectOutput(ProcessBuilder.Redirect.appendTo(outFile));
                } else {
                    pb.redirectOutput(outFile);
                }
            } else {
                pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            }

            if (redirectStderr != null) {
                pb.redirectError(new File(redirectStderr));
            } else {
                pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            }

            Process process = pb.start();
            process.waitFor();
        } catch (Exception e) {
            System.out.println(command + ": command not found");
        }
    }

    private static String findInPath(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return null;

        for (String dir : pathEnv.split(File.pathSeparator)) {
            File file = new File(dir, command);
            if (file.exists() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }

        return null;
    }
}