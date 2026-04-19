package compiler.parser;

import compiler.lexer.Token;
import compiler.lexer.TokenType;
import java.util.List;

public class Parser {
    private List<Token> tokens;
    private int crtIdx = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    private Token crtTk() {
        return tokens.get(crtIdx);
    }

    private boolean consume(TokenType type) {
        if (crtTk().getTokenType() == type) {
            crtIdx++;
            return true;
        }
        return false;
    }

    // Rule: unit ::= (varDef)* END
    public boolean unit() {
        while (!consume(TokenType.EOF)) {
            int startIdx = crtIdx;

            // Try to parse a struct definition
            if (structDef()) {
                continue;
            }
            crtIdx = startIdx; // Backtrack if it wasn't a struct

            // Try to parse a variable definition
            if (varDef()) {
                continue;
            }
            crtIdx = startIdx; // Backtrack

            // If we reach here, it's a syntax error (neither struct, var, nor EOF)
            System.out.println("Syntax Error: Expected struct or variable at line " + crtTk().getLine());
            return false;
        }
        return true;
    }

    // Rule: structDef ::= STRUCT ID LACC varDef* RACC SEMICOLON
    private boolean structDef() {
        int startIdx = crtIdx;

        if (consume(TokenType.STRUCT)) {
            if (consume(TokenType.IDENTIFIER)) {
                if (consume(TokenType.LBRACE)) { // This is LACC
                    
                    // Consume zero or more variable definitions
                    while (varDef()); 
                    
                    if (consume(TokenType.RBRACE)) { // This is RACC
                        if (consume(TokenType.SEMICOLON)) {
                            return true;
                        }
                    }
                }
            }
        }

        crtIdx = startIdx; // Backtrack if any part of the struct failed
        return false;
    }

    // Rule: varDef ::= typeBase ID arrayDecl? SEMICOLON
    private boolean varDef() {
        int startIdx = crtIdx; // Save position for backtracking

        if (typeBase()) {
            if (consume(TokenType.IDENTIFIER)) {
                // arrayDecl is optional (a?), so we call it but don't care if it returns false
                arrayDecl(); 
                
                if (consume(TokenType.SEMICOLON)) {
                    return true;
                }
            }
        }

        crtIdx = startIdx; // Restore finger position if rule failed
        return false;
    }

    // Rule: typeBase ::= INT | DOUBLE | CHAR | STRUCT ID
    private boolean typeBase() {
        if (consume(TokenType.INT)) return true;
        if (consume(TokenType.DOUBLE)) return true;
        if (consume(TokenType.CHAR)) return true;
        
        if (consume(TokenType.STRUCT)) {
            if (consume(TokenType.IDENTIFIER)) return true;
        }
        return false;
    }

    // Rule: arrayDecl ::= LBRACKET CT_INT? RBRACKET
    private boolean arrayDecl() {
        int startIdx = crtIdx;
        if (consume(TokenType.LBRACK)) {
            // The number inside [ ] is optional: [ ] or [10]
            consume(TokenType.BASE10_NUMBER); 
            
            if (consume(TokenType.RBRACK)) {
                return true;
            }
        }
        crtIdx = startIdx;
        return false;
    }
}