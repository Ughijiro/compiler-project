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
        if (crtIdx >= tokens.size()) {
            return tokens.get(tokens.size() - 1);
        }
        return tokens.get(crtIdx);
    }

    private boolean consume(TokenType type) {
        if (crtTk().getTokenType() == type) {
            crtIdx++;
            return true;
        }
        return false;
    }

    private void tkerr(String message) {
        Token t = crtTk();
        throw new RuntimeException(
            "Syntax Error at line " + t.getLine() +
            " near '" + t.getValue() +
            "': " + message
        );
    }

    // unit: ( structDef | fnDef | varDef )*EOF
    public boolean unit() {
        while (!consume(TokenType.EOF)) {
            int startIdx = crtIdx;

            if (structDef()) continue;
            crtIdx = startIdx;

            if (fnDef()) continue;
            crtIdx = startIdx;

            if (varDef()) continue;
            crtIdx = startIdx;

            tkerr("Unexpected token at top level");
        }

        return true;
    }

    // structDef: STRUCT ID LBRACE varDef* RBRACE SEMICOLON
    private boolean structDef() {
        int startIdx = crtIdx;

        if (consume(TokenType.STRUCT)) {
            if (consume(TokenType.IDENTIFIER)) {
                if (consume(TokenType.LBRACE)) {

                    while (varDef());

                    if (!consume(TokenType.RBRACE)) {
                        tkerr("Missing } after struct body");
                    }

                    if (!consume(TokenType.SEMICOLON)) {
                        tkerr("Missing ; after struct definition");
                    }

                    return true;
                }
            }
        }

        crtIdx = startIdx;
        return false;
    }

    // fnDef: ( typeBase | VOID ) ID LPAREN ( fnParam ( COMMA fnParam )* )? RPAREN stmCompound
    private boolean fnDef() {
        int startIdx = crtIdx;

        if (typeBase() || consume(TokenType.VOID)) {
            if (consume(TokenType.IDENTIFIER)) {
                if (consume(TokenType.LPAREN)) {

                    if (fnParam()) {
                        while (consume(TokenType.COMMA)) {
                            if (!fnParam()) {
                                tkerr("Invalid function parameter");
                            }
                        }
                    }

                    if (!consume(TokenType.RPAREN)) {
                        tkerr("Missing ) after function parameters");
                    }

                    if (!stmCompound()) {
                        tkerr("Missing function body");
                    }

                    return true;
                }
            }
        }

        crtIdx = startIdx;
        return false;
    }

    // fnParam: typeBase ID arrayDecl?
    private boolean fnParam() {
        int startIdx = crtIdx;

        if (typeBase()) {
            if (consume(TokenType.IDENTIFIER)) {
                arrayDecl();
                return true;
            }
        }

        crtIdx = startIdx;
        return false;
    }

    // varDef: typeBase ID arrayDecl? SEMICOLON
    private boolean varDef() {
        int startIdx = crtIdx;

        if (typeBase()) {
            if (consume(TokenType.IDENTIFIER)) {
                arrayDecl();

                if (consume(TokenType.SEMICOLON)) {
                    return true;
                }

                tkerr("Missing ; after variable declaration");
            }
        }

        crtIdx = startIdx;
        return false;
    }

    // typeBase: INT | DOUBLE | CHAR | STRUCT ID
    private boolean typeBase() {
        if (consume(TokenType.INT)) return true;
        if (consume(TokenType.DOUBLE)) return true;
        if (consume(TokenType.CHAR)) return true;

        int startIdx = crtIdx;

        if (consume(TokenType.STRUCT)) {
            if (consume(TokenType.IDENTIFIER)) {
                return true;
            }
        }

        crtIdx = startIdx;
        return false;
    }

    // arrayDecl: LBRACK CT_INT? RBRACK
    private boolean arrayDecl() {
        int startIdx = crtIdx;

        if (consume(TokenType.LBRACK)) {

            //consume(TokenType.BASE10_NUMBER);
            expr();
            if (consume(TokenType.RBRACK)) {
                return true;
            }

            tkerr("Missing ] in array declaration");
        }

        crtIdx = startIdx;
        return false;
    }

    // stmCompound: LBRACE ( varDef | stm )* RBRACE
    private boolean stmCompound() {
        int startIdx = crtIdx;

        if (consume(TokenType.LBRACE)) {

            while (varDef() || stm());

            if (consume(TokenType.RBRACE)) {
                return true;
            }

            tkerr("Missing } after compound statement");
        }

        crtIdx = startIdx;
        return false;
    }

    // stm:
    //     stmCompound
    //   | IF LPAREN expr RPAREN stm ( ELSE stm )?
    //   | WHILE LPAREN expr RPAREN stm
    //   | FOR LPAREN expr? SEMICOLON expr? SEMICOLON expr? RPAREN stm
    //   | BREAK SEMICOLON
    //   | RETURN expr? SEMICOLON
    //   | expr? SEMICOLON
    private boolean stm() {
        if (stmCompound()) return true;

        if (consume(TokenType.IF)) {
            if (!consume(TokenType.LPAREN)) tkerr("Missing ( after if");
            if (!expr()) tkerr("Invalid expression in if");
            if (!consume(TokenType.RPAREN)) tkerr("Missing ) after if condition");
            if (!stm()) tkerr("Missing statement after if");

            if (consume(TokenType.ELSE)) {
                if (!stm()) tkerr("Missing statement after else");
            }

            return true;
        }

        if (consume(TokenType.WHILE)) {
            if (!consume(TokenType.LPAREN)) tkerr("Missing ( after while");
            if (!expr()) tkerr("Invalid expression in while");
            if (!consume(TokenType.RPAREN)) tkerr("Missing ) after while condition");
            if (!stm()) tkerr("Missing statement after while");

            return true;
        }

        if (consume(TokenType.FOR)) {
            if (!consume(TokenType.LPAREN)) tkerr("Missing ( after for");

            expr();

            if (!consume(TokenType.SEMICOLON)) {
                tkerr("Missing first ; in for");
            }

            expr();

            if (!consume(TokenType.SEMICOLON)) {
                tkerr("Missing second ; in for");
            }

            expr();

            if (!consume(TokenType.RPAREN)) {
                tkerr("Missing ) after for");
            }

            if (!stm()) {
                tkerr("Missing statement after for");
            }

            return true;
        }

        if (consume(TokenType.BREAK)) {
            if (!consume(TokenType.SEMICOLON)) {
                tkerr("Missing ; after break");
            }

            return true;
        }

        if (consume(TokenType.RETURN)) {
            expr();

            if (!consume(TokenType.SEMICOLON)) {
                tkerr("Missing ; after return");
            }

            return true;
        }

        if (expr()) {
            if (!consume(TokenType.SEMICOLON)) {
                tkerr("Missing ; after expression");
            }

            return true;
        }

        return consume(TokenType.SEMICOLON);
    }

    // expr: exprAssign
    private boolean expr() {
        return exprAssign();
    }

    // exprAssign: exprUnary ASSIGN exprAssign | exprOr
    private boolean exprAssign() {
        int startIdx = crtIdx;

        if (exprUnary()) {
            if (consume(TokenType.ASSIGN)) {
                if (exprAssign()) {
                    return true;
                }
            }
        }

        crtIdx = startIdx;
        return exprOr();
    }

    // exprOr: exprAnd ( OR exprAnd )*
    private boolean exprOr() {
        if (exprAnd()) {
            while (consume(TokenType.OR)) {
                if (!exprAnd()) {
                    tkerr("Invalid expression after ||");
                }
            }

            return true;
        }

        return false;
    }

    // exprAnd: exprEq ( AND exprEq )*
    private boolean exprAnd() {
        if (exprEq()) {
            while (consume(TokenType.AND)) {
                if (!exprEq()) {
                    tkerr("Invalid expression after &&");
                }
            }

            return true;
        }

        return false;
    }

    // exprEq: exprRel ( ( EQUAL | NOT_EQUAL ) exprRel )*
    private boolean exprEq() {
        if (exprRel()) {
            while (consume(TokenType.EQUAL) || consume(TokenType.NOT_EQUAL)) {
                if (!exprRel()) {
                    tkerr("Invalid equality expression");
                }
            }

            return true;
        }

        return false;
    }

    // exprRel: exprAdd ( ( LESS | LESS_EQUAL | GREATER | GREATER_EQUAL ) exprAdd )*
    private boolean exprRel() {
        if (exprAdd()) {
            while (consume(TokenType.LESS) ||
                   consume(TokenType.LESS_EQUAL) ||
                   consume(TokenType.GREATER) ||
                   consume(TokenType.GREATER_EQUAL)) {

                if (!exprAdd()) {
                    tkerr("Invalid relational expression");
                }
            }

            return true;
        }

        return false;
    }

    // exprAdd: exprMul ( ( PLUS | MINUS ) exprMul )*
    private boolean exprAdd() {
        if (exprMul()) {
            while (consume(TokenType.PLUS) || consume(TokenType.MINUS)) {
                if (!exprMul()) {
                    tkerr("Invalid additive expression");
                }
            }

            return true;
        }

        return false;
    }

    // exprMul: exprCast ( ( MULTIPLY | DIVIDE | MODULO ) exprCast )*
    private boolean exprMul() {
        if (exprCast()) {
            while (consume(TokenType.MULTIPLY) ||
                   consume(TokenType.DIVIDE) ||
                   consume(TokenType.MODULO)) {

                if (!exprCast()) {
                    tkerr("Invalid multiplicative expression");
                }
            }

            return true;
        }

        return false;
    }

    // exprCast: LPAREN typeBase arrayDecl? RPAREN exprCast | exprUnary
    private boolean exprCast() {
        int startIdx = crtIdx;

        if (consume(TokenType.LPAREN)) {
            if (typeBase()) {
                arrayDecl();

                if (consume(TokenType.RPAREN)) {
                    if (exprCast()) {
                        return true;
                    }
                }
            }
        }

        crtIdx = startIdx;
        return exprUnary();
    }

    // exprUnary: ( MINUS | NOT ) exprUnary | exprPostfix
    private boolean exprUnary() {
        int startIdx = crtIdx;

        if (consume(TokenType.MINUS) || consume(TokenType.NOT)) {
            if (exprUnary()) {
                return true;
            }
        }

        crtIdx = startIdx;
        return exprPostfix();
    }

    // exprPostfix:
    //     exprPrimary
    //   | exprPostfix LBRACK expr RBRACK
    //   | exprPostfix DOT ID
    private boolean exprPostfix() {
        if (exprPrimary()) {
            while (true) {
                if (consume(TokenType.LBRACK)) {
                    if (!expr()) {
                        tkerr("Invalid array index expression");
                    }

                    if (!consume(TokenType.RBRACK)) {
                        tkerr("Missing ] after array index");
                    }

                    continue;
                }

                if (consume(TokenType.DOT)) {
                    if (!consume(TokenType.IDENTIFIER)) {
                        tkerr("Missing field name after .");
                    }

                    continue;
                }

                break;
            }

            return true;
        }

        return false;
    }

    // exprPrimary:
    //     ID ( LPAREN ( expr ( COMMA expr )* )? RPAREN )?
    //   | CT_INT
    //   | CT_REAL
    //   | CT_CHAR
    //   | CT_STRING
    //   | LPAREN expr RPAREN
    private boolean exprPrimary() {
        int startIdx = crtIdx;

        if (consume(TokenType.IDENTIFIER)) {

            if (consume(TokenType.LPAREN)) {
                if (expr()) {
                    while (consume(TokenType.COMMA)) {
                        if (!expr()) {
                            tkerr("Invalid function call argument");
                        }
                    }
                }

                if (!consume(TokenType.RPAREN)) {
                    tkerr("Missing ) after function call");
                }
            }

            return true;
        }

        if (consume(TokenType.BASE10_NUMBER) ||
            consume(TokenType.BASE16_NUMBER) ||
            consume(TokenType.BASE8_NUMBER) ||
            consume(TokenType.BASE2_NUMBER) ||
            consume(TokenType.REAL_NUMBER) ||
            consume(TokenType.CT_CHAR) ||
            consume(TokenType.STRING)) {

            return true;
        }

        if (consume(TokenType.LPAREN)) {
            if (!expr()) {
                tkerr("Invalid expression after (");
            }

            if (!consume(TokenType.RPAREN)) {
                tkerr("Missing ) after expression");
            }

            return true;
        }

        crtIdx = startIdx;
        return false;
    }

    public static void main(String[] args) {

        try {

            String path = "src/compiler/lexer/testers/9.c";

            String input = java.nio.file.Files.readString(
                    java.nio.file.Paths.get(path)
            );

            // ---------------- LEXER ----------------

            compiler.lexer.Lexer lexer =
                    new compiler.lexer.Lexer(input);

            java.util.List<Token> tokens =
                    new java.util.ArrayList<>();

            Token tk;

            System.out.println("=== TOKENS ===");

            do {
                tk = lexer.getNextToken();
                tokens.add(tk);

                System.out.println(tk);

            } while (tk.getTokenType() != TokenType.EOF);

            // ---------------- PARSER ----------------

            System.out.println("\n=== PARSER ===");

            Parser parser = new Parser(tokens);

            if (parser.unit()) {
                System.out.println("Syntax is CORRECT!");
            }

        } catch (Exception e) {

            System.out.println("PARSER ERROR:");
            System.out.println(e.getMessage());

        }
    }

}

