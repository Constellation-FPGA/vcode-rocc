# NOTES #
Currently the stack for each HART is 128KiB.
This means for tests like pair-wise vector addition (`+_INT`), which requires 4 vectors (if you are validating the result), you are limited to 4090 elements!
This number leaves enough stack space for the functions to run as well.
