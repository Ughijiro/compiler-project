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

    public boolean unit() {
        while (!consume(TokenType.EOF)) {
            int startIdx = crtIdx;

            if (structDef()) continue;
            crtIdx = startIdx;

            if (fnDef()) continue; // Now we try to parse functions!
            crtIdx = startIdx;

            if (varDef()) continue;
            crtIdx = startIdx;

            System.out.println("Syntax Error: Unexpected token at line " + crtTk().getLine());
            return false;
        }
        return true;
    }

    // Rule: fnDef ::= (typeBase | VOID) ID LPAR (fnParam (COMMA fnParam)* )? RPAR stmCompound
    private boolean fnDef() {
        int startIdx = crtIdx;

        // Check for return type
        if (typeBase() || consume(TokenType.VOID)) {
            if (consume(TokenType.IDENTIFIER)) {
                if (consume(TokenType.LPAREN)) {
                    
                    // Parameters are optional: (fnParam (COMMA fnParam)* )?
                    if (fnParam()) {
                        while (consume(TokenType.COMMA)) {
                            if (!fnParam()) return false;
                        }
                    }
                    
                    if (consume(TokenType.RPAREN)) { // Match your Lexer's RPARAN
                        if (stmCompound()) {
                            return true;
                        }
                    }
                }
            }
        }

        crtIdx = startIdx;
        return false;
    }

    // Rule: fnParam ::= typeBase ID arrayDecl?
    private boolean fnParam() {
        int startIdx = crtIdx;
        if (typeBase()) {
            if (consume(TokenType.IDENTIFIER)) {
                arrayDecl(); // optional
                return true;
            }
        }
        crtIdx = startIdx;
        return false;
    }

    // Rule: stmCompound ::= LACC (varDef | stm)* RACC
    // For now, this only accepts variables inside { }
    private boolean stmCompound() {
        int startIdx = crtIdx;
        if (consume(TokenType.LBRACE)) {
            
            while (varDef() || stm()); // Accept variables or statements
            
            if (consume(TokenType.RBRACE)) {
                return true;
            }
        }
        crtIdx = startIdx;
        return false;
    }

    // Rule: stm ::= stmCompound | IF(...) | WHILE(...) | FOR(...) | BREAK; | RETURN expr?; | expr?;
    private boolean stm() {
        int startIdx = crtIdx;

        // 1. stm ::= stmCompound (Nested braces { })
        if (stmCompound()) return true;
        crtIdx = startIdx;

        // 2. stm ::= IF LPAR expr RPAR stm (ELSE stm)?
        if (consume(TokenType.IF)) {
            if (consume(TokenType.LPAREN)) {
                if (expr()) {
                    if (consume(TokenType.RPAREN)) {
                        if (stm()) {
                            // The ELSE part is optional
                            int elseIdx = crtIdx;
                            if (consume(TokenType.ELSE)) {
                                if (!stm()) crtIdx = elseIdx; 
                            }
                            return true;
                        }
                    }
                }
            }
            crtIdx = startIdx; return false;
        }

        // 3. stm ::= WHILE LPAR expr RPAR stm
        if (consume(TokenType.WHILE)) {
            if (consume(TokenType.LPAREN)) {
                if (expr()) {
                    if (consume(TokenType.RPAREN)) {
                        if (stm()) return true;
                    }
                }
            }
            crtIdx = startIdx; return false;
        }

        // 4. stm ::= RETURN expr? SEMICOLON
        if (consume(TokenType.RETURN)) {
            expr(); // optional expression
            if (consume(TokenType.SEMICOLON)) return true;
            crtIdx = startIdx; return false;
        }

        // 5. stm ::= expr? SEMICOLON (This is for things like x = 10; or function calls)
        if (expr()) {
            if (consume(TokenType.SEMICOLON)) return true;
            crtIdx = startIdx; 
        }
        
        // 6. stm ::= SEMICOLON (The empty statement ;)
        if (consume(TokenType.SEMICOLON)) return true;

        // 7. stm ::= FOR LPAR expr? ; expr? ; expr? RPAR stm
        if (consume(TokenType.FOR)) {
            if (consume(TokenType.LPAREN)) {
                expr(); // optional init
                if (consume(TokenType.SEMICOLON)) {
                    expr(); // optional condition
                    if (consume(TokenType.SEMICOLON)) {
                        expr(); // optional step
                        if (consume(TokenType.RPAREN)) {
                            if (stm()) return true;
                        }
                    }
                }
            }
            crtIdx = startIdx; return false;
        }

        // 8. stm ::= BREAK SEMICOLON
        if (consume(TokenType.BREAK)) {
            if (consume(TokenType.SEMICOLON)) return true;
            crtIdx = startIdx; return false;
        }
        
        crtIdx = startIdx;
        return false;

    }

    // Rule: expr ::= exprAssign
    private boolean expr() {
        return exprAssign();
    }

    // Rule: exprAssign ::= exprUnary ASSIGN exprAssign | exprOr
    private boolean exprAssign() {
        int startIdx = crtIdx;
        
        // Try exprUnary = exprAssign
        if (exprUnary()) {
            if (consume(TokenType.ASSIGN)) {
                if (exprAssign()) return true;
            }
        }
        crtIdx = startIdx; // Backtrack to try exprOr

        if (exprOr()) return true;

        crtIdx = startIdx;
        return false;
    }

    // For now, let's jump straight to the bottom to keep it working!
    // Real parser would have Or, And, Eq, Rel, Add, Mul in between.
    private boolean exprOr() { return exprPrimary(); }
    private boolean exprUnary() { return exprPrimary(); }

    // Rule: exprPrimary ::= ID | CT_INT | CT_REAL | LPAR expr RPAR
    private boolean exprPrimary() {
        int startIdx = crtIdx;

        if (consume(TokenType.IDENTIFIER)) {
            // Check for optional function call: ID ( expr? )
            if (consume(TokenType.LPAREN)) {
                expr(); // optional
                consume(TokenType.RPAREN);
            }
            return true;
        }
        
        if (consume(TokenType.BASE10_NUMBER)) return true;
        if (consume(TokenType.REAL_NUMBER)) return true;
        if (consume(TokenType.STRING)) return true;

        if (consume(TokenType.LPAREN)) {
            if (expr()) {
                if (consume(TokenType.RPAREN)) return true;
            }
        }

        crtIdx = startIdx;
        return false;
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