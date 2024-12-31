#include <rocc.h>
#include <stdint.h>
#include <stdio.h>

int main() {
    int64_t c[10],status;
    int64_t a[10] = {6, 9, 4, 0, 3, 5, 2, 1, 7, 8};
    int64_t b[10] = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
    ROCC_INSTRUCTION_S(0, 10, 0x40);  // Send "length" of vector
    ROCC_INSTRUCTION_S(0, &c, 0x41); // Send destination address
    // DSS used to block the main core.
    ROCC_INSTRUCTION_DSS(0, status, &a, &b, 35); // Wait for result
    /* The value put back into the rd register is IMMEDIATELY stored back into
     * memory! */
    int64_t expected[10]; 
    for(int i = 0; i < 10; i++) {
        expected[a[i]] = b[i];
    }

    for(int i = 0; i < 10; i++) {
        printf("i = %d, c = %x, expected = %x\n", i, c[i], expected[i]);
    }

    int arrays_equal = 1;
    if (status == 0) {
        for(int i = 0; i < 10; i++) {
            //printf("i = %d, c = %x, expected = %x\n", i, c[i], expected[i]);
            if(c[i] != expected[i]) {
                arrays_equal = 0;
                return i+1;
            }
        }
    }
    else { return 10; }

    return ((status == 0) && arrays_equal) ? 0 : 4;
}