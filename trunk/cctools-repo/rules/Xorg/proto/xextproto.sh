build_xextproto() {
    PKG=xextproto
    PKG_VERSION=7.2.1
    PKG_SUBVERSION=
    PKG_URL="http://www.x.org/releases/X11R7.7/src/proto/${PKG}-${PKG_VERSION}.tar.bz2"
    PKG_DESC="X11 various extension wire protocol"
    PKG_DEPS=""
    O_FILE=$SRC_PREFIX/${PKG}/${PKG}-${PKG_VERSION}.tar.bz2
    S_DIR=$src_dir/${PKG}-${PKG_VERSION}
    B_DIR=$build_dir/${PKG}

    c_tag $PKG && return

    pushd .

    banner "Build $PKG"

    download $PKG_URL $O_FILE

    unpack $src_dir $O_FILE

    patchsrc $S_DIR $PKG $PKG_VERSION

    mkdir -p $B_DIR
    cd $B_DIR

    # Configure here

    ${S_DIR}/configure	\
			--host=${TARGET_ARCH} \
                        --prefix=$TMPINST_DIR \
			|| error "Configure $PKG."

    $MAKE $MAKEARGS || error "make $MAKEARGS"

    $MAKE install || error "make install"

    $MAKE install prefix=${TMPINST_DIR}/${PKG}/cctools || error "package install"

    if [ -d ${TMPINST_DIR}/${PKG}/cctools/lib/pkgconfig ]; then
	sed -i "s|^prefix=.*\$|prefix=${TARGET_INST_DIR}|" ${TMPINST_DIR}/${PKG}/cctools/lib/pkgconfig/*.pc
    fi

    $TARGET_ARCH-strip ${TMPINST_DIR}/${PKG}/cctools/bin/*

    local filename="xorg-${PKG}-dev_${PKG_VERSION}${PKG_SUBVERSION}_${PKG_ARCH}.zip"
    build_package_desc ${TMPINST_DIR}/${PKG} $filename xorg-${PKG}-dev ${PKG_VERSION}${PKG_SUBVERSION} $PKG_ARCH "$PKG_DESC" "$PKG_DEPS"
    cd ${TMPINST_DIR}/${PKG}
    rm -f ${REPO_DIR}/$filename; zip -r9y ${REPO_DIR}/$filename *

    popd
    s_tag $PKG
}
