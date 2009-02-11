/*
 * Copyright (c) 2007 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
package com.sun.max.vm.compiler.builtin;

import com.sun.max.annotate.*;
import com.sun.max.memory.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.compiler.ir.*;
import com.sun.max.vm.compiler.target.*;
import com.sun.max.vm.runtime.*;

/**
 * @author Bernd Mathiske
 */
public abstract class SpecialBuiltin extends Builtin {

    protected SpecialBuiltin() {
        super(SpecialBuiltin.class);
    }

    protected SpecialBuiltin(Class foldingMethodHolder) {
        super(foldingMethodHolder);
    }

    @Override
    public boolean isFoldable(IrValue[] arguments) {
        return false;
    }

    @BUILTIN(builtinClass = SpecialBuiltin.GetFloatingPointRegister.class)
    public static native Pointer getFloatingPointRegister(VMRegister.Role r);

    public static class GetFloatingPointRegister extends SpecialBuiltin {

        @Override
        public <IR_Type> void acceptVisitor(BuiltinVisitor<IR_Type> visitor, IR_Type result, IR_Type[] arguments) {
            assert arguments.length == 1;
            visitor.visitGetFloatingPointRegister(this, result, arguments);
        }

        public static final GetFloatingPointRegister BUILTIN = new GetFloatingPointRegister();
    }

    @BUILTIN(builtinClass = SpecialBuiltin.GetIntegerRegister.class)
    public static native Pointer getIntegerRegister(VMRegister.Role r);

    public static class GetIntegerRegister extends SpecialBuiltin {

        @Override
        public <IR_Type> void acceptVisitor(BuiltinVisitor<IR_Type> visitor, IR_Type result, IR_Type[] arguments) {
            assert arguments.length == 1;
            visitor.visitGetIntegerRegister(this, result, arguments);
        }

        public static final GetIntegerRegister BUILTIN = new GetIntegerRegister();
    }

    @BUILTIN(builtinClass = SpecialBuiltin.SetIntegerRegister.class)
    public static native Pointer setIntegerRegister(VMRegister.Role r, Word value);

    public static class SetIntegerRegister extends SpecialBuiltin {

        @Override
        public <IR_Type> void acceptVisitor(BuiltinVisitor<IR_Type> visitor, IR_Type result, IR_Type[] arguments) {
            assert arguments.length == 2;
            visitor.visitSetIntegerRegister(this, result, arguments);
        }

        public static final SetIntegerRegister BUILTIN = new SetIntegerRegister();
    }

    @BUILTIN(builtinClass = SpecialBuiltin.AddWordsToIntegerRegister.class)
    public static native void addWordsToIntegerRegister(VMRegister.Role registerRole, int numberOfWords);

    public static class AddWordsToIntegerRegister extends SpecialBuiltin {

        @Override
        public <IR_Type> void acceptVisitor(BuiltinVisitor<IR_Type> visitor, IR_Type result, IR_Type[] arguments) {
            assert arguments.length == 2;
            visitor.visitAddWordsToIntegerRegister(this, result, arguments);
        }

        public static final AddWordsToIntegerRegister BUILTIN = new AddWordsToIntegerRegister();
    }

    @BUILTIN(builtinClass = SpecialBuiltin.GetInstructionPointer.class)
    public static native Pointer getInstructionPointer();

    public static class GetInstructionPointer extends SpecialBuiltin {

        @Override
        public <IR_Type> void acceptVisitor(BuiltinVisitor<IR_Type> visitor, IR_Type result, IR_Type[] arguments) {
            assert arguments.length == 0;
            visitor.visitGetInstructionPointer(this, result, arguments);
        }

        public static final GetInstructionPointer BUILTIN = new GetInstructionPointer();
    }

    /**
     * @see Pause
     */
    @BUILTIN(builtinClass = SpecialBuiltin.Pause.class)
    public static native void pause();

    /**
     * If the CPU supports it, then this builtin issues an instruction that improves the performance of spin loops by
     * providing a hint to the processor that the current thread is in a spin loop. The processor may use this to
     * optimize power power consumption while in the spin loop.
     *
     * If the CPU does not support such an instruction, then nothing is emitted for this builtin.
     */
    public static class Pause extends SpecialBuiltin {

        @Override
        public <IR_Type> void acceptVisitor(BuiltinVisitor<IR_Type> visitor, IR_Type result, IR_Type[] arguments) {
            assert arguments.length == 0;
            visitor.visitPause(this, result, arguments);
        }

        public static final Pause BUILTIN = new Pause();
    }

    @BUILTIN(builtinClass = SpecialBuiltin.Jump.class)
    public static native void jump(Address address);

    public static class Jump extends SpecialBuiltin {

        @Override
        public <IR_Type> void acceptVisitor(BuiltinVisitor<IR_Type> visitor, IR_Type result, IR_Type[] arguments) {
            assert arguments.length == 1;
            visitor.visitJump(this, result, arguments);
        }

        public static final Jump BUILTIN = new Jump();
    }

    // The following native methods all map to the same builtin call. The purpose is to be able to
    // have different types of return values. The builtin itself is agnostic to the
    // return type, so we don't need to have one SpecialBuiltin class per returnType.

    @BUILTIN(builtinClass = SpecialBuiltin.Call.class)
    public static native void call();

    @BUILTIN(builtinClass = SpecialBuiltin.Call.class)
    public static native float callFloat();

    @BUILTIN(builtinClass = SpecialBuiltin.Call.class)
    public static native long callLong();

    @BUILTIN(builtinClass = SpecialBuiltin.Call.class)
    public static native double callDouble();

    @BUILTIN(builtinClass = SpecialBuiltin.Call.class)
    public static native Word callWord();

    @BUILTIN(builtinClass = SpecialBuiltin.Call.class)
    public static native void call(Word address);

    @BUILTIN(builtinClass = SpecialBuiltin.Call.class)
    public static native float callFloat(Word address);

    @BUILTIN(builtinClass = SpecialBuiltin.Call.class)
    public static native long callLong(Word address);

    @BUILTIN(builtinClass = SpecialBuiltin.Call.class)
    public static native double callDouble(Word address);

    @BUILTIN(builtinClass = SpecialBuiltin.Call.class)
    public static native Word callWord(Word address);

    @BUILTIN(builtinClass = SpecialBuiltin.Call.class)
    public static native void call(Word address, Object receiver);

    @BUILTIN(builtinClass = SpecialBuiltin.Call.class)
    public static native float callFloat(Word address, Object receiver);

    @BUILTIN(builtinClass = SpecialBuiltin.Call.class)
    public static native long callLong(Word address, Object receiver);

    @BUILTIN(builtinClass = SpecialBuiltin.Call.class)
    public static native double callDouble(Word address, Object receiver);

    @BUILTIN(builtinClass = SpecialBuiltin.Call.class)
    public static native Word callWord(Word address, Object receiver);

    public static class Call extends SpecialBuiltin {

        @Override
        public <IR_Type> void acceptVisitor(BuiltinVisitor<IR_Type> visitor, IR_Type result, IR_Type[] arguments) {
            assert arguments.length == 0 || arguments.length == 1 || arguments.length == 2;
            visitor.visitCall(this, result, arguments);
        }

        public static final Call BUILTIN = new Call();
    }

    @BUILTIN(builtinClass = SpecialBuiltin.UnsignedIntGreaterEqual.class)
    public static boolean unsignedIntGreaterEqual(int value1, int value2) {
        final long unsignedInt1 = value1 & 0xFFFFFFFFL;
        final long unsignedInt2 = value2 & 0xFFFFFFFFL;
        return unsignedInt1 >= unsignedInt2;
    }

    public static class UnsignedIntGreaterEqual extends SpecialBuiltin {

        @Override
        public final boolean hasSideEffects() {
            return false;
        }

        @Override
        public <IR_Type> void acceptVisitor(BuiltinVisitor<IR_Type> visitor, IR_Type result, IR_Type[] arguments) {
            assert arguments.length == 2;
            visitor.visitUnsignedIntGreaterEqual(this, result, arguments);
        }

        public static final UnsignedIntGreaterEqual BUILTIN = new UnsignedIntGreaterEqual();
    }

    /**
     * A compare instruction modifying condition flags, without returning a value in a register or memory location.
     */
    @BUILTIN(builtinClass = SpecialBuiltin.CompareInts.class)
    public static native void compareInts(int value1, int value2);

    public static class CompareInts extends SpecialBuiltin {

        @Override
        public final boolean hasSideEffects() {
            return false;
        }

        @Override
        public <IR_Type> void acceptVisitor(BuiltinVisitor<IR_Type> visitor, IR_Type result, IR_Type[] arguments) {
            assert arguments.length == 2;
            visitor.visitCompareInts(this, result, arguments);
        }

        public static final CompareInts BUILTIN = new CompareInts();
    }

    /**
     * A compare instruction modifying condition flags, without returning a value in a register or memory location.
     */
    @BUILTIN(builtinClass = SpecialBuiltin.CompareReferences.class)
    public static native void compareReferences(Object value1, Object value2);

    public static class CompareReferences extends SpecialBuiltin {

        @Override
        public final boolean hasSideEffects() {
            return false;
        }

        @Override
        public <IR_Type> void acceptVisitor(BuiltinVisitor<IR_Type> visitor, IR_Type result, IR_Type[] arguments) {
            assert arguments.length == 2;
            visitor.visitCompareReferences(this, result, arguments);
        }

        public static final CompareReferences BUILTIN = new CompareReferences();
    }

    public static class BarMemory extends SpecialBuiltin {

        public BarMemory() {
            super(MemoryBarrier.class);
        }

        @Override
        public <IR_Type> void acceptVisitor(BuiltinVisitor<IR_Type> visitor, IR_Type result, IR_Type[] arguments) {
            assert arguments.length == 1;
            visitor.visitBarMemory(this, result, arguments);
        }

        public static final BarMemory BUILTIN = new BarMemory();
    }

    @BUILTIN(builtinClass = SpecialBuiltin.FlushRegisterWindows.class)
    public static native void flushRegisterWindows();

    public static class FlushRegisterWindows extends SpecialBuiltin {

        @Override
        public <IR_Type> void acceptVisitor(BuiltinVisitor<IR_Type> visitor, IR_Type result, IR_Type[] arguments) {
            assert arguments.length == 0;
            visitor.visitFlushRegisterWindows(this, result, arguments);
        }

        public static final FlushRegisterWindows BUILTIN = new FlushRegisterWindows();
    }

    public static class IntToFloat extends SpecialBuiltin {

        public IntToFloat() {
            super(UnsafeLoophole.class);
        }

        @Override
        public <IR_Type> void acceptVisitor(BuiltinVisitor<IR_Type> visitor, IR_Type result, IR_Type[] arguments) {
            assert arguments.length == 1;
            visitor.visitIntToFloat(this, result, arguments);
        }

        public static final IntToFloat BUILTIN = new IntToFloat();
    }

    public static class FloatToInt extends SpecialBuiltin {

        public FloatToInt() {
            super(UnsafeLoophole.class);
        }

        @Override
        public <IR_Type> void acceptVisitor(BuiltinVisitor<IR_Type> visitor, IR_Type result, IR_Type[] arguments) {
            assert arguments.length == 1;
            visitor.visitFloatToInt(this, result, arguments);
        }

        public static final FloatToInt BUILTIN = new FloatToInt();
    }

    public static class LongToDouble extends SpecialBuiltin {

        public LongToDouble() {
            super(UnsafeLoophole.class);
        }

        @Override
        public <IR_Type> void acceptVisitor(BuiltinVisitor<IR_Type> visitor, IR_Type result, IR_Type[] arguments) {
            assert arguments.length == 1;
            visitor.visitLongToDouble(this, result, arguments);
        }

        public static final LongToDouble BUILTIN = new LongToDouble();
    }

    public static class DoubleToLong extends SpecialBuiltin {

        public DoubleToLong() {
            super(UnsafeLoophole.class);
        }

        @Override
        public <IR_Type> void acceptVisitor(BuiltinVisitor<IR_Type> visitor, IR_Type result, IR_Type[] arguments) {
            assert arguments.length == 1;
            visitor.visitDoubleToLong(this, result, arguments);
        }

        public static final DoubleToLong BUILTIN = new DoubleToLong();
    }

    /**
     * Marks the current position in a {@link TargetMethod}.
     */
    @BUILTIN(builtinClass = SpecialBuiltin.Marker.class)
    public static native void mark();

    public static class Marker extends SpecialBuiltin {

        @Override
        public boolean hasSideEffects() {
            return false;
        }

        @Override
        public <IR_Type> void acceptVisitor(BuiltinVisitor<IR_Type> visitor, IR_Type result, IR_Type[] arguments) {
            assert arguments.length == 0;
            visitor.visitMarker(this, result, arguments);
        }

        public static final Marker BUILTIN = new Marker();
    }

}
