#include <rocc.h>
#include <stdint.h>

// TODO: Rework to use a #define-d number?
// #define NUM_ELEMENTS 1
// int64_t a[NUM_ELEMENTS],b[NUM_ELEMENTS],c[NUM_ELEMENTS];
// ROCC_INSTRUCTION_S(0, NUM_ELEMENTS, 0x40);
int main() {
    int64_t c[3],status;
    int64_t a[3] = {1, 0xdc32, 4};
    int64_t b[3] = {3, 0x2cf1, 4};
    ROCC_INSTRUCTION_S(0, 3, 0x40);  // Send "length" of vector
    ROCC_INSTRUCTION_S(0, &c, 0x41); // Send destination address
    // DSS used to block the main core.
    ROCC_INSTRUCTION_DSS(0, status, &a, &b, 15); // Wait for result
    /* The value put back into the rd register is IMMEDIATELY stored back into
     * memory! */
    // Host-side mul: (a != b)
    int64_t expected[3]; 
    for(int i = 0; i < 3; i++) {
        expected[i] = (a[i] != b[i]) ? 1 : 0;
    }

    int arrays_equal = 1;
    if (status == 0) {
        for(int i = 0; i < 3; i++) {
            if(c[i] != expected[i]) {
                arrays_equal = 0;
                return i+1;
            }
        }
    }
    else { return 10; }

    return ((status == 0) && arrays_equal) ? 0 : 4;
}