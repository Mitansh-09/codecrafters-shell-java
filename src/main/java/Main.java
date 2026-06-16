import java.io.File;
import java.util.Scanner;

public class Main {
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
            } else if (input.equals("type") || input.startsWith("type ")) {
                String command = input.length() > 4 ? input.substring(5).trim() : "";

                if (command.equals("echo") || command.equals("exit") || command.equals("type")) {
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

    private static void runExternalCommand(String input) {
        String[] parts = input.split(" ");
        String command = parts[0];

        if (findInPath(command) == null) {
            System.out.println(command + ": command not found");
            return;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(parts);
            pb.inheritIO();
            Process process = pb.start();
            process.waitFor();
        } catch (Exception e) {
            System.out.println(command + ": command not found");
        }
    }

    private static String findInPath(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) {
            return null;
        }

        String[] directories = pathEnv.split(File.pathSeparator);

        for (String dir : directories) {
            File file = new File(dir, command);
            if (file.exists() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }

        return null;
    }
}