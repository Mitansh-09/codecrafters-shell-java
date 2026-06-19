import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        try {
            while (true) {
                System.out.print("$ ");
                if (!sc.hasNextLine()) break;
                String input = sc.nextLine();

            if (input.equals("exit")) {
                break;
            } else if (input.startsWith("echo ")) {
                String message = input.substring(5);
                System.out.println(message);
            } else if (input.equals("pwd")) {
                System.out.println(System.getProperty("user.dir"));
            } else if (input.equals("type") || input.startsWith("type ")) {
                String command = input.length() > 4 ? input.substring(5).trim() : "";

                if (command.equals("echo") || command.equals("exit") || command.equals("type") || command.equals("pwd")) {
                    System.out.println(command + " is a shell builtin");
                } else {
                    System.out.println(command + ": not found");
                }
            } else {
                System.out.println(input + ": command not found");
            }
            }
        } finally {
            sc.close();
        }
    }

}