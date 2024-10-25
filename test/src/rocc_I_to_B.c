#include <rocc.h>
#include <stdint.h>
#include <stdbool.h>

int main() {
    int64_t c[8],status;
    int64_t a[8] = { 0x0001, 0x0000, 0x7200, 0x3FC1, 0xF92A, 0xDBE5, 0x0, 0xFFFF };
    ROCC_INSTRUCTION_S(0, 8, 0x40);  // Send "length" of vector
    ROCC_INSTRUCTION_S(0, &c, 0x41); // Send destination address
    ROCC_INSTRUCTION_DS(0, status, &a, 23); // Wait for result

    int64_t expected[8];
    for(int i = 0; i < 8; i++){
        expected[i] = (a[i] == 0) ? false : true;
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
