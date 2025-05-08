import java.util.Scanner;

public class Main {

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("$ ");
            if (!scanner.hasNextLine()) {
                break;
            }
            String input = scanner.nextLine();
            if(input.equals("exit 0")){
                break;
            }
            if(input.contains("echo")){
                String result = input.replace("echo","");
                System.out.println(result);
            }else{
                System.out.println(input + ": command not found");
            }
        }
    }
}
