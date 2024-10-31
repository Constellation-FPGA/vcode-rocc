#include <rocc.h>
#include <stdint.h>

#define IDENTITY INT64_MIN

int main() {
    int64_t status;
    int64_t a[8] = { 1, 2, 3, 0, 5, 2, 7, 8 };
    int64_t rocc_computed[8];
    int64_t expected[8];

    ROCC_INSTRUCTION_S(0, 8, 0x40);  // Send "length" of vector
    ROCC_INSTRUCTION_S(0, &rocc_computed, 0x41); // Send destination address
    ROCC_INSTRUCTION_DS(0, status, &a, 24); // Wait for result

    expected[0] = IDENTITY;
    for(int i = 1; i <= 7; i++){
        expected[i] = (a[i-1] > expected[i-1]) ? a[i-1] : expected[i-1];
        printf("i = %d: rocc_computed = %x, expected = %x\n", i, rocc_computed[i], expected[i]);
    }
    printf("i = 0: rocc_computed = %llx, expected = %llx\n", rocc_computed[0], expected[0]);

    int arrays_equal = 1;
    for (int i = 0; i < 8; i++) {
        if(expected[i] != rocc_computed[i]) {
            arrays_equal = 0;
            return 3;
        }
    }

    return ((status == 0) && arrays_equal) ? 0 : 1;
}