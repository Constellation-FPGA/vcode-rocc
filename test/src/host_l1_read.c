#include <stdint.h>

#define NUM_ELEMENTS (3 * 4090)

void read_vec(volatile uint64_t *src, uint64_t count) {
  volatile uint64_t *first = src;
  volatile uint64_t *last = src + count;

  __asm__ __volatile__ ("loop: \n"
                        "ld t0, 0(%0) \n"
                        "addi %0, %0, 8 \n"
                        "blt %0, %1, loop \n"
: "=r" (first), "=r" (last)
  : //"r" (last)
: "t0");
}

int main() {
  volatile uint64_t src[NUM_ELEMENTS];
  read_vec(src, NUM_ELEMENTS);
  return 0;
}
