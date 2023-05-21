#include <rocc.h>
#include <stdint.h>

// TODO: Rework to use a #define-d number?
// #define NUM_ELEMENTS 1
// int64_t a[NUM_ELEMENTS],b[NUM_ELEMENTS],c[NUM_ELEMENTS];
// ROCC_INSTRUCTION_S(0, NUM_ELEMENTS, 0x40);
int main() {
    int64_t b,b2;
    int64_t c[17];
    int64_t c2[7];
    int64_t a[17] = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17};
    b = 17;
    ROCC_INSTRUCTION_S(0, 1, 0x40);  // Send "length" of vector
    ROCC_INSTRUCTION_S(0, &c, 0x41); // Send destination address
    ROCC_INSTRUCTION_DSS(0, b, &a, b, 8); // Wait for result
    int64_t expected = 0;
    int64_t check = 0;
    for(int i = 0; i < 17; i++){
        expected += a[i];
        if(c[i] != expected){
            check = 1;
        }
    }

    int64_t a2[7] = { 3, 8, 4, 5, 9, -1, -6};
    b2 = 7;
    ROCC_INSTRUCTION_S(0, &c2, 0x41); // Send destination address
    ROCC_INSTRUCTION_DSS(0, b, &a2, b2, 8); // Wait for result
    int64_t expected2 = 0;
    for(int i = 0; i < 7; i++){
        expected2 += a2[i];
        if(c2[i] != expected2){
            check = 1;
        }
    }

    return check;
}
