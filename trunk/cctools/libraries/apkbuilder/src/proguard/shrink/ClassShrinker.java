/*
 * ProGuard -- shrinking, optimization, obfuscation, and preverification
 *             of Java bytecode.
 *
 * Copyright (c) 2002-2009 Eric Lafortune (eric@graphics.cornell.edu)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package proguard.shrink;

import proguard.classfile.*;
import proguard.classfile.attribute.*;
import proguard.classfile.attribute.annotation.*;
import proguard.classfile.attribute.annotation.visitor.*;
import proguard.classfile.attribute.visitor.AttributeVisitor;
import proguard.classfile.constant.*;
import proguard.classfile.editor.*;
import proguard.classfile.util.*;
import proguard.classfile.visitor.*;

/**
 * This ClassVisitor removes constant pool entries and class members that
 * are not marked as being used.
 *
 * @see UsageMarker
 *
 * @author Eric Lafortune
 */
public class ClassShrinker
extends      SimplifiedVisitor
implements   ClassVisitor,
             MemberVisitor,
             AttributeVisitor,
             AnnotationVisitor,
             ElementValueVisitor
{
    private final UsageMarker usageMarker;

    private int[] constantIndexMap = new int[ClassConstants.TYPICAL_CONSTANT_POOL_SIZE];

    private final ConstantPoolRemapper constantPoolRemapper = new ConstantPoolRemapper();


    /**
     * Creates a new ClassShrinker.
     * @param usageMarker the usage marker that is used to mark the classes
     *                    and class members.
     */
    public ClassShrinker(UsageMarker usageMarker)
    {
        this.usageMarker = usageMarker;
    }


    // Implementations for ClassVisitor.

    public void visitProgramClass(ProgramClass programClass)
    {
        // Shrink the arrays for constant pool, interfaces, fields, methods,
        // and class attributes.
        programClass.u2interfacesCount =
            shrinkConstantIndexArray(programClass.constantPool,
                                     programClass.u2interfaces,
                                     programClass.u2interfacesCount);

        // Shrinking the constant pool also sets up an index map.
        programClass.u2constantPoolCount =
            shrinkConstantPool(programClass.constantPool,
                               programClass.u2constantPoolCount);

        programClass.u2fieldsCount =
            shrinkArray(programClass.fields,
                        programClass.u2fieldsCount);

        programClass.u2methodsCount =
            shrinkArray(programClass.methods,
                        programClass.u2methodsCount);

        programClass.u2attributesCount =
            shrinkArray(programClass.attributes,
                        programClass.u2attributesCount);

        // Compact the remaining fields, methods, and attributes,
        // and remap their references to the constant pool.
        programClass.fieldsAccept(this);
        programClass.methodsAccept(this);
        programClass.attributesAccept(this);

        // Remap all constant pool references.
        constantPoolRemapper.setConstantIndexMap(constantIndexMap);
        constantPoolRemapper.visitProgramClass(programClass);

        // Remove the unused interfaces from the class signature.
        programClass.attributesAccept(new SignatureShrinker());

        // Compact the extra field pointing to the subclasses of this class.
        programClass.subClasses =
            shrinkToNewArray(programClass.subClasses);
    }


    public void visitLibraryClass(LibraryClass libraryClass)
    {
        // Library classes are left unchanged.

        // Compact the extra field pointing to the subclasses of this class.
        libraryClass.subClasses =
            shrinkToNewArray(libraryClass.subClasses);
    }


    // Implementations for MemberVisitor.

    public void visitProgramMember(ProgramClass programClass, ProgramMember programMember)
    {
        // Shrink the attributes array.
        programMember.u2attributesCount =
            shrinkArray(programMember.attributes,
                        programMember.u2attributesCount);

        // Shrink any attributes.
        programMember.attributesAccept(programClass, this);
    }


    // Implementations for AttributeVisitor.

    public void visitAnyAttribute(Clazz clazz, Attribute attribute) {}


    public void visitInnerClassesAttribute(Clazz clazz, InnerClassesAttribute innerClassesAttribute)
    {
        // Shrink the array of InnerClassesInfo objects.
        innerClassesAttribute.u2classesCount =
            shrinkArray(innerClassesAttribute.classes,
                        innerClassesAttribute.u2classesCount);
    }


    public void visitEnclosingMethodAttribute(Clazz clazz, EnclosingMethodAttribute enclosingMethodAttribute)
    {
        // Sometimes, a class is still referenced (apparently as a dummy class),
        // but its enclosing method is not. Then remove the reference to
        // the enclosing method.
        // E.g. the anonymous inner class javax.swing.JList$1 is defined inside
        // a constructor of javax.swing.JList, but it is also referenced as a
        // dummy argument in a constructor of javax.swing.JList$ListSelectionHandler.
        if (enclosingMethodAttribute.referencedMethod != null &&
            !usageMarker.isUsed(enclosingMethodAttribute.referencedMethod))
        {
            enclosingMethodAttribute.u2nameAndTypeIndex = 0;

            enclosingMethodAttribute.referencedMethod = null;
        }
    }


    public void visitCodeAttribute(Clazz clazz, Method method, CodeAttribute codeAttribute)
    {
        // Shrink the attributes array.
        codeAttribute.u2attributesCount =
            shrinkArray(codeAttribute.attributes,
                        codeAttribute.u2attributesCount);
    }


    public void visitAnyAnnotationsAttribute(Clazz clazz, AnnotationsAttribute annotationsAttribute)
    {
        // Shrink the annotations array.
        annotationsAttribute.u2annotationsCount =
            shrinkArray(annotationsAttribute.annotations,
                        annotationsAttribute.u2annotationsCount);

        // Shrink the annotations themselves.
        annotationsAttribute.annotationsAccept(clazz, this);
    }


    public void visitAnyParameterAnnotationsAttribute(Clazz clazz, Method method, ParameterAnnotationsAttribute parameterAnnotationsAttribute)
    {
        // Loop over all parameters.
        for (int parameterIndex = 0; parameterIndex < parameterAnnotationsAttribute.u2parametersCount; parameterIndex++)
        {
            // Shrink the parameter annotations array.
            parameterAnnotationsAttribute.u2parameterAnnotationsCount[parameterIndex] =
                shrinkArray(parameterAnnotationsAttribute.parameterAnnotations[parameterIndex],
                            parameterAnnotationsAttribute.u2parameterAnnotationsCount[parameterIndex]);
        }

        // Shrink the annotations themselves.
        parameterAnnotationsAttribute.annotationsAccept(clazz, method, this);
    }


    // Implementations for AnnotationVisitor.

    public void visitAnnotation(Clazz clazz, Annotation annotation)
    {
        // Shrink the element values array.
        annotation.u2elementValuesCount =
            shrinkArray(annotation.elementValues,
                        annotation.u2elementValuesCount);

        // Shrink the element values themselves.
        annotation.elementValuesAccept(clazz, this);
    }


    /**
     * This AttributeVisitor updates the Utf8 constants of class signatures,
     * removing any unused interfaces.
     */
    private class SignatureShrinker
    extends       SimplifiedVisitor
    implements    AttributeVisitor
    {
        public void visitAnyAttribute(Clazz clazz, Attribute attribute) {}


        public void visitSignatureAttribute(Clazz clazz, SignatureAttribute  signatureAttribute)
        {
            Clazz[] referencedClasses = signatureAttribute.referencedClasses;
            if (referencedClasses != null)
            {
                // Go over the generic definitions, superclass and implemented interfaces.
                String signature = clazz.getString(signatureAttribute.u2signatureIndex);

                InternalTypeEnumeration internalTypeEnumeration =
                    new InternalTypeEnumeration(signature);

                StringBuffer newSignatureBuffer = new StringBuffer();

                int referencedClassIndex    = 0;
                int newReferencedClassIndex = 0;

                while (internalTypeEnumeration.hasMoreTypes())
                {
                    // Consider the classes referenced by this signature.
                    String type       = internalTypeEnumeration.nextType();
                    int    classCount = new DescriptorClassEnumeration(type).classCount();

                    Clazz referencedClass = referencedClasses[referencedClassIndex];
                    if (referencedClass == null ||
                        usageMarker.isUsed(referencedClass))
                    {
                        // Append the superclass or interface.
                        newSignatureBuffer.append(type);

                        // Copy the referenced classes.
                        for (int counter = 0; counter < classCount; counter++)
                        {
                            referencedClasses[newReferencedClassIndex++] =
                                referencedClasses[referencedClassIndex++];
                        }
                    }
                    else
                    {
                        // Skip the referenced classes.
                        referencedClassIndex += classCount;
                    }
                }

                if (newReferencedClassIndex < referencedClassIndex)
                {
                    // Update the signature.
                    ((Utf8Constant)((ProgramClass)clazz).constantPool[signatureAttribute.u2signatureIndex]).setString(newSignatureBuffer.toString());

                    // Clear the unused entries.
                    while (newReferencedClassIndex < referencedClassIndex)
                    {
                        referencedClasses[newReferencedClassIndex++] = null;
                    }
                }
            }
        }
    }


    // Implementations for ElementValueVisitor.

    public void visitAnyElementValue(Clazz clazz, Annotation annotation, ElementValue elementValue) {}


    public void visitAnnotationElementValue(Clazz clazz, Annotation annotation, AnnotationElementValue annotationElementValue)
    {
        // Shrink the contained annotation.
        annotationElementValue.annotationAccept(clazz, this);
    }


    public void visitArrayElementValue(Clazz clazz, Annotation annotation, ArrayElementValue arrayElementValue)
    {
        // Shrink the element values array.
        arrayElementValue.u2elementValuesCount =
            shrinkArray(arrayElementValue.elementValues,
                        arrayElementValue.u2elementValuesCount);

        // Shrink the element values themselves.
        arrayElementValue.elementValuesAccept(clazz, annotation, this);
    }


    // Small utility methods.

    /**
     * Removes all entries that are not marked as being used from the given
     * constant pool.
     * @return the new number of entries.
     */
    private int shrinkConstantPool(Constant[] constantPool, int length)
    {
        if (constantIndexMap.length < length)
        {
            constantIndexMap = new int[length];
        }

        int     counter = 1;
        boolean isUsed  = false;

        // Shift the used constant pool entries together.
        for (int index = 1; index < length; index++)
        {
            constantIndexMap[index] = counter;

            Constant constant = constantPool[index];

            // Don't update the flag if this is the second half of a long entry.
            if (constant != null)
            {
                isUsed = usageMarker.isUsed(constant);
            }

            if (isUsed)
            {
                constantPool[counter++] = constant;
            }
        }

        // Clear the remaining constant pool elements.
        for (int index = counter; index < length; index++)
        {
            constantPool[index] = null;
        }

        return counter;
    }


    /**
     * Removes all indices that point to unused constant pool entries
     * from the given array.
     * @return the new number of indices.
     */
    private int shrinkConstantIndexArray(Constant[] constantPool, int[] array, int length)
    {
        int counter = 0;

        // Shift the used objects together.
        for (int index = 0; index < length; index++)
        {
            if (usageMarker.isUsed(constantPool[array[index]]))
            {
                array[counter++] = array[index];
            }
        }

        // Clear the remaining array elements.
        for (int index = counter; index < length; index++)
        {
            array[index] = 0;
        }

        return counter;
    }


    /**
     * Removes all Clazz objects that are not marked as being used
     * from the given array and returns the remaining objects in a an array
     * of the right size.
     * @return the new array.
     */
    private Clazz[] shrinkToNewArray(Clazz[] array)
    {
        if (array == null)
        {
            return null;
        }

        // Shrink the given array in-place.
        int length = shrinkArray(array, array.length);
        if (length == 0)
        {
            return null;
        }

        // Return immediately if the array is of right size already.
        if (length == array.length)
        {
            return array;
        }

        // Copy the remaining elements into a new array of the right size.
        Clazz[] newArray = new Clazz[length];
        System.arraycopy(array, 0, newArray, 0, length);
        return newArray;
    }


    /**
     * Removes all VisitorAccepter objects that are not marked as being used
     * from the given array.
     * @return the new number of VisitorAccepter objects.
     */
    private int shrinkArray(VisitorAccepter[] array, int length)
    {
        int counter = 0;

        // Shift the used objects together.
        for (int index = 0; index < length; index++)
        {
            if (usageMarker.isUsed(array[index]))
            {
                array[counter++] = array[index];
            }
        }

        // Clear the remaining array elements.
        for (int index = counter; index < length; index++)
        {
            array[index] = null;
        }

        return counter;
    }
}
