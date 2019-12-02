build_apr() {
    PKG=apr
    PKG_VERSION=1.5.2
    PKG_SUBVERSION=
    PKG_URL="http://archive.apache.org/dist/apr/${PKG}-${PKG_VERSION}.tar.bz2"
    PKG_DESC="The mission of the Apache Portable Runtime (APR) project is to create and maintain software libraries that provide a predictable and consistent interface to underlying platform-specific implementations."
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

    ac_cv_file__dev_zero=yes \
    ac_cv_func_setpgrp_void=yes \
    apr_cv_process_shared_works=yes \
    apr_cv_mutex_robust_shared=no \
    apr_cv_tcp_nodelay_with_cork=yes \
    ac_cv_sizeof_struct_iovec=8 \
    ac_cv_func_getifaddrs=no \
    ${S_DIR}/configure	\
			--host=${TARGET_ARCH} \
                        --prefix=$TMPINST_DIR \
			--disable-shared \
			--enable-static \
			|| error "Configure $PKG."

    mkdir -p tools

    gcc ${S_DIR}/tools/gen_test_char.c -o tools/gen_test_char || error "make util"

    $MAKE $MAKEARGS || error "make $MAKEARGS"

    $MAKE install || error "make install"

    $MAKE install prefix=${TMPINST_DIR}/${PKG}/cctools || error "package install"

    make_packages

    popd
    s_tag $PKG
}
