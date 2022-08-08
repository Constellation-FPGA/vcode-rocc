int main() {
    int a,b,c;
    a = 1;
    b = 2;
    volatile int zero = 0;
    // We intentionally integer divide by zero here to try to raise an exception
    // RISC-V does NOT do that though!
    // See RISC-V Unprivileged spec, M standard extension, Table 6.1
    c = b / zero;
    return 0;
}
