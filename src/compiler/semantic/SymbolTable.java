package compiler.semantic;

import java.util.ArrayList;
import java.util.List;

public class SymbolTable {
    private List<Symbol> symbols = new ArrayList<>();
    public int currentDepth = 0;
    public Symbol currentOwner = null; // To track if we are in a struct or function

    // Just increases depth
    public void pushDomain() {
        currentDepth++;
    }

    // Deletes symbols from the current depth
    public void dropDomain() {
        // We remove everything that was added at this depth
        for (int i = symbols.size() - 1; i >= 0; i--) {
            if (symbols.get(i).depth == currentDepth) {
                symbols.remove(i);
            }
        }
        currentDepth--;
    }

    //Adds at the end of the list
    public void addSymbol(Symbol s) {
        s.depth = currentDepth;
        symbols.add(s);
    }

    // Search from right to left (most recent first)
    public Symbol findSymbol(String name) {
        for (int i = symbols.size() - 1; i >= 0; i--) {
            if (symbols.get(i).name.equals(name)) {
                return symbols.get(i);
            }
        }
        return null;
    }

    // Used for redefinition checks in the current scope only
    public Symbol findInCurrentDomain(String name) {
        for (int i = symbols.size() - 1; i >= 0; i--) {
            Symbol s = symbols.get(i);
            if (s.depth < currentDepth) break; // We went past the current scope
            if (s.name.equals(name)) return s;
        }
        return null;
    }
}