build_xorg_server() {
    PKG=xorg-server
    PKG_VERSION=1.12.2
    PKG_SUBVERSION=
    PKG_URL="http://www.x.org/releases/X11R7.7/src/xserver/${PKG}-${PKG_VERSION}.tar.bz2"
    PKG_DESC="Xorg X server"
    PKG_DEPS=""
    O_FILE=$SRC_PREFIX/${PKG}/${PKG}-${PKG_VERSION}.tar.bz2
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

    CFLAGS="-Os -I$TMPINST_DIR/include -D_LINUX_IPC_H -Dipc_perm=debian_ipc_perm" \
    CXXFLAGS="-Os -I$TMPINST_DIR/include -D_LINUX_IPC_H -Dipc_perm=debian_ipc_perm" \
    LDFLAGS="-L$TMPINST_DIR/lib -Wl,-rpath-link,${SYSROOT}/usr/lib" \
    LIBS="-landroid-shmem" \
    ${S_DIR}/configure	\
			--host=${TARGET_ARCH}	\
			--prefix=$TMPINST_DIR	\
			--disable-xorg		\
			--disable-dmx		\
			--disable-xvfb		\
			--disable-xnest		\
			--disable-xquartz	\
			--disable-xwin		\
			--disable-xephyr	\
			--disable-xfbdev	\
			--disable-unit-tests	\
			--disable-dri		\
			--disable-dri2		\
			--disable-glx		\
			--disable-glx-tls	\
			--disable-aiglx		\
			--disable-xf86vidmode	\
			--disable-config-udev	\
			--disable-config-dbus	\
			--disable-config-hal	\
			--enable-xsdl		\
			--enable-xfake		\
			--enable-kdrive		\
			|| error "Configure $PKG."

#			--enable-kdrive-kbd=no	\
#			--enable-kdrive-mouse=no\
#			--enable-tslib=no	\
#			--disable-kdrive-evdev	\

#			--disable-tslib		\
#			--enable-kdrive-evdev	\

#error "asd"

    $MAKE $MAKEARGS || error "make $MAKEARGS"

error "asd"

    $MAKE install || error "make install"

    $MAKE install prefix=${TMPINST_DIR}/${PKG}/cctools || error "package install"

    make_packages

    popd
    s_tag $FUNCNAME
}
