package compiler.lexer;
import compiler.parser.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Lexer {

    private String input;
    private int idx = 0;
    private int line = 1;

    public Lexer(String input) {
        this.input = input + "\0"; // Add a null terminator for safety
    }

    private boolean isAtEnd() {
        return idx >= input.length() || input.charAt(idx) == '\0';
    }

    private char currentChar() {
        return input.charAt(idx);
    }

    private char peek() {
        if (idx + 1 >= input.length()) return '\0';
        return input.charAt(idx + 1);
    }

    private boolean isHexDigit(char c) {
        return Character.isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private void skipWhiteSpace() {
        while (!isAtEnd()) {
            char c = currentChar();
            if (c == '\n') {
                line++;
                idx++;
            } else if (c == ' ' || c == '\t' || c == '\r') {
                idx++;
            } else if (c == '/' && peek() == '/') {
                idx += 2;
                while (!isAtEnd() && currentChar() != '\n') idx++;
            } else {
                break;
            }
        }
    }

    // KeyWord helper from your original code
    private Token checkIfKeyWord(String text, int tokenLine) {
        switch (text) {
            case "if": return new Token(TokenType.IF, text, tokenLine);
            case "else": return new Token(TokenType.ELSE, text, tokenLine);
            case "for": return new Token(TokenType.FOR, text, tokenLine);
            case "break": return new Token(TokenType.BREAK, text, tokenLine);
            case "continue": return new Token(TokenType.CONTINUE, text, tokenLine);
            case "switch": return new Token(TokenType.SWITCH, text, tokenLine);
            case "case": return new Token(TokenType.CASE, text, tokenLine);
            case "int": return new Token(TokenType.INT, text, tokenLine);
            case "double": return new Token(TokenType.DOUBLE, text, tokenLine);
            case "float": return new Token(TokenType.FLOAT, text, tokenLine);
            case "boolean": return new Token(TokenType.BOOLEAN, text, tokenLine);
            case "void": return new Token(TokenType.VOID, text, tokenLine);
            case "return": return new Token(TokenType.RETURN, text, tokenLine);
            case "while": return new Token(TokenType.WHILE, text, tokenLine);
            case "enum": return new Token(TokenType.ENUM, text, tokenLine);
            case "struct": return new Token(TokenType.STRUCT, text, tokenLine);
            case "char": return new Token(TokenType.CHAR, text, tokenLine);
            case "true":
            case "false": return new Token(TokenType.BOOL_VAL, text, tokenLine);
            default: return new Token(TokenType.IDENTIFIER, text, tokenLine);
        }
    }

    // Operator helper from your original code
    private Token checkIfOperator(char c, int tokenLine) {
        switch (c) {
            case '+':
                if (peek() == '+') { idx += 2; return new Token(TokenType.PLUS, "++", tokenLine); }
                idx++; return new Token(TokenType.PLUS, "+", tokenLine);
            case '-':
                if (peek() == '-') { idx += 2; return new Token(TokenType.MINUS, "--", tokenLine); }
                idx++; return new Token(TokenType.MINUS, "-", tokenLine);
            case '*': idx++; return new Token(TokenType.MULTIPLY, "*", tokenLine);
            case '/': idx++; return new Token(TokenType.DIVIDE, "/", tokenLine);
            case '%': idx++; return new Token(TokenType.MODULO, "%", tokenLine);
            case '=':
                if (peek() == '=') { idx += 2; return new Token(TokenType.EQUAL, "==", tokenLine); }
                idx++; return new Token(TokenType.ASSIGN, "=", tokenLine);
            case '!':
                if (peek() == '=') { idx += 2; return new Token(TokenType.NOT_EQUAL, "!=", tokenLine); }
                idx++; return new Token(TokenType.NOT, "!", tokenLine);
            case '>':
                if (peek() == '=') { idx += 2; return new Token(TokenType.GREATER_EQUAL, ">=", tokenLine); }
                idx++; return new Token(TokenType.GREATER, ">", tokenLine);
            case '<':
                if (peek() == '=') { idx += 2; return new Token(TokenType.LESS_EQUAL, "<=", tokenLine); }
                idx++; return new Token(TokenType.LESS, "<", tokenLine);
            case '&':
                if (peek() == '&') { idx += 2; return new Token(TokenType.AND, "&&", tokenLine); }
                idx++; return new Token(TokenType.BIT_AND, "&", tokenLine);
            case '|':
                if (peek() == '|') { idx += 2; return new Token(TokenType.OR, "||", tokenLine); }
                idx++; return new Token(TokenType.BIT_OR, "|", tokenLine);
            case '.': idx++; return new Token(TokenType.DOT, ".", tokenLine);
            case ';': idx++; return new Token(TokenType.SEMICOLON, ";", tokenLine);
            case ',': idx++; return new Token(TokenType.COMMA, ",", tokenLine);
            case '(': idx++; return new Token(TokenType.LPAREN, "(", tokenLine);
            case ')': idx++; return new Token(TokenType.RPAREN, ")", tokenLine);
            case '[': idx++; return new Token(TokenType.LBRACK, "[", tokenLine);
            case ']': idx++; return new Token(TokenType.RBRACK, "]", tokenLine);
            case '{': idx++; return new Token(TokenType.LBRACE, "{", tokenLine);
            case '}': idx++; return new Token(TokenType.RBRACE, "}", tokenLine);
            default: idx++; return new Token(TokenType.INVALID, String.valueOf(c), tokenLine);
        }
    }

    public Token getNextToken() {
        skipWhiteSpace();

        if (isAtEnd()) {
            return new Token(TokenType.EOF, "", line);
        }

        int state = 0; // State variable
        StringBuilder buf = new StringBuilder();
        int tokenLine = line;

        while (true) {
            char c = currentChar();

            switch (state) {
                case 0: // START
                    if (Character.isLetter(c) || c == '_') {
                        state = 1; buf.append(c); idx++;
                    } else if (c == '0') {
                        state = 2; buf.append(c); idx++;
                    } else if (Character.isDigit(c)) {
                        state = 3; buf.append(c); idx++;
                    } else if (c == '"') {
                        state = 8; idx++;
                    } else if (c == '\'') {
                        state = 9; idx++;
                    }else if (c == '.') {
                        // FIX: If there is no digit after the dot, it's just a DOT operator
                        if (!Character.isDigit(peek())) {
                            return checkIfOperator(c, tokenLine);
                        }
                        // Otherwise, it's a real number start (.5)
                        state = 4; buf.append('0'); buf.append(c); idx++;
                    }
                    else {
                        return checkIfOperator(c, tokenLine);
                    }
                    break;

                case 1: // IDENTIFIER
                    if (Character.isLetterOrDigit(c) || c == '_') {
                        buf.append(c); idx++;
                    } else {
                        return checkIfKeyWord(buf.toString(), tokenLine);
                    }
                    break;

                case 2: // Starting with 0 (check Base 2, 8, 16 or Real)
                    if (c == 'x' || c == 'X') {
                        buf.append(c); idx++;
                        while (isHexDigit(currentChar())) { buf.append(currentChar()); idx++; }
                        return new Token(TokenType.BASE16_NUMBER, buf.toString(), tokenLine);
                    } else if (c == 'b' || c == 'B') {
                        buf.append(c); idx++;
                        while (currentChar() == '0' || currentChar() == '1') { buf.append(currentChar()); idx++; }
                        return new Token(TokenType.BASE2_NUMBER, buf.toString(), tokenLine);
                    } else if (c >= '0' && c <= '7') {
                        while (currentChar() >= '0' && currentChar() <= '7') { buf.append(currentChar()); idx++; }
                        return new Token(TokenType.BASE8_NUMBER, buf.toString(), tokenLine);
                    } else if (c == '.') {
                        state = 4; buf.append(c); idx++;
                    } else {
                        return new Token(TokenType.BASE10_NUMBER, buf.toString(), tokenLine);
                    }
                    break;

                case 3: // BASE 10 (Integer part)
                    if (Character.isDigit(c)) {
                        buf.append(c); idx++;
                    } else if (c == '.') {
                        state = 4; buf.append(c); idx++;
                    } else if (c == 'e' || c == 'E') {
                        state = 5; buf.append(c); idx++;
                    } else {
                        return new Token(TokenType.BASE10_NUMBER, buf.toString(), tokenLine);
                    }
                    break;

                case 4: // REAL (Fractional part)
                    if (Character.isDigit(c)) {
                        buf.append(c); idx++;
                    } else if (c == 'e' || c == 'E') {
                        state = 5; buf.append(c); idx++;
                    } else {
                        return new Token(TokenType.REAL_NUMBER, buf.toString(), tokenLine);
                    }
                    break;

                case 5: // REAL (Exponent part start)
                    if (c == '+' || c == '-') {
                        state = 6; buf.append(c); idx++;
                    } else if (Character.isDigit(c)) {
                        state = 7; buf.append(c); idx++;
                    } else return new Token(TokenType.INVALID, buf.toString(), tokenLine);
                    break;

                case 6: // Exponent Sign
                    if (Character.isDigit(c)) {
                        state = 7; buf.append(c); idx++;
                    } else return new Token(TokenType.INVALID, buf.toString(), tokenLine);
                    break;

                case 7: // Exponent digits
                    if (Character.isDigit(c)) {
                        buf.append(c); idx++;
                    } else {
                        return new Token(TokenType.REAL_NUMBER, buf.toString(), tokenLine);
                    }
                    break;

                case 8: // STRING
                    if (c == '\\') { // If we see a backslash
                        idx++; // Skip the backslash
                        char next = currentChar();
                        // Handle common escapes
                        if (next == 'n') buf.append('\n');
                        else if (next == 't') buf.append('\t');
                        else if (next == '"') buf.append('"');
                        else if (next == '\\') buf.append('\\');
                        else buf.append(next); // Just append the char if it's something else
                        idx++;
                    } else if (c != '"' && !isAtEnd()) {
                        if (c == '\n') line++;
                        buf.append(c); 
                        idx++;
                    } else {
                        idx++; // skip closing "
                        return new Token(TokenType.STRING, buf.toString(), tokenLine);
                    }
                    break;

                case 9: // CHAR
                    if (c == '\\') { // Handle backslash in char too like '\\'
                        idx++;
                        char next = currentChar();
                        if (next == 'n') buf.append('\n');
                        else if (next == 't') buf.append('\t');
                        else if (next == '\'') buf.append('\'');
                        else if (next == '\\') buf.append('\\');
                        else buf.append(next);
                        idx++;
                    } else {
                        buf.append(c);
                        idx++;
                    }
                    if (currentChar() == '\'') idx++; // skip closing '
                    return new Token(TokenType.CHAR, buf.toString(), tokenLine);
            }
        }
    }

    public static void main(String[] args) {
        try {
            String path = "C:\\Users\\tamas\\Desktop\\CT_PROIECT\\src\\compiler\\parser\\testers\\test2.c";
            String input = Files.readString(Paths.get(path));

            // Pass the string to the Lexer
            Lexer lx = new Lexer(input);
            
            // Collect all tokens from the Lexer into a List
            List<Token> tokens = new ArrayList<>();
            Token t;
            do {
                t = lx.getNextToken();
                tokens.add(t);
            } while (t.getTokenType() != TokenType.EOF);

            // Pass that List of tokens to the Parser
            Parser parser = new Parser(tokens);
            
            // Start parsing from the top-level rule (unit)
            if (parser.unit()) {
                System.out.println("Syntax is CORRECT!");
            } else {
                // If it returns false, it found a sequence it doesn't recognize
                System.out.println("Syntax ERROR!");
            }

        } catch (IOException e) {
            System.out.println("Error reading file: " + e.getMessage());
        }
    }
}