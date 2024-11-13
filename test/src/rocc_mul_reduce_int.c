#include <rocc.h>
#include <stdint.h>

int main() {
    int64_t rocc_computed,status;
    int64_t a[8] = { 1, 2, 3, 2, 3, 6, 1, 4 };
    ROCC_INSTRUCTION_S(0, 8, 0x40);  // Send "length" of vector
    ROCC_INSTRUCTION_S(0, &rocc_computed, 0x41); // Send destination address
    ROCC_INSTRUCTION_DS(0, status, &a, 0x1D); // Wait for result

    int64_t expected = 1;
    for(int i = 0; i < 8; i++){
        expected *= a[i];
    }

    if (status == 0) {
        return (expected == rocc_computed) ? 0 : 1;
    } else {
        return 2;
    }
}
