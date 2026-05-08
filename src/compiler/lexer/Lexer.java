package compiler.lexer;

import java.nio.file.Files;
import java.nio.file.Paths;

public class Lexer {

    private String input;
    private int idx = 0;
    private int line = 1;

    private enum State {
        START,
        IDENTIFIER,

        ZERO,
        BASE8,
        BASE16,
        BASE2,
        BASE10,

        FRAC,
        EXP,
        SIGN,
        POWER,

        STRING,
        CHAR,

        COMMENT,

        NOT,
        ASSIGN
    }

    public Lexer(String input) {
        this.input = input;
    }

    private boolean isAtEnd() {
        return idx >= input.length();
    }

    private char currentChar() {
        if (isAtEnd()) return '\0';
        return input.charAt(idx);
    }

    private void skipSpaces() {
        while (!isAtEnd()) {
            char c = currentChar();

            if (c == '\n') {
                line++;
                idx++;
            } else if (c == ' ' || c == '\t' || c == '\r') {
                idx++;
            } else {
                break;
            }
        }
    }

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
            case "true":
            case "false":
                return new Token(TokenType.BOOL_VAL, word, tokenLine);
            default:
                return new Token(TokenType.IDENTIFIER, word, tokenLine);
        }
    }

    private Token makeOperator(char c, int line) {
        idx++;

        switch (c) {
            case '+':
                if (currentChar() == '+') { idx++; return new Token(TokenType.PLUS, "++", line); }
                return new Token(TokenType.PLUS, "+", line);
            case '-':
                if (currentChar() == '-') { idx++; return new Token(TokenType.MINUS, "--", line); }
                return new Token(TokenType.MINUS, "-", line);
            case '*': return new Token(TokenType.MULTIPLY, "*", line);
            case '/': return new Token(TokenType.DIVIDE, "/", line);
            case '=':
                if (currentChar() == '=') { idx++; return new Token(TokenType.EQUAL, "==", line); }
                return new Token(TokenType.ASSIGN, "=", line);
            case '!':
                if (currentChar() == '=') { idx++; return new Token(TokenType.NOT_EQUAL, "!=", line); }
                return new Token(TokenType.NOT, "!", line);
            case '<':
                if (currentChar() == '=') { idx++; return new Token(TokenType.LESS_EQUAL, "<=", line); }
                return new Token(TokenType.LESS, "<", line);
            case '>':
                if (currentChar() == '=') { idx++; return new Token(TokenType.GREATER_EQUAL, ">=", line); }
                return new Token(TokenType.GREATER, ">", line);
            case '&':
                if (currentChar() == '&') { idx++; return new Token(TokenType.AND, "&&", line); }
                return new Token(TokenType.BIT_AND, "&", line);
            case '|':
                if (currentChar() == '|') { idx++; return new Token(TokenType.OR, "||", line); }
                return new Token(TokenType.BIT_OR, "|", line);
            case '.': return new Token(TokenType.DOT, ".", line);
            case ';': return new Token(TokenType.SEMICOLON, ";", line);
            case ',': return new Token(TokenType.COMMA, ",", line);
            case '(': return new Token(TokenType.LPAREN, "(", line);
            case ')': return new Token(TokenType.RPAREN, ")", line);
            case '[': return new Token(TokenType.LBRACK, "[", line);
            case ']': return new Token(TokenType.RBRACK, "]", line);
            case '{': return new Token(TokenType.LBRACE, "{", line);
            case '}': return new Token(TokenType.RBRACE, "}", line);
            default: return new Token(TokenType.INVALID, String.valueOf(c), line);
        }
    }

    public Token getNextToken() {
        skipSpaces();
        if (isAtEnd()) return new Token(TokenType.EOF, "", line);

        State state = State.START;
        StringBuilder buf = new StringBuilder();
        int tokenLine = line;

        while (true) {
            char c = currentChar();
            switch (state) {
                case START:
                    if (Character.isLetter(c) || c == '_') { state = State.IDENTIFIER; buf.append(c); idx++; }
                    else if (c == '0') { state = State.ZERO; buf.append(c); idx++; }
                    else if (c >= '1' && c <= '9') { state = State.BASE10; buf.append(c); idx++; }
                    else if (c == '"') { state = State.STRING; idx++; }
                    else if (c == '\'') { state = State.CHAR; idx++; }
                    else if (c == '/') { state = State.COMMENT; idx++; }
                    else return makeOperator(c, tokenLine);
                    break;

                case IDENTIFIER:
                    if (Character.isLetterOrDigit(c) || c == '_') { buf.append(c); idx++; }
                    else return checkIfKeyword(buf.toString(), tokenLine);
                    break;

                case ZERO:
                    if (c == 'x' || c == 'X') { state = State.BASE16; buf.append(c); idx++; }
                    else if (c == 'b' || c == 'B') { state = State.BASE2; buf.append(c); idx++; }
                    else if (c >= '0' && c <= '7') { state = State.BASE8; buf.append(c); idx++; }
                    else if (c == '.') { state = State.FRAC; buf.append('.'); idx++; }
                    else if (c == 'e' || c == 'E') { state = State.EXP; buf.append(c); idx++; }
                    else return new Token(TokenType.BASE10_NUMBER, buf.toString(), tokenLine);
                    break;

                case BASE10:
                    if (Character.isDigit(c)) { buf.append(c); idx++; }
                    else if (c == '.') { state = State.FRAC; buf.append('.'); idx++; }
                    else if (c == 'e' || c == 'E') { state = State.EXP; buf.append(c); idx++; }
                    else return new Token(TokenType.BASE10_NUMBER, buf.toString(), tokenLine);
                    break;

                case BASE16:
                    if (Character.isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')) { buf.append(c); idx++; }
                    else return new Token(TokenType.BASE16_NUMBER, buf.toString(), tokenLine);
                    break;
                
                case BASE8:
                    if (c >= '0' && c <= '7') { buf.append(c); idx++; }
                    else return new Token(TokenType.BASE8_NUMBER, buf.toString(), tokenLine);
                    break;

                case BASE2:
                    if (c == '0' || c == '1') { buf.append(c); idx++; }
                    else return new Token(TokenType.BASE2_NUMBER, buf.toString(), tokenLine);
                    break;

                case FRAC:
                    if (Character.isDigit(c)) { buf.append(c); idx++; }
                    else if (c == 'e' || c == 'E') { state = State.EXP; buf.append(c); idx++; }
                    else return new Token(TokenType.REAL_NUMBER, buf.toString(), tokenLine);
                    break;

                case EXP:
                    if (c == '+' || c == '-') { state = State.SIGN; buf.append(c); idx++; }
                    else if (Character.isDigit(c)) { state = State.POWER; buf.append(c); idx++; }
                    else return new Token(TokenType.INVALID, buf.toString(), tokenLine);
                    break;

                case SIGN:
                    if (Character.isDigit(c)) { state = State.POWER; buf.append(c); idx++; }
                    else return new Token(TokenType.INVALID, buf.toString(), tokenLine);
                    break;

                case POWER:
                    if (Character.isDigit(c)) { buf.append(c); idx++; }
                    else return new Token(TokenType.REAL_NUMBER, buf.toString(), tokenLine);
                    break;

                case COMMENT:
                    if (c == '/') {
                        while (!isAtEnd() && currentChar() != '\n') idx++;
                        skipSpaces();
                        if (isAtEnd()) return new Token(TokenType.EOF, "", line);
                        state = State.START;
                        buf.setLength(0);
                    } else return new Token(TokenType.DIVIDE, "/", tokenLine);
                    break;

                  case STRING:
                    if (c == '\\') {
                        idx++;
                        char next = currentChar();
                        if (next == 'n') buf.append('\n');
                        else if (next == 't') buf.append('\t');
                        else buf.append(next);
                        idx++;
                    }
                    else if (c == '"') {
                        idx++;
                        return new Token(TokenType.STRING, buf.toString(), tokenLine);
                    }
                    else {
                        buf.append(c);
                        if (c == '\n') line++;
                        idx++;
                    }
                    break;

                case CHAR:
                    char value;
                    if (c == '\\') {
                        idx++;
                        char next = currentChar();
                        if (next == 'n') value = '\n';
                        else if (next == 't') value = '\t';
                        else value = next;
                        idx++;
                    } else {
                        value = c;
                        idx++;
                    }

                    if (currentChar() == '\'') {
                        idx++;
                        return new Token(TokenType.CHAR, String.valueOf(value), tokenLine);
                    }
                    return new Token(TokenType.INVALID, "", tokenLine);
                default:
                    throw new RuntimeException("Unknown state: " + state);
            }
        }
    }

    public static void main(String[] args) {
        try {
            String path = "C:\\Users\\tamas\\Desktop\\CT_PROIECT\\src\\compiler\\lexer\\testers\\8.c";
            String input = Files.readString(Paths.get(path));

            Lexer lexer = new Lexer(input);
            Token t;

            do {
                t = lexer.getNextToken();
                System.out.println(t);
            } while (t.getTokenType() != TokenType.EOF);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}