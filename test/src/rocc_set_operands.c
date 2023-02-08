#include <rocc.h>
#include <stdint.h>

#define NUM_ELEMENTS 1

int main() {
    ROCC_INSTRUCTION_S(0, NUM_ELEMENTS, 0x40);

    return 0;
}
