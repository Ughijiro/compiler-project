package compiler.lexer;

public enum TokenType {

    IDENTIFIER,
    INTEGER_LITERAL,
    FLOAT_LITERAL,
    STRING_LITERAL,
    CHAR_LITERAL,

    ASSIGN,
    EQUAL,
    NOT,
    NOT_EQUAL,

    LESS,
    LESS_EQUAL,
    GREATER,
    GREATER_EQUAL,

    AND,
    OR,

    PLUS,
    MINUS,
    MULTIPLY,
    DIVIDE,

    LPAREN,
    RPAREN,
    SEMICOLON,

    EOF,
    INVALID
}

