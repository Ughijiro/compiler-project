package compiler.lexer;

public class Lexer{
    public static void main(String args[]){
        String input = "int x = 10;";
        int idx = 0;

        while(idx < input.length()){
            char c = input.charAt(idx);

            if(Character.isDigit(c)){
                StringBuilder number = new StringBuilder();

                while(idx < input.length() &&
                    Character.isDigit(input.charAt(idx))){
                        number.append(input.charAt(idx));
                        idx++;
                }

                System.out.println("Number: " + number);
                continue;
            }

            if(Character.isLetter(c)){
                StringBuilder word = new StringBuilder();

                while(idx < input.length() &&
                    Character.isLetterOrDigit(input.charAt(idx))){
                        word.append(input.charAt(idx));
                        idx++;
                }
                System.out.println("Word: " + word);
                continue;
            }

            if(c == '+' || c == '-' || c == '=' || c == '/' || c == '%' || c == ';'){
                System.out.println("Operator: " + c);
                idx++;
                continue;
            }
            idx++;

        }
    }
}