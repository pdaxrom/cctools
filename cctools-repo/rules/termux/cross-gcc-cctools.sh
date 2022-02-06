build_cross_gcc_cctools() {
    PKG=cross-gcc-cctools
    PKG_VERSION=10.3.0
    PKG_SUBVERSION=
    PKG_URL="http://mirrors.concertpass.com/gcc/releases/gcc-10.3.0/gcc-${PKG_VERSION}.tar.xz"
    PKG_MAINTAINER="sashz <sashz@pdaXrom.org>"
    PKG_HOME="https://gcc.gnu.org/"
    PKG_DESC="The GNU Compiler Collection"
    PKG_DEPS=""
    O_FILE=$SRC_PREFIX/gcc/gcc-${PKG_VERSION}.tar.xz
    S_DIR=$src_dir/gcc-${PKG_VERSION}
    B_DIR=$build_dir/${PKG}

    c_tag $PKG && return

    pushd .

    banner "Build $PKG"

    if [ ! -f $O_FILE ]; then
	download $PKG_URL $O_FILE
    fi

if true; then
    unpack $src_dir $O_FILE

    cd $S_DIR
    ./contrib/download_prerequisites || error "Downloading prerequisites"

    patchsrc $S_DIR gcc $PKG_VERSION

    if [ "$USE_NATIVE_BUILD" = "yes" ]; then
	fix_bionic_shell $S_DIR
    fi

    mkdir -p $B_DIR
    cd $B_DIR

    # Configure here

    export PATH=${TARGET_DIR}-host/bin:$PATH

    local EXTRA_CONF=
    case $TARGET_ARCH in
    aarch64*)
	EXTRA_CONF="--enable-fix-cortex-a53-835769"
	;;
    mips64el*)
	EXTRA_CONF="--with-arch=mips64r6 --disable-fixed-point"
	;;
    x86_64*)
	EXTRA_CONF="--with-arch=x86-64 --with-tune=intel --with-fpmath=sse --with-multilib-list=m32,m64 --disable-libquadmath-support --disable-libcilkrts"
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
    *)
	;;
    esac

    if [ "$BUILD_PIE_COMPILER" = "yes" ]; then
	EXTRA_CONF="$EXTRA_CONF --enable-default-pie"
    fi

    ${S_DIR}/configure	\
	--target=$TARGET_ARCH \
	--host=x86_64-linux-gnu \
	--prefix=${TARGET_DIR}-host \
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
	--enable-objc-gc=auto \
	--enable-eh-frame-hdr-for-static \
	--enable-target-optspace \
	--with-host-libstdcxx='-static-libgcc -Wl,-Bstatic,-lstdc++,-Bdynamic -lm' \
	--with-gxx-include-dir=${TARGET_DIR}-host/${TARGET_ARCH}/include/c++/${PKG_VERSION} \
	$EXTRA_CONF \
	|| error "Configure $PKG."

else
    export PATH=${TARGET_DIR}-host/bin:$PATH

    cd $B_DIR
fi

    $MAKE $MAKEARGS || error "make $MAKEARGS"

    $MAKE install || error "make install"

    popd
    s_tag $PKG
}
