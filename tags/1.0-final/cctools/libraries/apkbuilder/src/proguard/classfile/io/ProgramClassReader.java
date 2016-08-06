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
package proguard.classfile.io;

import proguard.classfile.*;
import proguard.classfile.attribute.*;
import proguard.classfile.attribute.annotation.*;
import proguard.classfile.attribute.annotation.visitor.*;
import proguard.classfile.attribute.preverification.*;
import proguard.classfile.attribute.preverification.visitor.*;
import proguard.classfile.attribute.visitor.*;
import proguard.classfile.constant.*;
import proguard.classfile.constant.visitor.ConstantVisitor;
import proguard.classfile.util.*;
import proguard.classfile.visitor.*;

import java.io.DataInput;

/**
 * This ClassVisitor fills out the ProgramClass objects that it visits with data
 * from the given DataInput object.
 *
 * @author Eric Lafortune
 */
public class ProgramClassReader
extends      SimplifiedVisitor
implements   ClassVisitor,
             MemberVisitor,
             ConstantVisitor,
             AttributeVisitor,
             InnerClassesInfoVisitor,
             ExceptionInfoVisitor,
             StackMapFrameVisitor,
             VerificationTypeVisitor,
             LineNumberInfoVisitor,
             LocalVariableInfoVisitor,
             LocalVariableTypeInfoVisitor,
             AnnotationVisitor,
             ElementValueVisitor
{
    private final RuntimeDataInput dataInput;


    /**
     * Creates a new ProgramClassReader for reading from the given DataInput.
     */
    public ProgramClassReader(DataInput dataInput)
    {
        this.dataInput = new RuntimeDataInput(dataInput);
    }


    // Implementations for ClassVisitor.

    public void visitProgramClass(ProgramClass programClass)
    {
        // Read and check the magic number.
        programClass.u4magic = dataInput.readInt();

        ClassUtil.checkMagicNumber(programClass.u4magic);

        // Read and check the version numbers.
        int u2minorVersion = dataInput.readUnsignedShort();
        int u2majorVersion = dataInput.readUnsignedShort();

        programClass.u4version = ClassUtil.internalClassVersion(u2majorVersion,
                                                                u2minorVersion);

        ClassUtil.checkVersionNumbers(programClass.u4version);

        // Read the constant pool. Note that the first entry is not used.
        programClass.u2constantPoolCount = dataInput.readUnsignedShort();

        programClass.constantPool = new Constant[programClass.u2constantPoolCount];
        for (int index = 1; index < programClass.u2constantPoolCount; index++)
        {
            Constant constant = createConstant();
            constant.accept(programClass, this);
            programClass.constantPool[index] = constant;

            // Long constants and double constants take up two entries in the
            // constant pool.
            int tag = constant.getTag();
            if (tag == ClassConstants.CONSTANT_Long ||
                tag == ClassConstants.CONSTANT_Double)
            {
                programClass.constantPool[++index] = null;
            }
        }

        // Read the general class information.
        programClass.u2accessFlags = dataInput.readUnsignedShort();
        programClass.u2thisClass   = dataInput.readUnsignedShort();
        programClass.u2superClass  = dataInput.readUnsignedShort();

        // Read the interfaces.
        programClass.u2interfacesCount = dataInput.readUnsignedShort();

        programClass.u2interfaces = new int[programClass.u2interfacesCount];
        for (int index = 0; index < programClass.u2interfacesCount; index++)
        {
            programClass.u2interfaces[index] = dataInput.readUnsignedShort();
        }

        // Read the fields.
        programClass.u2fieldsCount = dataInput.readUnsignedShort();

        programClass.fields = new ProgramField[programClass.u2fieldsCount];
        for (int index = 0; index < programClass.u2fieldsCount; index++)
        {
            ProgramField programField = new ProgramField();
            this.visitProgramField(programClass, programField);
            programClass.fields[index] = programField;
        }

        // Read the methods.
        programClass.u2methodsCount = dataInput.readUnsignedShort();

        programClass.methods = new ProgramMethod[programClass.u2methodsCount];
        for (int index = 0; index < programClass.u2methodsCount; index++)
        {
            ProgramMethod programMethod = new ProgramMethod();
            this.visitProgramMethod(programClass, programMethod);
            programClass.methods[index] = programMethod;
        }

        // Read the class attributes.
        programClass.u2attributesCount = dataInput.readUnsignedShort();

        programClass.attributes = new Attribute[programClass.u2attributesCount];
        for (int index = 0; index < programClass.u2attributesCount; index++)
        {
            Attribute attribute = createAttribute(programClass);
            attribute.accept(programClass, this);
            programClass.attributes[index] = attribute;
        }
    }


    public void visitLibraryClass(LibraryClass libraryClass)
    {
    }


    // Implementations for MemberVisitor.

    public void visitProgramField(ProgramClass programClass, ProgramField programField)
    {
        // Read the general field information.
        programField.u2accessFlags     = dataInput.readUnsignedShort();
        programField.u2nameIndex       = dataInput.readUnsignedShort();
        programField.u2descriptorIndex = dataInput.readUnsignedShort();

        // Read the field attributes.
        programField.u2attributesCount = dataInput.readUnsignedShort();

        programField.attributes = new Attribute[programField.u2attributesCount];
        for (int index = 0; index < programField.u2attributesCount; index++)
        {
            Attribute attribute = createAttribute(programClass);
            attribute.accept(programClass, programField, this);
            programField.attributes[index] = attribute;
        }
    }


    public void visitProgramMethod(ProgramClass programClass, ProgramMethod programMethod)
    {
        // Read the general method information.
        programMethod.u2accessFlags     = dataInput.readUnsignedShort();
        programMethod.u2nameIndex       = dataInput.readUnsignedShort();
        programMethod.u2descriptorIndex = dataInput.readUnsignedShort();

        // Read the method attributes.
        programMethod.u2attributesCount = dataInput.readUnsignedShort();

        programMethod.attributes = new Attribute[programMethod.u2attributesCount];
        for (int index = 0; index < programMethod.u2attributesCount; index++)
        {
            Attribute attribute = createAttribute(programClass);
            attribute.accept(programClass, programMethod, this);
            programMethod.attributes[index] = attribute;
        }
    }


    public void visitLibraryMember(LibraryClass libraryClass, LibraryMember libraryMember)
    {
    }


    // Implementations for ConstantVisitor.

    public void visitIntegerConstant(Clazz clazz, IntegerConstant integerConstant)
    {
        integerConstant.u4value = dataInput.readInt();
    }


    public void visitLongConstant(Clazz clazz, LongConstant longConstant)
    {
        longConstant.u8value = dataInput.readLong();
    }


    public void visitFloatConstant(Clazz clazz, FloatConstant floatConstant)
    {
        floatConstant.f4value = dataInput.readFloat();
    }


    public void visitDoubleConstant(Clazz clazz, DoubleConstant doubleConstant)
    {
        doubleConstant.f8value = dataInput.readDouble();
    }


    public void visitStringConstant(Clazz clazz, StringConstant stringConstant)
    {
        stringConstant.u2stringIndex = dataInput.readUnsignedShort();
    }


    public void visitUtf8Constant(Clazz clazz, Utf8Constant utf8Constant)
    {
        int u2length = dataInput.readUnsignedShort();

        // Read the UTF-8 bytes.
        byte[] bytes = new byte[u2length];
        dataInput.readFully(bytes);
        utf8Constant.setBytes(bytes);
    }


    public void visitAnyRefConstant(Clazz clazz, RefConstant refConstant)
    {
        refConstant.u2classIndex       = dataInput.readUnsignedShort();
        refConstant.u2nameAndTypeIndex = dataInput.readUnsignedShort();
    }


    public void visitClassConstant(Clazz clazz, ClassConstant classConstant)
    {
        classConstant.u2nameIndex = dataInput.readUnsignedShort();
    }


    public void visitNameAndTypeConstant(Clazz clazz, NameAndTypeConstant nameAndTypeConstant)
    {
        nameAndTypeConstant.u2nameIndex       = dataInput.readUnsignedShort();
        nameAndTypeConstant.u2descriptorIndex = dataInput.readUnsignedShort();
    }


    // Implementations for AttributeVisitor.

    public void visitUnknownAttribute(Clazz clazz, UnknownAttribute unknownAttribute)
    {
        // Read the unknown information.
        byte[] info = new byte[unknownAttribute.u4attributeLength];
        dataInput.readFully(info);
        unknownAttribute.info = info;
    }


    public void visitSourceFileAttribute(Clazz clazz, SourceFileAttribute sourceFileAttribute)
    {
        sourceFileAttribute.u2sourceFileIndex = dataInput.readUnsignedShort();
    }


    public void visitSourceDirAttribute(Clazz clazz, SourceDirAttribute sourceDirAttribute)
    {
        sourceDirAttribute.u2sourceDirIndex = dataInput.readUnsignedShort();
    }


    public void visitInnerClassesAttribute(Clazz clazz, InnerClassesAttribute innerClassesAttribute)
    {
        // Read the inner classes.
        innerClassesAttribute.u2classesCount = dataInput.readUnsignedShort();

        innerClassesAttribute.classes = new InnerClassesInfo[innerClassesAttribute.u2classesCount];
        for (int index = 0; index < innerClassesAttribute.u2classesCount; index++)
        {
            InnerClassesInfo innerClassesInfo = new InnerClassesInfo();
            this.visitInnerClassesInfo(clazz, innerClassesInfo);
            innerClassesAttribute.classes[index] = innerClassesInfo;
        }
    }


    public void visitEnclosingMethodAttribute(Clazz clazz, EnclosingMethodAttribute enclosingMethodAttribute)
    {
        enclosingMethodAttribute.u2classIndex       = dataInput.readUnsignedShort();
        enclosingMethodAttribute.u2nameAndTypeIndex = dataInput.readUnsignedShort();
    }


    public void visitDeprecatedAttribute(Clazz clazz, DeprecatedAttribute deprecatedAttribute)
    {
        // This attribute does not contain any additional information.
    }


    public void visitSyntheticAttribute(Clazz clazz, SyntheticAttribute syntheticAttribute)
    {
        // This attribute does not contain any additional information.
    }


    public void visitSignatureAttribute(Clazz clazz, SignatureAttribute signatureAttribute)
    {
        signatureAttribute.u2signatureIndex = dataInput.readUnsignedShort();
    }


    public void visitConstantValueAttribute(Clazz clazz, Field field, ConstantValueAttribute constantValueAttribute)
    {
        constantValueAttribute.u2constantValueIndex = dataInput.readUnsignedShort();
    }


    public void visitExceptionsAttribute(Clazz clazz, Method method, ExceptionsAttribute exceptionsAttribute)
    {
        // Read the exceptions.
        exceptionsAttribute.u2exceptionIndexTableLength = dataInput.readUnsignedShort();

        exceptionsAttribute.u2exceptionIndexTable = new int[exceptionsAttribute.u2exceptionIndexTableLength];
        for (int index = 0; index < exceptionsAttribute.u2exceptionIndexTableLength; index++)
        {
            exceptionsAttribute.u2exceptionIndexTable[index] = dataInput.readUnsignedShort();
        }
    }


    public void visitCodeAttribute(Clazz clazz, Method method, CodeAttribute codeAttribute)
    {
        // Read the stack size and local variable frame size.
        codeAttribute.u2maxStack   = dataInput.readUnsignedShort();
        codeAttribute.u2maxLocals  = dataInput.readUnsignedShort();

        // Read the byte code.
        codeAttribute.u4codeLength = dataInput.readInt();

        byte[] code = new byte[codeAttribute.u4codeLength];
        dataInput.readFully(code);
        codeAttribute.code = code;

        // Read the exceptions.
        codeAttribute.u2exceptionTableLength = dataInput.readUnsignedShort();

        codeAttribute.exceptionTable = new ExceptionInfo[codeAttribute.u2exceptionTableLength];
        for (int index = 0; index < codeAttribute.u2exceptionTableLength; index++)
        {
            ExceptionInfo exceptionInfo = new ExceptionInfo();
            this.visitExceptionInfo(clazz, method, codeAttribute, exceptionInfo);
            codeAttribute.exceptionTable[index] = exceptionInfo;
        }

        // Read the code attributes.
        codeAttribute.u2attributesCount = dataInput.readUnsignedShort();

        codeAttribute.attributes = new Attribute[codeAttribute.u2attributesCount];
        for (int index = 0; index < codeAttribute.u2attributesCount; index++)
        {
            Attribute attribute = createAttribute(clazz);
            attribute.accept(clazz, method, codeAttribute, this);
            codeAttribute.attributes[index] = attribute;
        }
    }


    public void visitStackMapAttribute(Clazz clazz, Method method, CodeAttribute codeAttribute, StackMapAttribute stackMapAttribute)
    {
        // Read the stack map frames (only full frames, without tag).
        stackMapAttribute.u2stackMapFramesCount = dataInput.readUnsignedShort();

        stackMapAttribute.stackMapFrames = new FullFrame[stackMapAttribute.u2stackMapFramesCount];
        for (int index = 0; index < stackMapAttribute.u2stackMapFramesCount; index++)
        {
            FullFrame stackMapFrame = new FullFrame();
            this.visitFullFrame(clazz, method, codeAttribute, index, stackMapFrame);
            stackMapAttribute.stackMapFrames[index] = stackMapFrame;
        }
    }


    public void visitStackMapTableAttribute(Clazz clazz, Method method, CodeAttribute codeAttribute, StackMapTableAttribute stackMapTableAttribute)
    {
        // Read the stack map frames.
        stackMapTableAttribute.u2stackMapFramesCount = dataInput.readUnsignedShort();

        stackMapTableAttribute.stackMapFrames = new StackMapFrame[stackMapTableAttribute.u2stackMapFramesCount];
        for (int index = 0; index < stackMapTableAttribute.u2stackMapFramesCount; index++)
        {
            StackMapFrame stackMapFrame = createStackMapFrame();
            stackMapFrame.accept(clazz, method, codeAttribute, 0, this);
            stackMapTableAttribute.stackMapFrames[index] = stackMapFrame;
        }
    }


    public void visitLineNumberTableAttribute(Clazz clazz, Method method, CodeAttribute codeAttribute, LineNumberTableAttribute lineNumberTableAttribute)
    {
        // Read the line numbers.
        lineNumberTableAttribute.u2lineNumberTableLength = dataInput.readUnsignedShort();

        lineNumberTableAttribute.lineNumberTable = new LineNumberInfo[lineNumberTableAttribute.u2lineNumberTableLength];
        for (int index = 0; index < lineNumberTableAttribute.u2lineNumberTableLength; index++)
        {
            LineNumberInfo lineNumberInfo = new LineNumberInfo();
            this.visitLineNumberInfo(clazz, method, codeAttribute, lineNumberInfo);
            lineNumberTableAttribute.lineNumberTable[index] = lineNumberInfo;
        }
    }


    public void visitLocalVariableTableAttribute(Clazz clazz, Method method, CodeAttribute codeAttribute, LocalVariableTableAttribute localVariableTableAttribute)
    {
        // Read the local variables.
        localVariableTableAttribute.u2localVariableTableLength = dataInput.readUnsignedShort();

        localVariableTableAttribute.localVariableTable = new LocalVariableInfo[localVariableTableAttribute.u2localVariableTableLength];
        for (int index = 0; index < localVariableTableAttribute.u2localVariableTableLength; index++)
        {
            LocalVariableInfo localVariableInfo = new LocalVariableInfo();
            this.visitLocalVariableInfo(clazz, method, codeAttribute, localVariableInfo);
            localVariableTableAttribute.localVariableTable[index] = localVariableInfo;
        }
    }


    public void visitLocalVariableTypeTableAttribute(Clazz clazz, Method method, CodeAttribute codeAttribute, LocalVariableTypeTableAttribute localVariableTypeTableAttribute)
    {
        // Read the local variable types.
        localVariableTypeTableAttribute.u2localVariableTypeTableLength = dataInput.readUnsignedShort();

        localVariableTypeTableAttribute.localVariableTypeTable = new LocalVariableTypeInfo[localVariableTypeTableAttribute.u2localVariableTypeTableLength];
        for (int index = 0; index < localVariableTypeTableAttribute.u2localVariableTypeTableLength; index++)
        {
            LocalVariableTypeInfo localVariableTypeInfo = new LocalVariableTypeInfo();
            this.visitLocalVariableTypeInfo(clazz, method, codeAttribute, localVariableTypeInfo);
            localVariableTypeTableAttribute.localVariableTypeTable[index] = localVariableTypeInfo;
        }
    }


    public void visitAnyAnnotationsAttribute(Clazz clazz, AnnotationsAttribute annotationsAttribute)
    {
        // Read the annotations.
        annotationsAttribute.u2annotationsCount = dataInput.readUnsignedShort();

        annotationsAttribute.annotations = new Annotation[annotationsAttribute.u2annotationsCount];
        for (int index = 0; index < annotationsAttribute.u2annotationsCount; index++)
        {
            Annotation annotation = new Annotation();
            this.visitAnnotation(clazz, annotation);
            annotationsAttribute.annotations[index] = annotation;
        }
    }


    public void visitAnyParameterAnnotationsAttribute(Clazz clazz, Method method, ParameterAnnotationsAttribute parameterAnnotationsAttribute)
    {
        // Read the parameter annotations.
        parameterAnnotationsAttribute.u2parametersCount           = dataInput.readUnsignedByte();

        // The java compilers of JDK 1.5, JDK 1.6, and Eclipse all count the
        // number of parameters of constructors of non-static inner classes
        // incorrectly. Fix it right here.
        int parameterStart = 0;
        if (method.getName(clazz).equals(ClassConstants.INTERNAL_METHOD_NAME_INIT))
        {
            int realParametersCount = ClassUtil.internalMethodParameterCount(method.getDescriptor(clazz));
            parameterStart = realParametersCount - parameterAnnotationsAttribute.u2parametersCount;
            parameterAnnotationsAttribute.u2parametersCount = realParametersCount;
        }

        parameterAnnotationsAttribute.u2parameterAnnotationsCount = new int[parameterAnnotationsAttribute.u2parametersCount];
        parameterAnnotationsAttribute.parameterAnnotations        = new Annotation[parameterAnnotationsAttribute.u2parametersCount][];

        for (int parameterIndex = parameterStart; parameterIndex < parameterAnnotationsAttribute.u2parametersCount; parameterIndex++)
        {
            // Read the parameter annotations of the given parameter.
            int u2annotationsCount = dataInput.readUnsignedShort();

            Annotation[] annotations = new Annotation[u2annotationsCount];

            for (int index = 0; index < u2annotationsCount; index++)
            {
                Annotation annotation = new Annotation();
                this.visitAnnotation(clazz, annotation);
                annotations[index] = annotation;
            }

            parameterAnnotationsAttribute.u2parameterAnnotationsCount[parameterIndex] = u2annotationsCount;
            parameterAnnotationsAttribute.parameterAnnotations[parameterIndex]        = annotations;
        }
    }


    public void visitAnnotationDefaultAttribute(Clazz clazz, Method method, AnnotationDefaultAttribute annotationDefaultAttribute)
    {
        // Read the default element value.
        ElementValue elementValue = createElementValue();
        elementValue.accept(clazz, null, this);
        annotationDefaultAttribute.defaultValue = elementValue;
    }


    // Implementations for InnerClassesInfoVisitor.

    public void visitInnerClassesInfo(Clazz clazz, InnerClassesInfo innerClassesInfo)
    {
        innerClassesInfo.u2innerClassIndex       = dataInput.readUnsignedShort();
        innerClassesInfo.u2outerClassIndex       = dataInput.readUnsignedShort();
        innerClassesInfo.u2innerNameIndex        = dataInput.readUnsignedShort();
        innerClassesInfo.u2innerClassAccessFlags = dataInput.readUnsignedShort();
    }


    // Implementations for ExceptionInfoVisitor.

    public void visitExceptionInfo(Clazz clazz, Method method, CodeAttribute codeAttribute, ExceptionInfo exceptionInfo)
    {
        exceptionInfo.u2startPC   = dataInput.readUnsignedShort();
        exceptionInfo.u2endPC     = dataInput.readUnsignedShort();
        exceptionInfo.u2handlerPC = dataInput.readUnsignedShort();
        exceptionInfo.u2catchType = dataInput.readUnsignedShort();
    }


    // Implementations for StackMapFrameVisitor.

    public void visitSameZeroFrame(Clazz clazz, Method method, CodeAttribute codeAttribute, int offset, SameZeroFrame sameZeroFrame)
    {
        if (sameZeroFrame.getTag() == StackMapFrame.SAME_ZERO_FRAME_EXTENDED)
        {
            sameZeroFrame.u2offsetDelta = dataInput.readUnsignedShort();
        }
    }


    public void visitSameOneFrame(Clazz clazz, Method method, CodeAttribute codeAttribute, int offset, SameOneFrame sameOneFrame)
    {
        if (sameOneFrame.getTag() == StackMapFrame.SAME_ONE_FRAME_EXTENDED)
        {
            sameOneFrame.u2offsetDelta = dataInput.readUnsignedShort();
        }

        // Read the verification type of the stack entry.
        VerificationType verificationType = createVerificationType();
        verificationType.accept(clazz, method, codeAttribute, offset, this);
        sameOneFrame.stackItem = verificationType;
    }


    public void visitLessZeroFrame(Clazz clazz, Method method, CodeAttribute codeAttribute, int offset, LessZeroFrame lessZeroFrame)
    {
        lessZeroFrame.u2offsetDelta = dataInput.readUnsignedShort();
    }


    public void visitMoreZeroFrame(Clazz clazz, Method method, CodeAttribute codeAttribute, int offset, MoreZeroFrame moreZeroFrame)
    {
        moreZeroFrame.u2offsetDelta = dataInput.readUnsignedShort();

        // Read the verification types of the additional local variables.
        moreZeroFrame.additionalVariables = new VerificationType[moreZeroFrame.additionalVariablesCount];
        for (int index = 0; index < moreZeroFrame.additionalVariablesCount; index++)
        {
            VerificationType verificationType = createVerificationType();
            verificationType.accept(clazz, method, codeAttribute, offset, this);
            moreZeroFrame.additionalVariables[index] = verificationType;
        }
    }


    public void visitFullFrame(Clazz clazz, Method method, CodeAttribute codeAttribute, int offset, FullFrame fullFrame)
    {
        fullFrame.u2offsetDelta = dataInput.readUnsignedShort();

        // Read the verification types of the local variables.
        fullFrame.variablesCount = dataInput.readUnsignedShort();
        fullFrame.variables = new VerificationType[fullFrame.variablesCount];
        for (int index = 0; index < fullFrame.variablesCount; index++)
        {
            VerificationType verificationType = createVerificationType();
            verificationType.variablesAccept(clazz, method, codeAttribute, offset, index, this);
            fullFrame.variables[index] = verificationType;
        }

        // Read the verification types of the stack entries.
        fullFrame.stackCount = dataInput.readUnsignedShort();
        fullFrame.stack = new VerificationType[fullFrame.stackCount];
        for (int index = 0; index < fullFrame.stackCount; index++)
        {
            VerificationType verificationType = createVerificationType();
            verificationType.stackAccept(clazz, method, codeAttribute, offset, index, this);
            fullFrame.stack[index] = verificationType;
        }
    }


    // Implementations for VerificationTypeVisitor.

    public void visitAnyVerificationType(Clazz clazz, Method method, CodeAttribute codeAttribute, int offset, VerificationType verificationType)
    {
        // Most verification types don't contain any additional information.
    }


    public void visitObjectType(Clazz clazz, Method method, CodeAttribute codeAttribute, int offset, ObjectType objectType)
    {
        objectType.u2classIndex = dataInput.readUnsignedShort();
    }


    public void visitUninitializedType(Clazz clazz, Method method, CodeAttribute codeAttribute, int offset, UninitializedType uninitializedType)
    {
        uninitializedType.u2newInstructionOffset = dataInput.readUnsignedShort();
    }


    // Implementations for LineNumberInfoVisitor.

    public void visitLineNumberInfo(Clazz clazz, Method method, CodeAttribute codeAttribute, LineNumberInfo lineNumberInfo)
    {
        lineNumberInfo.u2startPC    = dataInput.readUnsignedShort();
        lineNumberInfo.u2lineNumber = dataInput.readUnsignedShort();
    }


    // Implementations for LocalVariableInfoVisitor.

    public void visitLocalVariableInfo(Clazz clazz, Method method, CodeAttribute codeAttribute, LocalVariableInfo localVariableInfo)
    {
        localVariableInfo.u2startPC         = dataInput.readUnsignedShort();
        localVariableInfo.u2length          = dataInput.readUnsignedShort();
        localVariableInfo.u2nameIndex       = dataInput.readUnsignedShort();
        localVariableInfo.u2descriptorIndex = dataInput.readUnsignedShort();
        localVariableInfo.u2index           = dataInput.readUnsignedShort();
    }


    // Implementations for LocalVariableTypeInfoVisitor.

    public void visitLocalVariableTypeInfo(Clazz clazz, Method method, CodeAttribute codeAttribute, LocalVariableTypeInfo localVariableTypeInfo)
    {
        localVariableTypeInfo.u2startPC        = dataInput.readUnsignedShort();
        localVariableTypeInfo.u2length         = dataInput.readUnsignedShort();
        localVariableTypeInfo.u2nameIndex      = dataInput.readUnsignedShort();
        localVariableTypeInfo.u2signatureIndex = dataInput.readUnsignedShort();
        localVariableTypeInfo.u2index          = dataInput.readUnsignedShort();
    }


    // Implementations for AnnotationVisitor.

    public void visitAnnotation(Clazz clazz, Annotation annotation)
    {
        // Read the annotation type.
        annotation.u2typeIndex = dataInput.readUnsignedShort();

        // Read the element value pairs.
        annotation.u2elementValuesCount = dataInput.readUnsignedShort();

        annotation.elementValues = new ElementValue[annotation.u2elementValuesCount];
        for (int index = 0; index < annotation.u2elementValuesCount; index++)
        {
            int u2elementNameIndex = dataInput.readUnsignedShort();
            ElementValue elementValue = createElementValue();
            elementValue.u2elementNameIndex = u2elementNameIndex;
            elementValue.accept(clazz, annotation, this);
            annotation.elementValues[index] = elementValue;
        }
    }


    // Implementations for ElementValueVisitor.

    public void visitConstantElementValue(Clazz clazz, Annotation annotation, ConstantElementValue constantElementValue)
    {
        constantElementValue.u2constantValueIndex = dataInput.readUnsignedShort();
    }


    public void visitEnumConstantElementValue(Clazz clazz, Annotation annotation, EnumConstantElementValue enumConstantElementValue)
    {
        enumConstantElementValue.u2typeNameIndex     = dataInput.readUnsignedShort();
        enumConstantElementValue.u2constantNameIndex = dataInput.readUnsignedShort();
    }


    public void visitClassElementValue(Clazz clazz, Annotation annotation, ClassElementValue classElementValue)
    {
        classElementValue.u2classInfoIndex = dataInput.readUnsignedShort();
    }


    public void visitAnnotationElementValue(Clazz clazz, Annotation annotation, AnnotationElementValue annotationElementValue)
    {
        // Read the annotation.
        Annotation annotationValue = new Annotation();
        this.visitAnnotation(clazz, annotationValue);
        annotationElementValue.annotationValue = annotationValue;
    }


    public void visitArrayElementValue(Clazz clazz, Annotation annotation, ArrayElementValue arrayElementValue)
    {
        // Read the element values.
        arrayElementValue.u2elementValuesCount = dataInput.readUnsignedShort();

        arrayElementValue.elementValues = new ElementValue[arrayElementValue.u2elementValuesCount];
        for (int index = 0; index < arrayElementValue.u2elementValuesCount; index++)
        {
            ElementValue elementValue = createElementValue();
            elementValue.accept(clazz, annotation, this);
            arrayElementValue.elementValues[index] = elementValue;
        }
    }


    // Small utility methods.

    private Constant createConstant()
    {
        int u1tag = dataInput.readUnsignedByte();

        switch (u1tag)
        {
            case ClassConstants.CONSTANT_Utf8:               return new Utf8Constant();
            case ClassConstants.CONSTANT_Integer:            return new IntegerConstant();
            case ClassConstants.CONSTANT_Float:              return new FloatConstant();
            case ClassConstants.CONSTANT_Long:               return new LongConstant();
            case ClassConstants.CONSTANT_Double:             return new DoubleConstant();
            case ClassConstants.CONSTANT_String:             return new StringConstant();
            case ClassConstants.CONSTANT_Fieldref:           return new FieldrefConstant();
            case ClassConstants.CONSTANT_Methodref:          return new MethodrefConstant();
            case ClassConstants.CONSTANT_InterfaceMethodref: return new InterfaceMethodrefConstant();
            case ClassConstants.CONSTANT_Class:              return new ClassConstant();
            case ClassConstants.CONSTANT_NameAndType:        return new NameAndTypeConstant();

            default: throw new RuntimeException("Unknown constant type ["+u1tag+"] in constant pool");
        }
    }


    private Attribute createAttribute(Clazz clazz)
    {
        int u2attributeNameIndex = dataInput.readUnsignedShort();
        int u4attributeLength    = dataInput.readInt();
        String attributeName     = clazz.getString(u2attributeNameIndex);
        Attribute attribute =
            attributeName.equals(ClassConstants.ATTR_SourceFile)                           ? (Attribute)new SourceFileAttribute():
            attributeName.equals(ClassConstants.ATTR_SourceDir)                            ? (Attribute)new SourceDirAttribute():
            attributeName.equals(ClassConstants.ATTR_InnerClasses)                         ? (Attribute)new InnerClassesAttribute():
            attributeName.equals(ClassConstants.ATTR_EnclosingMethod)                      ? (Attribute)new EnclosingMethodAttribute():
            attributeName.equals(ClassConstants.ATTR_Deprecated)                           ? (Attribute)new DeprecatedAttribute():
            attributeName.equals(ClassConstants.ATTR_Synthetic)                            ? (Attribute)new SyntheticAttribute():
            attributeName.equals(ClassConstants.ATTR_Signature)                            ? (Attribute)new SignatureAttribute():
            attributeName.equals(ClassConstants.ATTR_ConstantValue)                        ? (Attribute)new ConstantValueAttribute():
            attributeName.equals(ClassConstants.ATTR_Exceptions)                           ? (Attribute)new ExceptionsAttribute():
            attributeName.equals(ClassConstants.ATTR_Code)                                 ? (Attribute)new CodeAttribute():
            attributeName.equals(ClassConstants.ATTR_StackMap)                             ? (Attribute)new StackMapAttribute():
            attributeName.equals(ClassConstants.ATTR_StackMapTable)                        ? (Attribute)new StackMapTableAttribute():
            attributeName.equals(ClassConstants.ATTR_LineNumberTable)                      ? (Attribute)new LineNumberTableAttribute():
            attributeName.equals(ClassConstants.ATTR_LocalVariableTable)                   ? (Attribute)new LocalVariableTableAttribute():
            attributeName.equals(ClassConstants.ATTR_LocalVariableTypeTable)               ? (Attribute)new LocalVariableTypeTableAttribute():
            attributeName.equals(ClassConstants.ATTR_RuntimeVisibleAnnotations)            ? (Attribute)new RuntimeVisibleAnnotationsAttribute():
            attributeName.equals(ClassConstants.ATTR_RuntimeInvisibleAnnotations)          ? (Attribute)new RuntimeInvisibleAnnotationsAttribute():
            attributeName.equals(ClassConstants.ATTR_RuntimeVisibleParameterAnnotations)   ? (Attribute)new RuntimeVisibleParameterAnnotationsAttribute():
            attributeName.equals(ClassConstants.ATTR_RuntimeInvisibleParameterAnnotations) ? (Attribute)new RuntimeInvisibleParameterAnnotationsAttribute():
            attributeName.equals(ClassConstants.ATTR_AnnotationDefault)                    ? (Attribute)new AnnotationDefaultAttribute():
                                                                                             (Attribute)new UnknownAttribute(u4attributeLength);
        attribute.u2attributeNameIndex = u2attributeNameIndex;

        return attribute;
    }


    private StackMapFrame createStackMapFrame()
    {
        int u1tag = dataInput.readUnsignedByte();

        return
            u1tag < StackMapFrame.SAME_ONE_FRAME           ? (StackMapFrame)new SameZeroFrame(u1tag) :
            u1tag < StackMapFrame.SAME_ONE_FRAME_EXTENDED  ? (StackMapFrame)new SameOneFrame(u1tag)  :
            u1tag < StackMapFrame.LESS_ZERO_FRAME          ? (StackMapFrame)new SameOneFrame(u1tag)  :
            u1tag < StackMapFrame.SAME_ZERO_FRAME_EXTENDED ? (StackMapFrame)new LessZeroFrame(u1tag) :
            u1tag < StackMapFrame.MORE_ZERO_FRAME          ? (StackMapFrame)new SameZeroFrame(u1tag) :
            u1tag < StackMapFrame.FULL_FRAME               ? (StackMapFrame)new MoreZeroFrame(u1tag) :
                                                             (StackMapFrame)new FullFrame();
    }


    private VerificationType createVerificationType()
    {
        int u1tag = dataInput.readUnsignedByte();

        switch (u1tag)
        {
            case VerificationType.INTEGER_TYPE:            return new IntegerType();
            case VerificationType.FLOAT_TYPE:              return new FloatType();
            case VerificationType.LONG_TYPE:               return new LongType();
            case VerificationType.DOUBLE_TYPE:             return new DoubleType();
            case VerificationType.TOP_TYPE:                return new TopType();
            case VerificationType.OBJECT_TYPE:             return new ObjectType();
            case VerificationType.NULL_TYPE:               return new NullType();
            case VerificationType.UNINITIALIZED_TYPE:      return new UninitializedType();
            case VerificationType.UNINITIALIZED_THIS_TYPE: return new UninitializedThisType();

            default: throw new RuntimeException("Unknown verification type ["+u1tag+"] in stack map frame");
        }
    }


    private ElementValue createElementValue()
    {
        int u1tag = dataInput.readUnsignedByte();

        switch (u1tag)
        {
            case ClassConstants.INTERNAL_TYPE_BOOLEAN:
            case ClassConstants.INTERNAL_TYPE_BYTE:
            case ClassConstants.INTERNAL_TYPE_CHAR:
            case ClassConstants.INTERNAL_TYPE_SHORT:
            case ClassConstants.INTERNAL_TYPE_INT:
            case ClassConstants.INTERNAL_TYPE_FLOAT:
            case ClassConstants.INTERNAL_TYPE_LONG:
            case ClassConstants.INTERNAL_TYPE_DOUBLE:
            case ClassConstants.ELEMENT_VALUE_STRING_CONSTANT: return new ConstantElementValue(u1tag);

            case ClassConstants.ELEMENT_VALUE_ENUM_CONSTANT:   return new EnumConstantElementValue();
            case ClassConstants.ELEMENT_VALUE_CLASS:           return new ClassElementValue();
            case ClassConstants.ELEMENT_VALUE_ANNOTATION:      return new AnnotationElementValue();
            case ClassConstants.ELEMENT_VALUE_ARRAY:           return new ArrayElementValue();

            default: throw new IllegalArgumentException("Unknown element value tag ["+u1tag+"]");
        }
    }
}
