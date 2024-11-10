#include <rocc.h>
#include <stdint.h>

#define IDENTITY 0

int main() {
    int64_t status;
    int64_t a[8] = {0x0033, 0xdc32, 1, 0x7f2a, 3, 9, 0x6294, 0x7bce};
    int64_t rocc_computed[8];
    int64_t expected[8];

    ROCC_INSTRUCTION_S(0, 8, 0x40);  // Send "length" of vector
    ROCC_INSTRUCTION_S(0, &rocc_computed, 0x41); // Send destination address
    ROCC_INSTRUCTION_DS(0, status, &a, 27); // Wait for result

    expected[0] = IDENTITY;
    for(int i = 1; i <= 7; i++){
        expected[i] = (a[i-1] | expected[i-1]);
    }

    int arrays_equal = 1;
    for (int i = 0; i < 8; i++) {
        if(expected[i] != rocc_computed[i]) {
            arrays_equal = 0;
            return 3;
        }
    }

    return ((status == 0) && arrays_equal) ? 0 : 1;
}
