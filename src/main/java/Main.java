import java.io.File;
import java.util.Scanner;

public class Main {
    static String currentDir = System.getProperty("user.dir");

    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");
            String input = sc.nextLine();

            if (input.equals("exit")) {
                break;
            } else if (input.startsWith("echo ")) {
                String message = input.substring(5);
                System.out.println(message);
            } else if (input.equals("pwd")) {
                System.out.println(currentDir);
            } else if (input.startsWith("cd ")) {
                String path = input.substring(3).trim();
                handleCd(path);
            } else if (input.equals("type") || input.startsWith("type ")) {
                String command = input.length() > 4 ? input.substring(5).trim() : "";

                if (command.equals("echo") || command.equals("exit") || command.equals("type")
                        || command.equals("pwd") || command.equals("cd")) {
                    System.out.println(command + " is a shell builtin");
                } else {
                    String foundPath = findInPath(command);
                    if (foundPath != null) {
                        System.out.println(command + " is " + foundPath);
                    } else {
                        System.out.println(command + ": not found");
                    }
                }
            } else {
                runExternalCommand(input);
            }
        }
    }

    private static void handleCd(String path) {
    File dir = new File(path);

    if (!dir.isAbsolute()) {
        dir = new File(currentDir, path);
    }

    try {
        String resolved = dir.getCanonicalPath();
        File resolved_dir = new File(resolved);
        if (resolved_dir.exists() && resolved_dir.isDirectory()) {
            currentDir = resolved;
        } else {
            System.out.println("cd: " + path + ": No such file or directory");
        }
    } catch (Exception e) {
        System.out.println("cd: " + path + ": No such file or directory");
    }
}

    private static void runExternalCommand(String input) {
        String[] parts = input.split(" ");
        String command = parts[0];

        String commandPath = findInPath(command);
        if (commandPath == null) {
            System.out.println(command + ": command not found");
            return;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(parts);
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