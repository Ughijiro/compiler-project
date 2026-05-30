package compiler.lexer;

// A token is the result of lexical analysis.
// It contains:
// - the token type, such as IDENTIFIER or INT
// - the lexeme/value, such as "x" or "123"
// - the source line, used for error messages


public class Token{

    private TokenType type;
    private String value;
    private int line;

    public Token(TokenType type, String value, int line){
        this.type = type;
        this.value = value;
        this.line = line;
    }

    public TokenType getTokenType(){
        return this.type;
    }

    public String getValue(){
        return this.value;
    }

    public int getLine(){
        return this.line;
    }

    public String toString(){
        return this.type + ": " + this.value + " (line " + this.line + ")" ;  
    }
}