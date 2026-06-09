package compiler.parser;

import compiler.lexer.Token;
import compiler.lexer.TokenType;
import compiler.semantic.*;

import java.util.List;

// Recursive descent parser with semantic and type analysis.
public class Parser {

    // Parser state

    private List<Token> tokens;
    private int crtIdx = 0;

    private SymbolTable symbolTable = new SymbolTable();

    // Current function, used for return type checking.
    private Symbol currentFunction = null;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    // Basic parser helpers

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

    // Top-level grammar rule

    // unit: ( structDef | fnDef | varDef )* EOF
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

    // Declarations and types

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

                    Symbol oldFunction = currentFunction;
                    currentFunction = fnSymbol;

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

                    // Function body uses the same scope as its parameters.
                    if (!stmCompound(false)) {
                        tkerr("Missing function body");
                    }

                    symbolTable.dropDomain();
                    symbolTable.currentOwner = oldOwner;
                    currentFunction = oldFunction;

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

                boolean isArrayParam = arrayDecl(type);

                // Lab rule: int v[10] parameter becomes int v[]
                if (isArrayParam) {
                    type.nElements = 0;
                }

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

                if (symbolTable.findInCurrentDomain(tk.getValue()) != null) {
                    tkerr("Redefinition of symbol: " + tk.getValue());
                }

                Symbol var = new Symbol(tk.getValue(), SymbolKind.SK_VAR);
                var.type = type;

                if (symbolTable.currentOwner != null &&
                    symbolTable.currentOwner.kind == SymbolKind.SK_STRUCT) {
                    var.mem = null;
                } else if (symbolTable.currentDepth == 0) {
                    var.mem = MemoryLocation.MEM_GLOBAL;
                } else {
                    var.mem = MemoryLocation.MEM_LOCAL;
                }

                if (symbolTable.currentOwner != null) {
                    if (symbolTable.currentOwner.kind == SymbolKind.SK_FN) {
                        symbolTable.currentOwner.fnLocals.add(var);
                    }

                    if (symbolTable.currentOwner.kind == SymbolKind.SK_STRUCT) {
                        symbolTable.currentOwner.structMembers.add(var);
                    }
                }

                arrayDecl(type);

                if (type.nElements == 0) {
                    tkerr("A vector variable must have a specified dimension");
                }

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
        if (consume(TokenType.INT)) {
            type.typeBase = TypeBase.TB_INT;
            return true;
        }

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
    private boolean arrayDecl(Type type) {
        int startIdx = crtIdx;

        if (consume(TokenType.LBRACK)) {
            int nElements = 0; // 0 means []

            if (crtTk().getTokenType() != TokenType.RBRACK) {
                Token sizeToken = crtTk();

                if (consume(TokenType.BASE10_NUMBER)) {
                    try {
                        nElements = Integer.decode(sizeToken.getValue());
                    } catch (NumberFormatException e) {
                        tkerr("Invalid array size");
                    }

                    if (nElements <= 0) {
                        tkerr("Array size must be greater than 0");
                    }
                } else {
                    tkerr("Array size must be an integer constant");
                }
            }

            if (!consume(TokenType.RBRACK)) {
                tkerr("Missing ] in array declaration");
            }

            if (type != null) {
                type.nElements = nElements;
            }

            return true;
        }

        crtIdx = startIdx;
        return false;
    }

    // Statements

    private boolean stmCompound() {
        return stmCompound(true);
    }

    // stmCompound: LBRACE ( varDef | stm )* RBRACE
    private boolean stmCompound(boolean newDomain) {
        int startIdx = crtIdx;

        if (consume(TokenType.LBRACE)) {
            if (newDomain) {
                symbolTable.pushDomain();
            }

            while (varDef() || stm());

            if (consume(TokenType.RBRACE)) {
                if (newDomain) {
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
            if (!consume(TokenType.LPAREN)) {
                tkerr("Missing ( after if");
            }

            RetVal cond = new RetVal();

            if (!expr(cond)) {
                tkerr("Invalid expression in if");
            }

            if (!canBeScalar(cond)) {
                tkerr("The if condition must be scalar");
            }

            if (!consume(TokenType.RPAREN)) {
                tkerr("Missing ) after if condition");
            }

            if (!stm()) {
                tkerr("Missing statement after if");
            }

            if (consume(TokenType.ELSE)) {
                if (!stm()) {
                    tkerr("Missing statement after else");
                }
            }

            return true;
        }

        if (consume(TokenType.WHILE)) {
            if (!consume(TokenType.LPAREN)) {
                tkerr("Missing ( after while");
            }

            RetVal cond = new RetVal();

            if (!expr(cond)) {
                tkerr("Invalid expression in while");
            }

            if (!canBeScalar(cond)) {
                tkerr("The while condition must be scalar");
            }

            if (!consume(TokenType.RPAREN)) {
                tkerr("Missing ) after while condition");
            }

            if (!stm()) {
                tkerr("Missing statement after while");
            }

            return true;
        }

        if (consume(TokenType.FOR)) {
            if (!consume(TokenType.LPAREN)) {
                tkerr("Missing ( after for");
            }

            // init expression: optional
            expr();

            if (!consume(TokenType.SEMICOLON)) {
                tkerr("Missing first ; in for");
            }

            // condition expression: optional, but if present it must be scalar
            if (crtTk().getTokenType() != TokenType.SEMICOLON) {
                RetVal cond = new RetVal();

                if (!expr(cond)) {
                    tkerr("Invalid for condition");
                }

                if (!canBeScalar(cond)) {
                    tkerr("The for condition must be scalar");
                }
            }

            if (!consume(TokenType.SEMICOLON)) {
                tkerr("Missing second ; in for");
            }

            // step expression: optional
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
            if (currentFunction == null) {
                tkerr("Return outside function");
            }

            RetVal rv = new RetVal();
            boolean hasExpr = expr(rv);

            if (currentFunction.type.typeBase == TypeBase.TB_VOID) {
                if (hasExpr) {
                    tkerr("Void function cannot return a value");
                }
            } else {
                if (!hasExpr) {
                    tkerr("Non-void function must return a value");
                }

                if (!canBeScalar(rv)) {
                    tkerr("Return value must be scalar");
                }

                if (!canConvert(rv.type, currentFunction.type)) {
                    tkerr("Invalid return type");
                }
            }

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

    // =====================
    // Expressions
    // =====================

    private boolean expr() {
        RetVal rv = new RetVal();
        return expr(rv);
    }

    // expr: exprAssign
    private boolean expr(RetVal rv) {
        return exprAssign(rv);
    }

    // exprAssign: exprUnary ASSIGN exprAssign | exprOr
    private boolean exprAssign(RetVal rv) {
        int startIdx = crtIdx;

        // Important:
        // If the expression starts with a cast, like (int)v,
        // do not try to parse it as an assignment destination.
        // Let exprOr -> ... -> exprCast handle it.
        if (!isCastStart()) {
            RetVal left = new RetVal();

            if (exprUnary(left)) {
                if (consume(TokenType.ASSIGN)) {
                    RetVal right = new RetVal();

                    if (!exprAssign(right)) {
                        tkerr("Invalid expression after =");
                    }

                    if (!left.isLVal) {
                        tkerr("Assignment destination must be a left-value");
                    }

                    if (left.isCtVal) {
                        tkerr("Assignment destination cannot be constant");
                    }

                    if (!canBeScalar(left)) {
                        tkerr("Assignment destination must be scalar");
                    }

                    if (!canBeScalar(right)) {
                        tkerr("Assignment source must be scalar");
                    }

                    if (!canConvert(right.type, left.type)) {
                        tkerr("Assignment source cannot be converted to destination");
                    }

                    rv.type = right.type;
                    rv.isLVal = false;
                    rv.isCtVal = true;

                    return true;
                }
            }
        }

        crtIdx = startIdx;
        return exprOr(rv);
    }
    // exprOr: exprAnd ( OR exprAnd )*
    private boolean exprOr(RetVal rv) {
        if (exprAnd(rv)) {
            while (consume(TokenType.OR)) {
                RetVal right = new RetVal();

                if (!exprAnd(right)) {
                    tkerr("Invalid expression after ||");
                }

                if (!canBeScalar(rv) || !canBeScalar(right)) {
                    tkerr("Invalid operand type for ||");
                }

                rv.type = new Type(TypeBase.TB_INT);
                rv.isLVal = false;
                rv.isCtVal = true;
            }

            return true;
        }

        return false;
    }

    // exprAnd: exprEq ( AND exprEq )*
    private boolean exprAnd(RetVal rv) {
        if (exprEq(rv)) {
            while (consume(TokenType.AND)) {
                RetVal right = new RetVal();

                if (!exprEq(right)) {
                    tkerr("Invalid expression after &&");
                }

                if (!canBeScalar(rv) || !canBeScalar(right)) {
                    tkerr("Invalid operand type for &&");
                }

                rv.type = new Type(TypeBase.TB_INT);
                rv.isLVal = false;
                rv.isCtVal = true;
            }

            return true;
        }

        return false;
    }

    // exprEq: exprRel ( ( EQUAL | NOT_EQUAL ) exprRel )*
    private boolean exprEq(RetVal rv) {
        if (exprRel(rv)) {
            while (consume(TokenType.EQUAL) || consume(TokenType.NOT_EQUAL)) {
                RetVal right = new RetVal();

                if (!exprRel(right)) {
                    tkerr("Invalid equality expression");
                }

                if (!canBeScalar(rv) || !canBeScalar(right)) {
                    tkerr("Invalid operand type for == or !=");
                }

                rv.type = new Type(TypeBase.TB_INT);
                rv.isLVal = false;
                rv.isCtVal = true;
            }

            return true;
        }

        return false;
    }

    // exprRel: exprAdd ( ( LESS | LESS_EQUAL | GREATER | GREATER_EQUAL ) exprAdd )*
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

                if (!canBeScalar(rv) || !canBeScalar(right)) {
                    tkerr("Invalid operand type for relational operator");
                }

                rv.type = new Type(TypeBase.TB_INT);
                rv.isLVal = false;
                rv.isCtVal = true;
            }

            return true;
        }

        return false;
    }

    // exprAdd: exprMul ( ( PLUS | MINUS ) exprMul )*
    private boolean exprAdd(RetVal rv) {
        if (exprMul(rv)) {
            while (consume(TokenType.PLUS) || consume(TokenType.MINUS)) {
                RetVal right = new RetVal();

                if (!exprMul(right)) {
                    tkerr("Invalid additive expression");
                }

                rv.type = getArithType(rv.type, right.type);
                rv.isLVal = false;
                rv.isCtVal = true;
            }

            return true;
        }

        return false;
    }

    // exprMul: exprCast ( ( MULTIPLY | DIVIDE | MODULO ) exprCast )*
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
                rv.isCtVal = true;
            }

            return true;
        }

        return false;
    }

    // exprCast: LPAREN typeBase arrayDecl? RPAREN exprCast | exprUnary
    private boolean exprCast(RetVal rv) {
        int startIdx = crtIdx;
        Type castType = new Type(null);

        if (consume(TokenType.LPAREN)) {
            if (typeBase(castType)) {
                arrayDecl(castType);

                if (consume(TokenType.RPAREN)) {
                    RetVal inner = new RetVal();

                    if (exprCast(inner)) {
                        if (castType.typeBase == TypeBase.TB_STRUCT) {
                            tkerr("Cannot cast to a struct type");
                        }

                        if (inner.type.typeBase == TypeBase.TB_STRUCT) {
                            tkerr("Cannot cast a struct value");
                        }

                        if (inner.type.nElements >= 0 && castType.nElements < 0) {
                            tkerr("An array can be converted only to another array");
                        }

                        if (inner.type.nElements < 0 && castType.nElements >= 0) {
                            tkerr("A scalar can be converted only to another scalar");
                        }

                        rv.type = castType;
                        rv.isLVal = false;
                        rv.isCtVal = true;
                        return true;
                    }
                }
            }
        }

        crtIdx = startIdx;
        return exprUnary(rv);
    }

    // exprUnary: ( MINUS | NOT ) exprUnary | exprPostfix
    private boolean exprUnary(RetVal rv) {
        int startIdx = crtIdx;

        boolean isMinus = false;
        boolean isNot = false;

        if (consume(TokenType.MINUS)) {
            isMinus = true;
        } else if (consume(TokenType.NOT)) {
            isNot = true;
        }

        if (isMinus || isNot) {
            RetVal inner = new RetVal();

            if (!exprUnary(inner)) {
                tkerr("Invalid expression after unary operator");
            }

            if (!canBeScalar(inner)) {
                tkerr("Unary operator must have a scalar operand");
            }

            if (isNot) {
                rv.type = new Type(TypeBase.TB_INT);
            } else {
                rv.type = inner.type;
            }

            rv.isLVal = false;
            rv.isCtVal = true;

            return true;
        }

        crtIdx = startIdx;
        return exprPostfix(rv);
    }

    // exprPostfix: exprPrimary ( LBRACK expr RBRACK | DOT ID )*
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

                    if (index.type.nElements >= 0) {
                        tkerr("Array index cannot be an array");
                    }

                    Type intType = new Type(TypeBase.TB_INT);

                    if (!canConvert(index.type, intType)) {
                        tkerr("Array index must be convertible to int");
                    }

                    if (!consume(TokenType.RBRACK)) {
                        tkerr("Missing ] after array index");
                    }

                    Type oldType = rv.type;
                    rv.type = new Type(oldType.typeBase);
                    rv.type.structSymbol = oldType.structSymbol;

                    rv.isLVal = true;
                    rv.isCtVal = false;

                    continue;
                }

                if (consume(TokenType.DOT)) {
                    if (rv.type.typeBase != TypeBase.TB_STRUCT) {
                        tkerr("Left side of . is not a struct");
                    }

                    Token fieldTk = crtTk();

                    if (!consume(TokenType.IDENTIFIER)) {
                        tkerr("Missing field name after .");
                    }

                    Symbol field = null;

                    for (Symbol member : rv.type.structSymbol.structMembers) {
                        if (member.name.equals(fieldTk.getValue())) {
                            field = member;
                            break;
                        }
                    }

                    if (field == null) {
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

    // exprPrimary: ID call? | constants | LPAREN expr RPAREN
    private boolean exprPrimary(RetVal rv) {
        int startIdx = crtIdx;

        if (consume(TokenType.IDENTIFIER)) {
            Token tk = tokens.get(crtIdx - 1);

            Symbol s = symbolTable.findSymbol(tk.getValue());

            if (s == null) {
                tkerr("Undefined symbol: " + tk.getValue());
            }

            boolean wasCall = false;

            rv.type = s.type;
            rv.isLVal = s.kind == SymbolKind.SK_VAR || s.kind == SymbolKind.SK_PARAM;
            rv.isCtVal = false;

            if (consume(TokenType.LPAREN)) {
                wasCall = true;

                if (s.kind != SymbolKind.SK_FN) {
                    tkerr("Symbol is not a function: " + tk.getValue());
                }

                int argCount = 0;
                int argIndex = 0;
                RetVal arg = new RetVal();

                if (expr(arg)) {
                    if (argIndex >= s.fnParams.size()) {
                        tkerr("Too many arguments in call to " + tk.getValue());
                    }

                    if (!canConvert(arg.type, s.fnParams.get(argIndex).type)) {
                        tkerr("Invalid argument type in call to " + tk.getValue());
                    }

                    argCount++;
                    argIndex++;

                    while (consume(TokenType.COMMA)) {
                        arg = new RetVal();

                        if (!expr(arg)) {
                            tkerr("Invalid function call argument");
                        }

                        if (argIndex >= s.fnParams.size()) {
                            tkerr("Too many arguments in call to " + tk.getValue());
                        }

                        if (!canConvert(arg.type, s.fnParams.get(argIndex).type)) {
                            tkerr("Invalid argument type in call to " + tk.getValue());
                        }

                        argCount++;
                        argIndex++;
                    }
                }

                if (!consume(TokenType.RPAREN)) {
                    tkerr("Missing ) after function call");
                }

                if (argCount != s.fnParams.size()) {
                    tkerr("Invalid number of arguments in call to " + tk.getValue());
                }

                rv.type = s.type;
                rv.isLVal = false;
                rv.isCtVal = true;
            }

            if (s.kind == SymbolKind.SK_FN && !wasCall) {
                tkerr("A function can only be called");
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

        if (consume(TokenType.REAL_NUMBER)) {
            rv.type = new Type(TypeBase.TB_DOUBLE);
            rv.isLVal = false;
            rv.isCtVal = true;
            return true;
        }

        if (consume(TokenType.CT_CHAR)) {
            rv.type = new Type(TypeBase.TB_CHAR);
            rv.isLVal = false;
            rv.isCtVal = true;
            return true;
        }

        if (consume(TokenType.STRING)) {
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

    // =====================
    // Type checking helpers
    // =====================

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
        if (src == null || dst == null) {
            return false;
        }

        // Array conversions
        if (src.nElements >= 0 || dst.nElements >= 0) {
            // Both must be arrays.
            if (src.nElements < 0 || dst.nElements < 0) {
                return false;
            }

            // Base type must match.
            if (src.typeBase != dst.typeBase) {
                return false;
            }

            // For struct arrays, the struct definition must also match.
            if (src.typeBase == TypeBase.TB_STRUCT &&
                src.structSymbol != dst.structSymbol) {
                return false;
            }

            // Destination int v[] accepts any sized int array.
            if (dst.nElements == 0) {
                return true;
            }

            // Otherwise exact size must match.
            return src.nElements == dst.nElements;
        }

        // Same scalar type.
        if (src.typeBase == dst.typeBase) {
            if (src.typeBase == TypeBase.TB_STRUCT) {
                return src.structSymbol == dst.structSymbol;
            }

            return true;
        }

        // Numeric scalar conversions.
        if ((src.typeBase == TypeBase.TB_INT ||
            src.typeBase == TypeBase.TB_CHAR) &&
            (dst.typeBase == TypeBase.TB_INT ||
            dst.typeBase == TypeBase.TB_CHAR ||
            dst.typeBase == TypeBase.TB_DOUBLE)) {
            return true;
        }

        if (src.typeBase == TypeBase.TB_DOUBLE &&
            (dst.typeBase == TypeBase.TB_DOUBLE ||
            dst.typeBase == TypeBase.TB_INT ||
            dst.typeBase == TypeBase.TB_CHAR)) {
            return true;
        }

        return false;
    }

    private boolean canBeScalar(RetVal r) {
        return r != null &&
               r.type != null &&
               r.type.nElements < 0 &&
               r.type.typeBase != TypeBase.TB_STRUCT &&
               r.type.typeBase != TypeBase.TB_VOID;
    }

    private boolean isCastStart() {
        if (crtTk().getTokenType() != TokenType.LPAREN) {
            return false;
        }

        if (crtIdx + 1 >= tokens.size()) {
            return false;
        }

        TokenType next = tokens.get(crtIdx + 1).getTokenType();

        return next == TokenType.INT ||
            next == TokenType.DOUBLE ||
            next == TokenType.CHAR ||
            next == TokenType.STRUCT;
    }

    // =====================
    // Test main
    // =====================

    public static void main(String[] args) {
        try {
            String path = "src/compiler/tests/10.c";

            String input = java.nio.file.Files.readString(
                    java.nio.file.Paths.get(path)
            );

            compiler.lexer.Lexer lexer = new compiler.lexer.Lexer(input);
            java.util.List<Token> tokens = new java.util.ArrayList<>();

            Token tk;

            System.out.println("=== TOKENS ===");

            do {
                tk = lexer.getNextToken();
                tokens.add(tk);
                System.out.println(tk);
            } while (tk.getTokenType() != TokenType.EOF);

            System.out.println("\n=== PARSER ===");

            Parser parser = new Parser(tokens);

            if (parser.unit()) {
                System.out.println("Syntax is CORRECT!");
            }

            parser.symbolTable.printSymbols();

        } catch (Exception e) {
            System.out.println("PARSER ERROR:");
            System.out.println(e.getMessage());
        }
    }
}