build_gcc_cctools() {
    PKG=gcc-cctools
    PKG_VERSION=10.1.0
    PKG_SUBVERSION=
    PKG_URL="http://mirrors.concertpass.com/gcc/releases/gcc-10.1.0/gcc-${PKG_VERSION}.tar.xz"
    PKG_MAINTAINER="sashz <sashz@pdaXrom.org>"
    PKG_HOME="https://gcc.gnu.org/"
    PKG_DESC="The GNU Compiler Collection"
    PKG_DEPS="binutils-cctools"
    O_FILE=$SRC_PREFIX/gcc/gcc-${PKG_VERSION}.tar.xz
    S_DIR=$src_dir/gcc-${PKG_VERSION}
    B_DIR=$build_dir/${PKG}

    c_tag $PKG && return

    pushd .

    banner "Build $PKG"

#    if [ ! -f $O_FILE ]; then
#	download $PKG_URL $O_FILE
#    fi

#    unpack $src_dir $O_FILE

#    cd $S_DIR
#    ./contrib/download_prerequisites || error "Downloading prerequisites"

#    patchsrc $S_DIR $PKG $PKG_VERSION

    if [ "$USE_NATIVE_BUILD" = "yes" ]; then
	fix_bionic_shell $S_DIR
    fi

    mkdir -p $B_DIR
    cd $B_DIR

if true; then

    # Configure here

    local EXTRA_CONF=
    case $TARGET_ARCH in
    aarch64*)
	EXTRA_CONF="--enable-fix-cortex-a53-835769"
	;;
    mips64el*)
	EXTRA_CONF="--with-arch=mips64r6 --disable-fixed-point"
	;;
    x86_64*)
	EXTRA_CONF="--with-arch=x86-64 --with-tune=intel --with-fpmath=sse --enable-multilib --disable-libquadmath-support --disable-libcilkrts"
	;;
    mips*)
	EXTRA_CONF="--with-arch=mips32 --disable-threads --disable-fixed-point"
	;;
    arm*)
	EXTRA_CONF="--with-arch=armv5te --with-float=soft --with-fpu=vfp"
	;;
    *86*)
	EXTRA_CONF="--disable-libquadmath-support --disable-libcilkrts"
	;;
    esac

    mkdir gcc
    echo "ac_cv_c_bigendian=no" > gcc/config.cache
    echo "gcc_cv_c_no_fpie=no" >> gcc/config.cache
    echo "gcc_cv_no_pie=no"    >> gcc/config.cache

    if [ "$BUILD_PIE_COMPILER" = "yes" ]; then
	EXTRA_CONF="$EXTRA_CONF --enable-default-pie"
    fi

    ${S_DIR}/configure	\
	--target=$TARGET_ARCH \
	--host=$TARGET_ARCH \
	--prefix=$TERMUX_TARGET_INST_DIR \
	--libexecdir=${TERMUX_TARGET_INST_DIR}/lib \
	--build=x86_64-linux-gnu \
	--with-gnu-as \
	--with-gnu-ld \
	--enable-languages=c,c++,fortran,objc,obj-c++ \
	--enable-bionic-libs \
	--enable-libatomic-ifuncs=no \
	--enable-cloog-backend=isl \
	--disable-libssp \
	--enable-threads \
	--disable-libmudflap \
	--disable-sjlj-exceptions \
	--disable-shared \
	--disable-tls \
	--disable-libitm \
	--enable-initfini-array \
	--disable-nls \
	--disable-bootstrap \
	--disable-libquadmath \
	--enable-plugins \
	--enable-libgomp \
	--disable-libsanitizer \
	--enable-graphite=yes \
	--with-sysroot=$SYSROOT \
	--with-gcc-major-version-only \
	--program-suffix=-${PKG_VERSION/.*} \
	--enable-objc-gc=auto \
	--enable-eh-frame-hdr-for-static \
	--enable-target-optspace \
	--with-host-libstdcxx='-static-libgcc -Wl,-Bstatic,-lstdc++,-Bdynamic -lm' \
	$EXTRA_CONF \
	|| error "Configure $PKG."

fi

    $MAKE $MAKEARGS || error "make $MAKEARGS"

    $MAKE install DESTDIR=${TMPINST_DIR}/${PKG} || error "package install"

    for f in c++ g++ gcc gcc-ar gcc-nm gcc-ranlib gfortran; do
	rm -f ${TMPINST_DIR}/${PKG}/${TERMUX_TARGET_INST_DIR}/bin/${TARGET_ARCH}-${f}-${PKG_VERSION/.*}
#	ln -sf ${f}-${PKG_VERSION/.*} ${TMPINST_DIR}/${PKG}/${TERMUX_TARGET_INST_DIR}/bin/${TARGET_ARCH}-${f}-${PKG_VERSION/.*}
	ln -sf ${f}-${PKG_VERSION/.*} ${TMPINST_DIR}/${PKG}/${TERMUX_TARGET_INST_DIR}/bin/${TARGET_ARCH}-${f}
	ln -sf ${f}-${PKG_VERSION/.*} ${TMPINST_DIR}/${PKG}/${TERMUX_TARGET_INST_DIR}/bin/${f}
    done

#    ln -sf cpp-${PKG_VERSION/.*} ${TMPINST_DIR}/${PKG}/${TERMUX_TARGET_INST_DIR}/bin/${TARGET_ARCH}-cpp-${PKG_VERSION/.*}
    ln -sf cpp-${PKG_VERSION/.*} ${TMPINST_DIR}/${PKG}/${TERMUX_TARGET_INST_DIR}/bin/${TARGET_ARCH}-cpp
    ln -sf cpp-${PKG_VERSION/.*} ${TMPINST_DIR}/${PKG}/${TERMUX_TARGET_INST_DIR}/bin/cpp

    rm -f ${TMPINST_DIR}/${PKG}/${TERMUX_TARGET_INST_DIR}/bin/c++-${PKG_VERSION/.*}
    ln -sf g++-${PKG_VERSION/.*} ${TMPINST_DIR}/${PKG}/${TERMUX_TARGET_INST_DIR}/bin/c++-${PKG_VERSION/.*}

    ${TARGET_ARCH}-strip ${TMPINST_DIR}/${PKG}/${TERMUX_TARGET_INST_DIR}/bin/* || true

    for f in lto1 install-tools/fixincl cc1obj plugin/gengtype liblto_plugin.so.0.0.0 \
		cc1 cc1objplus f951 collect2 lto-wrapper cc1plus; do
	${TARGET_ARCH}-strip ${TMPINST_DIR}/${PKG}/${TERMUX_TARGET_INST_DIR}/lib/gcc/${TARGET_ARCH}/${PKG_VERSION/.*}/$f || true
    done

    rm -f  ${TMPINST_DIR}/${PKG}/${TERMUX_TARGET_INST_DIR}/share/info/dir

    PKG_SIZE=$(du -k ${TMPINST_DIR}/${PKG} | tail -1 | awk '{ print $1}')

    mkdir ${TMPINST_DIR}/${PKG}/DEBIAN

cat > ${TMPINST_DIR}/${PKG}/DEBIAN/control <<EOF
Package: $PKG
Architecture: $DEB_ARCH
Installed-Size: $PKG_SIZE
Maintainer: $PKG_MAINTAINER
Version: ${PKG_VERSION}${PKG_SUBVERSION}
Homepage: $PKG_HOME
Depends: $PKG_DEPS
Description: $PKG_DESC
EOF

    dpkg -b ${TMPINST_DIR}/${PKG} ${REPO_DIR}/${PKG}_${PKG_VERSION}${PKG_SUBVERSION}_${DEB_ARCH}.deb

    popd
    s_tag $PKG
}
