#!/bin/bash

BUILD_START=$(date +%s)

#WRKDIR=$PWD/tmp
WRKDIR=/mnt/nfs/Work/cctools-pro/tmp

#
# uncomment to build pie compiler
# -------------------------------
export BUILD_PIE_COMPILER="yes"
WRKDIR=${WRKDIR}-pie
# -------------------------------
#

NDKDIR=/opt/android-ndk
#NDKSRC=/home/sash/Work/android/ndk-source
NDKSRC=/mnt/nfs/Work/android/ndk-source

for d in binutils gcc gmp mpc mpfr cloog isl ppl llvm-3.3 llvm-3.4; do
    ln -sf ${NDKSRC}/${d} src/
done

export PATH=/opt/CodeSourcery/bin:$PATH

./build-shell-utils.sh ${PWD}/src arm-linux-androideabi ${WRKDIR}/arm-repo  || exit 1

exit 0

./build-shell-utils.sh ${PWD}/src mipsel-linux-android  ${WRKDIR}/mips-repo || exit 1

./build-shell-utils.sh ${PWD}/src i686-linux-android    ${WRKDIR}/i686-repo || exit 1

test -e ${WRKDIR}/repo/armeabi-v7a || ln -sf armeabi ${WRKDIR}/repo/armeabi-v7a
test -e ${WRKDIR}/repo/mips-r2     || ln -sf mips    ${WRKDIR}/repo/mips-r2

for d in armeabi mips x86; do
    pushd .
    cp -f make_packages.sh ${WRKDIR}/repo/${d}/
    cd ${WRKDIR}/repo/${d}
    ./make_packages.sh
    popd
done

mkdir -p ${WRKDIR}/repo/src

find `find src -type d` -type f -exec cp -f {} ${WRKDIR}/repo/src/ \;

BUILD_END=$(date +%s)

TOTAL_TIME=$(($BUILD_END - $BUILD_START))

echo
echo
echo "Build time: $(date -u -d @$TOTAL_TIME +%T)"
echo
echo "DONE!"
