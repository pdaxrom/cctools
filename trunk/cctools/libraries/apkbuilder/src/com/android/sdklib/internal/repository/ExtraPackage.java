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

package com.android.sdklib.internal.repository;

import com.android.sdklib.SdkConstants;
import com.android.sdklib.SdkManager;
import com.android.sdklib.internal.repository.Archive.Arch;
import com.android.sdklib.internal.repository.Archive.Os;
import com.android.sdklib.repository.SdkRepository;

import org.w3c.dom.Node;

import java.io.File;
import java.util.Map;
import java.util.Properties;

/**
 * Represents a extra XML node in an SDK repository.
 */
public class ExtraPackage extends MinToolsPackage
    implements IMinApiLevelDependency {

    private static final String PROP_PATH          = "Extra.Path";         //$NON-NLS-1$
    private static final String PROP_MIN_API_LEVEL = "Extra.MinApiLevel";  //$NON-NLS-1$

    /**
     * The install folder name. It must be a single-segment path.
     * The paths "add-ons", "platforms", "tools" and "docs" are reserved and cannot be used.
     * This limitation cannot be written in the XML Schema and must be enforced here by using
     * the method {@link #isPathValid()} *before* installing the package.
     */
    private final String mPath;

    /**
     * The minimal API level required by this extra package, if > 0,
     * or {@link #MIN_API_LEVEL_NOT_SPECIFIED} if there is no such requirement.
     */
    private final int mMinApiLevel;

    /**
     * Creates a new tool package from the attributes and elements of the given XML node.
     * <p/>
     * This constructor should throw an exception if the package cannot be created.
     */
    ExtraPackage(RepoSource source, Node packageNode, Map<String,String> licenses) {
        super(source, packageNode, licenses);

        mPath = XmlParserUtils.getXmlString(packageNode, SdkRepository.NODE_PATH);

        mMinApiLevel = XmlParserUtils.getXmlInt(packageNode, SdkRepository.NODE_MIN_API_LEVEL,
                MIN_API_LEVEL_NOT_SPECIFIED);
    }

    /**
     * Manually create a new package with one archive and the given attributes or properties.
     * This is used to create packages from local directories in which case there must be
     * one archive which URL is the actual target location.
     * <p/>
     * By design, this creates a package with one and only one archive.
     */
    ExtraPackage(RepoSource source,
            Properties props,
            String path,
            int revision,
            String license,
            String description,
            String descUrl,
            Os archiveOs,
            Arch archiveArch,
            String archiveOsPath) {
        super(source,
                props,
                revision,
                license,
                description,
                descUrl,
                archiveOs,
                archiveArch,
                archiveOsPath);

        // The path argument comes before whatever could be in the properties
        mPath = path != null ? path : getProperty(props, PROP_PATH, path);

        mMinApiLevel = Integer.parseInt(
            getProperty(props, PROP_MIN_API_LEVEL, Integer.toString(MIN_API_LEVEL_NOT_SPECIFIED)));
    }

    /**
     * Save the properties of the current packages in the given {@link Properties} object.
     * These properties will later be give the constructor that takes a {@link Properties} object.
     */
    @Override
    void saveProperties(Properties props) {
        super.saveProperties(props);

        props.setProperty(PROP_PATH, mPath);

        if (getMinApiLevel() != MIN_API_LEVEL_NOT_SPECIFIED) {
            props.setProperty(PROP_MIN_API_LEVEL, Integer.toString(getMinApiLevel()));
        }
    }

    /**
     * Returns the minimal API level required by this extra package, if > 0,
     * or {@link #MIN_API_LEVEL_NOT_SPECIFIED} if there is no such requirement.
     */
    public int getMinApiLevel() {
        return mMinApiLevel;
    }

    /**
     * Static helper to check if a given path is acceptable for an "extra" package.
     */
    public boolean isPathValid() {
        if (SdkConstants.FD_ADDONS.equals(mPath) ||
                SdkConstants.FD_PLATFORMS.equals(mPath) ||
                SdkConstants.FD_TOOLS.equals(mPath) ||
                SdkConstants.FD_DOCS.equals(mPath)) {
            return false;
        }
        return mPath != null && mPath.indexOf('/') == -1 && mPath.indexOf('\\') == -1;
    }

    /**
     * The install folder name. It must be a single-segment path.
     * The paths "add-ons", "platforms", "tools" and "docs" are reserved and cannot be used.
     * This limitation cannot be written in the XML Schema and must be enforced here by using
     * the method {@link #isPathValid()} *before* installing the package.
     */
    public String getPath() {
        return mPath;
    }

    /** Returns a short description for an {@link IDescription}. */
    @Override
    public String getShortDescription() {
        String name = getPath();
        if (name != null) {
            // Uniformize all spaces in the name and upper case words.

            name = name.replaceAll("[ _\t\f-]+", " ");     //$NON-NLS-1$ //$NON-NLS-2$

            // Look at all lower case characters in range [1..n-1] and replace them by an upper
            // case if they are preceded by a space. Also upper cases the first character of the
            // string.
            boolean changed = false;
            char[] chars = name.toCharArray();
            for (int n = chars.length - 1, i = 0; i < n; i++) {
                if (Character.isLowerCase(chars[i]) && (i == 0 || chars[i - 1] == ' ')) {
                    chars[i] = Character.toUpperCase(chars[i]);
                    changed = true;
                }
            }
            if (changed) {
                name = new String(chars);
            }
        }

        String s = String.format("%1$s package, revision %2$d%3$s",
                name,
                getRevision(),
                isObsolete() ? " (Obsolete)" : "");

        return s;
    }

    /**
     * Returns a long description for an {@link IDescription}.
     *
     * The long description is whatever the XML contains for the &lt;description&gt; field,
     * or the short description if the former is empty.
     */
    @Override
    public String getLongDescription() {
        String s = getDescription();
        if (s == null || s.length() == 0) {
            s = String.format("Extra %1$s package", getPath());
        }

        if (s.indexOf("revision") == -1) {
            s += String.format("\nRevision %1$d%2$s",
                    getRevision(),
                    isObsolete() ? " (Obsolete)" : "");
        }

        if (getMinToolsRevision() != MIN_TOOLS_REV_NOT_SPECIFIED) {
            s += String.format("\nRequires tools revision %1$d", getMinToolsRevision());
        }

        if (getMinApiLevel() != MIN_API_LEVEL_NOT_SPECIFIED) {
            s += String.format("\nRequires SDK Platform Android API %1$s", getMinApiLevel());
        }

        return s;
    }

    /**
     * Computes a potential installation folder if an archive of this package were
     * to be installed right away in the given SDK root.
     * <p/>
     * A "tool" package should always be located in SDK/tools.
     *
     * @param osSdkRoot The OS path of the SDK root folder.
     * @param suggestedDir A suggestion for the installation folder name, based on the root
     *                     folder used in the zip archive.
     * @param sdkManager An existing SDK manager to list current platforms and addons.
     * @return A new {@link File} corresponding to the directory to use to install this package.
     */
    @Override
    public File getInstallFolder(String osSdkRoot, String suggestedDir, SdkManager sdkManager) {
        return new File(osSdkRoot, getPath());
    }

    @Override
    public boolean sameItemAs(Package pkg) {
        // Extra packages are similar if they have the same path.
        return pkg instanceof ExtraPackage && ((ExtraPackage)pkg).mPath.equals(mPath);
    }
}
