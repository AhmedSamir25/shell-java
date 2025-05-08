import java.util.Scanner;

public class Main {

    public static void main(String[] args) throws Exception {
        // Uncomment this block to pass the first stage
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("$ ");
            if (!scanner.hasNextLine()) {
                break;
            }
            String input = scanner.nextLine();
            System.out.println(input + ": command not found");
            if(input.equals("0")){
                break;
            }
        }
    }
}
