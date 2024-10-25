#include <rocc.h>
#include <stdint.h>
#include <stdbool.h>

int main() {
    int64_t c[8],status;
    int64_t a[8] = { true, false, true, true, false, true, false, false };
    ROCC_INSTRUCTION_S(0, 8, 0x40);  // Send "length" of vector
    ROCC_INSTRUCTION_S(0, &c, 0x41); // Send destination address
    ROCC_INSTRUCTION_DS(0, status, &a, 24); // Wait for result

    int64_t expected[8];
    for(int i = 0; i < 8; i++){
        expected[i] = (a[i] == true) ? 1 : 0;
    }

    int arrays_equal = 1;
    if (status == 0) {
        for(int i = 0; i < 8; i++) {
            if(c[i] != expected[i]) {
                arrays_equal = 0;
                return i+1;
            }
        }
    }
    else { return 10; }

    return ((status == 0) && arrays_equal) ? 0 : 4;
}
