#include <rocc.h>
#include <stdint.h>

#define NUM_ELEMENTS 4090

int main() {
    int64_t status;
    int64_t c[NUM_ELEMENTS];
    int64_t a[NUM_ELEMENTS],b[NUM_ELEMENTS];

    for(int64_t i = 0; i < NUM_ELEMENTS; i++) {
        a[i] = i;
        b[i] = i;
    }

    ROCC_INSTRUCTION_S(0, NUM_ELEMENTS, 0x40);  // Send "length" of vector
    ROCC_INSTRUCTION_S(0, &c, 0x41); // Send destination address
    // DSS used to block the main core.
    ROCC_INSTRUCTION_DSS(0, status, &a, &b, 1); // Wait for result
    /* The value put back into the rd register is IMMEDIATELY stored back into
     * memory! */
    // Host-side add: a + b
    int64_t expected[NUM_ELEMENTS];
    for(int i = 0; i < NUM_ELEMENTS; i++) {
        expected[i] = a[i] + b[i];
    }

    int arrays_equal = 1;
    if (status == 0) {
        for(int i = 0; i < NUM_ELEMENTS; i++) {
            if(c[i] != expected[i]) {
                arrays_equal = 0;
                return i+1;
            }
        }
    }
    else { return 10; }

    return ((status == 0) && arrays_equal) ? 0 : 4;
}
