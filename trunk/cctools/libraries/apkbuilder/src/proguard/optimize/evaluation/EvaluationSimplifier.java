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
package proguard.optimize.evaluation;

import proguard.classfile.*;
import proguard.classfile.attribute.*;
import proguard.classfile.attribute.visitor.AttributeVisitor;
import proguard.classfile.editor.*;
import proguard.classfile.instruction.*;
import proguard.classfile.instruction.visitor.InstructionVisitor;
import proguard.classfile.util.*;
import proguard.classfile.visitor.*;
import proguard.evaluation.*;
import proguard.evaluation.value.*;
import proguard.optimize.info.*;

/**
 * This AttributeVisitor simplifies the code attributes that it visits, based
 * on partial evaluation.
 *
 * @author Eric Lafortune
 */
public class EvaluationSimplifier
extends      SimplifiedVisitor
implements   AttributeVisitor,
             InstructionVisitor
{
    //*
    private static final boolean DEBUG = false;
    /*/
    private static boolean DEBUG       = true;
    //*/

    private final InstructionVisitor extraInstructionVisitor;

    private final PartialEvaluator             partialEvaluator;
    private final SideEffectInstructionChecker sideEffectInstructionChecker = new SideEffectInstructionChecker(true);
    private final CodeAttributeEditor          codeAttributeEditor          = new CodeAttributeEditor(false);


    /**
     * Creates a new EvaluationSimplifier.
     */
    public EvaluationSimplifier()
    {
        this(new PartialEvaluator(), null);
    }


    /**
     * Creates a new EvaluationSimplifier.
     * @param partialEvaluator        the partial evaluator that will
     *                                execute the code and provide
     *                                information about the results.
     * @param extraInstructionVisitor an optional extra visitor for all
     *                                simplified instructions.
     */
    public EvaluationSimplifier(PartialEvaluator   partialEvaluator,
                                InstructionVisitor extraInstructionVisitor)
    {
        this.partialEvaluator        = partialEvaluator;
        this.extraInstructionVisitor = extraInstructionVisitor;
    }


    // Implementations for AttributeVisitor.

    public void visitAnyAttribute(Clazz clazz, Attribute attribute) {}


    public void visitCodeAttribute(Clazz clazz, Method method, CodeAttribute codeAttribute)
    {
//        DEBUG =
//            clazz.getName().equals("abc/Def") &&
//            method.getName(clazz).equals("abc");

        // TODO: Remove this when the evaluation simplifier has stabilized.
        // Catch any unexpected exceptions from the actual visiting method.
        try
        {
            // Process the code.
            visitCodeAttribute0(clazz, method, codeAttribute);
        }
        catch (RuntimeException ex)
        {
            System.err.println("Unexpected error while simplifying instructions after partial evaluation:");
            System.err.println("  Class       = ["+clazz.getName()+"]");
            System.err.println("  Method      = ["+method.getName(clazz)+method.getDescriptor(clazz)+"]");
            System.err.println("  Exception   = ["+ex.getClass().getName()+"] ("+ex.getMessage()+")");
            System.err.println("Not optimizing this method");

            if (DEBUG)
            {
                method.accept(clazz, new ClassPrinter());

                throw ex;
            }
        }
    }


    public void visitCodeAttribute0(Clazz clazz, Method method, CodeAttribute codeAttribute)
    {
        if (DEBUG)
        {
            System.out.println();
            System.out.println("Class "+ClassUtil.externalClassName(clazz.getName()));
            System.out.println("Method "+ClassUtil.externalFullMethodDescription(clazz.getName(),
                                                                                 0,
                                                                                 method.getName(clazz),
                                                                                 method.getDescriptor(clazz)));
        }

        // Evaluate the method.
        partialEvaluator.visitCodeAttribute(clazz, method, codeAttribute);

        int codeLength = codeAttribute.u4codeLength;

        // Reset the code changes.
        codeAttributeEditor.reset(codeLength);

        // Replace any instructions that can be simplified.
        for (int offset = 0; offset < codeLength; offset++)
        {
            if (partialEvaluator.isTraced(offset))
            {
                Instruction instruction = InstructionFactory.create(codeAttribute.code,
                                                                    offset);

                instruction.accept(clazz, method, codeAttribute, offset, this);
            }
        }

        // Apply all accumulated changes to the code.
        codeAttributeEditor.visitCodeAttribute(clazz, method, codeAttribute);
    }


    // Implementations for InstructionVisitor.

    public void visitSimpleInstruction(Clazz clazz, Method method, CodeAttribute codeAttribute, int offset, SimpleInstruction simpleInstruction)
    {
        switch (simpleInstruction.opcode)
        {
            case InstructionConstants.OP_IALOAD:
            case InstructionConstants.OP_BALOAD:
            case InstructionConstants.OP_CALOAD:
            case InstructionConstants.OP_SALOAD:
            case InstructionConstants.OP_IADD:
            case InstructionConstants.OP_ISUB:
            case InstructionConstants.OP_IMUL:
            case InstructionConstants.OP_IDIV:
            case InstructionConstants.OP_IREM:
            case InstructionConstants.OP_INEG:
            case InstructionConstants.OP_ISHL:
            case InstructionConstants.OP_ISHR:
            case InstructionConstants.OP_IUSHR:
            case InstructionConstants.OP_IAND:
            case InstructionConstants.OP_IOR:
            case InstructionConstants.OP_IXOR:
            case InstructionConstants.OP_L2I:
            case InstructionConstants.OP_F2I:
            case InstructionConstants.OP_D2I:
            case InstructionConstants.OP_I2B:
            case InstructionConstants.OP_I2C:
            case InstructionConstants.OP_I2S:
                replaceIntegerPushInstruction(clazz, offset, simpleInstruction);
                break;

            case InstructionConstants.OP_LALOAD:
            case InstructionConstants.OP_LADD:
            case InstructionConstants.OP_LSUB:
            case InstructionConstants.OP_LMUL:
            case InstructionConstants.OP_LDIV:
            case InstructionConstants.OP_LREM:
            case InstructionConstants.OP_LNEG:
            case InstructionConstants.OP_LSHL:
            case InstructionConstants.OP_LSHR:
            case InstructionConstants.OP_LUSHR:
            case InstructionConstants.OP_LAND:
            case InstructionConstants.OP_LOR:
            case InstructionConstants.OP_LXOR:
            case InstructionConstants.OP_I2L:
            case InstructionConstants.OP_F2L:
            case InstructionConstants.OP_D2L:
                replaceLongPushInstruction(clazz, offset, simpleInstruction);
                break;

            case InstructionConstants.OP_FALOAD:
            case InstructionConstants.OP_FADD:
            case InstructionConstants.OP_FSUB:
            case InstructionConstants.OP_FMUL:
            case InstructionConstants.OP_FDIV:
            case InstructionConstants.OP_FREM:
            case InstructionConstants.OP_FNEG:
            case InstructionConstants.OP_I2F:
            case InstructionConstants.OP_L2F:
            case InstructionConstants.OP_D2F:
                replaceFloatPushInstruction(clazz, offset, simpleInstruction);
                break;

            case InstructionConstants.OP_DALOAD:
            case InstructionConstants.OP_DADD:
            case InstructionConstants.OP_DSUB:
            case InstructionConstants.OP_DMUL:
            case InstructionConstants.OP_DDIV:
            case InstructionConstants.OP_DREM:
            case InstructionConstants.OP_DNEG:
            case InstructionConstants.OP_I2D:
            case InstructionConstants.OP_L2D:
            case InstructionConstants.OP_F2D:
                replaceDoublePushInstruction(clazz, offset, simpleInstruction);
                break;

            case InstructionConstants.OP_AALOAD:
                replaceReferencePushInstruction(clazz, offset, simpleInstruction);
                break;
        }
    }


    public void visitVariableInstruction(Clazz clazz, Method method, CodeAttribute codeAttribute, int offset, VariableInstruction variableInstruction)
    {
        int variableIndex = variableInstruction.variableIndex;

        switch (variableInstruction.opcode)
        {
            case InstructionConstants.OP_ILOAD:
            case InstructionConstants.OP_ILOAD_0:
            case InstructionConstants.OP_ILOAD_1:
            case InstructionConstants.OP_ILOAD_2:
            case InstructionConstants.OP_ILOAD_3:
                replaceIntegerPushInstruction(clazz, offset, variableInstruction, variableIndex);
                break;

            case InstructionConstants.OP_LLOAD:
            case InstructionConstants.OP_LLOAD_0:
            case InstructionConstants.OP_LLOAD_1:
            case InstructionConstants.OP_LLOAD_2:
            case InstructionConstants.OP_LLOAD_3:
                replaceLongPushInstruction(clazz, offset, variableInstruction, variableIndex);
                break;

            case InstructionConstants.OP_FLOAD:
            case InstructionConstants.OP_FLOAD_0:
            case InstructionConstants.OP_FLOAD_1:
            case InstructionConstants.OP_FLOAD_2:
            case InstructionConstants.OP_FLOAD_3:
                replaceFloatPushInstruction(clazz, offset, variableInstruction, variableIndex);
                break;

            case InstructionConstants.OP_DLOAD:
            case InstructionConstants.OP_DLOAD_0:
            case InstructionConstants.OP_DLOAD_1:
            case InstructionConstants.OP_DLOAD_2:
            case InstructionConstants.OP_DLOAD_3:
                replaceDoublePushInstruction(clazz, offset, variableInstruction, variableIndex);
                break;

            case InstructionConstants.OP_ALOAD:
            case InstructionConstants.OP_ALOAD_0:
            case InstructionConstants.OP_ALOAD_1:
            case InstructionConstants.OP_ALOAD_2:
            case InstructionConstants.OP_ALOAD_3:
                replaceReferencePushInstruction(clazz, offset, variableInstruction);
                break;

            case InstructionConstants.OP_ASTORE:
            case InstructionConstants.OP_ASTORE_0:
            case InstructionConstants.OP_ASTORE_1:
            case InstructionConstants.OP_ASTORE_2:
            case InstructionConstants.OP_ASTORE_3:
                deleteReferencePopInstruction(clazz, offset, variableInstruction);
                break;

            case InstructionConstants.OP_RET:
                replaceBranchInstruction(clazz, offset, variableInstruction);
                break;
        }
    }


    public void visitConstantInstruction(Clazz clazz, Method method, CodeAttribute codeAttribute, int offset, ConstantInstruction constantInstruction)
    {
        switch (constantInstruction.opcode)
        {
            case InstructionConstants.OP_GETSTATIC:
            case InstructionConstants.OP_GETFIELD:
                replaceAnyPushInstruction(clazz, offset, constantInstruction);
                break;

            case InstructionConstants.OP_INVOKEVIRTUAL:
            case InstructionConstants.OP_INVOKESPECIAL:
            case InstructionConstants.OP_INVOKESTATIC:
            case InstructionConstants.OP_INVOKEINTERFACE:
                if (constantInstruction.stackPushCount(clazz) > 0 &&
                    !sideEffectInstructionChecker.hasSideEffects(clazz,
                                                                 method,
                                                                 codeAttribute,
                                                                 offset,
                                                                 constantInstruction))
                {
                    replaceAnyPushInstruction(clazz, offset, constantInstruction);
                }

                break;

            case InstructionConstants.OP_CHECKCAST:
                replaceReferencePushInstruction(clazz, offset, constantInstruction);
                break;
        }
    }


    public void visitBranchInstruction(Clazz clazz, Method method, CodeAttribute codeAttribute, int offset, BranchInstruction branchInstruction)
    {
        switch (branchInstruction.opcode)
        {
            case InstructionConstants.OP_GOTO:
            case InstructionConstants.OP_GOTO_W:
                // Don't replace unconditional branches.
                break;

            case InstructionConstants.OP_JSR:
            case InstructionConstants.OP_JSR_W:
                replaceJsrInstruction(clazz, offset, branchInstruction);
                break;

            default:
                replaceBranchInstruction(clazz, offset, branchInstruction);
                break;
        }
    }


    public void visitAnySwitchInstruction(Clazz clazz, Method method, CodeAttribute codeAttribute, int offset, SwitchInstruction switchInstruction)
    {
        // First try to simplify it to a simple branch.
        replaceBranchInstruction(clazz, offset, switchInstruction);

        // Otherwise make sure all branch targets are valid.
        if (!codeAttributeEditor.isModified(offset))
        {
            replaceSwitchInstruction(clazz, offset, switchInstruction);
        }
    }


    // Small utility methods.

    /**
     * Replaces the push instruction at the given offset by a simpler push
     * instruction, if possible.
     */
    private void replaceAnyPushInstruction(Clazz       clazz,
                                           int         offset,
                                           Instruction instruction)
    {
        Value pushedValue = partialEvaluator.getStackAfter(offset).getTop(0);
        if (pushedValue.isParticular())
        {
            switch (pushedValue.computationalType())
            {
                case Value.TYPE_INTEGER:
                    replaceIntegerPushInstruction(clazz, offset, instruction);
                    break;
                case Value.TYPE_LONG:
                    replaceLongPushInstruction(clazz, offset, instruction);
                    break;
                case Value.TYPE_FLOAT:
                    replaceFloatPushInstruction(clazz, offset, instruction);
                    break;
                case Value.TYPE_DOUBLE:
                    replaceDoublePushInstruction(clazz, offset, instruction);
                    break;
                case Value.TYPE_REFERENCE:
                    replaceReferencePushInstruction(clazz, offset, instruction);
                    break;
            }
        }
    }


    /**
     * Replaces the integer pushing instruction at the given offset by a simpler
     * push instruction, if possible.
     */
    private void replaceIntegerPushInstruction(Clazz       clazz,
                                               int         offset,
                                               Instruction instruction)
    {
        replaceIntegerPushInstruction(clazz,
                                      offset,
                                      instruction,
                                      partialEvaluator.getVariablesBefore(offset).size());
    }


    /**
     * Replaces the integer pushing instruction at the given offset by a simpler
     * push instruction, if possible.
     */
    private void replaceIntegerPushInstruction(Clazz       clazz,
                                               int         offset,
                                               Instruction instruction,
                                               int         maxVariableIndex)
    {
        Value pushedValue = partialEvaluator.getStackAfter(offset).getTop(0);
        if (pushedValue.isParticular())
        {
            int value = pushedValue.integerValue().value();
            if (value << 16 >> 16 == value)
            {
                replaceConstantPushInstruction(clazz,
                                               offset,
                                               instruction,
                                               InstructionConstants.OP_SIPUSH,
                                               value);
            }
            else
            {
                ConstantPoolEditor constantPoolEditor =
                    new ConstantPoolEditor((ProgramClass)clazz);

                Instruction replacementInstruction =
                    new ConstantInstruction(InstructionConstants.OP_LDC,
                                            constantPoolEditor.addIntegerConstant(value)).shrink();

                replaceInstruction(clazz, offset, instruction, replacementInstruction);
            }
        }
        else if (pushedValue.isSpecific())
        {
            TracedVariables variables = partialEvaluator.getVariablesBefore(offset);
            for (int variableIndex = 0; variableIndex < maxVariableIndex; variableIndex++)
            {
                if (pushedValue.equals(variables.load(variableIndex)))
                {
                    replaceVariablePushInstruction(clazz,
                                                   offset,
                                                   instruction,
                                                   InstructionConstants.OP_ILOAD,
                                                   variableIndex);
                }
            }
        }
    }


    /**
     * Replaces the long pushing instruction at the given offset by a simpler
     * push instruction, if possible.
     */
    private void replaceLongPushInstruction(Clazz       clazz,
                                            int         offset,
                                            Instruction instruction)
    {
        replaceLongPushInstruction(clazz,
                                   offset,
                                   instruction,
                                   partialEvaluator.getVariablesBefore(offset).size());
    }


    /**
     * Replaces the long pushing instruction at the given offset by a simpler
     * push instruction, if possible.
     */
    private void replaceLongPushInstruction(Clazz       clazz,
                                            int         offset,
                                            Instruction instruction,
                                            int         maxVariableIndex)
    {
        Value pushedValue = partialEvaluator.getStackAfter(offset).getTop(0);
        if (pushedValue.isParticular())
        {
            long value = pushedValue.longValue().value();
            if (value == 0L ||
                value == 1L)
            {
                replaceConstantPushInstruction(clazz,
                                       offset,
                                       instruction,
                                       InstructionConstants.OP_LCONST_0,
                                       (int)value);
            }
            else
            {
                ConstantPoolEditor constantPoolEditor =
                    new ConstantPoolEditor((ProgramClass)clazz);

                Instruction replacementInstruction =
                    new ConstantInstruction(InstructionConstants.OP_LDC2_W,
                                            constantPoolEditor.addLongConstant(value)).shrink();

                replaceInstruction(clazz, offset, instruction, replacementInstruction);
            }
        }
        else if (pushedValue.isSpecific())
        {
            TracedVariables variables = partialEvaluator.getVariablesBefore(offset);
            for (int variableIndex = 0; variableIndex < maxVariableIndex; variableIndex++)
            {
                if (pushedValue.equals(variables.load(variableIndex)))
                {
                    replaceVariablePushInstruction(clazz,
                                                   offset,
                                                   instruction,
                                                   InstructionConstants.OP_LLOAD,
                                                   variableIndex);
                }
            }
        }
    }


    /**
     * Replaces the float pushing instruction at the given offset by a simpler
     * push instruction, if possible.
     */
    private void replaceFloatPushInstruction(Clazz       clazz,
                                             int         offset,
                                             Instruction instruction)
    {
        replaceFloatPushInstruction(clazz,
                                    offset,
                                    instruction,
                                    partialEvaluator.getVariablesBefore(offset).size());
    }


    /**
     * Replaces the float pushing instruction at the given offset by a simpler
     * push instruction, if possible.
     */
    private void replaceFloatPushInstruction(Clazz       clazz,
                                             int         offset,
                                             Instruction instruction,
                                             int         maxVariableIndex)
    {
        Value pushedValue = partialEvaluator.getStackAfter(offset).getTop(0);
        if (pushedValue.isParticular())
        {
            float value = pushedValue.floatValue().value();
            if (value == 0f ||
                value == 1f ||
                value == 2f)
            {
                replaceConstantPushInstruction(clazz,
                                               offset,
                                               instruction,
                                               InstructionConstants.OP_FCONST_0,
                                               (int)value);
            }
            else
            {
                ConstantPoolEditor constantPoolEditor =
                    new ConstantPoolEditor((ProgramClass)clazz);

                Instruction replacementInstruction =
                    new ConstantInstruction(InstructionConstants.OP_LDC,
                                            constantPoolEditor.addFloatConstant(value)).shrink();

                replaceInstruction(clazz, offset, instruction, replacementInstruction);
            }
        }
        else if (pushedValue.isSpecific())
        {
            TracedVariables variables = partialEvaluator.getVariablesBefore(offset);
            for (int variableIndex = 0; variableIndex < maxVariableIndex; variableIndex++)
            {
                if (pushedValue.equals(variables.load(variableIndex)))
                {
                    replaceVariablePushInstruction(clazz,
                                                   offset,
                                                   instruction,
                                                   InstructionConstants.OP_FLOAD,
                                                   variableIndex);
                }
            }
        }
    }


    /**
     * Replaces the double pushing instruction at the given offset by a simpler
     * push instruction, if possible.
     */
    private void replaceDoublePushInstruction(Clazz       clazz,
                                              int         offset,
                                              Instruction instruction)
    {
        replaceDoublePushInstruction(clazz,
                                     offset,
                                     instruction,
                                     partialEvaluator.getVariablesBefore(offset).size());
    }


    /**
     * Replaces the double pushing instruction at the given offset by a simpler
     * push instruction, if possible.
     */
    private void replaceDoublePushInstruction(Clazz       clazz,
                                              int         offset,
                                              Instruction instruction,
                                              int         maxVariableIndex)
    {
        Value pushedValue = partialEvaluator.getStackAfter(offset).getTop(0);
        if (pushedValue.isParticular())
        {
            double value = pushedValue.doubleValue().value();
            if (value == 0.0 ||
                value == 1.0)
            {
                replaceConstantPushInstruction(clazz,
                                               offset,
                                               instruction,
                                               InstructionConstants.OP_DCONST_0,
                                               (int)value);
            }
            else
            {
                ConstantPoolEditor constantPoolEditor =
                    new ConstantPoolEditor((ProgramClass)clazz);

                Instruction replacementInstruction =
                    new ConstantInstruction(InstructionConstants.OP_LDC2_W,
                                            constantPoolEditor.addDoubleConstant(value)).shrink();

                replaceInstruction(clazz, offset, instruction, replacementInstruction);
            }
        }
        else if (pushedValue.isSpecific())
        {
            TracedVariables variables = partialEvaluator.getVariablesBefore(offset);
            for (int variableIndex = 0; variableIndex < maxVariableIndex; variableIndex++)
            {
                if (pushedValue.equals(variables.load(variableIndex)))
                {
                    replaceVariablePushInstruction(clazz,
                                                   offset,
                                                   instruction,
                                                   InstructionConstants.OP_DLOAD,
                                                   variableIndex);
                }
            }
        }
    }


    /**
     * Replaces the reference pushing instruction at the given offset by a
     * simpler push instruction, if possible.
     */
    private void replaceReferencePushInstruction(Clazz       clazz,
                                                 int         offset,
                                                 Instruction instruction)
    {
        Value pushedValue = partialEvaluator.getStackAfter(offset).getTop(0);
        if (pushedValue.isParticular())
        {
            // A reference value can only be specific if it is null.
            replaceConstantPushInstruction(clazz,
                                           offset,
                                           instruction,
                                           InstructionConstants.OP_ACONST_NULL,
                                           0);
        }
    }


    /**
     * Replaces the instruction at a given offset by a given push instruction
     * of a constant.
     */
    private void replaceConstantPushInstruction(Clazz       clazz,
                                                int         offset,
                                                Instruction instruction,
                                                byte        replacementOpcode,
                                                int         value)
    {
        Instruction replacementInstruction =
            new SimpleInstruction(replacementOpcode, value).shrink();

        replaceInstruction(clazz, offset, instruction, replacementInstruction);
    }


    /**
     * Replaces the instruction at a given offset by a given push instruction
     * of a variable.
     */
    private void replaceVariablePushInstruction(Clazz       clazz,
                                                int         offset,
                                                Instruction instruction,
                                                byte        replacementOpcode,
                                                int         variableIndex)
    {
        Instruction replacementInstruction =
            new VariableInstruction(replacementOpcode, variableIndex).shrink();

        replaceInstruction(clazz, offset, instruction, replacementInstruction);
    }


    /**
     * Replaces the given 'jsr' instruction by a simpler branch instruction,
     * if it jumps to a subroutine that doesn't return or a subroutine that
     * is only called from one place.
     */
    private void replaceJsrInstruction(Clazz             clazz,
                                       int               offset,
                                       BranchInstruction branchInstruction)
    {
        // Is the subroutine ever returning?
        int subroutineStart = offset + branchInstruction.branchOffset;
        if (!partialEvaluator.isSubroutineReturning(subroutineStart) ||
            partialEvaluator.branchOrigins(subroutineStart).instructionOffsetCount() == 1)
        {
            // All 'jsr' instructions to this subroutine can be replaced
            // by unconditional branch instructions.
            replaceBranchInstruction(clazz, offset, branchInstruction);
        }
        else if (!partialEvaluator.isTraced(offset + branchInstruction.length(offset)))
        {
            // We have to make sure the instruction after this 'jsr'
            // instruction is valid, even if it is never reached.
            replaceByInfiniteLoop(clazz, offset + branchInstruction.length(offset), branchInstruction);
        }
    }


    /**
     * Deletes the reference popping instruction at the given offset, if
     * it is at the start of a subroutine that doesn't return or a subroutine
     * that is only called from one place.
     */
    private void deleteReferencePopInstruction(Clazz       clazz,
                                               int         offset,
                                               Instruction instruction)
    {
        if (partialEvaluator.isSubroutineStart(offset) &&
            (!partialEvaluator.isSubroutineReturning(offset) ||
             partialEvaluator.branchOrigins(offset).instructionOffsetCount() == 1))
        {
            if (DEBUG) System.out.println("  Deleting store of subroutine return address "+instruction.toString(offset));

            // A reference value can only be specific if it is null.
            codeAttributeEditor.deleteInstruction(offset);
        }
    }


    /**
     * Deletes the given branch instruction, or replaces it by a simpler branch
     * instruction, if possible.
     */
    private void replaceBranchInstruction(Clazz       clazz,
                                          int         offset,
                                          Instruction instruction)
    {
        InstructionOffsetValue branchTargets = partialEvaluator.branchTargets(offset);

        // Is there exactly one branch target (not from a goto or jsr)?
        if (branchTargets != null &&
            branchTargets.instructionOffsetCount() == 1)
        {
            // Is it branching to the next instruction?
            int branchOffset = branchTargets.instructionOffset(0) - offset;
            if (branchOffset == instruction.length(offset))
            {
                if (DEBUG) System.out.println("  Ignoring zero branch instruction at ["+offset+"]");
            }
            else
            {
                // Replace the branch instruction by a simple branch instruction.
                Instruction replacementInstruction =
                    new BranchInstruction(InstructionConstants.OP_GOTO_W,
                                          branchOffset).shrink();

                replaceInstruction(clazz, offset, instruction, replacementInstruction);
            }
        }
    }


    /**
     * Makes sure all branch targets of the given switch instruction are valid.
     */
    private void replaceSwitchInstruction(Clazz             clazz,
                                          int               offset,
                                          SwitchInstruction switchInstruction)
    {
        // Get the actual branch targets.
        InstructionOffsetValue branchTargets = partialEvaluator.branchTargets(offset);

        // Get an offset that can serve as a valid default offset.
        int defaultOffset =
            branchTargets.instructionOffset(branchTargets.instructionOffsetCount()-1) -
            offset;

        Instruction replacementInstruction = null;

        // Check the jump offsets.
        int[] jumpOffsets = switchInstruction.jumpOffsets;
        for (int index = 0; index < jumpOffsets.length; index++)
        {
            if (!branchTargets.contains(offset + jumpOffsets[index]))
            {
                // Replace the unused offset.
                jumpOffsets[index] = defaultOffset;

                // Remember to replace the instruction.
                replacementInstruction = switchInstruction;
            }
        }

        // Check the default offset.
        if (!branchTargets.contains(offset + switchInstruction.defaultOffset))
        {
            // Replace the unused offset.
            switchInstruction.defaultOffset = defaultOffset;

            // Remember to replace the instruction.
            replacementInstruction = switchInstruction;
        }

        if (replacementInstruction != null)
        {
            replaceInstruction(clazz, offset, switchInstruction, replacementInstruction);
        }
    }


    /**
     * Replaces the given instruction by an infinite loop.
     */
    private void replaceByInfiniteLoop(Clazz       clazz,
                                       int         offset,
                                       Instruction instruction)
    {
        // Replace the instruction by an infinite loop.
        Instruction replacementInstruction =
            new BranchInstruction(InstructionConstants.OP_GOTO, 0);

        if (DEBUG) System.out.println("  Replacing unreachable instruction by infinite loop "+replacementInstruction.toString(offset));

        codeAttributeEditor.replaceInstruction(offset, replacementInstruction);

        // Visit the instruction, if required.
        if (extraInstructionVisitor != null)
        {
            // Note: we're not passing the right arguments for now, knowing that
            // they aren't used anyway.
            instruction.accept(clazz, null, null, offset, extraInstructionVisitor);
        }
    }


    /**
     * Replaces the instruction at a given offset by a given push instruction.
     */
    private void replaceInstruction(Clazz       clazz,
                                    int         offset,
                                    Instruction instruction,
                                    Instruction replacementInstruction)
    {
        // Pop unneeded stack entries if necessary.
        int popCount =
            instruction.stackPopCount(clazz) -
            replacementInstruction.stackPopCount(clazz);

        insertPopInstructions(offset, popCount);

        if (DEBUG) System.out.println("  Replacing instruction "+instruction.toString(offset)+" -> "+replacementInstruction.toString()+(popCount == 0 ? "" : " ("+popCount+" pops)"));

        codeAttributeEditor.replaceInstruction(offset, replacementInstruction);

        // Visit the instruction, if required.
        if (extraInstructionVisitor != null)
        {
            // Note: we're not passing the right arguments for now, knowing that
            // they aren't used anyway.
            instruction.accept(clazz, null, null, offset, extraInstructionVisitor);
        }
    }


    /**
     * Pops the given number of stack entries before the instruction at the
     * given offset.
     */
    private void insertPopInstructions(int offset, int popCount)
    {
        switch (popCount)
        {
            case 0:
            {
                break;
            }
            case 1:
            {
                // Insert a single pop instruction.
                Instruction popInstruction =
                    new SimpleInstruction(InstructionConstants.OP_POP);

                codeAttributeEditor.insertBeforeInstruction(offset,
                                                            popInstruction);
                break;
            }
            case 2:
            {
                // Insert a single pop2 instruction.
                Instruction popInstruction =
                    new SimpleInstruction(InstructionConstants.OP_POP2);

                codeAttributeEditor.insertBeforeInstruction(offset,
                                                            popInstruction);
                break;
            }
            default:
            {
                // Insert the specified number of pop instructions.
                Instruction[] popInstructions =
                    new Instruction[popCount / 2 + popCount % 2];

                Instruction popInstruction =
                    new SimpleInstruction(InstructionConstants.OP_POP2);

                for (int index = 0; index < popCount / 2; index++)
                {
                      popInstructions[index] = popInstruction;
                }

                if (popCount % 2 == 1)
                {
                    popInstruction =
                        new SimpleInstruction(InstructionConstants.OP_POP);

                    popInstructions[popCount / 2] = popInstruction;
                }

                codeAttributeEditor.insertBeforeInstruction(offset,
                                                            popInstructions);
                break;
            }
        }
    }
}
