package compiler.lexer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Lexer{

    private String input;
    private int idx = 0;
    private int line = 1;

    public Lexer(String input){
        this.input = input;
    }

    //check if we don t have anymore chars
    private boolean isAtEnd(){
        return idx >= input.length();
    }

    //give the current character
    private char currentChar(){
        return input.charAt(idx);
    }

    //peek to the next char for the double operators
    private char peek(){
        if (idx + 1 >= input.length()) return '\0';
        return input.charAt(idx + 1);
    }

    private boolean isHexDigit(char c) {
        return Character.isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private void skipWhiteSpace(){
        while(!isAtEnd()){
            char c = currentChar();

            if(c == '\n'){
                line++;
                idx++;
            }
            else if(c == ' ' || c == '\t' || c == '\r'){
                idx++;
            }
            else{
                break;
            }
        }
    }


    private Token checkIfKeyWord(String input, int line){
        switch(input){
            case "if":
                return new Token(TokenType.IF, input, line);
            case "else":
                return new Token(TokenType.ELSE, input, line);
            case "for":
                return new Token(TokenType.FOR, input, line);
            case "break":
                return new Token(TokenType.BREAK, input, line);
            case "continue":
                return new Token(TokenType.CONTINUE, input, line);
            case "switch":
                return new Token(TokenType.SWITCH, input, line);
            case "case":
                return new Token(TokenType.CASE, input, line);
            case "int":
                return new Token(TokenType.INT, input, line);
            case "double":
                return new Token(TokenType.DOUBLE, input, line);
            case "float":
                return new Token(TokenType.FLOAT, input, line);
            case "boolean":
                return new Token(TokenType.BOOLEAN, input, line);
            case "void":
                return new Token(TokenType.VOID, input, line);
            case "return":
                return new Token(TokenType.RETURN, input, line);
            case "while":
                return new Token(TokenType.WHILE, input, line);
            case "enum":
                return new Token(TokenType.ENUM, input, line);
            case "struct":
                return new Token(TokenType.STRUCT, input, line);
            case "true":
            case "false":
                return new Token(TokenType.BOOL_VAL, input, line);
            default:
                return new Token(TokenType.IDENTIFIER, input, line);
        }
    }

    private Token checkIfOperator(char c, int tokenLine){
            switch(c){
            case '+':{
                idx++;
                if(peek() == '+'){
                    idx++;
                    return new Token(TokenType.PLUS, "++", tokenLine);
                }
                return new Token(TokenType.PLUS, "+", tokenLine);
            }
            case '-':{
                idx++;
                if(peek() == '-'){
                    idx++;
                    return new Token(TokenType.MINUS, "--", tokenLine);
                }
                return new Token(TokenType.MINUS, "-", tokenLine);
            }
            case '*':{
                idx++;
                if(peek() == '*'){
                    idx++;
                    return new Token(TokenType.MULTIPLY, "**", tokenLine);
                }
                return new Token(TokenType.MULTIPLY, "*", tokenLine);
            }
            case '/':{
                idx++;
                return new Token(TokenType.DIVIDE, "/", tokenLine);
            }
            case '%':{
                idx++;
                return new Token(TokenType.MODULO, "%", tokenLine);
            }
            case '=':{
                idx++;
                if(peek() == '='){
                    idx++;
                    return new Token(TokenType.EQUAL, "==", tokenLine);
                }
                return new Token(TokenType.ASSIGN, "=", tokenLine);
            }
            case '&':{
                idx++;
                if(peek() == '&'){
                    idx++;
                    return new Token(TokenType.AND, "&&", tokenLine);
                }
                return new Token(TokenType.BIT_AND, "&", tokenLine);
            }
            case '|':{
                idx++;
                if(peek() == '|'){
                    idx++;
                    return new Token(TokenType.OR, "||", tokenLine);
                }
                return new Token(TokenType.BIT_OR, "|", tokenLine);
            }
            case '!':{
                idx++;
                if(peek() == '='){
                    idx++;
                    return new Token(TokenType.NOT_EQUAL, "!=", tokenLine);
                }
                return new Token(TokenType.NOT, "!", tokenLine);
            }
            case '>':
                idx++;
                if(peek() == '='){
                    idx++;
                    return new Token(TokenType.GREATER_EQUAL, ">=", tokenLine);
                }
                return new Token(TokenType.GREATER, ">", tokenLine);
            case '<':
                idx++;
                if(peek() == '='){
                    idx++;
                    return new Token(TokenType.LESS_EQUAL, "<=", tokenLine);
                }
                return new Token(TokenType.LESS, "<", tokenLine);
            case ';':{
                idx++;
                return new Token(TokenType.SEMICOLON, ";", tokenLine);
            }
            case ',':{
                idx++;
                return new Token(TokenType.COMMA, ",", tokenLine);
            }
            case '(':{
                idx++;
                return new Token(TokenType.LPAREN, "(", tokenLine);
            }
            case ')':{
                idx++;
                return new Token(TokenType.RPAREN, ")", tokenLine);
            }
            case '[':{
                idx++;
                return new Token(TokenType.LBRACK, "[", tokenLine);
            }
            case ']':{
                idx++;
                return new Token(TokenType.RBRACK, "]", tokenLine);
            }
            case '{':{
                idx++;
                return new Token(TokenType.LBRACE, "{", tokenLine);
            }
            case '}':{
                idx++;
                return new Token(TokenType.RBRACE, "}", tokenLine);
            }
            default:{
                idx++;
                return new Token(TokenType.INVALID, String.valueOf(c), tokenLine);
            }
        }

    }

    public Token getNextToken(){
        skipWhiteSpace();

        if(isAtEnd()){
            return new Token(TokenType.EOF, "", line);
        }

        char c = currentChar();
        int tokenLine = line;

        //IDENTIFIER
        if(Character.isLetter(c) || currentChar() == '_'){
            StringBuilder word = new StringBuilder();

            while(!isAtEnd() && (Character.isLetterOrDigit(currentChar()) || currentChar() == '_')){
                word.append(currentChar());
                idx++;
            }

            return checkIfKeyWord(word.toString(), tokenLine);
        }
        //NUMBER
        else if (Character.isDigit(c)) {
    StringBuilder number = new StringBuilder();

    // --- case: starts with 0 ---
    if (c == '0') {
        number.append(c);
        idx++;

        if (!isAtEnd()) {
            char next = currentChar();

            // 🔵 Binary: 0b101
            if (next == 'b' || next == 'B') {
                number.append(next);
                idx++;

                while (!isAtEnd() && (currentChar() == '0' || currentChar() == '1')) {
                    number.append(currentChar());
                    idx++;
                }

                return new Token(TokenType.BASE2_NUMBER, number.toString(), tokenLine);
            }

            // 🟣 Hex: 0x1A
            if (next == 'x' || next == 'X') {
                number.append(next);
                idx++;

                while (!isAtEnd() && isHexDigit(currentChar())) {
                    number.append(currentChar());
                    idx++;
                }

                return new Token(TokenType.BASE16_NUMBER, number.toString(), tokenLine);
            }

            // 🟡 Octal: 077
            if (next >= '0' && next <= '7') {
                while (!isAtEnd() && currentChar() >= '0' && currentChar() <= '7') {
                    number.append(currentChar());
                    idx++;
                }

                return new Token(TokenType.BASE8_NUMBER, number.toString(), tokenLine);
            }
        }

        return new Token(TokenType.BASE10_NUMBER, number.toString(), tokenLine);
        }

        // --- decimal / real ---
        while (!isAtEnd() && Character.isDigit(currentChar())) {
            number.append(currentChar());
            idx++;
        }

        // 🔥 check for float (3.14)
        if (!isAtEnd() && currentChar() == '.') {
            number.append('.');
            idx++;

            while (!isAtEnd() && Character.isDigit(currentChar())) {
                number.append(currentChar());
                idx++;
            }

            return new Token(TokenType.REAL_NUMBER, number.toString(), tokenLine);
        }

        return new Token(TokenType.BASE10_NUMBER, number.toString(), tokenLine);
    }
        //operator & more
        return checkIfOperator(c, tokenLine);
    }

    public static void main(String args[]) {
    try {
        String input = Files.readString(Paths.get("C:\\Users\\tamas\\Desktop\\CT_PROIECT\\src\\compiler\\lexer\\testers\\1.c"));

        Lexer lx = new Lexer(input);

        Token token = lx.getNextToken();

        while (token.getTokenType() != TokenType.EOF) {
            System.out.println(token);
            token = lx.getNextToken();
        }

    } catch (IOException e) {
        System.out.println("Error reading file: " + e.getMessage());
    }
}
}