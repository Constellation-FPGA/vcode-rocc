#include <rocc.h>
#include <stdint.h>

// TODO: Rework to use a #define-d number?
// #define NUM_ELEMENTS 1
// int64_t a[NUM_ELEMENTS],b[NUM_ELEMENTS],c[NUM_ELEMENTS];
// ROCC_INSTRUCTION_S(0, NUM_ELEMENTS, 0x40);
int main() {
    int64_t b,b2,c,c2,c3,c4;
    int64_t a[17] = { -1, 2, -3, 0, 5, 9, 7, 8, 9, 22, -11, 12, 3, 14, -15, 116, 17};
    b = 17;
    
    ROCC_INSTRUCTION_DSS(0, c, &a, b, 5); // Wait for result
    int64_t expected = a[0];
    for(int i = 0; i < b; i++){
        if (expected < a[i]){
            expected = a[i];
        }
    }

    int64_t a2[7] = { -1, -2, -4, -8, 4095, -1, -6};
    b2 = 7;
    ROCC_INSTRUCTION_DSS(0, c2, &a2, b2, 5); // Wait for result
    int64_t expected2 = a2[0];
    for(int i = 0; i < b2; i++){
        if (expected2 < a2[i]){
            expected2 = a2[i];
        }
    }

    ROCC_INSTRUCTION_DSS(0, c3, &a, b, 6); // Wait for result
    int64_t expected3 = a[0];
    for(int i = 0; i < b; i++){
        if (expected3 > a[i]){
            expected3 = a[i];
        }
    }

    ROCC_INSTRUCTION_DSS(0, c4, &a2, b2, 6); // Wait for result
    int64_t expected4 = a2[0];
    for(int i = 0; i < b2; i++){
        if (expected4 > a2[i]){
            expected4 = a2[i];
        }
    }

    return ((expected == c) && (expected2 == c2) && (expected3 == c3) && (expected4 == c4)) ? 0 : 1;
}
