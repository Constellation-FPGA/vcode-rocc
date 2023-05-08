#include <rocc.h>
#include <stdint.h>

// TODO: Rework to use a #define-d number?
// #define NUM_ELEMENTS 1
// int64_t a[NUM_ELEMENTS],b[NUM_ELEMENTS],c[NUM_ELEMENTS];
// ROCC_INSTRUCTION_S(0, NUM_ELEMENTS, 0x40);
int main() {
    int64_t b,c;
    int64_t a[10000];
    b = 10000;
    ROCC_INSTRUCTION_DSS(0, c, &a, b, 2); // Wait for result

    return 0;
}
