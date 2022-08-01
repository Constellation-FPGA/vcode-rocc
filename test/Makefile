SHELL=/bin/bash

ifndef RISCV
$(error RISCV is unset. You must set RISCV yourself, or through the Chipyard auto-generated env file)
else
$(info Running with RISCV=$(RISCV))
endif

basedir := $(abspath .)

RISCV_PREFIX := riscv64-unknown-elf

CC :=  $(RISCV_PREFIX)-gcc
OBJDUMP := $(RISCV_PREFIX)-objdump


TEST_SRCS := $(wildcard src/*.c)
INCLUDE_DIRS := $(abspath ./include)
TEST_BIN_DIR := bin
TEST_BINS := $(TEST_SRCS:src/%.c=$(TEST_BIN_DIR)/%)

INCLUDE_FLAGS := -I$(INCLUDE_DIRS)

CFLAGS := -g
CFLAGS += $(INCLUDE_FLAGS)

.PHONY: build

build: $(TEST_BINS)
	@echo "$(TEST_BINS)"

bin/%: src/%.c
	@mkdir -p bin
	$(CC) $(CFLAGS) $< -o $@

clean:
	rm -rf $(TEST_BIN_DIR)
