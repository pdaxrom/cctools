build_cross_binutils_cctools() {
    PKG=cross-binutils-cctools
    PKG_VERSION=2.36.1
    PKG_SUBVERSION=
    PKG_URL="https://mirror.kumi.systems/gnu/binutils/binutils-${PKG_VERSION}.tar.xz"
    PKG_DESC="GNU assembler, linker and binary utilities"
    PKG_MAINTAINER="sashz <sashz@pdaXrom.org>"
    PKG_HOME="https://www.gnu.org/software/binutils/"
    PKG_DEPS="libc++, zlib"
    O_FILE=$SRC_PREFIX/binutils/binutils-${PKG_VERSION}.tar.xz
    S_DIR=$src_dir/binutils-${PKG_VERSION}
    B_DIR=$build_dir/${PKG}

    c_tag $PKG && return

    pushd .

    banner "Build $PKG"

    if [ ! -f $O_FILE ]; then
	download $PKG_URL $O_FILE
    fi

    unpack $src_dir $O_FILE
    patchsrc $S_DIR binutils $PKG_VERSION

    if [ "$USE_NATIVE_BUILD" = "yes" ]; then
	fix_bionic_shell $S_DIR
    fi

    mkdir -p $B_DIR
    cd $B_DIR

    # Configure here

    ${S_DIR}/configure	\
	--target=${TARGET_ARCH} \
	--prefix=${TARGET_DIR}-host \
	--with-sysroot=$SYSROOT \
	--enable-multilib \
	--disable-nls \
	--disable-werror \
	|| error "Configure $PKG."

    $MAKE $MAKEARGS || error "make $MAKEARGS"

    $MAKE install || error "make install"

    popd
    s_tag $PKG
}
