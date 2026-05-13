package compiler.lexer;

public enum TokenType {
    
    IDENTIFIER,

    IF,
    ELSE,
    FOR,
    BREAK,
    CONTINUE,
    SWITCH,
    CASE,
    INT,
    DOUBLE,
    FLOAT,
    BOOLEAN,
    VOID,
    RETURN,
    WHILE,
    ENUM,
    STRUCT,

    BASE10_NUMBER,
    BASE16_NUMBER,
    BASE8_NUMBER,
    BASE2_NUMBER,
    REAL_NUMBER,
    BOOL_VAL,
    STRING,
    CT_CHAR,     // character literal: 'a'
    CHAR,        // keyword: char

    ASSIGN,
    EQUAL,
    NOT,
    NOT_EQUAL,

    LESS,
    GREATER,
    LESS_EQUAL,
    GREATER_EQUAL,
    
    AND,
    OR,
    BIT_AND,
    BIT_OR,

    PLUS,
    MINUS,
    MULTIPLY,
    DIVIDE,
    MODULO,

    LPAREN,
    RPAREN,
    LBRACK,
    RBRACK,
    LBRACE,
    RBRACE,
    COMMA,
    SEMICOLON,
    DOT,

    EOF,
    INVALID
}