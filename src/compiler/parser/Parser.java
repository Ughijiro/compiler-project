package compiler.parser;

import compiler.lexer.*;
import compiler.semantic.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Parser {
    private List<Token> tokens;
    private int crtIdx = 0;
    private Token consumedTk;

    private SymbolTable symbolTable = new SymbolTable();
    private Symbol currentOwner = null;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    private Token crtTk() {
        if (crtIdx >= tokens.size()) return tokens.get(tokens.size() - 1);
        return tokens.get(crtIdx);
    }

    private void tkerr(String message) {
        Token t = crtTk();
        throw new RuntimeException("Syntax Error at line " + t.getLine() + " near '" + t.getValue() + "': " + message);
    }

    private boolean consume(TokenType type) {
        if (crtTk().getTokenType() == type) {
            consumedTk = crtTk();
            crtIdx++;
            return true;
        }
        return false;
    }

    private void semanticError(String message) {
        Token t = crtTk();
        throw new RuntimeException(
            "Semantic Error at line " +
            t.getLine() +
            " near '" +
            t.getValue() +
            "': " +
            message
        );
    }

    // --- TOP LEVEL ---

    public boolean unit() {
        while (!consume(TokenType.EOF)) {
            int startIdx = crtIdx;
            if (structDef()) continue;
            crtIdx = startIdx;
            if (fnDef()) continue;
            crtIdx = startIdx;
            if (varDef()) continue;
            tkerr("Unexpected token at top level");
        }
        return true;
    }

    private boolean structDef() {
        int startIdx = crtIdx;

        if (consume(TokenType.STRUCT)) {
            if (consume(TokenType.IDENTIFIER)) {

                String structName = consumedTk.getValue();

                if (symbolTable.findInCurrentDomain(structName) != null) {
                    semanticError("Symbol redefinition: " + structName);
                }

                Symbol structSymbol = new Symbol(structName, SymbolKind.SK_STRUCT);
                structSymbol.type = new Type(TypeBase.TB_STRUCT);
                structSymbol.type.structSymbol = structSymbol;

                symbolTable.addSymbol(structSymbol);

                currentOwner = structSymbol;
                symbolTable.pushDomain();

                if (consume(TokenType.LBRACE)) {

                    while (varDef());

                    if (!consume(TokenType.RBRACE)) {
                        tkerr("Missing } for struct");
                    }

                    if (!consume(TokenType.SEMICOLON)) {
                        tkerr("Missing ; after struct");
                    }

                    symbolTable.dropDomain();
                    currentOwner = null;

                    return true;
                }
            }
        }

        crtIdx = startIdx;
        return false;
    }

    private boolean fnDef() {
        int startIdx = crtIdx;
        if (typeBase() || consume(TokenType.VOID)) {
            if (consume(TokenType.IDENTIFIER)) {
                if (consume(TokenType.LPAREN)) {
                    if (fnParam()) {
                        while (consume(TokenType.COMMA)) if (!fnParam()) tkerr("Invalid param");
                    }
                    if (consume(TokenType.RPAREN)) {
                        if (stmCompound()) return true;
                    }
                }
            }
        }
        crtIdx = startIdx;
        return false;
    }

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

    private boolean stmCompound() {
        int startIdx = crtIdx;
        if (consume(TokenType.LBRACE)) {
            while (varDef() || stm());
            if (consume(TokenType.RBRACE)) return true;
        }
        crtIdx = startIdx;
        return false;
    }

    // --- STATEMENTS ---

    private boolean stm() {
        //int startIdx = crtIdx;
        if (stmCompound()) return true;

        if (consume(TokenType.IF)) {
            if (!consume(TokenType.LPAREN)) tkerr("missing (");
            if (!expr()) tkerr("invalid expression in if");
            if (!consume(TokenType.RPAREN)) tkerr("missing )");
            if (!stm()) tkerr("missing statement");
            if (consume(TokenType.ELSE)) if (!stm()) tkerr("missing else statement");
            return true;
        }

        if (consume(TokenType.WHILE)) {
            if (!consume(TokenType.LPAREN)) tkerr("missing (");
            if (!expr()) tkerr("invalid expression");
            if (!consume(TokenType.RPAREN)) tkerr("missing )");
            if (!stm()) tkerr("missing statement");
            return true;
        }

        if (consume(TokenType.FOR)) {
            if (!consume(TokenType.LPAREN)) tkerr("missing (");
            expr(); 
            if (!consume(TokenType.SEMICOLON)) tkerr("missing ; in for");
            expr(); 
            if (!consume(TokenType.SEMICOLON)) tkerr("missing ; in for");
            expr(); 
            if (!consume(TokenType.RPAREN)) tkerr("missing )");
            if (!stm()) tkerr("missing statement");
            return true;
        }

        if (consume(TokenType.RETURN)) {
            expr(); 
            if (!consume(TokenType.SEMICOLON)) tkerr("missing ;");
            return true;
        }

        if (consume(TokenType.BREAK)) {
            if (!consume(TokenType.SEMICOLON)) tkerr("missing ;");
            return true;
        }

        if (expr()) {
            if (!consume(TokenType.SEMICOLON)) tkerr("missing ; after expression");
            return true;
        }
        return consume(TokenType.SEMICOLON);
    }

    // --- EXPRESSIONS ---

    private boolean expr() { return exprAssign(); }

    private boolean exprAssign() {
        int startIdx = crtIdx;
        if (exprUnary() && consume(TokenType.ASSIGN) && exprAssign()) return true;
        crtIdx = startIdx;
        return exprOr();
    }

    private boolean exprOr() {
        if (exprAnd()) {
            while (consume(TokenType.OR)) if (!exprAnd()) tkerr("invalid or");
            return true;
        }
        return false;
    }

    private boolean exprAnd() {
        if (exprEq()) {
            while (consume(TokenType.AND)) if (!exprEq()) tkerr("invalid and");
            return true;
        }
        return false;
    }

    private boolean exprEq() {
        if (exprRel()) {
            while (consume(TokenType.EQUAL) || consume(TokenType.NOT_EQUAL)) if (!exprRel()) tkerr("invalid eq");
            return true;
        }
        return false;
    }

    private boolean exprRel() {
        if (exprAdd()) {
            while (consume(TokenType.LESS) || consume(TokenType.LESS_EQUAL) || 
                   consume(TokenType.GREATER) || consume(TokenType.GREATER_EQUAL)) {
                if (!exprAdd()) tkerr("Invalid expression in relation");
            }
            return true;
        }
        return false;
    }

    private boolean exprAdd() {
        if (exprMul()) {
            while (consume(TokenType.PLUS) || consume(TokenType.MINUS)) if (!exprMul()) tkerr("invalid add");
            return true;
        }
        return false;
    }

    private boolean exprMul() {
        if (exprCast()) {
            while (consume(TokenType.MULTIPLY) || consume(TokenType.DIVIDE) || consume(TokenType.MODULO)) if (!exprCast()) tkerr("invalid mul");
            return true;
        }
        return false;
    }

    private boolean exprCast() {
        int startIdx = crtIdx;
        if (consume(TokenType.LPAREN)) {
            if (typeBase()) {
                arrayDecl();
                if (consume(TokenType.RPAREN) && exprCast()) return true;
            }
        }
        crtIdx = startIdx;
        return exprUnary();
    }

    private boolean exprUnary() {
        int startIdx = crtIdx;
        if ((consume(TokenType.MINUS) || consume(TokenType.NOT)) && exprUnary()) return true;
        crtIdx = startIdx;
        return exprPostfix();
    }

    private boolean exprPostfix() {
        if (exprPrimary()) {
            while (true) {
                if (consume(TokenType.LBRACK)) {
                    if (expr() && consume(TokenType.RBRACK)) continue;
                    tkerr("invalid array access");
                }
                if (consume(TokenType.DOT)) {
                    if (consume(TokenType.IDENTIFIER)) continue;
                    tkerr("missing field after .");
                }
                break;
            }
            return true;
        }
        return false;
    }

    private boolean exprPrimary() {
        int startIdx = crtIdx;
        if (consume(TokenType.IDENTIFIER)) {
            if (consume(TokenType.LPAREN)) {
                if (expr()) {
                    while (consume(TokenType.COMMA)) if (!expr()) tkerr("invalid arg");
                }
                if (!consume(TokenType.RPAREN)) tkerr("missing )");
            }
            return true;
        }
        // ALL NUMBER TYPES MUST BE HERE
        if (consume(TokenType.BASE10_NUMBER) || consume(TokenType.BASE16_NUMBER) || 
            consume(TokenType.BASE8_NUMBER) || consume(TokenType.BASE2_NUMBER) ||
            consume(TokenType.REAL_NUMBER) || consume(TokenType.STRING) || consume(TokenType.CT_CHAR)) return true;
        
        if (consume(TokenType.LPAREN) && expr() && consume(TokenType.RPAREN)) return true;
        crtIdx = startIdx;
        return false;
    }

    // --- HELPERS ---

    private boolean varDef() {
        int startIdx = crtIdx;
        if (typeBase()) {
            if (consume(TokenType.IDENTIFIER)) {
                arrayDecl();
                while (consume(TokenType.COMMA)) {
                    if (!consume(TokenType.IDENTIFIER)) tkerr("Expected ID");
                    arrayDecl();
                }
                if (consume(TokenType.SEMICOLON)) return true;
            }
        }
        crtIdx = startIdx;
        return false;
    }

    private boolean typeBase() {
        if (consume(TokenType.INT) || consume(TokenType.DOUBLE) || consume(TokenType.CT_CHAR)) return true;
        
        int startIdx = crtIdx;
        if (consume(TokenType.STRUCT)) {
            if (consume(TokenType.IDENTIFIER)) {
                return true;
            }
        }
        crtIdx = startIdx;
        return false;
    }

    private boolean arrayDecl() {
        int startIdx = crtIdx;
        if (consume(TokenType.LBRACK)) {
            expr(); // Change: Now we allow math like 20/4+5 inside brackets
            if (consume(TokenType.RBRACK)) {
                return true;
            }
        }
        crtIdx = startIdx;
        return false;
    }

    public static void main(String[] args) {
        try {
            // Path to your .c file
            String path = "C:\\Users\\tamas\\Desktop\\CT_PROIECT\\src\\compiler\\lexer\\testers\\9.c";
            String input = Files.readString(Paths.get(path));

            // 1. Get tokens from Lexer
            Lexer lexer = new Lexer(input);
            List<Token> tokens = new ArrayList<>();
            Token t;
            do {
                t = lexer.getNextToken();
                tokens.add(t);
            } while (t.getTokenType() != TokenType.EOF);

            // 2. Run Parser
            Parser parser = new Parser(tokens);
            if (parser.unit()) {
                System.out.println("--- SUCCESS ---");
                System.out.println("Syntax is CORRECT! 🎉");
            }

        } catch (Exception e) {
            // This will catch the tkerr() "Syntax Error" messages
            System.out.println("--- PARSER ERROR ---");
            System.out.println(e.getMessage());
        }
    }
}