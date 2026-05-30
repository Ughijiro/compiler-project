package compiler.lexer;

import java.nio.file.Files;
import java.nio.file.Paths;

// The lexer splits the source code into tokens.
// Spaces and comments are skipped because the parser does not need them.

public class Lexer {

    private String input;
    private int idx = 0;
    private int line = 1;

    // Explicit states are used to implement the transition diagram from the course.
    // Each state represents that we are currently recognizing a specific token type.

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

        COMMENT
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

    private boolean isHexDigit(char c) {
        return Character.isDigit(c)
                || (c >= 'a' && c <= 'f')
                || (c >= 'A' && c <= 'F');
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

    // Keywords have the same lexical structure as identifiers.
    // First we recognize an identifier, then we check if its text is a reserved keyword.

    private Token checkIfKeyword(String word, int tokenLine) {
        switch (word) {
            case "if": return new Token(TokenType.IF, word, tokenLine);
            case "else": return new Token(TokenType.ELSE, word, tokenLine);
            case "for": return new Token(TokenType.FOR, word, tokenLine);
            case "while": return new Token(TokenType.WHILE, word, tokenLine);
            case "break": return new Token(TokenType.BREAK, word, tokenLine);
            case "continue": return new Token(TokenType.CONTINUE, word, tokenLine);
            case "return": return new Token(TokenType.RETURN, word, tokenLine);

            case "int": return new Token(TokenType.INT, word, tokenLine);
            case "double": return new Token(TokenType.DOUBLE, word, tokenLine);
            case "float": return new Token(TokenType.FLOAT, word, tokenLine);
            case "char": return new Token(TokenType.CHAR, word, tokenLine);
            case "boolean": return new Token(TokenType.BOOLEAN, word, tokenLine);
            case "void": return new Token(TokenType.VOID, word, tokenLine);

            case "struct": return new Token(TokenType.STRUCT, word, tokenLine);
            case "enum": return new Token(TokenType.ENUM, word, tokenLine);
            case "switch": return new Token(TokenType.SWITCH, word, tokenLine);
            case "case": return new Token(TokenType.CASE, word, tokenLine);

            case "true":
            case "false":
                return new Token(TokenType.BOOL_VAL, word, tokenLine);

            default:
                return new Token(TokenType.IDENTIFIER, word, tokenLine);
        }
    }

    private Token makeOperator(char c, int tokenLine) {
        idx++;

        switch (c) {
            case '+':
                if (currentChar() == '+') {
                    idx++;
                    return new Token(TokenType.PLUS, "++", tokenLine);
                }
                return new Token(TokenType.PLUS, "+", tokenLine);

            case '-':
                if (currentChar() == '-') {
                    idx++;
                    return new Token(TokenType.MINUS, "--", tokenLine);
                }
                return new Token(TokenType.MINUS, "-", tokenLine);

            case '*': return new Token(TokenType.MULTIPLY, "*", tokenLine);
            case '/': return new Token(TokenType.DIVIDE, "/", tokenLine);
            case '%': return new Token(TokenType.MODULO, "%", tokenLine);

            case '=':
                if (currentChar() == '=') {
                    idx++;
                    return new Token(TokenType.EQUAL, "==", tokenLine);
                }
                return new Token(TokenType.ASSIGN, "=", tokenLine);

            case '!':
                if (currentChar() == '=') {
                    idx++;
                    return new Token(TokenType.NOT_EQUAL, "!=", tokenLine);
                }
                return new Token(TokenType.NOT, "!", tokenLine);

            case '<':
                if (currentChar() == '=') {
                    idx++;
                    return new Token(TokenType.LESS_EQUAL, "<=", tokenLine);
                }
                return new Token(TokenType.LESS, "<", tokenLine);

            case '>':
                if (currentChar() == '=') {
                    idx++;
                    return new Token(TokenType.GREATER_EQUAL, ">=", tokenLine);
                }
                return new Token(TokenType.GREATER, ">", tokenLine);

            case '&':
                if (currentChar() == '&') {
                    idx++;
                    return new Token(TokenType.AND, "&&", tokenLine);
                }
                return new Token(TokenType.BIT_AND, "&", tokenLine);

            case '|':
                if (currentChar() == '|') {
                    idx++;
                    return new Token(TokenType.OR, "||", tokenLine);
                }
                return new Token(TokenType.BIT_OR, "|", tokenLine);

            case '.': return new Token(TokenType.DOT, ".", tokenLine);
            case ';': return new Token(TokenType.SEMICOLON, ";", tokenLine);
            case ',': return new Token(TokenType.COMMA, ",", tokenLine);
            case '(': return new Token(TokenType.LPAREN, "(", tokenLine);
            case ')': return new Token(TokenType.RPAREN, ")", tokenLine);
            case '[': return new Token(TokenType.LBRACK, "[", tokenLine);
            case ']': return new Token(TokenType.RBRACK, "]", tokenLine);
            case '{': return new Token(TokenType.LBRACE, "{", tokenLine);
            case '}': return new Token(TokenType.RBRACE, "}", tokenLine);

            default:
                return new Token(TokenType.INVALID, String.valueOf(c), tokenLine);
        }
    }

    // getNextToken() returns one token at a time.
    // The parser will later work only with tokens, not raw characters.

    public Token getNextToken() {
        skipSpaces();

        if (isAtEnd()) {
            return new Token(TokenType.EOF, "", line);
        }

        State state = State.START;
        StringBuilder buf = new StringBuilder();
        int tokenLine = line;

        while (true) {
            char c = currentChar();

            switch (state) {

                case START:
                    if (Character.isLetter(c) || c == '_') {
                        state = State.IDENTIFIER;
                        buf.append(c);
                        idx++;
                    }
                    else if (c == '0') {
                        state = State.ZERO;
                        buf.append(c);
                        idx++;
                    }
                    else if (c >= '1' && c <= '9') {
                        state = State.BASE10;
                        buf.append(c);
                        idx++;
                    }
                    else if (c == '"') {
                        state = State.STRING;
                        idx++;
                    }
                    else if (c == '\'') {
                        state = State.CHAR;
                        idx++;
                    }
                    else if (c == '/') {
                        state = State.COMMENT;
                        idx++;
                    }
                    else {
                        return makeOperator(c, tokenLine);
                    }
                    break;

                case IDENTIFIER:
                    if (Character.isLetterOrDigit(c) || c == '_') {
                        buf.append(c);
                        idx++;
                    } else {
                        // the current character does not belong
                        // to the identifier anymore, so we do not consume it here.
                        return checkIfKeyword(buf.toString(), tokenLine);
                    }
                    break;

                case ZERO:
                    if (c == 'x' || c == 'X') {
                        state = State.BASE16;
                        buf.append(c);
                        idx++;
                    }
                    else if (c == 'b' || c == 'B') {
                        state = State.BASE2;
                        buf.append(c);
                        idx++;
                    }
                    else if (c >= '0' && c <= '7') {
                        state = State.BASE8;
                        buf.append(c);
                        idx++;
                    }
                    else if (c == '8' || c == '9') {
                        buf.append(c);
                        idx++;
                        return new Token(TokenType.INVALID, buf.toString(), tokenLine);
                    }
                    else if (c == '.') {
                        state = State.FRAC;
                        buf.append(c);
                        idx++;
                    }
                    else if (c == 'e' || c == 'E') {
                        state = State.EXP;
                        buf.append(c);
                        idx++;
                    }
                    else {
                        return new Token(TokenType.BASE10_NUMBER, buf.toString(), tokenLine);
                    }
                    break;

                case BASE10:
                    if (Character.isDigit(c)) {
                        buf.append(c);
                        idx++;
                    }
                    else if (c == '.') {
                        state = State.FRAC;
                        buf.append(c);
                        idx++;
                    }
                    else if (c == 'e' || c == 'E') {
                        state = State.EXP;
                        buf.append(c);
                        idx++;
                    }
                    else {
                        return new Token(TokenType.BASE10_NUMBER, buf.toString(), tokenLine);
                    }
                    break;

                case BASE8:
                    if (c >= '0' && c <= '7') {
                        buf.append(c);
                        idx++;
                    }
                    else if (c == '8' || c == '9') {
                        buf.append(c);
                        idx++;
                        return new Token(TokenType.INVALID, buf.toString(), tokenLine);
                    }
                    else {
                        return new Token(TokenType.BASE8_NUMBER, buf.toString(), tokenLine);
                    }
                    break;

                case BASE16:
                    if (buf.length() == 2 && !isHexDigit(c)) {
                        return new Token(TokenType.INVALID, buf.toString(), tokenLine);
                    }

                    if (isHexDigit(c)) {
                        buf.append(c);
                        idx++;
                    }
                    else if (Character.isLetterOrDigit(c)) {
                        buf.append(c);
                        idx++;
                        return new Token(TokenType.INVALID, buf.toString(), tokenLine);
                    }
                    else {
                        return new Token(TokenType.BASE16_NUMBER, buf.toString(), tokenLine);
                    }
                    break;

                case BASE2:
                    if (buf.length() == 2 && c != '0' && c != '1') {
                        return new Token(TokenType.INVALID, buf.toString(), tokenLine);
                    }

                    if (c == '0' || c == '1') {
                        buf.append(c);
                        idx++;
                    }
                    else if (Character.isDigit(c)) {
                        buf.append(c);
                        idx++;
                        return new Token(TokenType.INVALID, buf.toString(), tokenLine);
                    }
                    else {
                        return new Token(TokenType.BASE2_NUMBER, buf.toString(), tokenLine);
                    }
                    break;

                case FRAC:
                    if (buf.charAt(buf.length() - 1) == '.' && !Character.isDigit(c)) {
                        return new Token(TokenType.INVALID, buf.toString(), tokenLine);
                    }

                    if (Character.isDigit(c)) {
                        buf.append(c);
                        idx++;
                    }
                    else if (c == 'e' || c == 'E') {
                        state = State.EXP;
                        buf.append(c);
                        idx++;
                    }
                    else {
                        return new Token(TokenType.REAL_NUMBER, buf.toString(), tokenLine);
                    }
                    break;

                case EXP:
                    if (c == '+' || c == '-') {
                        state = State.SIGN;
                        buf.append(c);
                        idx++;
                    }
                    else if (Character.isDigit(c)) {
                        state = State.POWER;
                        buf.append(c);
                        idx++;
                    }
                    else {
                        return new Token(TokenType.INVALID, buf.toString(), tokenLine);
                    }
                    break;

                case SIGN:
                    if (Character.isDigit(c)) {
                        state = State.POWER;
                        buf.append(c);
                        idx++;
                    }
                    else {
                        return new Token(TokenType.INVALID, buf.toString(), tokenLine);
                    }
                    break;

                case POWER:
                    if (Character.isDigit(c)) {
                        buf.append(c);
                        idx++;
                    }
                    else {
                        return new Token(TokenType.REAL_NUMBER, buf.toString(), tokenLine);
                    }
                    break;

                case COMMENT:
                    if (c == '/') {
                        while (!isAtEnd() && currentChar() != '\n') {
                            idx++;
                        }
                        return getNextToken();
                    }
                    else if (c == '*') {
                        idx++;

                        while (!isAtEnd()) {
                            if (currentChar() == '\n') {
                                line++;
                                idx++;
                                continue;
                            }

                            if (currentChar() == '*'
                                    && idx + 1 < input.length()
                                    && input.charAt(idx + 1) == '/') {
                                idx += 2;
                                return getNextToken();
                            }

                            idx++;
                        }

                        return new Token(TokenType.INVALID, "Unterminated comment", tokenLine);
                    }
                    else {
                        return new Token(TokenType.DIVIDE, "/", tokenLine);
                    }

                case STRING:
                    if (isAtEnd()) {
                        return new Token(TokenType.INVALID, "Unterminated string", tokenLine);
                    }

                    if (c == '\\') {
                        idx++;

                        if (isAtEnd()) {
                            return new Token(TokenType.INVALID, "Unterminated string escape", tokenLine);
                        }

                        char next = currentChar();

                        if (next == 'n') buf.append('\n');
                        else if (next == 't') buf.append('\t');
                        else if (next == 'r') buf.append('\r');
                        else if (next == '"') buf.append('"');
                        else if (next == '\\') buf.append('\\');
                        else buf.append(next);

                        idx++;
                    }
                    else if (c == '"') {
                        idx++;
                        return new Token(TokenType.STRING, buf.toString(), tokenLine);
                    }
                    else {
                        if (c == '\n') {
                            return new Token(TokenType.INVALID, "Unterminated string before newline", tokenLine);
                        }

                        buf.append(c);
                        idx++;
                    }
                    break;

                case CHAR:
                    if (isAtEnd()) {
                        return new Token(TokenType.INVALID, "Unterminated char", tokenLine);
                    }

                    char value;

                    if (c == '\\') {
                        idx++;

                        if (isAtEnd()) {
                            return new Token(TokenType.INVALID, "Unterminated char escape", tokenLine);
                        }

                        char next = currentChar();

                        if (next == 'n') value = '\n';
                        else if (next == 't') value = '\t';
                        else if (next == 'r') value = '\r';
                        else if (next == '\'') value = '\'';
                        else if (next == '\\') value = '\\';
                        else value = next;

                        idx++;
                    }
                    else {
                        if (c == '\n') {
                            return new Token(TokenType.INVALID, "Unterminated char before newline", tokenLine);
                        }

                        value = c;
                        idx++;
                    }

                    if (currentChar() == '\'') {
                        idx++;
                        return new Token(TokenType.CT_CHAR, String.valueOf(value), tokenLine);
                    }

                    return new Token(TokenType.INVALID, "Invalid char literal", tokenLine);

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