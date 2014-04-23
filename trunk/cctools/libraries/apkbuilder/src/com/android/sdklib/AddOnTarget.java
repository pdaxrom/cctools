/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.sdklib;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Represents an add-on target in the SDK.
 * An add-on extends a standard {@link PlatformTarget}.
 */
final class AddOnTarget implements IAndroidTarget {
    /**
     * String to compute hash for add-on targets.
     * Format is vendor:name:apiVersion
     * */
    private final static String ADD_ON_FORMAT = "%s:%s:%s"; //$NON-NLS-1$

    private final static class OptionalLibrary implements IOptionalLibrary {
        private final String mJarName;
        private final String mJarPath;
        private final String mName;
        private final String mDescription;

        OptionalLibrary(String jarName, String jarPath, String name, String description) {
            mJarName = jarName;
            mJarPath = jarPath;
            mName = name;
            mDescription = description;
        }

        public String getJarName() {
            return mJarName;
        }

        public String getJarPath() {
            return mJarPath;
        }

        public String getName() {
            return mName;
        }

        public String getDescription() {
            return mDescription;
        }
    }

    private final String mLocation;
    private final PlatformTarget mBasePlatform;
    private final String mName;
    private final String mVendor;
    private final int mRevision;
    private final String mDescription;
    private String[] mSkins;
    private String mDefaultSkin;
    private IOptionalLibrary[] mLibraries;
    private int mVendorId = NO_USB_ID;

    /**
     * Creates a new add-on
     * @param location the OS path location of the add-on
     * @param name the name of the add-on
     * @param vendor the vendor name of the add-on
     * @param revision the revision of the add-on
     * @param description the add-on description
     * @param libMap A map containing the optional libraries. The map key is the fully-qualified
     * library name. The value is a 2 string array with the .jar filename, and the description.
     * @param basePlatform the platform the add-on is extending.
     */
    AddOnTarget(String location, String name, String vendor, int revision, String description,
            Map<String, String[]> libMap, PlatformTarget basePlatform) {
        if (location.endsWith(File.separator) == false) {
            location = location + File.separator;
        }

        mLocation = location;
        mName = name;
        mVendor = vendor;
        mRevision = revision;
        mDescription = description;
        mBasePlatform = basePlatform;

        // handle the optional libraries.
        if (libMap != null) {
            mLibraries = new IOptionalLibrary[libMap.size()];
            int index = 0;
            for (Entry<String, String[]> entry : libMap.entrySet()) {
                String jarFile = entry.getValue()[0];
                String desc = entry.getValue()[1];
                mLibraries[index++] = new OptionalLibrary(jarFile,
                        mLocation + SdkConstants.OS_ADDON_LIBS_FOLDER + jarFile,
                        entry.getKey(), desc);
            }
        }
    }

    public String getLocation() {
        return mLocation;
    }

    public String getName() {
        return mName;
    }

    public String getVendor() {
        return mVendor;
    }

    public String getFullName() {
        return String.format("%1$s (%2$s)", mName, mVendor);
    }

    public String getClasspathName() {
        return String.format("%1$s [%2$s]", mName, mBasePlatform.getName());
    }

    public String getDescription() {
        return mDescription;
    }

    public AndroidVersion getVersion() {
        // this is always defined by the base platform
        return mBasePlatform.getVersion();
    }

    public String getVersionName() {
        return mBasePlatform.getVersionName();
    }

    public int getRevision() {
        return mRevision;
    }

    public boolean isPlatform() {
        return false;
    }

    public IAndroidTarget getParent() {
        return mBasePlatform;
    }

    public String getPath(int pathId) {
        switch (pathId) {
            case IMAGES:
                return mLocation + SdkConstants.OS_IMAGES_FOLDER;
            case SKINS:
                return mLocation + SdkConstants.OS_SKINS_FOLDER;
            case DOCS:
                return mLocation + SdkConstants.FD_DOCS + File.separator
                        + SdkConstants.FD_DOCS_REFERENCE;
            case SAMPLES:
                // only return the add-on samples folder if there is actually a sample (or more)
                File sampleLoc = new File(mLocation, SdkConstants.FD_SAMPLES);
                if (sampleLoc.isDirectory()) {
                    File[] files = sampleLoc.listFiles(new FileFilter() {
                        public boolean accept(File pathname) {
                            return pathname.isDirectory();
                        }

                    });
                    if (files != null && files.length > 0) {
                        return sampleLoc.getAbsolutePath();
                    }
                }
                // INTENDED FALL-THROUGH
            default :
                return mBasePlatform.getPath(pathId);
        }
    }

    public String[] getSkins() {
        return mSkins;
    }

    public String getDefaultSkin() {
        return mDefaultSkin;
    }

    public IOptionalLibrary[] getOptionalLibraries() {
        return mLibraries;
    }

    /**
     * Returns the list of libraries of the underlying platform.
     *
     * {@inheritDoc}
     */
    public String[] getPlatformLibraries() {
        return mBasePlatform.getPlatformLibraries();
    }

    public String getProperty(String name) {
        return mBasePlatform.getProperty(name);
    }

    public Integer getProperty(String name, Integer defaultValue) {
        return mBasePlatform.getProperty(name, defaultValue);
    }

    public Boolean getProperty(String name, Boolean defaultValue) {
        return mBasePlatform.getProperty(name, defaultValue);
    }

    public Map<String, String> getProperties() {
        return mBasePlatform.getProperties();
    }

    public int getUsbVendorId() {
        return mVendorId;
    }

    public boolean canRunOn(IAndroidTarget target) {
        // basic test
        if (target == this) {
            return true;
        }

        /*
         * The method javadoc indicates:
         * Returns whether the given target is compatible with the receiver.
         * <p/>A target is considered compatible if applications developed for the receiver can
         * run on the given target.
         */

        // The receiver is an add-on. There are 2 big use cases: The add-on has libraries
        // or the add-on doesn't (in which case we consider it a platform).
        if (mLibraries == null || mLibraries.length == 0) {
            return mBasePlatform.canRunOn(target);
        } else {
            // the only targets that can run the receiver are the same add-on in the same or later
            // versions.
            // first check: vendor/name
            if (mVendor.equals(target.getVendor()) == false ||
                            mName.equals(target.getName()) == false) {
                return false;
            }

            // now check the version. At this point since we checked the add-on part,
            // we can revert to the basic check on version/codename which are done by the
            // base platform already.
            return mBasePlatform.canRunOn(target);
        }

    }

    public String hashString() {
        return String.format(ADD_ON_FORMAT, mVendor, mName,
                mBasePlatform.getVersion().getApiString());
    }

    @Override
    public int hashCode() {
        return hashString().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof AddOnTarget) {
            AddOnTarget addon = (AddOnTarget)obj;

            return mVendor.equals(addon.mVendor) && mName.equals(addon.mName) &&
                mBasePlatform.getVersion().equals(addon.mBasePlatform.getVersion());
        }

        return false;
    }

    /*
     * Order by API level (preview/n count as between n and n+1).
     * At the same API level, order as: Platform first, then add-on ordered by vendor and then name
     * (non-Javadoc)
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    public int compareTo(IAndroidTarget target) {
        // quick check.
        if (this == target) {
            return 0;
        }

        int versionDiff = getVersion().compareTo(target.getVersion());

        // only if the version are the same do we care about platform/add-ons.
        if (versionDiff == 0) {
            // platforms go before add-ons.
            if (target.isPlatform()) {
                return +1;
            } else {
                AddOnTarget targetAddOn = (AddOnTarget)target;

                // both are add-ons of the same version. Compare per vendor then by name
                int vendorDiff = mVendor.compareTo(targetAddOn.mVendor);
                if (vendorDiff == 0) {
                    return mName.compareTo(targetAddOn.mName);
                } else {
                    return vendorDiff;
                }
            }

        }

        return versionDiff;
    }

    // ---- local methods.

    void setSkins(String[] skins, String defaultSkin) {
        mDefaultSkin = defaultSkin;

        // we mix the add-on and base platform skins
        HashSet<String> skinSet = new HashSet<String>();
        skinSet.addAll(Arrays.asList(skins));
        skinSet.addAll(Arrays.asList(mBasePlatform.getSkins()));

        mSkins = skinSet.toArray(new String[skinSet.size()]);
    }

    /**
     * Sets the USB vendor id in the add-on.
     */
    void setUsbVendorId(int vendorId) {
        if (vendorId == 0) {
            throw new IllegalArgumentException( "VendorId must be > 0");
        }

        mVendorId = vendorId;
    }
}
