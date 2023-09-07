#include <rocc.h>
#include <stdint.h>

// TODO: Rework to use a #define-d number?
// #define NUM_ELEMENTS 1
// int64_t a[NUM_ELEMENTS],b[NUM_ELEMENTS],c[NUM_ELEMENTS];
// ROCC_INSTRUCTION_S(0, NUM_ELEMENTS, 0x40);
int main() {
    int64_t c[2],status;
    int64_t a[2] = {1, 0xcd9c};
    int64_t b[2] = {2, 0x1111};
    ROCC_INSTRUCTION_S(0, 2, 0x40);  // Send "length" of vector
    ROCC_INSTRUCTION_S(0, &c, 0x41); // Send destination address
    // DSS used to block the main core.
    ROCC_INSTRUCTION_DSS(0, status, &a, &b, 1); // Wait for result
    /* The value put back into the rd register is IMMEDIATELY stored back into
     * memory! */
    // a + b
    int64_t expected[2]; // {3, 0xdead}
    for(int i = 0; i < 2; i++) {
        expected[i] = a[i] + b[i];
    }

    int arrays_equal = 1;
    if (status == 0) {
        for(int i = 0; i < 2; i++) {
            if(c[i] != expected[i]) {
                arrays_equal = 0;
                return i+1;
            }
        }
    }
    else { return 3; }

    return ((status == 0) && arrays_equal) ? 0 : 4;
}
