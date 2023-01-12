#include <malloc.h>

int main() {
    int a,c;
    int *b = NULL;
    a = 1;
    b = malloc(sizeof(int));
    c = a + *b;
    return (c == 3) ? 0 : 1;
}
