#include <rocc.h>
#include <stdint.h>

// TODO: Rework to use a #define-d number?
// #define NUM_ELEMENTS 1
// int64_t a[NUM_ELEMENTS],b[NUM_ELEMENTS],c[NUM_ELEMENTS];
// ROCC_INSTRUCTION_S(0, NUM_ELEMENTS, 0x40);
int main() {
    int64_t dest[5],status;
    int64_t true_vec[5] = {1, 0xdc32, 4, 0x46a, 0x8f2b};
    int64_t false_vec[5] = {3, 0x2cf1, 2, 0x9e, 0x3dea};
    int64_t flag = 0xB;
    ROCC_INSTRUCTION_S(0, 5, 0x40);  // Send "length" of vector
    ROCC_INSTRUCTION_S(0, &dest, 0x41); // Send destination address
    ROCC_INSTRUCTION_S(0, &flag, 0x42); // Send flags
    // DSS used to block the main core.
    ROCC_INSTRUCTION_DSS(0, status, &true_vec, &false_vec, 22); // Wait for result (not sure)
    /* The value put back into the rd register is IMMEDIATELY stored back into
     * memory! */
    // Host-side select: c ? a : b
    int64_t expected[5]; 
    for(int i = 0; i < 5; i++) {
        if(flag & 0x1 == 0x1){
            expected[i] = true_vec[i];
        }
        else /*if(flag & 0x1 == 0x0)*/{
            expected[i] = false_vec[i];
        }
        printf("flag bit is %x, true value is %x, false value is %x, dest is %x, expected value is %x\n", flag & 0x1, true_vec[i], false_vec[i], dest[i], expected[i]);
        flag = flag >> 1;
    }


    int arrays_equal = 1;
    if (status == 0) {
        for(int i = 0; i < 5; i++) {
            if(dest[i] != expected[i]) {
                arrays_equal = 0;
                return i+1;
            }
        }
    }
    else { return 10; }

    return ((status == 0) && arrays_equal) ? 0 : 4;
}