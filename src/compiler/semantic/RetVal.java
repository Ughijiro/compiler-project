package compiler.semantic;

public class RetVal {
    public Type type;
    public boolean isLVal;  // true if the expression can appear on the left side of =
    public boolean isCtVal; // Compile-time constant value
}
