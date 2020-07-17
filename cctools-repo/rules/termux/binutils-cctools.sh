build_binutils_cctools() {
    PKG=binutils-cctools
    PKG_VERSION=2.34
    PKG_SUBVERSION=
    PKG_URL="https://mirror.kumi.systems/gnu/binutils/binutils-${PKG_VERSION}.tar.xz"
    PKG_DESC="GNU assembler, linker and binary utilities"
    PKG_MAINTAINER="sashz <sashz@pdaXrom.org>"
    PKG_HOME="https://www.gnu.org/software/binutils/"
    PKG_DEPS=""
    O_FILE=$SRC_PREFIX/binutils/binutils-${PKG_VERSION}.tar.xz
    S_DIR=$src_dir/binutils-${PKG_VERSION}
    B_DIR=$build_dir/${PKG}

    c_tag $PKG && return

    pushd .

    banner "Build $PKG"

#    if [ ! -f $O_FILE ]; then
#	download $PKG_URL $O_FILE
#    fi

#    unpack $src_dir $O_FILE
#    patchsrc $S_DIR $PKG $PKG_VERSION

    if [ "$USE_NATIVE_BUILD" = "yes" ]; then
	fix_bionic_shell $S_DIR
    fi

    mkdir -p $B_DIR
    cd $B_DIR

if true; then
    # Configure here

    local EXTRA_LDFLAGS="-Wl,-rpath-link,${SYSROOT}/usr/lib"

    case $TARGET_ARCH in
    x86*)
	EXTRA_LDFLAGS="-Wl,-rpath-link,${SYSROOT}/usr/lib64"
	;;
    esac

    mkdir bfd
cat >bfd/config.cache<<EOF
ac_cv_func_fopen64=no
ac_cv_func_fseeko64=no
ac_cv_func_ftello64=no
EOF

    ${S_DIR}/configure	\
	--host=$TARGET_ARCH \
	--prefix=$TERMUX_TARGET_INST_DIR \
	--target=$TARGET_ARCH \
	--enable-targets=arm-linux-androideabi,mipsel-linux-android,i686-linux-android,aarch64-linux-android,mips64el-linux-android,x86_64-linux-android \
	--enable-multilib \
	--disable-nls \
	--disable-werror \
	LDFLAGS="$EXTRA_LDFLAGS" \
	|| error "Configure $PKG."

#	--disable-static \
#	--enable-shared \

fi
    $MAKE $MAKEARGS || error "make $MAKEARGS"

    $MAKE install DESTDIR=${TMPINST_DIR}/${PKG} || error "package install"

    rm -f  ${TMPINST_DIR}/${PKG}/${TERMUX_TARGET_INST_DIR}/bin/ld.bfd
    ln -sf ld ${TMPINST_DIR}/${PKG}/${TERMUX_TARGET_INST_DIR}/bin/ld.bfd

    for f in $(find ${TMPINST_DIR}/${PKG}/${TERMUX_TARGET_INST_DIR}/${TARGET_ARCH}/bin/ -type f -executable); do
	if [ -f ${TMPINST_DIR}/${PKG}/${TERMUX_TARGET_INST_DIR}/bin/$(basename $f) ]; then
	    rm -f $f
	    ln -sf ../../bin/$(basename $f) $f
	fi
    done

    rm -f  ${TMPINST_DIR}/${PKG}/${TERMUX_TARGET_INST_DIR}/share/info/dir

    ${TARGET_ARCH}-strip ${TMPINST_DIR}/${PKG}/${TERMUX_TARGET_INST_DIR}/bin/* || true
    ${TARGET_ARCH}-strip ${TMPINST_DIR}/${PKG}/${TERMUX_TARGET_INST_DIR}/lib/* || true

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

#cat > ${TMPINST_DIR}/${PKG}/DEBIAN/postinst <<EOF
#for f in ${TERMUX_TARGET_INST_DIR}/bin/*; do
#    update-alternatives --install /data/data/com.termux/files/usr/bin/$$f $$f ${TERMUX_TARGET_INST_DIR}/bin/$$f 10
#done
#EOF

#cat > ${TMPINST_DIR}/${PKG}/DEBIAN/prerm <<EOF
#for f in ${TERMUX_TARGET_INST_DIR}/bin/*; do
#    update-alternatives --remove $$f ${TERMUX_TARGET_INST_DIR}/bin/$$f
#done
#EOF

#    chmod 755 ${TMPINST_DIR}/${PKG}/DEBIAN/postinst ${TMPINST_DIR}/${PKG}/DEBIAN/prerm

    dpkg -b ${TMPINST_DIR}/${PKG} ${REPO_DIR}/${PKG}_${PKG_VERSION}${PKG_SUBVERSION}_${DEB_ARCH}.deb

    popd
    s_tag $PKG
}
