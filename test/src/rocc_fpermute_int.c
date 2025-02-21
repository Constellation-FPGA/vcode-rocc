#include <rocc.h>
#include <stdint.h>
#include <stdio.h>

#define NUM_ELEMENTS 8

int main() {
    int64_t out_actual[NUM_ELEMENTS],status;
    int64_t data[NUM_ELEMENTS]    = {0, 1, 2, 3, 4, 5, 6, 7};
    int64_t indices[NUM_ELEMENTS] = {5, 2, 4, 2, 1, 0, 5, 3};
    int64_t flag = 0xBD;   // 0b10_1011_1101

    ROCC_INSTRUCTION_S(0, NUM_ELEMENTS, 0x40);  // Send "length" of vector
    ROCC_INSTRUCTION_S(0, &out_actual, 0x41); // Send destination address
    ROCC_INSTRUCTION_S(0, &flag, 0x42); // Send flags
    // DSS used to block the main core.
    ROCC_INSTRUCTION_DSS(0, status, &data, &indices, 36); // Wait for result
    /* The value put back into the rd register is IMMEDIATELY stored back into
     * memory! */

    /* We expect: {5, 4, 3, 7, 2, 0} */
    int64_t expected[NUM_ELEMENTS];
    for(int i = 0; i < NUM_ELEMENTS-2; i++) {
        if(flag & 0x1 == 0x1) {
            expected[indices[i]] = data[i];
        }
        printf("i = %d, flag bit is %x, actual = %x, expected = %x\n", i, flag & 0x1, out_actual[i], expected[i]);
        flag = flag >> 1;
    }

    /*for(int i = 0; i < NUM_ELEMENTS-3; i++) {
        printf("i = %d, actual = %x, expected = %x\n", i, out_actual[i], expected[i]);
    }*/

    int arrays_equal = 1;
    if (status == 0) {
        for(int i = 0; i < NUM_ELEMENTS-2; i++) {
            if(out_actual[i] != expected[i]) {
                arrays_equal = 0;
                return i+1;
            }
        }
    }
    else { return 10; }

    return ((status == 0) && arrays_equal) ? 0 : 4;
}