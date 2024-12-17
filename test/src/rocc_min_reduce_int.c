#include <rocc.h>
#include <stdint.h>

int main() {
    int64_t rocc_computed,status;
    int64_t a[8] = { 1, -2, 3, 4, 0, -3, -1, 9};
    ROCC_INSTRUCTION_S(0, 8, 0x40);  // Send "length" of vector
    ROCC_INSTRUCTION_S(0, &rocc_computed, 0x41); // Send destination address
    ROCC_INSTRUCTION_DS(0, status, &a, 31); // Wait for result

    int64_t expected = INT64_MAX;
    for(int i = 0; i < 8; i++){
        expected = (a[i] < expected) ? a[i] : expected;
    }

    if (status == 0) {
        return (expected == rocc_computed) ? 0 : 1;
    } else {
        return 2;
    }
}
