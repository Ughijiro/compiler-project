package compiler.lexer;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Lexer {

    private String input;
    private int idx = 0;
    private int line = 1;

    public Lexer(String input) {
        this.input = input + "\0"; // null terminator
    }

    private boolean isAtEnd() {
        return idx >= input.length() || input.charAt(idx) == '\0';
    }

    private char currentChar(){
        return input.charAt(idx);
    }

    private char peek() {
        if (idx + 1 >= input.length()) return '\0';
        return input.charAt(idx + 1);
    }

    private void skipSpaces() {
        while (!isAtEnd()) {
            char c = input.charAt(idx);
            if (c == '\n') {
                line++;
                idx++;
            } else if (c == ' ' || c == '\t' || c == '\r') {
                idx++;
            } else if (c == '/' && peek() == '/') {
                // simple comment skip
                while (!isAtEnd() && input.charAt(idx) != '\n') idx++;
            } else {
                break;
            }
        }
    }

    // Instead of HashMaps, we use a simple switch
    private Token checkIfKeyword(String word, int tokenLine) {
        switch (word) {
            case "if": return new Token(TokenType.IF, word, tokenLine);
            case "else": return new Token(TokenType.ELSE, word, tokenLine);
            case "for": return new Token(TokenType.FOR, word, tokenLine);
            case "while": return new Token(TokenType.WHILE, word, tokenLine);
            case "break": return new Token(TokenType.BREAK, word, tokenLine);
            case "return": return new Token(TokenType.RETURN, word, tokenLine);
            case "int": return new Token(TokenType.INT, word, tokenLine);
            case "double": return new Token(TokenType.DOUBLE, word, tokenLine);
            case "char": return new Token(TokenType.CHAR, word, tokenLine);
            case "void": return new Token(TokenType.VOID, word, tokenLine);
            case "struct": return new Token(TokenType.STRUCT, word, tokenLine);
            default: return new Token(TokenType.IDENTIFIER, word, tokenLine);
        }
    }

    private Token checkIfOperator(char c, int tokenLine) {
        idx++; // Consume the first character (like < or =)
        
        switch (c) {
            case '+': 
                if (!isAtEnd() && currentChar() == '+') { idx++; return new Token(TokenType.PLUS, "++", tokenLine); }
                return new Token(TokenType.PLUS, "+", tokenLine);
            case '-':
                if (!isAtEnd() && currentChar() == '-') { idx++; return new Token(TokenType.MINUS, "--", tokenLine); }
                return new Token(TokenType.MINUS, "-", tokenLine);
            case '*': return new Token(TokenType.MULTIPLY, "*", tokenLine);
            case '/': return new Token(TokenType.DIVIDE, "/", tokenLine);
            case '=':
                if (!isAtEnd() && currentChar() == '=') { idx++; return new Token(TokenType.EQUAL, "==", tokenLine); }
                return new Token(TokenType.ASSIGN, "=", tokenLine);
            case '!':
                if (!isAtEnd() && currentChar() == '=') { idx++; return new Token(TokenType.NOT_EQUAL, "!=", tokenLine); }
                return new Token(TokenType.NOT, "!", tokenLine);
            case '>':
                if (!isAtEnd() && currentChar() == '=') { idx++; return new Token(TokenType.GREATER_EQUAL, ">=", tokenLine); }
                return new Token(TokenType.GREATER, ">", tokenLine);
            case '<':
                if (!isAtEnd() && currentChar() == '=') { idx++; return new Token(TokenType.LESS_EQUAL, "<=", tokenLine); }
                return new Token(TokenType.LESS, "<", tokenLine);
            case '&':
                if (!isAtEnd() && currentChar() == '&') { idx++; return new Token(TokenType.AND, "&&", tokenLine); }
                return new Token(TokenType.BIT_AND, "&", tokenLine);
            case '|':
                if (!isAtEnd() && currentChar() == '|') { idx++; return new Token(TokenType.OR, "||", tokenLine); }
                return new Token(TokenType.BIT_OR, "|", tokenLine);
            case ';': return new Token(TokenType.SEMICOLON, ";", tokenLine);
            case ',': return new Token(TokenType.COMMA, ",", tokenLine);
            case '(': return new Token(TokenType.LPAREN, "(", tokenLine);
            case ')': return new Token(TokenType.RPAREN, ")", tokenLine);
            case '[': return new Token(TokenType.LBRACK, "[", tokenLine);
            case ']': return new Token(TokenType.RBRACK, "]", tokenLine);
            case '{': return new Token(TokenType.LBRACE, "{", tokenLine);
            case '}': return new Token(TokenType.RBRACE, "}", tokenLine);
            case '.': return new Token(TokenType.DOT, ".", tokenLine);
            default: return new Token(TokenType.INVALID, String.valueOf(c), tokenLine);
        }
    }

    public Token getNextToken() {
        skipSpaces();

        if (isAtEnd()) return new Token(TokenType.EOF, "", line);

        int state = 0; // State variable from the diagram
        StringBuilder buf = new StringBuilder();
        int tokenLine = line;

        while (true) {
            char c = input.charAt(idx);

            switch (state) {
                case 0: // Start state
                    if (Character.isLetter(c) || c == '_') {
                        state = 12; buf.append(c); idx++;
                    } else if (c == '0') {
                        state = 1; buf.append(c); idx++;
                    } else if (Character.isDigit(c)) {
                        state = 5; buf.append(c); idx++;
                    } else if (c == '"') {
                        state = 14; idx++;
                    } else if (c == '\'') {
                        state = 13; idx++;
                    } else if (c == '.') {
                        // Check if it's a number like .5 or a struct dot
                        if (Character.isDigit(peek())) {
                            state = 7; buf.append('0'); buf.append(c); idx++;
                        } else {
                            idx++; return new Token(TokenType.DOT, ".", tokenLine);
                        }
                    } else {
                        return checkIfOperator(c, tokenLine);
                    }
                    break;

                case 1: // After seeing a '0'
                    if (c == 'x' || c == 'X') {
                        state = 3; buf.setLength(0); idx++; // Hex start
                    } else if (c == 'b' || c == 'B') {
                        state = 4; buf.setLength(0); idx++; // Bin start
                    } else if (c >= '0' && c <= '7') {
                        state = 2; buf.append(c); idx++; // Octal loop
                    } else if (c == '.') {
                        state = 7; buf.append(c); idx++; // Real number
                    } else {
                        return new Token(TokenType.BASE10_NUMBER, "0", tokenLine);
                    }
                    break;

                case 2: // Octal numbers
                    if (c >= '0' && c <= '7') { buf.append(c); idx++; }
                    else return new Token(TokenType.BASE8_NUMBER, buf.toString(), tokenLine);
                    break;

                case 3: // Hex numbers
                    if (Character.isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')) {
                        buf.append(c); idx++;
                    } else return new Token(TokenType.BASE16_NUMBER, buf.toString(), tokenLine);
                    break;

                case 4: // Binary numbers
                    if (c == '0' || c == '1') { buf.append(c); idx++; }
                    else return new Token(TokenType.BASE2_NUMBER, buf.toString(), tokenLine);
                    break;

                case 5: // Base 10 numbers
                    if (Character.isDigit(c)) { buf.append(c); idx++; }
                    else if (c == '.') { state = 7; buf.append(c); idx++; }
                    else if (c == 'e' || c == 'E') { state = 8; buf.append(c); idx++; }
                    else return new Token(TokenType.BASE10_NUMBER, buf.toString(), tokenLine);
                    break;

                case 7: // Fractional part of Real numbers
                    if (Character.isDigit(c)) { buf.append(c); idx++; }
                    else if (c == 'e' || c == 'E') { state = 8; buf.append(c); idx++; }
                    else return new Token(TokenType.REAL_NUMBER, buf.toString(), tokenLine);
                    break;

                case 8: // Exponent part
                    if (c == '+' || c == '-') { state = 10; buf.append(c); idx++; }
                    else if (Character.isDigit(c)) { state = 11; buf.append(c); idx++; }
                    else return new Token(TokenType.INVALID, buf.toString(), tokenLine);
                    break;

                case 10: // Exponent sign
                    if (Character.isDigit(c)) { state = 11; buf.append(c); idx++; }
                    else return new Token(TokenType.INVALID, buf.toString(), tokenLine);
                    break;

                case 11: // Exponent power
                    if (Character.isDigit(c)) { buf.append(c); idx++; }
                    else return new Token(TokenType.REAL_NUMBER, buf.toString(), tokenLine);
                    break;

                case 12: // Identifiers
                    if (Character.isLetterOrDigit(c) || c == '_') {
                        buf.append(c); idx++;
                    } else {
                        return checkIfKeyword(buf.toString(), tokenLine);
                    }
                    break;

                case 13: // Character literals
                    if (c == '\\') { // Handle escapes manually
                        idx++;
                        char next = input.charAt(idx);
                        if (next == 'n') buf.append('\n');
                        else if (next == 't') buf.append('\t');
                        else buf.append(next);
                        idx++;
                    } else if (c != '\'') {
                        buf.append(c); idx++;
                    } else {
                        idx++; return new Token(TokenType.CHAR, buf.toString(), tokenLine);
                    }
                    break;

                case 14: // String literals
                    if (c == '\\') {
                        idx++;
                        char next = input.charAt(idx);
                        if (next == 'n') buf.append('\n');
                        else if (next == 't') buf.append('\t');
                        else buf.append(next);
                        idx++;
                    } else if (c != '"' && !isAtEnd()) {
                        if (c == '\n') line++;
                        buf.append(c); idx++;
                    } else {
                        idx++; return new Token(TokenType.STRING, buf.toString(), tokenLine);
                    }
                    break;
            }
        }
    }

    public static void main(String[] args) {
        try {
            // Path to your .c file
            String path = "C:\\Users\\tamas\\Desktop\\CT_PROIECT\\src\\compiler\\lexer\\testers\\9.c";
            String input = Files.readString(Paths.get(path));

            Lexer lexer = new Lexer(input);
            List<Token> tokens = new ArrayList<>();
            Token t;

            System.out.println("--- LEXER TOKENS ---");
            do {
                t = lexer.getNextToken();
                tokens.add(t);
                System.out.println(t.toString());
            } while (t.getTokenType() != TokenType.EOF);

        } catch (Exception e) {
            System.out.println("Lexer Error: " + e.getMessage());
        }
    }
}