#!/bin/bash

for d in binutils gcc clang cloog gdb gmp isl llvm mpc mpfr ppl; do
    git clone --depth 1 --branch ndk-r12b https://android.googlesource.com/toolchain/$d
done
