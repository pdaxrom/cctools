build_binutils_cctools() {
    PKG=binutils-cctools
    PKG_VERSION=2.34
    PKG_SUBVERSION=
    PKG_URL="https://mirror.kumi.systems/gnu/binutils/binutils-${PKG_VERSION}.tar.xz"
    PKG_DESC="GNU assembler, linker and binary utilities"
    PKG_MAINTAINER="sashz <sashz@pdaXrom.org>"
    PKG_HOME="https://www.gnu.org/software/binutils/"
    PKG_DEPS="libc++, zlib"
    O_FILE=$SRC_PREFIX/${PKG}/binutils-${PKG_VERSION}.tar.xz
    S_DIR=$src_dir/binutils-${PKG_VERSION}
    B_DIR=$build_dir/${PKG}

    c_tag $PKG && return

    pushd .

    banner "Build $PKG"

    if [ ! -f $O_FILE ]; then
	download $PKG_URL $O_FILE
    fi

    unpack $src_dir $O_FILE
    patchsrc $S_DIR $PKG $PKG_VERSION

    if [ "$USE_NATIVE_BUILD" = "yes" ]; then
	fix_bionic_shell $S_DIR
    fi

    mkdir -p $B_DIR
    cd $B_DIR

    # Configure here

    local EXTRA_LDFLAGS="-Wl,-rpath-link,${SYSROOT}/usr/lib"

    case $TARGET_ARCH in
    x86*)
	EXTRA_LDFLAGS="-Wl,-rpath-link,${SYSROOT}/usr/lib64"
	;;
    esac

    ${S_DIR}/configure	\
	--host=$TARGET_ARCH \
	--prefix=$TERMUX_TARGET_INST_DIR \
	--target=$TARGET_ARCH \
	--with-sysroot=$SYSROOT \
	--enable-targets=arm-linux-androideabi,mipsel-linux-android,i686-linux-android,aarch64-linux-android,mips64el-linux-android,x86_64-linux-android \
	--enable-multilib \
	--disable-nls \
	--disable-static \
	--enable-shared \
	--disable-werror \
	LDFLAGS="$EXTRA_LDFLAGS" \
	|| error "Configure $PKG."

    $MAKE $MAKEARGS || error "make $MAKEARGS"

    $MAKE install DESTDIR=${TMPINST_DIR}/${PKG} || error "package install"

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
