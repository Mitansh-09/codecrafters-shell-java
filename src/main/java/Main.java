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
                    } else {
                        System.out.println(input + ": command not found");
                    }
                }
            }
        }