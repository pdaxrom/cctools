build_mc() {
    PKG=mc
    PKG_VERSION=4.6.2
    PKG_SUBVERSION="-2"
    PKG_URL="http://cctools.info/src/${PKG}/${PKG}-${PKG_VERSION}.tar.gz"
    PKG_DESC="Midnight Commander - a powerful file manager"
    O_FILE=$SRC_PREFIX/${PKG}/${PKG}-${PKG_VERSION}.tar.gz
    S_DIR=$src_dir/${PKG}-${PKG_VERSION}
    B_DIR=$build_dir/${PKG}

    c_tag $FUNCNAME && return

    pushd .

    banner "Build $PKG"

    download $PKG_URL $O_FILE

    unpack $src_dir $O_FILE

    patchsrc $S_DIR $PKG $PKG_VERSION

    mkdir -p $B_DIR
    cd $B_DIR

    # Configure here

    export PKG_CONFIG_PATH=${TMPINST_DIR}/lib/pkgconfig

    ${S_DIR}/configure	\
	--host=${TARGET_ARCH} \
	--prefix=$TARGET_INST_DIR \
	--disable-nls \
	--with-glib-prefix=$TMPINST_DIR \
	--with-libiconv-prefix=$TMPINST_DIR \
	CFLAGS="-I${TMPINST_DIR}/include" \
	LDFLAGS="-Wl,-rpath-link,${TMPINST_DIR}/lib -L${TMPINST_DIR}/lib" \
	LIBS="-lncurses" \
	|| error "Configure $PKG."

    $MAKE $MAKEARGS || error "make $MAKEARGS"

    gcc ${S_DIR}/src/man2hlp.c -o src/man2hlp.cross -I${TARGET_DIR}-host/include/glib-2.0 -I${TARGET_DIR}-host/lib/glib-2.0/include -L${TARGET_DIR}-host/lib -lglib-2.0 -I.

    $MAKE install prefix=${TMPINST_DIR}/${PKG}/cctools NATIVE_SUFFIX=".cross" || error "package install"

    $TARGET_ARCH-strip ${TMPINST_DIR}/${PKG}/cctools/bin/*
    $TARGET_ARCH-strip ${TMPINST_DIR}/${PKG}/cctools/libexec/mc/*

    replace_string ${TMPINST_DIR}/${PKG}/cctools/share/mc/extfs/ "/bin/sh" "/system/bin/sh"
    replace_string ${TMPINST_DIR}/${PKG}/cctools/share/mc/extfs/ "/usr/bin/perl" "${TARGET_INST_DIR}/bin/perl"
    replace_string ${TMPINST_DIR}/${PKG}/cctools/share/mc/extfs/ "/usr/bin" "${TARGET_INST_DIR}/bin"

    make_packages

    popd
    s_tag $FUNCNAME
}
