#include <rocc.h>
#include <stdint.h>

// TODO: Rework to use a #define-d number?
// #define NUM_ELEMENTS 1
// int64_t a[NUM_ELEMENTS],b[NUM_ELEMENTS],c[NUM_ELEMENTS];
// ROCC_INSTRUCTION_S(0, NUM_ELEMENTS, 0x40);
int main() {
    int64_t b;
    int64_t a[5000];
    int64_t c[5000];
    b = 5000;
    for(int i = 0; i < b; i++){
        c[i] = c[i] + a[i];
    }

    return 0;
}
