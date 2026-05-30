package compiler.semantic;

import java.util.ArrayList;
import java.util.List;

// Represents a symbol from the symbol table.
public class Symbol {

    public String name;

    public SymbolKind kind;

    public MemoryLocation mem;

    public Type type;

    // Scope depth:
    // 0 = global
    // 1 = function scope
    // 2+ = nested blocks
    public int depth;

    // Function parameters
    public List<Symbol> fnParams = new ArrayList<>();

    // Variables declared inside the function
    public List<Symbol> fnLocals = new ArrayList<>();

    // Struct fields
    public List<Symbol> structMembers = new ArrayList<>();

    public Symbol(String name, SymbolKind kind) {
        this.name = name;
        this.kind = kind;
    }
}