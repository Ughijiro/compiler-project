// Lexer Ultimate Test File
struct Pt {
    int x, y;
};

struct Pt points[0b1010]; // Binary base (10)

void test_numbers() {
    int h = 0xAF;       // Hex base
    int o = 0123;       // Octal base
    double r = 3.1415;  // Real number
    double e = 1.2e+10; // Scientific notation
    double f = .5;      // Should be DOT and then 5 (based on your change)
    double x = 0e10;
}

int main() {
    char c = '\n'; // Escaped char
    put_s("String with \"escapes\" and \t tabs\n");

    int i;
    for (i = 0; i < 10; i = i + 1) {
        if (i >= 5 && i != 7 || !true) {
            points[i].x = i * 2;
        } else {
            points[i].y = i / 2;
        }
    }
    
    return 0;
}