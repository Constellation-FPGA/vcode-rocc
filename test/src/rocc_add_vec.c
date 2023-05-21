#include <rocc.h>
#include <stdint.h>

// TODO: Rework to use a #define-d number?
// #define NUM_ELEMENTS 1
// int64_t a[NUM_ELEMENTS],b[NUM_ELEMENTS],c[NUM_ELEMENTS];
// ROCC_INSTRUCTION_S(0, NUM_ELEMENTS, 0x40);
int main() {
    int64_t ret;
    int64_t a[17] = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17 };
    int64_t b[17] = { 3, 1, 4, 1, 5, 9, 2, 6, 3, 1, 4, 1, 5, 9, 2, 6, 3 };
    int64_t c[17] = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

    ret = 0;
    ROCC_INSTRUCTION_S(0, 17, 0x40);  // Send "length" of vector
    ROCC_INSTRUCTION_S(0, &c, 0x41); // Send destination address
    ROCC_INSTRUCTION_DSS(0, ret, &a, &b, 7); // Wait for result
    int64_t expected = 0;
    for(int i = 0; i < 17; i++){
        if(c[i]!=a[i] + b[i]){
            expected = 1;
        }
    }
    return expected;
}
