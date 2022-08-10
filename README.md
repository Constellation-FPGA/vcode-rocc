# vcode-rocc #
Repository that holds the implementation for a VCODE RoCC accelerator.

# Prerequisites #
  1. SBT
  2. Scala
  3. Chipyard
  4. A working RISC-V toolchain installation

See https://github.com/Constellation-FPGA/notes/blob/main/vcode-rocc-chipyard-setup.org for how to set everything up.

# Usage #
This repository can stand alone for testing.
But it should be used integrated with either rocket-chip, or even better, Chipyard.

# Unit Testing #
Many of the modules used have unit tests to verify functionality.
SBT is **required** to run the unit tests.

To test them, you have two options:
```bash
$ sbt test
```
or
```sh
sbt:projectRoot> test
```

# Building Whole-Program Tests #
Whole-program tests are small C programs which test functionality of the built design for correctness.
They must be compiled with a working RISC-V toolchain.

To build the tests:
```bash
$ cd tests
$ make
```
If you do not have `$RISCV` set to the root of your RISC-V toolchain installation in your environment, a warning will be printed.

# Documentation #
Because Chisel is just scala code, we can automatically build documentation from the source code.
To to this, you will need SBT.
```bash
$ sbt doc
# A lot of output....
# Main Scala API documentation to .../vcode-rocc/target/scala-2.12/api
```

Inside of the `target/scala-2.12/api/` directory is an `index.html`, which can be opened in a web browser for easy viewing.
