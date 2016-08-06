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
package proguard.optimize;

import proguard.classfile.*;
import proguard.classfile.attribute.*;
import proguard.classfile.attribute.visitor.AttributeVisitor;
import proguard.classfile.constant.*;
import proguard.classfile.constant.visitor.ConstantVisitor;
import proguard.classfile.editor.CodeAttributeEditor;
import proguard.classfile.instruction.*;
import proguard.classfile.instruction.visitor.InstructionVisitor;
import proguard.classfile.util.SimplifiedVisitor;
import proguard.classfile.visitor.MemberVisitor;

/**
 * This AttributeVisitor adds an additional integer parameter to the tweaked
 * initialization method invocations that it visits.
 */
public class DuplicateInitializerInvocationFixer
extends      SimplifiedVisitor
implements   AttributeVisitor,
             InstructionVisitor,
             ConstantVisitor,
             MemberVisitor
{
    private static final boolean DEBUG = false;

    private final InstructionVisitor extraAddedInstructionVisitor;

    private final CodeAttributeEditor codeAttributeEditor = new CodeAttributeEditor();

    private String  descriptor;
    private boolean hasBeenFixed;


    /**
     * Creates a new EvaluationSimplifier.
     */
    public DuplicateInitializerInvocationFixer()
    {
        this(null);
    }


    /**
     * Creates a new EvaluationSimplifier.
     * @param extraAddedInstructionVisitor an optional extra visitor for all
     *                                     added instructions.
     */
    public DuplicateInitializerInvocationFixer(InstructionVisitor extraAddedInstructionVisitor)
    {
        this.extraAddedInstructionVisitor = extraAddedInstructionVisitor;
    }


   // Implementations for AttributeVisitor.

    public void visitAnyAttribute(Clazz clazz, Attribute attribute) {}


    public void visitCodeAttribute(Clazz clazz, Method method, CodeAttribute codeAttribute)
    {

        // Reset the code changes.
        codeAttributeEditor.reset(codeAttribute.u4codeLength);

        // Fix any duplicate constructor invocations.
        codeAttribute.instructionsAccept(clazz,
                                         method,
                                         this);

        // Apply all accumulated changes to the code.
        codeAttributeEditor.visitCodeAttribute(clazz, method, codeAttribute);
    }


    // Implementations for InstructionVisitor.

    public void visitAnyInstruction(Clazz clazz, Method method, CodeAttribute codeAttribute, int offset, Instruction instruction) {}


    public void visitConstantInstruction(Clazz clazz, Method method, CodeAttribute codeAttribute, int offset, ConstantInstruction constantInstruction)
    {
        if (constantInstruction.opcode == InstructionConstants.OP_INVOKESPECIAL)
        {
            hasBeenFixed = false;
            clazz.constantPoolEntryAccept(constantInstruction.constantIndex, this);

            if (hasBeenFixed)
            {
                Instruction extraInstruction =
                    new SimpleInstruction(InstructionConstants.OP_ICONST_0);

                codeAttributeEditor.insertBeforeInstruction(offset,
                                                            extraInstruction);

                if (DEBUG)
                {
                    System.out.println("DuplicateInitializerInvocationFixer:");
                    System.out.println("  Inserting "+extraInstruction.toString()+" before "+constantInstruction.toString(offset));
                }

                if (extraAddedInstructionVisitor != null)
                {
                    extraInstruction.accept(null, null, null, offset, extraAddedInstructionVisitor);
                }
            }
        }
    }


    // Implementations for ConstantVisitor.

    public void visitMethodrefConstant(Clazz clazz, MethodrefConstant methodrefConstant)
    {
        // Check the referenced constructor descriptor.
        descriptor = methodrefConstant.getType(clazz);
        methodrefConstant.referencedMemberAccept(this);
    }


    // Implementations for MemberVisitor.

    public void visitLibraryMethod(LibraryClass libraryClass, LibraryMethod libraryMethod) {}


    public void visitProgramMethod(ProgramClass programClass, ProgramMethod programMethod)
    {
        hasBeenFixed = !descriptor.equals(programMethod.getDescriptor(programClass));
    }
}