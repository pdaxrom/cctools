build_gcc_cctools() {
    PKG=gcc-cctools
    PKG_VERSION=10.1.0
    PKG_SUBVERSION=
    PKG_URL="http://mirror.koddos.net/gcc/releases/gcc-10.1.0/gcc-${PKG_VERSION}.tar.xz"
    PKG_MAINTAINER="sashz <sashz@pdaXrom.org>"
    PKG_HOME="https://gcc.gnu.org/"
    PKG_DESC="The GNU Compiler Collection"
    PKG_DEPS=""
    O_FILE=$SRC_PREFIX/${PKG}/gcc-${PKG_VERSION}.tar.xz
    S_DIR=$src_dir/gcc-${PKG_VERSION}
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

    ${S_DIR}/configure	\
	--host=$TARGET_ARCH \
	--prefix=$TERMUX_TARGET_INST_DIR \
	--target=$TARGET_ARCH \
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
