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
package proguard.optimize.peephole;

import proguard.classfile.*;
import proguard.classfile.attribute.*;
import proguard.classfile.attribute.visitor.*;
import proguard.classfile.constant.*;
import proguard.classfile.constant.visitor.ConstantVisitor;
import proguard.classfile.editor.*;
import proguard.classfile.instruction.*;
import proguard.classfile.instruction.visitor.InstructionVisitor;
import proguard.classfile.util.*;
import proguard.classfile.visitor.MemberVisitor;
import proguard.optimize.info.*;

import java.util.Stack;

/**
 * This AttributeVisitor inlines short methods or methods that are only invoked
 * once, in the code attributes that it visits.
 *
 * @author Eric Lafortune
 */
public class MethodInliner
extends      SimplifiedVisitor
implements   AttributeVisitor,
             InstructionVisitor,
             ConstantVisitor,
             MemberVisitor
{
    private static final int MAXIMUM_INLINED_CODE_LENGTH       = Integer.parseInt(System.getProperty("maximum.inlined.code.length",      "8"));
    private static final int MAXIMUM_RESULTING_CODE_LENGTH_JSE = Integer.parseInt(System.getProperty("maximum.resulting.code.length", "8000"));
    private static final int MAXIMUM_RESULTING_CODE_LENGTH_JME = Integer.parseInt(System.getProperty("maximum.resulting.code.length", "2000"));
    private static final int MAXIMUM_CODE_EXPANSION            = 2;
    private static final int MAXIMUM_EXTRA_CODE_LENGTH         = 128;

    //*
    private static final boolean DEBUG = false;
    /*/
    private static       boolean DEBUG = true;
    //*/


    private final boolean            microEdition;
    private final boolean            allowAccessModification;
    private final boolean            inlineSingleInvocations;
    private final InstructionVisitor extraInlinedInvocationVisitor;

    private final CodeAttributeComposer codeAttributeComposer = new CodeAttributeComposer();
    private final AccessMethodMarker    accessMethodMarker    = new AccessMethodMarker();
    private final CatchExceptionMarker  catchExceptionMarker  = new CatchExceptionMarker();
    private final StackSizeComputer     stackSizeComputer     = new StackSizeComputer();

    private ProgramClass       targetClass;
    private ProgramMethod      targetMethod;
    private ConstantAdder      constantAdder;
    private ExceptionInfoAdder exceptionInfoAdder;
    private int                estimatedResultingCodeLength;
    private boolean            inlining;
    private Stack              inliningMethods              = new Stack();
    private boolean            emptyInvokingStack;
    private int                uninitializedObjectCount;
    private int                variableOffset;
    private boolean            inlined;
    private boolean            inlinedAny;


    /**
     * Creates a new MethodInliner.
     * @param microEdition            indicates whether the resulting code is
     *                                targeted at Java Micro Edition.
     * @param allowAccessModification indicates whether the access modifiers of
     *                                classes and class members can be changed
     *                                in order to inline methods.
     * @param inlineSingleInvocations indicates whether the single invocations
     *                                should be inlined, or, alternatively,
     *                                short methods.
     */
    public MethodInliner(boolean microEdition,
                         boolean allowAccessModification,
                         boolean inlineSingleInvocations)
    {
        this(microEdition,
             allowAccessModification,
             inlineSingleInvocations,
             null);
    }


    /**
     * Creates a new MethodInliner.
     * @param microEdition            indicates whether the resulting code is
     *                                targeted at Java Micro Edition.
     * @param allowAccessModification indicates whether the access modifiers of
     *                                classes and class members can be changed
     *                                in order to inline methods.
     * @param inlineSingleInvocations indicates whether the single invocations
     *                                should be inlined, or, alternatively,
     *                                short methods.
     * @param extraInlinedInvocationVisitor an optional extra visitor for all
     *                                      inlined invocation instructions.
     */
    public MethodInliner(boolean            microEdition,
                         boolean            allowAccessModification,
                         boolean            inlineSingleInvocations,
                         InstructionVisitor extraInlinedInvocationVisitor)
    {
        this.microEdition                  = microEdition;
        this.allowAccessModification       = allowAccessModification;
        this.inlineSingleInvocations       = inlineSingleInvocations;
        this.extraInlinedInvocationVisitor = extraInlinedInvocationVisitor;
    }


    // Implementations for AttributeVisitor.

    public void visitAnyAttribute(Clazz clazz, Attribute attribute) {}


    public void visitCodeAttribute(Clazz clazz, Method method, CodeAttribute codeAttribute)
    {
        if (!inlining)
        {
//            codeAttributeComposer.DEBUG = DEBUG =
//                clazz.getName().equals("abc/Def") &&
//                method.getName(clazz).equals("abc");

            targetClass                  = (ProgramClass)clazz;
            targetMethod                 = (ProgramMethod)method;
            constantAdder                = new ConstantAdder(targetClass);
            exceptionInfoAdder           = new ExceptionInfoAdder(targetClass, codeAttributeComposer);
            estimatedResultingCodeLength = codeAttribute.u4codeLength;
            inliningMethods.clear();
            uninitializedObjectCount     = method.getName(clazz).equals(ClassConstants.INTERNAL_METHOD_NAME_INIT) ? 1 : 0;
            inlinedAny                   = false;
            codeAttributeComposer.reset();
            stackSizeComputer.visitCodeAttribute(clazz, method, codeAttribute);

            // Append the body of the code.
            copyCode(clazz, method, codeAttribute);

            targetClass   = null;
            targetMethod  = null;
            constantAdder = null;

            // Update the code attribute if any code has been inlined.
            if (inlinedAny)
            {
                codeAttributeComposer.visitCodeAttribute(clazz, method, codeAttribute);

                // Update the accessing flags.
                codeAttribute.instructionsAccept(clazz, method, accessMethodMarker);

                // Update the exception catching flags.
                catchExceptionMarker.visitCodeAttribute(clazz, method, codeAttribute);
            }
        }

        // Only inline the method if it is invoked once or if it is short.
        else if ((inlineSingleInvocations ?
                      MethodInvocationMarker.getInvocationCount(method) == 1 :
                      codeAttribute.u4codeLength <= MAXIMUM_INLINED_CODE_LENGTH) &&
                 estimatedResultingCodeLength + codeAttribute.u4codeLength <
                 (microEdition ?
                     MAXIMUM_RESULTING_CODE_LENGTH_JME :
                     MAXIMUM_RESULTING_CODE_LENGTH_JSE))
        {
            if (DEBUG)
            {
                System.out.println("MethodInliner: inlining ["+
                                   clazz.getName()+"."+method.getName(clazz)+method.getDescriptor(clazz)+"] in ["+
                                   targetClass.getName()+"."+targetMethod.getName(targetClass)+targetMethod.getDescriptor(targetClass)+"]");
            }

            // Ignore the removal of the original method invocation,
            // the addition of the parameter setup, and
            // the modification of a few inlined instructions.
            estimatedResultingCodeLength += codeAttribute.u4codeLength;

            // Append instructions to store the parameters.
            storeParameters(clazz, method);

            // Inline the body of the code.
            copyCode(clazz, method, codeAttribute);

            inlined    = true;
            inlinedAny = true;
        }
    }


    /**
     * Appends instructions to pop the parameters for the given method, storing
     * them in new local variables.
     */
    private void storeParameters(Clazz clazz, Method method)
    {
        String descriptor = method.getDescriptor(clazz);

        boolean isStatic =
            (method.getAccessFlags() & ClassConstants.INTERNAL_ACC_STATIC) != 0;

        // Count the number of parameters, taking into account their categories.
        int parameterCount  = ClassUtil.internalMethodParameterCount(descriptor);
        int parameterSize   = ClassUtil.internalMethodParameterSize(descriptor);
        int parameterOffset = isStatic ? 0 : 1;

        // Store the parameter types.
        String[] parameterTypes = new String[parameterSize];

        InternalTypeEnumeration internalTypeEnumeration =
            new InternalTypeEnumeration(descriptor);

        for (int parameterIndex = 0; parameterIndex < parameterSize; parameterIndex++)
        {
            String parameterType = internalTypeEnumeration.nextType();
            parameterTypes[parameterIndex] = parameterType;
            if (ClassUtil.internalTypeSize(parameterType) == 2)
            {
                parameterIndex++;
            }
        }

        codeAttributeComposer.beginCodeFragment(parameterSize+1);

        // Go over the parameter types backward, storing the stack entries
        // in their corresponding variables.
        for (int parameterIndex = parameterSize-1; parameterIndex >= 0; parameterIndex--)
        {
            String parameterType = parameterTypes[parameterIndex];
            if (parameterType != null)
            {
                byte opcode;
                switch (parameterType.charAt(0))
                {
                    case ClassConstants.INTERNAL_TYPE_BOOLEAN:
                    case ClassConstants.INTERNAL_TYPE_BYTE:
                    case ClassConstants.INTERNAL_TYPE_CHAR:
                    case ClassConstants.INTERNAL_TYPE_SHORT:
                    case ClassConstants.INTERNAL_TYPE_INT:
                        opcode = InstructionConstants.OP_ISTORE;
                        break;

                    case ClassConstants.INTERNAL_TYPE_LONG:
                        opcode = InstructionConstants.OP_LSTORE;
                        break;

                    case ClassConstants.INTERNAL_TYPE_FLOAT:
                        opcode = InstructionConstants.OP_FSTORE;
                        break;

                    case ClassConstants.INTERNAL_TYPE_DOUBLE:
                        opcode = InstructionConstants.OP_DSTORE;
                        break;

                    default:
                        opcode = InstructionConstants.OP_ASTORE;
                        break;
                }

                codeAttributeComposer.appendInstruction(parameterSize-parameterIndex-1,
                                                        new VariableInstruction(opcode, variableOffset + parameterOffset + parameterIndex).shrink());
            }
        }

        // Put the 'this' reference in variable 0 (plus offset).
        if (!isStatic)
        {
            codeAttributeComposer.appendInstruction(parameterSize,
                                                    new VariableInstruction(InstructionConstants.OP_ASTORE, variableOffset).shrink());
        }

        codeAttributeComposer.endCodeFragment();
    }


    /**
     * Appends the code of the given code attribute.
     */
    private void copyCode(Clazz clazz, Method method, CodeAttribute codeAttribute)
    {
        // The code may expand, due to expanding constant and variable
        // instructions.
        codeAttributeComposer.beginCodeFragment(codeAttribute.u4codeLength);

        // Copy the instructions.
        codeAttribute.instructionsAccept(clazz, method, this);

        // Copy the exceptions.
        codeAttribute.exceptionsAccept(clazz, method, exceptionInfoAdder);

        // Append a label just after the code.
        codeAttributeComposer.appendLabel(codeAttribute.u4codeLength);

        codeAttributeComposer.endCodeFragment();
    }


    // Implementations for InstructionVisitor.

    public void visitAnyInstruction(Clazz clazz, Method method, CodeAttribute codeAttribute, int offset, Instruction instruction)
    {
        codeAttributeComposer.appendInstruction(offset, instruction.shrink());
    }


    public void visitSimpleInstruction(Clazz clazz, Method method, CodeAttribute codeAttribute, int offset, SimpleInstruction simpleInstruction)
    {
        // Are we inlining this instruction?
        if (inlining)
        {
            // Replace any return instructions by branches to the end of the code.
            switch (simpleInstruction.opcode)
            {
                case InstructionConstants.OP_IRETURN:
                case InstructionConstants.OP_LRETURN:
                case InstructionConstants.OP_FRETURN:
                case InstructionConstants.OP_DRETURN:
                case InstructionConstants.OP_ARETURN:
                case InstructionConstants.OP_RETURN:
                    // Are we not at the last instruction?
                    if (offset < codeAttribute.u4codeLength-1)
                    {
                        // Replace the return instruction by a branch instruction.
                        Instruction branchInstruction =
                            new BranchInstruction(InstructionConstants.OP_GOTO_W,
                                                  codeAttribute.u4codeLength - offset);

                        codeAttributeComposer.appendInstruction(offset,
                                                                branchInstruction.shrink());
                    }
                    else
                    {
                        // Just leave out the instruction, but put in a label,
                        // for the sake of any other branch instructions.
                        codeAttributeComposer.appendLabel(offset);
                    }

                    return;
            }
        }

        codeAttributeComposer.appendInstruction(offset, simpleInstruction.shrink());
    }


    public void visitVariableInstruction(Clazz clazz, Method method, CodeAttribute codeAttribute, int offset, VariableInstruction variableInstruction)
    {
        // Are we inlining this instruction?
        if (inlining)
        {
            // Update the variable index.
            variableInstruction.variableIndex += variableOffset;
        }

        codeAttributeComposer.appendInstruction(offset, variableInstruction.shrink());
    }


    public void visitConstantInstruction(Clazz clazz, Method method, CodeAttribute codeAttribute, int offset, ConstantInstruction constantInstruction)
    {
        // Is it a method invocation?
        switch (constantInstruction.opcode)
        {
            case InstructionConstants.OP_NEW:
                uninitializedObjectCount++;
                break;

            case InstructionConstants.OP_INVOKEVIRTUAL:
            case InstructionConstants.OP_INVOKESPECIAL:
            case InstructionConstants.OP_INVOKESTATIC:
            case InstructionConstants.OP_INVOKEINTERFACE:
                // See if we can inline it.
                inlined = false;

                // Append a label, in case the invocation will be inlined.
                codeAttributeComposer.appendLabel(offset);

                emptyInvokingStack =
                    !inlining &&
                    stackSizeComputer.isReachable(offset) &&
                    stackSizeComputer.getStackSize(offset) == 0;

                variableOffset += codeAttribute.u2maxLocals;

                clazz.constantPoolEntryAccept(constantInstruction.constantIndex, this);

                variableOffset -= codeAttribute.u2maxLocals;

                // Was the method inlined?
                if (inlined)
                {
                    if (extraInlinedInvocationVisitor != null)
                    {
                        extraInlinedInvocationVisitor.visitConstantInstruction(clazz, method, codeAttribute, offset, constantInstruction);
                    }

                    // The invocation itself is no longer necessary.
                    return;
                }

                break;
        }

        // Are we inlining this instruction?
        if (inlining)
        {
            // Make sure the constant is present in the constant pool of the
            // target class.
            constantInstruction.constantIndex =
                constantAdder.addConstant(clazz, constantInstruction.constantIndex);
        }

        codeAttributeComposer.appendInstruction(offset, constantInstruction.shrink());
    }


    // Implementations for ConstantVisitor.

    public void visitInterfaceMethodrefConstant(Clazz clazz, InterfaceMethodrefConstant interfaceMethodrefConstant) {}


    public void visitMethodrefConstant(Clazz clazz, MethodrefConstant methodrefConstant)
    {
        methodrefConstant.referencedMemberAccept(this);
    }


    // Implementations for MemberVisitor.

    public void visitAnyMember(Clazz Clazz, Member member) {}


    public void visitProgramMethod(ProgramClass programClass, ProgramMethod programMethod)
    {
        int accessFlags = programMethod.getAccessFlags();

        if (// Only inline the method if it is private, static, or final.
            (accessFlags & (ClassConstants.INTERNAL_ACC_PRIVATE |
                            ClassConstants.INTERNAL_ACC_STATIC  |
                            ClassConstants.INTERNAL_ACC_FINAL)) != 0                               &&

            // Only inline the method if it is not synchronized, etc.
            (accessFlags & (ClassConstants.INTERNAL_ACC_SYNCHRONIZED |
                            ClassConstants.INTERNAL_ACC_NATIVE       |
                            ClassConstants.INTERNAL_ACC_INTERFACE    |
                            ClassConstants.INTERNAL_ACC_ABSTRACT)) == 0                            &&

            // Don't inline an <init> method, except in an <init> method in the
            // same class.
//            (!programMethod.getName(programClass).equals(ClassConstants.INTERNAL_METHOD_NAME_INIT) ||
//             (programClass.equals(targetClass) &&
//              targetMethod.getName(targetClass).equals(ClassConstants.INTERNAL_METHOD_NAME_INIT))) &&
            !programMethod.getName(programClass).equals(ClassConstants.INTERNAL_METHOD_NAME_INIT)  &&

            // Don't inline a method into itself.
            (!programMethod.equals(targetMethod) ||
             !programClass.equals(targetClass))                                                    &&

            // Only inline the method if it isn't recursing.
            !inliningMethods.contains(programMethod)                                               &&

            // Only inline the method if its target class has at least the
            // same version number as the source class, in order to avoid
            // introducing incompatible constructs.
            targetClass.u4version >= programClass.u4version                                        &&

            // Only inline the method if it doesn't invoke a super method, or if
            // it is in the same class.
            (!SuperInvocationMarker.invokesSuperMethods(programMethod) ||
             programClass.equals(targetClass))                                                     &&

            // Only inline the method if it doesn't branch backward while there
            // are uninitialized objects.
            (!BackwardBranchMarker.branchesBackward(programMethod) ||
             uninitializedObjectCount == 0)                                                        &&

            // Only inline if the code access of the inlined method allows it.
            (allowAccessModification ||
             ((!AccessMethodMarker.accessesPrivateCode(programMethod) ||
               programClass.equals(targetClass)) &&

              (!AccessMethodMarker.accessesPackageCode(programMethod) ||
               ClassUtil.internalPackageName(programClass.getName()).equals(
               ClassUtil.internalPackageName(targetClass.getName())))))                            &&

//               (!AccessMethodMarker.accessesProtectedCode(programMethod) ||
//                targetClass.extends_(programClass) ||
//                targetClass.implements_(programClass)) ||
            (!AccessMethodMarker.accessesProtectedCode(programMethod) ||
             programClass.equals(targetClass))                                                     &&

            // Only inline the method if it doesn't catch exceptions, or if it
            // is invoked with an empty stack.
            (!CatchExceptionMarker.catchesExceptions(programMethod) ||
             emptyInvokingStack)                                                                   &&

            // Only inline the method if it comes from the same class or from
            // a class with a static initializer.
            (programClass.equals(targetClass) ||
             programClass.findMethod(ClassConstants.INTERNAL_METHOD_NAME_CLINIT,
                                     ClassConstants.INTERNAL_METHOD_TYPE_CLINIT) == null))
        {
//            System.out.print("MethodInliner: inlining ");
//            programMethod.accept(programClass, new SimpleClassPrinter(true));
//            System.out.print("               in       ");
//            targetMethod.accept(targetClass, new SimpleClassPrinter(true));
//
//            System.out.println("  Private:   "+
//                               (!AccessMethodMarker.accessesPrivateCode(programMethod) ||
//                                programClass.equals(targetClass)));
//
//            System.out.println("  Package:   "+
//                               (!AccessMethodMarker.accessesPackageCode(programMethod) ||
//                                ClassUtil.internalPackageName(programClass.getName()).equals(
//                                ClassUtil.internalPackageName(targetClass.getName()))));
//
//            System.out.println("  Protected: "+
//                               ((!AccessMethodMarker.accessesProtectedCode(programMethod) ||
//                                 targetClass.extends_(programClass) ||
//                                 targetClass.implements_(programClass)) ||
//                                ClassUtil.internalPackageName(programClass.getName()).equals(
//                                ClassUtil.internalPackageName(targetClass.getName()))));

            boolean oldInlining = inlining;
            inlining = true;
            inliningMethods.push(programMethod);

            // Inline the method body.
            programMethod.attributesAccept(programClass, this);

            // Update the optimization information of the target method.
            MethodOptimizationInfo info =
                MethodOptimizationInfo.getMethodOptimizationInfo(targetMethod);
            if (info != null)
            {
                info.merge(MethodOptimizationInfo.getMethodOptimizationInfo(programMethod));
            }

            inlining = oldInlining;
            inliningMethods.pop();
        }
        else if (programMethod.getName(programClass).equals(ClassConstants.INTERNAL_METHOD_NAME_INIT))
        {
            uninitializedObjectCount--;
        }
    }
}
