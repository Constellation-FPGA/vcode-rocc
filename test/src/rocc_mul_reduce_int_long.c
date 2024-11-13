#include <rocc.h>
#include <stdint.h>

int main() {
    int64_t rocc_computed, status;
    int64_t a[17] = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17};
    ROCC_INSTRUCTION_S(0, 17, 0x40);  // Send "length" of vector
    ROCC_INSTRUCTION_S(0, &rocc_computed, 0x41); // Send destination address
    ROCC_INSTRUCTION_DS(0, status, &a, 29); // Wait for result

    int64_t expected = 0;
    for(int i = 0; i < 17; i++){
        expected *= a[i];
    }

    if (status == 0) {
        return (expected == rocc_computed) ? 0 : 1;
    } else {
        return 2;
    }
}
