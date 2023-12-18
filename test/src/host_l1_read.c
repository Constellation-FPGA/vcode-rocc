#include <stdint.h>

#define NUM_ELEMENTS (3 * 4090)

void read_vec(volatile uint64_t *src, uint64_t count) {
  volatile uint64_t *first = src;
  volatile uint64_t *last = src + count;

  __asm__ __volatile__ ("read_start: \n"
                        "ld t0, 0(%0) \n"
                        "addi %0, %0, 8 \n"
                        "blt %0, %1, read_start \n"
                        "read_end: \n"
: "=r" (first), "=r" (last)
  : //"r" (last)
: "t0");
}

void write_vec(volatile uint64_t *dest, uint64_t count) {
  volatile uint64_t *first = dest;
  volatile uint64_t *last = dest + count;

  __asm__ __volatile__ ("write_start: \n"
                        "sd t0, 0(%0) \n"
                        "addi %0, %0, 8 \n"
                        "blt %0, %1, write_start \n"
                        "write_end: \n"
: "=r" (first), "=r" (last)
  : //"r" (last)
: "t0");
}

int main() {
  volatile uint64_t src[NUM_ELEMENTS];
  read_vec(src, NUM_ELEMENTS);
  return 0;
}
