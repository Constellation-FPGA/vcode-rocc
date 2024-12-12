#include <rocc.h>
#include <stdint.h>
//#include <stdio.h>

int main() {
    int64_t rocc_computed,status;
    int64_t a[8] = { 1, 2, 3, 4, 5, 6, 7, 8};
    ROCC_INSTRUCTION_S(0, 8, 0x40);  // Send "length" of vector
    ROCC_INSTRUCTION_S(0, &rocc_computed, 0x41); // Send destination address
    ROCC_INSTRUCTION_DS(0, status, &a, 30); // Wait for result

    int64_t expected = INT64_MIN;
    for(int i = 0; i < 8; i++){
        expected = (a[i] > expected) ? a[i] : expected;
    }
    //printf("rocc_computed = %x, expected = %x", rocc_computed, expected);

    if (status == 0) {
        return (expected == rocc_computed) ? 0 : 1;
    } else {
        return 2;
    }
}
