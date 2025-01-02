#include <rocc.h>
#include <stdint.h>
#include <stdio.h>

#define NUM_ELEMENTS 10

int main() {
    int64_t out_actual[NUM_ELEMENTS],status;
    int64_t indices[NUM_ELEMENTS] = {6, 9, 4, 0, 3, 5, 2, 1, 7, 8};
    int64_t data[NUM_ELEMENTS]    = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};


    ROCC_INSTRUCTION_S(0, NUM_ELEMENTS, 0x40);  // Send "length" of vector
    ROCC_INSTRUCTION_S(0, &out_actual, 0x41); // Send destination address
    // DSS used to block the main core.
    ROCC_INSTRUCTION_DSS(0, status, &indices, &data, 35); // Wait for result
    /* The value put back into the rd register is IMMEDIATELY stored back into
     * memory! */

    /* We expect: {3, 7, 6, 4, 2, 5, 0, 8, 9, 1} */
    int64_t expected[NUM_ELEMENTS];
    for(int i = 0; i < NUM_ELEMENTS; i++) {
        expected[indices[i]] = data[i];
    }

    for(int i = 0; i < NUM_ELEMENTS; i++) {
        printf("i = %d, actual = %x, expected = %x\n", i, out_actual[i], expected[i]);
    }

    int arrays_equal = 1;
    if (status == 0) {
        for(int i = 0; i < NUM_ELEMENTS; i++) {
            if(out_actual[i] != expected[i]) {
                arrays_equal = 0;
                return i+1;
            }
        }
    }
    else { return 10; }

    return ((status == 0) && arrays_equal) ? 0 : 4;
}
