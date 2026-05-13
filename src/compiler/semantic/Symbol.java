package compiler.semantic;

import java.util.ArrayList;
import java.util.List;


public class Symbol {

    public String name;
    public SymbolKind kind;
    public MemoryLocation mem;
    public Type type;
    public int depth; // Scope level (0 for global, 1 for inside function, etc.)

    // For Functions: local variables and parameters
    public List<Symbol> fnParams = new ArrayList<>();
    public List<Symbol> fnLocals = new ArrayList<>();

    // For Structs: members
    public List<Symbol> structMembers = new ArrayList<>();

    public Symbol(String name, SymbolKind kind) {
        this.name = name;
        this.kind = kind;
    }
}