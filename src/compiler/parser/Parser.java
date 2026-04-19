package compiler.parser;

import compiler.lexer.Token;
import compiler.lexer.TokenType;
import java.util.List;

public class Parser {
    private List<Token> tokens; // The list we got from the Lexer
    private int crtIdx = 0;     // Our "finger" pointing to the current token

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    // Helper: Returns the token our "finger" is currently pointing at
    private Token crtTk() {
        return tokens.get(crtIdx);
    }

    // The most important function: "consume"
    // If the current token matches the type we want, move the finger forward.
    private boolean consume(TokenType expectedType) {
        if (crtTk().getTokenType() == expectedType) {
            System.out.println("Consumed: " + expectedType);
            crtIdx++;
            return true;
        }
        return false;
    }

    // Rule: unit ::= END
    // This is the "start" of our grammar.
    public boolean unit() {
        if (consume(TokenType.EOF)) {
            return true;
        }
        return false;
    }
}