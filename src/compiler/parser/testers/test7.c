struct B{
    int y;
    int c;
};

struct A{
    int x;
    struct B meow;
};

int main(){
    struct A hey;
    hey.x;
    hey.meow.c;
}
