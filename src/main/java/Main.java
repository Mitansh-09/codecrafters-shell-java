import java.io.File;
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

            String command = tokens.get(0);
            List<String> arguments = tokens.subList(1, tokens.size());

            if (command.equals("exit")) {
                break;
            } else if (command.equals("echo")) {
                System.out.println(String.join(" ", arguments));
            } else if (command.equals("pwd")) {
                System.out.println(currentDir);
            } else if (command.equals("cd")) {
                if (!arguments.isEmpty()) {
                    handleCd(arguments.get(0));
                }
            } else if (command.equals("type")) {
                if (!arguments.isEmpty()) {
                    String target = arguments.get(0);
                    if (target.equals("echo") || target.equals("exit") || target.equals("type")
                            || target.equals("pwd") || target.equals("cd")) {
                        System.out.println(target + " is a shell builtin");
                    } else {
                        String foundPath = findInPath(target);
                        if (foundPath != null) {
                            System.out.println(target + " is " + foundPath);
                        } else {
                            System.out.println(target + ": not found");
                        }
                    }
                }
            } else {
                runExternalCommand(command, arguments);
            }
        }
    }

    private static List<String> parseInput(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int i = 0;

        while (i < input.length()) {
            char c = input.charAt(i);

            if (c == '\\') {
                // outside quotes: skip backslash, take next char literally
                i++;
                if (i < input.length()) {
                    current.append(input.charAt(i));
                    i++;
                }

            } else if (c == '\'') {
                // single quote: everything literal until closing quote
                i++;
                while (i < input.length() && input.charAt(i) != '\'') {
                    current.append(input.charAt(i));
                    i++;
                }
                i++; // skip closing quote

            } else if (c == '"') {
                // double quote: everything literal except \" and \\
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
                i++; // skip closing quote

            } else if (c == ' ' || c == '\t') {
                // whitespace outside quotes: token delimiter
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

    private static void runExternalCommand(String command, List<String> arguments) {
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
            pb.inheritIO();
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