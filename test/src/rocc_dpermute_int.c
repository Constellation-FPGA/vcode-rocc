#include <rocc.h>
#include <stdint.h>
#include <stdio.h>
#include <stdbool.h>

#define NUM_ELEMENTS 10

int main() {
    int64_t out_actual[NUM_ELEMENTS+2],status;
    int64_t data[NUM_ELEMENTS]    = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
    int64_t indices[NUM_ELEMENTS] = {6, 11, 4, 0, 3, 5, 2, 10, 7, 8};
    int64_t defaultValue = 0xE;

    ROCC_INSTRUCTION_S(0, NUM_ELEMENTS, 0x40);  // Send "length" of vector
    ROCC_INSTRUCTION_S(0, &out_actual, 0x41); // Send destination address
    ROCC_INSTRUCTION_S(0, &defaultValue, 0x42); // Send default
    // DSS used to block the main core.
    ROCC_INSTRUCTION_DSS(0, status, &data, &indices, 36); // Wait for result
    /* The value put back into the rd register is IMMEDIATELY stored back into
     * memory! */

    /* We expect: {3, 0xF, 6, 4, 2, 5, 0, 8, 9, 0xF, 7, 1} */
    //int64_t sizeOfIndices = sizeof(indices) / sizeof(indices[0]);
    int64_t expected[NUM_ELEMENTS+2];
    //bool isValid;
    /*for(int i = 0; i < NUM_ELEMENTS; i++) {
        for(int j = 0; j < sizeOfIndices; j++) {
            if(indices[j] == i) {
                isValid = true;
                break;
            }
            else {
                isValid = false;
            }
        }
        if(isValid == true) {
            expected[indices[i]] = data[i];
        }
        else {
            expected[i] = defaultValue;
        }
    }*/
    for (int i = 0; i < NUM_ELEMENTS+2; i++) {
        expected[i] = defaultValue;
    }
    for (int i = 0; i < NUM_ELEMENTS; i++) {
        expected[indices[i]] = data[i];
    }

    for(int i = 0; i < NUM_ELEMENTS+2; i++) {
        printf("i = %d, actual = %x, expected = %x\n", i, out_actual[i], expected[i]);
    }

    int arrays_equal = 1;
    if (status == 0) {
        for(int i = 0; i < NUM_ELEMENTS+2; i++) {
            if(out_actual[i] != expected[i]) {
                arrays_equal = 0;
                return i+1;
            }
        }
    }
    else { return 10; }

    return ((status == 0) && arrays_equal) ? 0 : 4;
}