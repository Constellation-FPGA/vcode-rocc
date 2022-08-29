#include <rocc.h>
#include <stdint.h>

int main() {
    int64_t a,b,c;
    a = 1;
    b = 2;
    ROCC_INSTRUCTION_DSS(0, c, &a, &b, 1);
    return 0;
}
