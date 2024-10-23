#include <rocc.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <inttypes.h>

/* This test is NOT intended to be a performance benchmark (for now). This is
 * meant to be a validation test for the extra logic added to make the three
 * operand execution of VCODE's SELECT work.
 * In the loop where we generate */

/** Divide x by y and compute the integer ceiling of the quotient. This must be
 * a macro so that the arrays are allocated at compile-time.*/
#define CEIL(x, y) ((((x) + (y)) - 1) / (y))

#define NUM_ELEMENTS 1000
/* XXX: FLAG_WIDTH _MUST_ match the bit-width the type of the flags vector's
 * elements! */
#define FLAG_WIDTH 64
#define NUM_FLAGS CEIL(NUM_ELEMENTS, FLAG_WIDTH)

/** Build a random 64-bit value that we will "interpret" as an integer. */
int64_t random_int64() {
    int64_t high = (int64_t)rand();
    int64_t low = (int64_t)rand();
    return (high << 32) | low;
}

int main() {
    int64_t dest[NUM_ELEMENTS],status;
    int64_t true_vec[NUM_ELEMENTS];
    int64_t false_vec[NUM_ELEMENTS];
    int64_t flag[NUM_FLAGS];

    /* We send metadata about the operation to perform to the co-processor
     * ahead-of-time. We do this for no paritcular reason, other than we can.
     *
     * XXX: The vectors SHOULD NOT MOVE in-memory between us sending their
     * addresses to the coprocessor and us filling them. If they do, then we
     * must send this information AFTER we initialize them! */
    ROCC_INSTRUCTION_S(0, NUM_ELEMENTS, 0x40);  // Send "length" of vector
    ROCC_INSTRUCTION_S(0, &dest, 0x41); // Send destination address
    ROCC_INSTRUCTION_S(0, &flag, 0x42); // Send flags

    // Host-side select: c ? a : b
    int64_t expected[NUM_ELEMENTS];
    for(int j = 0; j < NUM_FLAGS; j++){
        flag[j] = random_int64();
        ROCC_INSTRUCTION_S(0, &flag[j], 0x42);
        for(int i = 64*j; i < 64*(j+1) && i < 1000; i++) {
            true_vec[i] = random_int64();
            false_vec[i] = random_int64();
            ROCC_INSTRUCTION_DSS(0, status, &true_vec[i], &false_vec[i], 22);
            ROCC_INSTRUCTION_S(0, &dest[i], 0x41);
            if(flag[j] & 0x1 == 0x1){
                expected[i] = true_vec[i];
            }
            else /*if(flag & 0x1 == 0x0)*/{
                expected[i] = false_vec[i];
            }
            /* printf("%3d: flag bit is 0b%x\ttrue value = 0x%016" PRIx64 */
            /*        "\tfalse value = 0x%016" PRIx64 */
            /*        "\texpected: 0x%016" PRIx64 "\n", */
            /*        i, flag[j] & 0x1, true_vec[i], false_vec[i], dest[i], expected[i]); */
            flag[j] = flag[j] >> 1;
        }
    }

    int arrays_equal = 1;
    if (status == 0) {
        for(int i = 0; i < 1000; i++) {
            if(dest[i] != expected[i]) {
                arrays_equal = 0;
                return i+1;
            }
        }
    }
    else { return 10; }

    return ((status == 0) && arrays_equal) ? 0 : 4;
}