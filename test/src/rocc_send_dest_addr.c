#include <rocc.h>
#include <stdint.h>

int main() {
    int64_t a;
    ROCC_INSTRUCTION_S(0, &a, 0x41);
    return 0;
}
