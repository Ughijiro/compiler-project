package compiler.semantic;

public class Type {

    public TypeBase typeBase;
    public Symbol structSymbol; // Only used if typeBase is TB_STRUCT
    public int nElements;      // -1 for non-array, 0 for int v[], >0 for int v[10]

    public Type(TypeBase typeBase) {
        this.typeBase = typeBase;
        this.nElements = -1; // Default for not an array
    }

    @Override
    public String toString() {
        String base = typeBase.toString();
        if (typeBase == TypeBase.TB_STRUCT && structSymbol != null) {
            base += " (" + structSymbol.name + ")";
        }
        if (nElements >= 0) {
            base += "[" + (nElements > 0 ? nElements : "") + "]";
        }
        return base;
    }
}