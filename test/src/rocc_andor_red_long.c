#include <rocc.h>
#include <stdint.h>

// TODO: Rework to use a #define-d number?
// #define NUM_ELEMENTS 1
// int64_t a[NUM_ELEMENTS],b[NUM_ELEMENTS],c[NUM_ELEMENTS];
// ROCC_INSTRUCTION_S(0, NUM_ELEMENTS, 0x40);
int main() {
    int64_t b,b2,c,c2,c3;
    int64_t a[17] = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17};
    b = 17;
    ROCC_INSTRUCTION_S(0, 1, 0x40);  // Send "length" of vector
    ROCC_INSTRUCTION_S(0, &c, 0x41); // Send destination address
    ROCC_INSTRUCTION_DSS(0, c, &a, b, 3); // Wait for result
    int64_t expected = 0;
    for(int i = 0; i < 17; i++){
        expected = expected | a[i];
    }

    int64_t a2[7] = { -1, -2, -4, -8, 4095, -1, -6};
    b2 = 7;
    ROCC_INSTRUCTION_DSS(0, c2, &a2, b2, 4); // Wait for result
    int64_t expected2 = -1;
    for(int i = 0; i < 7; i++){
        expected2 = expected2 & a2[i];
    }

    ROCC_INSTRUCTION_DSS(0, c3, &a, b, 2); // Wait for result
    int64_t expected3 = 0;
    for(int i = 0; i < 17; i++){
        expected3 = expected3 + a[i];
    }

    return ((expected == c) && (expected2 == c2) && (expected3 == c3)) ? 0 : 1;
}
