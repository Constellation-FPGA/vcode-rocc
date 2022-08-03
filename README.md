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
