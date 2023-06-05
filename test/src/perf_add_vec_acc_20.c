#include <rocc.h>
#include <stdint.h>

// TODO: Rework to use a #define-d number?
// #define NUM_ELEMENTS 1
// int64_t a[NUM_ELEMENTS],b[NUM_ELEMENTS],c[NUM_ELEMENTS];
// ROCC_INSTRUCTION_S(0, NUM_ELEMENTS, 0x40);
int main() {
    int64_t ret;
    int64_t b;
    int64_t a[20];
    int64_t c[20];
    
    ret = 0;
    ROCC_INSTRUCTION_S(0, 20, 0x40);  // Send "length" of vector
    ROCC_INSTRUCTION_S(0, &c, 0x41); // Send destination address
    ROCC_INSTRUCTION_DSS(0, ret, &a, &c, 7); // Wait for result

    return 0;
}
