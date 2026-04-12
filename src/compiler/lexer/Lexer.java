package compiler.lexer;

public class Lexer{
    private String input;
    private int line = 1;
    private int currentIdx = 0;

    public Lexer(String input){
        this.input = input;
    }

    private void forward(){
        if (currentIdx < input.length()) {
            if (input.charAt(currentIdx) == '\n') {
                line++;
            }
            currentIdx++;
        }
    }
    
}