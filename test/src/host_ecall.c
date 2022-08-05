/* This test is intended to cause a trap to machine mode (M-Mode).
 * The ecall will provide to the higher-privileged mode the trap number provided
 * in register a7.
 * The actual mode we end up executing in depends on medeleg register. */
int main() {
    asm volatile("addi a7, x0, 0\n\t"
                 "ecall");
    return 0;
}
