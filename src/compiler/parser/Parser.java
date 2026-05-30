package compiler.parser;

import compiler.lexer.Token;
import compiler.lexer.TokenType;
import compiler.semantic.*;

import java.util.List;

public class Parser {

    private List<Token> tokens;
    private int crtIdx = 0;

    private SymbolTable symbolTable = new SymbolTable();

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

    private Type voidType() {
        return new Type(TypeBase.TB_VOID);
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
            Token tk = crtTk();

            if (consume(TokenType.IDENTIFIER)) {
                if (!consume(TokenType.LBRACE)) {
                    crtIdx = startIdx;
                    return false;
                }

                if (symbolTable.findInCurrentDomain(tk.getValue()) != null) {
                    tkerr("Symbol redefinition: " + tk.getValue());
                }

                Symbol structSymbol = new Symbol(tk.getValue(), SymbolKind.SK_STRUCT);
                structSymbol.type = new Type(TypeBase.TB_STRUCT);
                structSymbol.type.structSymbol = structSymbol;
                structSymbol.mem = null;

                symbolTable.addSymbol(structSymbol);

                Symbol oldOwner = symbolTable.currentOwner;
                symbolTable.currentOwner = structSymbol;

                symbolTable.pushDomain();

                while (varDef());

                symbolTable.dropDomain();
                symbolTable.currentOwner = oldOwner;

                if (!consume(TokenType.RBRACE)) {
                    tkerr("Missing } after struct body");
                }

                if (!consume(TokenType.SEMICOLON)) {
                    tkerr("Missing ; after struct definition");
                }

                return true;
            }
        }

        crtIdx = startIdx;
        return false;
    }

    // fnDef: ( typeBase | VOID ) ID LPAREN ( fnParam ( COMMA fnParam )* )? RPAREN stmCompound
    private boolean fnDef() {
        int startIdx = crtIdx;
        Type type = new Type(null);
        if (typeBase(type) || (consume(TokenType.VOID) && ((type = voidType()) != null))) {
            Token tkName = crtTk();
            if (consume(TokenType.IDENTIFIER)) {
                if (consume(TokenType.LPAREN)) {
                    if (symbolTable.findInCurrentDomain(tkName.getValue()) != null) {
                        tkerr("Symbol redefinition: " + tkName.getValue());
                    }

                    Symbol fnSymbol = new Symbol(tkName.getValue(), SymbolKind.SK_FN);
                    fnSymbol.type = type;
                    fnSymbol.mem = MemoryLocation.MEM_GLOBAL;
                    symbolTable.addSymbol(fnSymbol);

                    Symbol oldOwner = symbolTable.currentOwner;
                    symbolTable.currentOwner = fnSymbol;
                    symbolTable.pushDomain();

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

                    if (!stmCompound(false)) {
                        tkerr("Missing function body");
                    }

                    symbolTable.dropDomain();
                    symbolTable.currentOwner = oldOwner;
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

        Type type = new Type(null);
        if (typeBase(type)) {
            if (consume(TokenType.IDENTIFIER)) {
                Token tk = tokens.get(crtIdx - 1);

                if (symbolTable.findInCurrentDomain(tk.getValue()) != null) {
                    tkerr("Redefinition of parameter: " + tk.getValue());
                }

                arrayDecl(type);

                Symbol param = new Symbol(tk.getValue(), SymbolKind.SK_PARAM);
                param.type = type;
                param.mem = MemoryLocation.MEM_ARG;

                if (symbolTable.currentOwner != null &&
                    symbolTable.currentOwner.kind == SymbolKind.SK_FN) {
                    symbolTable.currentOwner.fnParams.add(param);
                }

                symbolTable.addSymbol(param);
                return true;
            }
        }

        crtIdx = startIdx;
        return false;
    }

    // varDef: typeBase ID arrayDecl? SEMICOLON
    private boolean varDef() {
        int startIdx = crtIdx;

        Type type = new Type(null);
        if (typeBase(type)) {
            if (consume(TokenType.IDENTIFIER)) {
                
                Token tk = tokens.get(crtIdx - 1);
                Symbol s = symbolTable.findInCurrentDomain(tk.getValue());
                if (s != null) {
                    tkerr("Redefinition of symbol: " + tk.getValue());
                }
                
                Symbol var = new Symbol(tk.getValue(), SymbolKind.SK_VAR);
                var.type = type;

                if(symbolTable.currentOwner != null &&
                   symbolTable.currentOwner.kind == SymbolKind.SK_STRUCT){
                    var.mem = null;
                }
                else if(symbolTable.currentDepth == 0){
                    var.mem = MemoryLocation.MEM_GLOBAL;
                }
                else{
                    var.mem = MemoryLocation.MEM_LOCAL;
                }

                if(symbolTable.currentOwner != null){
                    if(symbolTable.currentOwner.kind == SymbolKind.SK_FN){
                        symbolTable.currentOwner.fnLocals.add(var);
                    }

                    if(symbolTable.currentOwner.kind == SymbolKind.SK_STRUCT){
                        symbolTable.currentOwner.structMembers.add(var);
                    }
                }
                
                arrayDecl(type);
                symbolTable.addSymbol(var);

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
    private boolean typeBase(Type type) {
        if (consume(TokenType.INT)){
            type.typeBase = TypeBase.TB_INT;
            return true;
        };
        if (consume(TokenType.DOUBLE)) {
            type.typeBase = TypeBase.TB_DOUBLE;
            return true;
        }
        if (consume(TokenType.CHAR)) {
            type.typeBase = TypeBase.TB_CHAR;
            return true;
        }

        int startIdx = crtIdx;

        if (consume(TokenType.STRUCT)) {
            Token tk = crtTk();
            if (consume(TokenType.IDENTIFIER)) {

                Symbol s = symbolTable.findSymbol(tk.getValue());
                if (s == null || s.kind != SymbolKind.SK_STRUCT) {
                    tkerr("Undefined struct type: " + tk.getValue());
                }

                type.typeBase = TypeBase.TB_STRUCT;
                type.structSymbol = s;
                return true;
            }
        }

        crtIdx = startIdx;
        return false;
    }

    // arrayDecl: LBRACK CT_INT? RBRACK
    private boolean arrayDecl() {
        return arrayDecl(null);
    }

    private boolean arrayDecl(Type type) {
        int startIdx = crtIdx;

        if (consume(TokenType.LBRACK)) {
            int nElements = 0;

            if (crtTk().getTokenType() != TokenType.RBRACK) {
                Token sizeToken = crtTk();
                if (!expr()) {
                    tkerr("Invalid array size expression");
                }

                if (sizeToken.getTokenType() == TokenType.BASE10_NUMBER) {
                    try {
                        nElements = Integer.parseInt(sizeToken.getValue());
                    } catch (NumberFormatException e) {
                        nElements = 0;
                    }
                }
            }

            if (consume(TokenType.RBRACK)) {
                if (type != null) {
                    type.nElements = nElements;
                }
                return true;
            }

            tkerr("Missing ] in array declaration");
        }

        crtIdx = startIdx;
        return false;
    }

    // stmCompound: LBRACE ( varDef | stm )* RBRACE
    private boolean stmCompound() {
        return stmCompound(true);
    }

    private boolean stmCompound(boolean newDomain){
        int startIdx = crtIdx;

        if(consume(TokenType.LBRACE)){
            if(newDomain){
                symbolTable.pushDomain();
            }

            while(varDef() || stm());

            if(consume(TokenType.RBRACE)){
                if(newDomain){
                    symbolTable.dropDomain();
                }
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
        RetVal rv = new RetVal();
        return expr(rv);
    }

    private boolean expr(RetVal rv) {
        return exprAssign(rv);
    }

    // exprAssign: exprUnary ASSIGN exprAssign | exprOr
    private boolean exprAssign() {
    RetVal rv = new RetVal();
    return exprAssign(rv);
}

    private boolean exprAssign(RetVal rv) {
        int startIdx = crtIdx;

        RetVal left = new RetVal();

        if (exprUnary(left)) {
            if (consume(TokenType.ASSIGN)) {
                if (!left.isLVal) {
                    tkerr("Left side of assignment is not assignable");
                }

                RetVal right = new RetVal();

                if (exprAssign(right)) {
                    if (!canConvert(right.type, left.type)) {
                        tkerr("Incompatible types in assignment");
                    }
                    rv.type = left.type;
                    rv.isLVal = false;
                    rv.isCtVal = false;
                    return true;
                }
            }
        }

        crtIdx = startIdx;
        return exprOr(rv);
    }

    // exprOr: exprAnd ( OR exprAnd )*
    private boolean exprOr() {
    RetVal rv = new RetVal();
    return exprOr(rv);
}

    private boolean exprOr(RetVal rv) {
        if (exprAnd(rv)) {
            while (consume(TokenType.OR)) {
                RetVal right = new RetVal();

                if (!exprAnd(right)) {
                    tkerr("Invalid expression after ||");
                }

                rv.type = new Type(TypeBase.TB_INT);
                rv.isLVal = false;
                rv.isCtVal = false;
            }

            return true;
        }

        return false;
    }

    // exprAnd: exprEq ( AND exprEq )*
    private boolean exprAnd() {
    RetVal rv = new RetVal();
    return exprAnd(rv);
}

    private boolean exprAnd(RetVal rv) {
        if (exprEq(rv)) {
            while (consume(TokenType.AND)) {
                RetVal right = new RetVal();

                if (!exprEq(right)) {
                    tkerr("Invalid expression after &&");
                }

                rv.type = new Type(TypeBase.TB_INT);
                rv.isLVal = false;
                rv.isCtVal = false;
            }

            return true;
        }

        return false;
    }

    // exprEq: exprRel ( ( EQUAL | NOT_EQUAL ) exprRel )*
    private boolean exprEq() {
    RetVal rv = new RetVal();
    return exprEq(rv);
}

    private boolean exprEq(RetVal rv) {
        if (exprRel(rv)) {
            while (consume(TokenType.EQUAL) || consume(TokenType.NOT_EQUAL)) {
                RetVal right = new RetVal();

                if (!exprRel(right)) {
                    tkerr("Invalid equality expression");
                }

                rv.type = new Type(TypeBase.TB_INT);
                rv.isLVal = false;
                rv.isCtVal = false;
            }

            return true;
        }

        return false;
    }

    // exprRel: exprAdd ( ( LESS | LESS_EQUAL | GREATER | GREATER_EQUAL ) exprAdd )*
    private boolean exprRel() {
        RetVal rv = new RetVal();
        return exprRel(rv);
    }

    private boolean exprRel(RetVal rv) {
        if (exprAdd(rv)) {
            while (consume(TokenType.LESS) ||
                consume(TokenType.LESS_EQUAL) ||
                consume(TokenType.GREATER) ||
                consume(TokenType.GREATER_EQUAL)) {

                RetVal right = new RetVal();

                if (!exprAdd(right)) {
                    tkerr("Invalid relational expression");
                }

                rv.type = new Type(TypeBase.TB_INT);
                rv.isLVal = false;
                rv.isCtVal = false;
            }

            return true;
        }

        return false;
    }

    // exprAdd: exprMul ( ( PLUS | MINUS ) exprMul )*
    private boolean exprAdd() {
        RetVal rv = new RetVal();
        return exprAdd(rv);
    }   

    private boolean exprAdd(RetVal rv) {
        if (exprMul(rv)) {
            while (consume(TokenType.PLUS) || consume(TokenType.MINUS)) {
                RetVal right = new RetVal();

                if (!exprMul(right)) {
                    tkerr("Invalid additive expression");
                }

                rv.type = getArithType(rv.type, right.type);
                rv.isLVal = false;
                rv.isCtVal = false;
            }

            return true;
        }

        return false;
    }

    // exprMul: exprCast ( ( MULTIPLY | DIVIDE | MODULO ) exprCast )*
    private boolean exprMul() {
        RetVal rv = new RetVal();
        return exprMul(rv);
    }

    private boolean exprMul(RetVal rv) {
        if (exprCast(rv)) {
            while (consume(TokenType.MULTIPLY) ||
                consume(TokenType.DIVIDE) ||
                consume(TokenType.MODULO)) {

                RetVal right = new RetVal();

                if (!exprCast(right)) {
                    tkerr("Invalid multiplicative expression");
                }

                rv.type = getArithType(rv.type, right.type);
                rv.isLVal = false;
                rv.isCtVal = false;
            }

            return true;
        }

        return false;
    }

    // exprCast: LPAREN typeBase arrayDecl? RPAREN exprCast | exprUnary
    private boolean exprCast() {
        RetVal rv = new RetVal();
        return exprCast(rv);
    }

    private boolean exprCast(RetVal rv) {
        int startIdx = crtIdx;
        Type castType = new Type(null);

        if (consume(TokenType.LPAREN)) {
            if (typeBase(castType)) {
                arrayDecl(castType);

                if (consume(TokenType.RPAREN)) {
                    RetVal inner = new RetVal();

                    if (exprCast(inner)) {
                        rv.type = castType;
                        rv.isLVal = false;
                        rv.isCtVal = false;
                        return true;
                    }
                }
            }
        }

        crtIdx = startIdx;
        return exprUnary(rv);
    }

    // exprUnary: ( MINUS | NOT ) exprUnary | exprPostfix
    private boolean exprUnary() {
        RetVal rv = new RetVal();
        return exprUnary(rv);
    }

    private boolean exprUnary(RetVal rv) {
        int startIdx = crtIdx;

        if (consume(TokenType.MINUS) || consume(TokenType.NOT)) {
            RetVal inner = new RetVal();

            if (exprUnary(inner)) {
                rv.type = inner.type;
                rv.isLVal = false;
                rv.isCtVal = false;
                return true;
            }
        }

        crtIdx = startIdx;
        return exprPostfix(rv);
    }

    // exprPostfix:
    //     exprPrimary
    //   | exprPostfix LBRACK expr RBRACK
    //   | exprPostfix DOT ID
    private boolean exprPostfix() {
        RetVal rv = new RetVal();
        return exprPostfix(rv);
    }

    private boolean exprPostfix(RetVal rv) {
        if (exprPrimary(rv)) {
            while (true) {
                if (consume(TokenType.LBRACK)) {
                    if (rv.type.nElements < 0) {
                        tkerr("Only an array can be indexed");
                    }
                    RetVal index = new RetVal();

                    if (!expr(index)) {
                        tkerr("Invalid array index expression");
                    }

                    if (index.type == null) {
                        tkerr("Invalid array index type");
                    }

                    if(index.type.nElements >=0){
                        tkerr("Array index cannot be an array");
                    }

                    if(index.type.typeBase != TypeBase.TB_INT && 
                        index.type.typeBase != TypeBase.TB_CHAR){
                            tkerr("Array index must be int or char");
                    }

                    if (!consume(TokenType.RBRACK)) {
                        tkerr("Missing1 ] after array index");
                    }

                    Type oldType = rv.type;
                    rv.type = new Type(oldType.typeBase);
                    rv.type.structSymbol = oldType.structSymbol;
                    rv.isLVal = true;
                    rv.isCtVal = false;
                    
                    continue;
                }

                if (consume(TokenType.DOT)) {
                    if(rv.type.typeBase != TypeBase.TB_STRUCT){
                        tkerr("Left side of . is not a struct");
                    }

                    Token fieldTk = crtTk();

                    if (!consume(TokenType.IDENTIFIER)) {
                        tkerr("Missing field name after .");
                    }

                    Symbol field = null;
                    for(Symbol member : rv.type.structSymbol.structMembers){
                        if(member.name.equals(fieldTk.getValue())){
                            field = member;
                            break;
                        }
                    }

                    if(field == null){
                        tkerr("Struct has no member: " + fieldTk.getValue());
                    }

                    rv.type = field.type;
                    rv.isLVal = true;
                    rv.isCtVal = false;

                    continue;
                }

                break;
            }

            return true;
        }

        return false;
    }

    private boolean exprPrimary(){
        RetVal rv = new RetVal();
        return exprPrimary(rv);
    }

    // exprPrimary:
    //     ID ( LPAREN ( expr ( COMMA expr )* )? RPAREN )?
    //   | CT_INT
    //   | CT_REAL
    //   | CT_CHAR
    //   | CT_STRING
    //   | LPAREN expr RPAREN
    private boolean exprPrimary(RetVal rv) {
        int startIdx = crtIdx;
        int argCount = 0;
        int argIndex = 0;
        RetVal arg = new RetVal();

        if (consume(TokenType.IDENTIFIER)) {
            Token tk = tokens.get(crtIdx - 1);

            Symbol s = symbolTable.findSymbol(tk.getValue());
            
            if(s == null){
                tkerr("Undefined symbol: " + tk.getValue());
            }

            rv.type = s.type;
            if(s.kind == SymbolKind.SK_VAR || s.kind == SymbolKind.SK_PARAM){
                rv.isLVal = true;
            }
            else{
                rv.isLVal = false;
            }
            rv.isCtVal = false;

            if (consume(TokenType.LPAREN)) {

                if (s.kind != SymbolKind.SK_FN) {
                    tkerr("Symbol is not a function: " + tk.getValue());
                }

                
                if (expr(arg)) {

                    if(argIndex >= s.fnParams.size()){
                        tkerr("Too many arguments in call to " + tk.getValue());
                    }

                    if(!canConvert(arg.type, s.fnParams.get(argIndex).type)){
                        tkerr("Invalid argument type is call to " + tk.getValue());
                    }

                    argCount++;
                    argIndex++;

                    while (consume(TokenType.COMMA)) {
                        arg = new RetVal();

                        if (!expr(arg)) {
                            tkerr("Invalid function call argument");
                        }
                        if(argIndex >= s.fnParams.size()){
                            tkerr("Too many arguments in call to " + tk.getValue());
                        }

                        if(!canConvert(arg.type, s.fnParams.get(argIndex).type)){
                            tkerr("Invalid argument type is call to " + tk.getValue());
                        }

                        argCount++;
                        argIndex++;
                    }
                }

                if (!consume(TokenType.RPAREN)) {
                    tkerr("Missing ) after function call");
                }
                
                if(argCount != s.fnParams.size()){
                    tkerr("Invalid number of arguments in call to " + tk.getValue());
                }

            }


            return true;
        }

        if (consume(TokenType.BASE10_NUMBER) ||
            consume(TokenType.BASE16_NUMBER) ||
            consume(TokenType.BASE8_NUMBER) ||
            consume(TokenType.BASE2_NUMBER)) {
            
                rv.type = new Type(TypeBase.TB_INT);
                rv.isLVal = false;
                rv.isCtVal = true;

            return true;
        }
        if(consume(TokenType.REAL_NUMBER)){
            rv.type = new Type(TypeBase.TB_DOUBLE);
            rv.isLVal = false;
            rv.isCtVal = true;

            return true;
        }
        if(consume(TokenType.CT_CHAR)){
            rv.type = new Type(TypeBase.TB_CHAR);
            rv.isLVal = false;
            rv.isCtVal = true;

            return true;
        }

        if(consume(TokenType.STRING)){
            rv.type = new Type(TypeBase.TB_CHAR);
            rv.type.nElements = 0;
            rv.isLVal = false;
            rv.isCtVal = true;

            return true;
        }

        if (consume(TokenType.LPAREN)) {
            RetVal inner = new RetVal();

            if (!expr(inner)) {
                tkerr("Invalid expression after (");
            }

            rv.type = inner.type;
            rv.isLVal = inner.isLVal;
            rv.isCtVal = inner.isCtVal;

            if (!consume(TokenType.RPAREN)) {
                tkerr("Missing ) after expression");
            }

            return true;
        }

        crtIdx = startIdx;
        return false;
    }

    private Type getArithType(Type a, Type b) {
        if (a == null || b == null) {
            tkerr("Invalid arithmetic expression");
        }

        if (a.nElements >= 0 || b.nElements >= 0) {
            tkerr("Array cannot be used in arithmetic expression");
        }

        if (a.typeBase == TypeBase.TB_STRUCT || b.typeBase == TypeBase.TB_STRUCT) {
            tkerr("Struct cannot be used in arithmetic expression");
        }

        if (a.typeBase == TypeBase.TB_DOUBLE || b.typeBase == TypeBase.TB_DOUBLE) {
            return new Type(TypeBase.TB_DOUBLE);
        }

        return new Type(TypeBase.TB_INT);
    }

    private boolean canConvert(Type src, Type dst) {

        if (src.nElements >= 0 || dst.nElements >= 0) {
            return src.nElements == dst.nElements
                && src.typeBase == dst.typeBase;
        }

        if (src.typeBase == dst.typeBase) {
            return true;
        }

        if ((src.typeBase == TypeBase.TB_INT ||
            src.typeBase == TypeBase.TB_CHAR)
            &&
            (dst.typeBase == TypeBase.TB_INT ||
            dst.typeBase == TypeBase.TB_CHAR ||
            dst.typeBase == TypeBase.TB_DOUBLE)) {

            return true;
        }

        if (src.typeBase == TypeBase.TB_DOUBLE
            &&
            (dst.typeBase == TypeBase.TB_DOUBLE ||
            dst.typeBase == TypeBase.TB_INT ||
            dst.typeBase == TypeBase.TB_CHAR)) {

            return true;
        }

        return false;
    }

    public static void main(String[] args) {

        try {

            String path = "src/compiler/parser/testers/test7.c";

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

            // SymbolTable

            parser.symbolTable.printSymbols();

        } catch (Exception e) {

            System.out.println("PARSER ERROR:");
            System.out.println(e.getMessage());

        }
    }

}
