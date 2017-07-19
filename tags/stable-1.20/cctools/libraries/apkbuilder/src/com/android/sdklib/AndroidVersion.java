/*
 * Copyright (C) 2009 The Android Open Source Project
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

import java.util.Properties;

/**
 * Represents the version of a target or device.
 * <p/>
 * A version is defined by an API level and an optional code name.
 * <ul><li>Release versions of the Android platform are identified by their API level (integer),
 * (technically the code name for release version is "REL" but this class will return
 * <code>null<code> instead.)</li>
 * <li>Preview versions of the platform are identified by a code name. Their API level
 * is usually set to the value of the previous platform.</li></ul>
 * <p/>
 * While this class contains both values, its goal is to abstract them, so that code comparing 2+
 * versions doesn't have to deal with the logic of handle both values.
 * <p/>
 * There are some cases where ones may want to access the values directly. This can be done
 * with {@link #getApiLevel()} and {@link #getCodename()}.
 * <p/>
 * For generic UI display of the API version, {@link #getApiString()} is to be used.
 *
 */
public final class AndroidVersion implements Comparable<AndroidVersion> {

    private static final String PROP_API_LEVEL = "AndroidVersion.ApiLevel";  //$NON-NLS-1$
    private static final String PROP_CODENAME = "AndroidVersion.CodeName";   //$NON-NLS-1$

    private final int mApiLevel;
    private final String mCodename;

    /**
     * Thrown when an {@link AndroidVersion} object could not be created.
     * @see AndroidVersion#AndroidVersion(Properties)
     */
    public final static class AndroidVersionException extends Exception {
        private static final long serialVersionUID = 1L;

        AndroidVersionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Creates an {@link AndroidVersion} with the given api level and codename.
     * Codename should be null for a release version, otherwise it's a preview codename.
     */
    public AndroidVersion(int apiLevel, String codename) {
        mApiLevel = apiLevel;
        mCodename = codename;
    }

    /**
     * Creates an {@link AndroidVersion} from {@link Properties}, with default values if the
     * {@link Properties} object doesn't contain the expected values.
     * <p/>The {@link Properties} is expected to have been filled with
     * {@link #saveProperties(Properties)}.
     */
    public AndroidVersion(Properties properties, int defaultApiLevel, String defaultCodeName) {
        if (properties == null) {
            mApiLevel = defaultApiLevel;
            mCodename = defaultCodeName;
        } else {
            mApiLevel = Integer.parseInt(properties.getProperty(PROP_API_LEVEL,
                    Integer.toString(defaultApiLevel)));
            mCodename = properties.getProperty(PROP_CODENAME, defaultCodeName);
        }
    }

    /**
     * Creates an {@link AndroidVersion} from {@link Properties}. The properties must contain
     * android version information, or an exception will be thrown.
     * @throws AndroidVersionException if no Android version information have been found
     *
     * @see #saveProperties(Properties)
     */
    public AndroidVersion(Properties properties) throws AndroidVersionException {
        Exception error = null;

        String apiLevel = properties.getProperty(PROP_API_LEVEL, null /*defaultValue*/);
        if (apiLevel != null) {
            try {
                mApiLevel = Integer.parseInt(apiLevel);
                mCodename = properties.getProperty(PROP_CODENAME, null /*defaultValue*/);
                return;
            } catch (NumberFormatException e) {
                error = e;
            }
        }

        // reaching here means the Properties object did not contain the apiLevel which is required.
        throw new AndroidVersionException(PROP_API_LEVEL + " not found!", error);
    }

    public void saveProperties(Properties props) {
        props.setProperty(PROP_API_LEVEL, Integer.toString(mApiLevel));
        if (mCodename != null) {
            props.setProperty(PROP_CODENAME, mCodename);
        }
    }

    /**
     * Returns the api level as an integer.
     * <p/>For target that are in preview mode, this can be superseded by
     * {@link #getCodename()}.
     * <p/>To display the API level in the UI, use {@link #getApiString()}, which will use the
     * codename if applicable.
     * @see #getCodename()
     * @see #getApiString()
     */
    public int getApiLevel() {
        return mApiLevel;
    }

    /**
     * Returns the version code name if applicable, null otherwise.
     * <p/>If the codename is non null, then the API level should be ignored, and this should be
     * used as a unique identifier of the target instead.
     */
    public String getCodename() {
        return mCodename;
    }

    /**
     * Returns a string representing the API level and/or the code name.
     */
    public String getApiString() {
        if (mCodename != null) {
            return mCodename;
        }

        return Integer.toString(mApiLevel);
    }

    /**
     * Returns whether or not the version is a preview version.
     */
    public boolean isPreview() {
        return mCodename != null;
    }

    /**
     * Checks whether a device running a version similar to the receiver can run a project compiled
     * for the given <var>version</var>.
     * <p/>
     * Be aware that this is not a perfect test, as other properties could break compatibility
     * despite this method returning true. For a more comprehensive test, see
     * {@link IAndroidTarget#canRunOn(IAndroidTarget)}.
     * <p/>
     * Nevertheless, when testing if an application can run on a device (where there is no
     * access to the list of optional libraries), this method can give a good indication of whether
     * there is a chance the application could run, or if there's a direct incompatibility.
     */
    public boolean canRun(AndroidVersion appVersion) {
        // if the application is compiled for a preview version, the device must be running exactly
        // the same.
        if (appVersion.mCodename != null) {
            return appVersion.mCodename.equals(mCodename);
        }

        // otherwise, we check the api level (note that a device running a preview version
        // will have the api level of the previous platform).
        return mApiLevel >= appVersion.mApiLevel;
    }

    /**
     * Returns <code>true</code> if the AndroidVersion is an API level equals to
     * <var>apiLevel</var>.
     */
    public boolean equals(int apiLevel) {
        return mCodename == null && apiLevel == mApiLevel;
    }

    /**
     * Compares the receiver with either an {@link AndroidVersion} object or a {@link String}
     * object.
     * <p/>If <var>obj</var> is a {@link String}, then the method will first check if it's a string
     * representation of a number, in which case it'll compare it to the api level. Otherwise, it'll
     * compare it against the code name.
     * <p/>For all other type of object give as parameter, this method will return
     * <code>false</code>.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof AndroidVersion) {
            AndroidVersion version = (AndroidVersion)obj;

            if (mCodename == null) {
                return version.mCodename == null &&
                        mApiLevel == version.mApiLevel;
            } else {
                return mCodename.equals(version.mCodename) &&
                        mApiLevel == version.mApiLevel;
            }

        } else if (obj instanceof String) {
            // if we have a code name, this must match.
            if (mCodename != null) {
                return mCodename.equals(obj);
            }

            // else we try to convert to a int and compare to the api level
            try {
                int value = Integer.parseInt((String)obj);
                return value == mApiLevel;
            } catch (NumberFormatException e) {
                // not a number? we'll return false below.
            }
        }

        return false;
    }

    @Override
    public int hashCode() {
        if (mCodename != null) {
            return mCodename.hashCode();
        }

        // there may be some collisions between the hashcode of the codename and the api level
        // but it's acceptable.
        return mApiLevel;
    }

    /**
     * Compares this object with the specified object for order. Returns a
     * negative integer, zero, or a positive integer as this object is less
     * than, equal to, or greater than the specified object.
     *
     * @param o the Object to be compared.
     * @return a negative integer, zero, or a positive integer as this object is
     *         less than, equal to, or greater than the specified object.
     */
    public int compareTo(AndroidVersion o) {
        return compareTo(o.mApiLevel, o.mCodename);
    }

    private int compareTo(int apiLevel, String codename) {
        if (mCodename == null) {
            if (codename == null) {
                return mApiLevel - apiLevel;
            } else {
                if (mApiLevel == apiLevel) {
                    return -1; // same api level but argument is a preview for next version
                }

                return mApiLevel - apiLevel;
            }
        } else {
            // 'this' is a preview
            if (mApiLevel == apiLevel) {
                if (codename == null) {
                    return +1;
                } else {
                    return mCodename.compareTo(codename);    // strange case where the 2 previews
                                                             // have different codename?
                }
            } else {
                return mApiLevel - apiLevel;
            }
        }
    }

    /**
     * Compares this version with the specified API and returns true if this version
     * is greater or equal than the requested API -- that is the current version is a
     * suitable min-api-level for the argument API.
     */
    public boolean isGreaterOrEqualThan(int api) {
        return compareTo(api, null /*codename*/) >= 0;
    }
}
