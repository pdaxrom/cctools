#!/bin/ash

if [ ! -f /system/bin/sh ]; then

    echo "Only for on-device native execution."

    exit 0
fi

WRKDIR=${HOME}/tmp

mkdir -p ${WRKDIR}/repo/armeabi
mkdir -p ${WRKDIR}/repo/mips
mkdir -p ${WRKDIR}/repo/x86
test -h ${WRKDIR}/repo/armeabi-v7a || ln -sf armeabi ${WRKDIR}/repo/armeabi-v7a
test -h ${WRKDIR}/repo/mips-r2     || ln -sf mips    ${WRKDIR}/repo/mips-r2

if ! cat ${CCTOOLSDIR}/etc/repos.list | grep -q "${WRKDIR}/repo"; then
    echo "Adding local repo to list"
    echo -n -e "\nfile://${WRKDIR}/repo\n" >> ${CCTOOLSDIR}/etc/repos.list
fi

case `uname -m` in
arm*)
    ash ./build-shell-utils.sh ${PWD}/src arm-linux-androideabi ${WRKDIR}/arm-repo  || exit 1
    ;;
mips*)
    ash ./build-shell-utils.sh ${PWD}/src mipsel-linux-android  ${WRKDIR}/mips-repo || exit 1
    ;;
*)
    ash ./build-shell-utils.sh ${PWD}/src i686-linux-android    ${WRKDIR}/i686-repo || exit 1
    ;;
esac

echo "DONE!"
