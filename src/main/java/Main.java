import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        System.out.print("$ ");
        Scanner sc = new Scanner(System.in);
        String input = sc.nextLine();
        System.out.println(input + ": command not found");

         while (true) {
            System.out.print("$ ");
            String input = sc.nextLine();
            System.out.println(input + ": command not found");
        }
    }
}
