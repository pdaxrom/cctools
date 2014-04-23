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

import org.w3c.dom.Node;

/**
 * Misc utilities to help extracting elements and attributes out of an XML document.
 */
class XmlParserUtils {

    /**
     * Returns the first child element with the given XML local name.
     * If xmlLocalName is null, returns the very first child element.
     */
    public static Node getFirstChild(Node node, String xmlLocalName) {

        String nsUri = node.getNamespaceURI();
        for(Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE &&
                    nsUri.equals(child.getNamespaceURI())) {
                if (xmlLocalName == null || xmlLocalName.equals(child.getLocalName())) {
                    return child;
                }
            }
        }

        return null;
    }

    /**
     * Retrieves the value of that XML element as a string.
     * Returns an empty string whether the element is missing or empty,
     * so you can't tell the difference.
     * <p/>
     * Note: use {@link #getOptionalXmlString(Node, String)} if you need to know when the
     * element is missing versus empty.
     *
     * @param node The XML <em>parent</em> node to parse.
     * @param xmlLocalName The XML local name to find in the parent node.
     * @return The text content of the element. Returns an empty string whether the element
     *         is missing or empty, so you can't tell the difference.
     */
    public static String getXmlString(Node node, String xmlLocalName) {
        Node child = getFirstChild(node, xmlLocalName);

        return child == null ? "" : child.getTextContent();  //$NON-NLS-1$
    }

    /**
     * Retrieves the value of that XML element as a string.
     * Returns null when the element is missing, so you can tell between a missing element
     * and an empty one.
     * <p/>
     * Note: use {@link #getXmlString(Node, String)} if you don't need to know when the
     * element is missing versus empty.
     *
     * @param node The XML <em>parent</em> node to parse.
     * @param xmlLocalName The XML local name to find in the parent node.
     * @return The text content of the element. Returns null when the element is missing.
     *         Returns an empty string whether the element is present but empty.
     */
    public static String getOptionalXmlString(Node node, String xmlLocalName) {
        Node child = getFirstChild(node, xmlLocalName);

        return child == null ? null : child.getTextContent();  //$NON-NLS-1$
    }

    /**
     * Retrieves the value of that XML element as an integer.
     * Returns the default value when the element is missing or is not an integer.
     */
    public static int getXmlInt(Node node, String xmlLocalName, int defaultValue) {
        String s = getXmlString(node, xmlLocalName);
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Retrieves the value of that XML element as a long.
     * Returns the default value when the element is missing or is not an integer.
     */
    public static long getXmlLong(Node node, String xmlLocalName, long defaultValue) {
        String s = getXmlString(node, xmlLocalName);
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Retrieve an attribute which value must match one of the given enums using a
     * case-insensitive name match.
     *
     * Returns defaultValue if the attribute does not exist or its value does not match
     * the given enum values.
     */
    public static Object getEnumAttribute(
            Node archiveNode,
            String attrName,
            Object[] values,
            Object defaultValue) {

        Node attr = archiveNode.getAttributes().getNamedItem(attrName);
        if (attr != null) {
            String found = attr.getNodeValue();
            for (Object value : values) {
                if (value.toString().equalsIgnoreCase(found)) {
                    return value;
                }
            }
        }

        return defaultValue;
    }

}
