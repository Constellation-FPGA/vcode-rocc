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

RISCV_GCC_OPTS ?= -static -mcmodel=medany -fno-common -fno-builtin-printf -Wall -fvisibility=hidden -nostdlib -nostartfiles

TEST_SRCS :=
include src/modules.mk
INCLUDE_DIRS := $(abspath ./include)
BUILD_DIR := build
TEST_BIN_DIR := bin

OBJS = $(addprefix $(BUILD_DIR)/,$(TEST_SRCS:%.c=%.o))
TEST_BINS := $(addprefix $(TEST_BIN_DIR)/,$(TEST_SRCS:%.c=%))
SIM_BINS := $(addprefix $(TEST_BIN_DIR)/,$(TEST_SRCS:%.c=%.riscv))

INCLUDE_FLAGS := -I$(INCLUDE_DIRS)

CFLAGS := -g
CFLAGS += $(INCLUDE_FLAGS)

SIM_LDSCRIPT := $(abspath utils/link.ld)
LDFLAGS := -static -nostdlib -nostartfiles -lgcc -T$(SIM_LDSCRIPT)
.PHONY: build

build: $(TEST_BINS)
	@echo "$(TEST_BINS)"

bin/%: src/%.c
	@mkdir -p bin
	$(CC) $(CFLAGS) $< -o $@

clean:
	rm -rf $(TEST_BIN_DIR)
