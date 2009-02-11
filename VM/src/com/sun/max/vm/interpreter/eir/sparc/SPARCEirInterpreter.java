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
package com.sun.max.vm.interpreter.eir.sparc;
import static com.sun.max.vm.interpreter.eir.sparc.SPARCEirCPU.FCCValue;
import static com.sun.max.vm.interpreter.eir.sparc.SPARCEirCPU.IntegerConditionFlag.*;

import com.sun.max.asm.sparc.*;
import com.sun.max.program.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.compiler.eir.*;
import com.sun.max.vm.compiler.eir.sparc.*;
import com.sun.max.vm.compiler.eir.sparc.SPARCEirInstruction.*;
import com.sun.max.vm.interpreter.eir.*;
import com.sun.max.vm.interpreter.eir.EirCPU.*;
import com.sun.max.vm.type.*;
import com.sun.max.vm.value.*;

/**
 *  An interpreter for SPARC EIR representations of methods.
 *
 *
 * @author Laurent Daynes
 */
public class SPARCEirInterpreter extends EirInterpreter implements SPARCEirInstructionVisitor {

    class SPARCEirFrame extends EirFrame {
        final SPARCEirCPU.RegisterWindow _registerWindow;

        SPARCEirFrame(EirFrame caller, EirMethod method) {
            super(caller, method);
            _registerWindow = new SPARCEirCPU.RegisterWindow();
        }

        SPARCEirCPU.RegisterWindow registerWindow() {
            return _registerWindow;
        }
    }

    class SPARCInitialEirFrame extends SPARCEirFrame {
        SPARCInitialEirFrame() {
            super(null, null);
        }

        @Override
        public EirABI abi() {
            return  eirGenerator().eirABIsScheme().nativeABI();
        }
    }

    @Override
    protected EirFrame initialEirFrame() {
        return new SPARCInitialEirFrame();
    }

    private SPARCEirCPU _cpu;

    @Override
    protected SPARCEirCPU cpu() {
        return _cpu;
    }

    public SPARCEirInterpreter(EirGenerator eirGenerator) {
        super(eirGenerator);
        _cpu = new SPARCEirCPU(this);
    }

    @Override
    protected EirLocation [] argumentLocations(EirMethod eirMethod) {
        if (!cpu().usesRegisterWindow()) {
            return eirMethod.parameterLocations();
        }

        final EirLocation [] argumentLocations = new EirLocation[eirMethod.parameterLocations().length];
        final int i0 = SPARCEirRegister.GeneralPurpose.I0.ordinal();
        int index = 0;
        for (EirLocation location : eirMethod.parameterLocations()) {
            if (location instanceof SPARCEirRegister.GeneralPurpose) {
                final SPARCEirRegister.GeneralPurpose inRegister = (SPARCEirRegister.GeneralPurpose) location;
                argumentLocations[index] = SPARCEirRegister.GeneralPurpose.OUT_REGISTERS.get(inRegister.ordinal() - i0);
            } else {
                argumentLocations[index] = location;
            }
            index++;
        }

        return argumentLocations;
    }


    /**
     * Returns the location of the receiver at the caller for the specified method.
     * Always O0 on SPARC.
     *
     * @param eirMethod
     * @return
     */
    @Override
    protected EirLocation receiverLocation(EirMethod eirMethod) {
        return  SPARCEirRegister.GeneralPurpose.O0;
    }

    @Override
    protected EirLocation returnedResultLocation(EirMethod eirMethod) {
        if (eirMethod.resultLocation() == SPARCEirRegister.GeneralPurpose.I0) {
            return SPARCEirRegister.GeneralPurpose.O0;
        }
        return eirMethod.resultLocation();
    }

    @Override
    protected InstructionAddress callAndLink(EirMethod eirMethod) {
        final InstructionAddress returnAddress = cpu().nextInstructionAddress();
        cpu().writeRegister(SPARCEirRegister.GeneralPurpose.O7, ReferenceValue.from(returnAddress));
        return returnAddress;
    }

    @Override
    public void visit(EirPrologue instruction) {
        if (!instruction.eirMethod().isTemplate()) {
            if (cpu().usesRegisterWindow()) {
                final int frameSize = instruction.eirMethod().frameSize();
                final Address fp = cpu().readStackPointer();
                final Address sp = fp.minus(frameSize);
                // Stack must be 16 byte align. FIXME: this is sparc v9
                assert sp.aligned(16).equals(sp);
                final SPARCEirFrame currentFrame = (SPARCEirFrame) frame();
                currentFrame.registerWindow().save(cpu());
                pushFrame(new SPARCEirFrame(currentFrame, instruction.eirMethod()));
                cpu().writeStackPointer(sp);
                assert cpu().readFramePointer().equals(fp);
            } else {
                Problem.unimplemented();
            }
        }
    }

    @Override
    public void visit(EirEpilogue instruction) {
    }

    @Override
    public void visit(EirAssignment assignment) {
        switch (assignment.kind().asEnum()) {
            case INT: {
                final int value = _cpu.readInt(assignment.sourceOperand().location());
                _cpu.writeInt(assignment.destinationOperand().location(), value);
                break;
            }
            case LONG:
            case WORD:
            case REFERENCE: {
                final Value value = _cpu.read(assignment.sourceOperand().location());
                _cpu.write(assignment.destinationOperand().location(), adjustToAssignmentType(value));
                break;
            }
            case FLOAT: {
                final float value = _cpu.readFloat(assignment.sourceOperand().location());
                _cpu.writeFloat(assignment.destinationOperand().location(), value);
                break;
            }
            case DOUBLE: {
                final double value = _cpu.readDouble(assignment.sourceOperand().location());
                _cpu.writeDouble(assignment.destinationOperand().location(), value);
                break;
            }
            default: {
                ProgramError.unknownCase();
                break;
            }
        }
    }

    /**
     * Helper method for load conversion.
     * SPARC doesn't have floating-point to integer register (and vice-versa) moving instructions.
     * Instead, one must transit via memory and performs the integer/floating-point conversion in floating-point register.
     * The compiler uses temporary storage on the stack for moving data. This cannot be emulated in the EIR interpreter without some explicit
     * type conversion when loading from the stack. The conversion is only between types of equals width.
     *
     * @param value
     * @param loadKind
     * @return
     */
    private static Value toLoadKind(Value value, Kind loadKind) {
        if (loadKind == value.kind()) {
            return value;
        }
        switch (loadKind.asEnum()) {
            case INT: {
                if (value.kind() == Kind.FLOAT) {
                    return IntValue.from((int) value.asFloat());
                }
                break;
            }
            case LONG: {
                if (value.kind() == Kind.DOUBLE) {
                    return LongValue.from((long) value.asDouble());
                }
                break;
            }
            case FLOAT: {
                if (value.kind() == Kind.INT) {
                    return FloatValue.from(value.asInt());
                }
                break;
            }
            case DOUBLE: {
                if (value.kind() == Kind.LONG) {
                    return DoubleValue.from(value.asLong());
                }
                break;
            }
            case BYTE:
            case SHORT:
            case BOOLEAN:
            case CHAR:
            case WORD:
            case REFERENCE:
            case VOID: {
                break;
            }
        }
        ProgramError.unexpected();
        return null;
    }

    @Override
    public void visit(SPARCEirLoad load) {
        final Value value;
        final Value pointer = _cpu.read(load.pointerOperand().location());
        if (pointer.kind() == Kind.WORD) {
            // This must be a load from the stack
            assert load.indexOperand() == null;
            final int offset = load.offsetOperand() != null ? _cpu.read(load.offsetOperand().location()).asInt() : 0;
            value = toLoadKind(_cpu.stack().read(pointer.asWord().asAddress().plus(offset)), load.kind());
        } else {
            final Value[] arguments = (load.indexOperand() != null) ? new Value[3] : new Value[2];
            arguments[0] = pointer;
            if (load.offsetOperand() != null) {
                arguments[1] = IntValue.from(_cpu.readInt(load.offsetOperand().location()));
            } else {
                arguments[1] = IntValue.from(0);
            }
            if (load.indexOperand() != null) {
                arguments[2] = IntValue.from(_cpu.readInt(load.indexOperand().location()));
            }
            value = pointerLoad(load.kind(), arguments);
        }
        switch (load.kind().asEnum()) {
            case BYTE: {
                _cpu.writeWord(load.destinationLocation(), Address.fromInt(value.toInt()));
                break;
            }
            case SHORT: {
                _cpu.writeWord(load.destinationLocation(), Address.fromInt(value.unsignedToShort()));
                break;
            }
            case BOOLEAN:
            case CHAR:
            case INT: {
                _cpu.writeWord(load.destinationLocation(), Address.fromInt(value.unsignedToInt()));
                break;
            }
            case LONG: {
                _cpu.writeLong(load.destinationLocation(), value.asLong());
                break;
            }
            case FLOAT: {
                _cpu.writeFloat(load.destinationLocation(), value.asFloat());
                break;
            }
            case DOUBLE: {
                _cpu.writeDouble(load.destinationLocation(), value.asDouble());
                break;
            }
            case WORD:
            case REFERENCE: {
                _cpu.write(load.destinationLocation(), value);
                break;
            }
            case VOID: {
                ProgramError.unexpected();
            }
        }
    }

    @Override
    public void visit(SPARCEirStore store) {
        final Value pointer = _cpu.read(store.pointerOperand().location());
        final Value value = _cpu.read(store.kind(), store.valueOperand().location());
        if (pointer.kind() == Kind.WORD) {
            // This must be a store to the stack
            assert store.indexOperand() == null;
            final int offset = store.offsetOperand() != null ? _cpu.read(store.offsetOperand().location()).asInt() : 0;
            _cpu.stack().write(pointer.asWord().asAddress().plus(offset), value);
        } else {
            final Value[] arguments = (store.indexOperand() != null) ? new Value[4] : new Value[3];
            arguments[0] = pointer;
            if (store.offsetOperand() != null) {
                arguments[1] = IntValue.from(_cpu.readInt(store.offsetOperand().location()));
            } else {
                arguments[1] = IntValue.from(0);
            }
            if (store.indexOperand() != null) {
                arguments[2] = IntValue.from(_cpu.readInt(store.indexOperand().location()));
                arguments[3] = value;
            } else {
                arguments[2] = value;
            }
            pointerStore(store.kind(), arguments);
        }
    }

    @Override
    public void visit(SPARCEirCompareAndSwap instruction) {
        Problem.unimplemented();
    }

    @Override
    public void visit(ADD_I32 instruction) {
        final int a = _cpu.readInt(instruction.leftLocation());
        final int b = _cpu.readInt(instruction.rightLocation());
        _cpu.writeInt(instruction.destinationLocation(), a + b);
    }

    @Override
    public void visit(ADD_I64 instruction) {
        final long a = _cpu.readLong(instruction.leftLocation());
        final long b = _cpu.readLong(instruction.rightLocation());
        _cpu.writeLong(instruction.destinationLocation(), a + b);
    }

    @Override
    public void visit(AND_I32 instruction) {
        final int a = _cpu.readInt(instruction.leftLocation());
        final int b = _cpu.readInt(instruction.rightLocation());
        _cpu.writeInt(instruction.destinationLocation(), a & b);
    }

    @Override
    public void visit(AND_I64 instruction) {
        final long a = _cpu.readLong(instruction.leftLocation());
        final long b = _cpu.readLong(instruction.rightLocation());
        _cpu.writeLong(instruction.destinationLocation(), a & b);
    }

    private boolean testE(ICCOperand cc) {
        return _cpu.test(Z, cc);
    }
    private boolean testNE(ICCOperand cc) {
        return !_cpu.test(Z, cc);
    }

    private boolean testL(ICCOperand cc) {
        return  _cpu.test(N, cc) ^ _cpu.test(V, cc);
    }

    private boolean testLE(ICCOperand cc) {
        return testE(cc)  || testL(cc);
    }

    private boolean testGE(ICCOperand cc) {
        return !testL(cc);
    }

    private boolean testG(ICCOperand cc) {
        return !testLE(cc);
    }

    private boolean testLU(ICCOperand cc) {
        return _cpu.test(C, cc);
    }

    private boolean testLEU(ICCOperand cc) {
        return _cpu.test(C, cc) || _cpu.test(Z, cc);
    }

    private boolean testGEU(ICCOperand cc) {
        return !_cpu.test(C, cc);
    }

    private boolean testGU(ICCOperand cc) {
        return !testLEU(cc);
    }

    private boolean testE(FCCOperand fcc) {
        return _cpu.get(fcc) == FCCValue.E;
    }

    private boolean testNE(FCCOperand fcc) {
        final FCCValue value = _cpu.get(fcc);
        return value == FCCValue.L ||  value == FCCValue.G ||  value == FCCValue.U;
    }

    private boolean testG(FCCOperand fcc) {
        return _cpu.get(fcc) == FCCValue.G;
    }

    private boolean testGE(FCCOperand fcc) {
        final FCCValue value = _cpu.get(fcc);
        return value == FCCValue.G ||  value == FCCValue.E;
    }

    private boolean testL(FCCOperand fcc) {
        return _cpu.get(fcc) == FCCValue.L;
    }

    private boolean testLE(FCCOperand fcc) {
        final FCCValue value = _cpu.get(fcc);
        return value == FCCValue.L ||  value == FCCValue.E;
    }

    @Override
    public void visit(MOVNE instruction) {
        final ConditionCodeRegister cc = instruction.testedConditionCode();

        if (!testNE((ICCOperand) cc)) {
            return;
        }

        final int i = _cpu.readInt(instruction.sourceLocation());
        _cpu.writeInt(instruction.destinationLocation(), i);
    }

    @Override
    public void visit(MOVFNE instruction) {
        final ConditionCodeRegister cc = instruction.testedConditionCode();
        assert cc instanceof FCCOperand;
        if (!testNE((FCCOperand) cc)) {
            return;

        }
        final int i = _cpu.readInt(instruction.sourceLocation());
        _cpu.writeInt(instruction.destinationLocation(), i);
    }


    @Override
    public void visit(MOVE instruction) {
        final ConditionCodeRegister cc = instruction.testedConditionCode();
        if (!testE((ICCOperand) cc)) {
            return;
        }

        final int i = _cpu.readInt(instruction.sourceLocation());
        _cpu.writeInt(instruction.destinationLocation(), i);
    }

    @Override
    public void visit(MOVFE instruction) {
        final ConditionCodeRegister cc = instruction.testedConditionCode();
        assert cc instanceof FCCOperand;
        if (!testE((FCCOperand) cc)) {
            return;
        }

        final int i = _cpu.readInt(instruction.sourceLocation());
        _cpu.writeInt(instruction.destinationLocation(), i);
        // TODO Auto-generated method stub
    }

    @Override
    public void visit(MOVG instruction) {
        final ConditionCodeRegister cc = instruction.testedConditionCode();
        if (!testG((ICCOperand) cc)) {
            return;
        }
        final int i = _cpu.readInt(instruction.sourceLocation());
        _cpu.writeInt(instruction.destinationLocation(), i);
    }

    @Override
    public void visit(MOVCC instruction) {
        final ConditionCodeRegister cc = instruction.testedConditionCode();
        //test for unsigned result
        if (!testGEU((ICCOperand) cc)) {
            return;
        }
        final int i = _cpu.readInt(instruction.sourceLocation());
        _cpu.writeInt(instruction.destinationLocation(), i);
    }

    @Override
    public void visit(MOVGU instruction) {
        final ConditionCodeRegister cc = instruction.testedConditionCode();
        //test for unsigned result
        if (!testGU((ICCOperand) cc)) {
            return;
        }
        final int i = _cpu.readInt(instruction.sourceLocation());
        _cpu.writeInt(instruction.destinationLocation(), i);



    }

    @Override
    public void visit(MOVL instruction) {
        final ConditionCodeRegister cc = instruction.testedConditionCode();
        if (!testL((ICCOperand) cc)) {
            return;
        }

        final int i = _cpu.readInt(instruction.sourceLocation());
        _cpu.writeInt(instruction.destinationLocation(), i);
    }


    @Override
    public void visit(MOVFG instruction) {
        final ConditionCodeRegister cc = instruction.testedConditionCode();
        assert cc instanceof FCCOperand;
        if (!testG((FCCOperand) cc)) {
            return;
        }
        final int i = _cpu.readInt(instruction.sourceLocation());
        _cpu.writeInt(instruction.destinationLocation(), i);
    }

    @Override
    public void visit(MOVCS instruction) {
        final ConditionCodeRegister cc = instruction.testedConditionCode();
        //tests for unsigned result
        if (!testLU((ICCOperand) cc)) {
            return;
        }
        final int i = _cpu.readInt(instruction.sourceLocation());
        _cpu.writeInt(instruction.destinationLocation(), i);

    }

    @Override
    public void visit(MOVLEU instruction) {
        final ConditionCodeRegister cc = instruction.testedConditionCode();
        if (!testLEU((ICCOperand) cc)) {
            return;
        }
        final int i = _cpu.readInt(instruction.sourceLocation());
        _cpu.writeInt(instruction.destinationLocation(), i);

    }



    @Override
    public void visit(MOVFL instruction) {
        final ConditionCodeRegister cc = instruction.testedConditionCode();
        assert cc instanceof FCCOperand;
        if (!testL((FCCOperand) cc)) {
            return;
        }
        final int i = _cpu.readInt(instruction.sourceLocation());
        _cpu.writeInt(instruction.destinationLocation(), i);

    }

    @Override
    public void visit(CMP_I32 instruction) {

        final int a = _cpu.readInt(instruction.leftLocation());
        final int b = _cpu.readInt(instruction.rightLocation());

        _cpu.set(C, ICCOperand.ICC, false);
        _cpu.set(Z, ICCOperand.ICC, false);
        _cpu.set(V, ICCOperand.ICC, false);
        _cpu.set(N, ICCOperand.ICC, false);

        final int result = a - b;
        _cpu.set(Z, ICCOperand.ICC, a == b);
        _cpu.set(N, ICCOperand.ICC, result < 0);
        _cpu.set(C, ICCOperand.ICC, Address.fromInt(a).lessThan(Address.fromInt(b)));

        final boolean operandsDifferInSign = (a >= 0 && b < 0) || (a < 0 && b >= 0);
        final boolean firstOperandDiffersInSignFromResult = (a >= 0 && result < 0) || (a < 0 && result > 0);
        _cpu.set(V, ICCOperand.ICC, operandsDifferInSign && firstOperandDiffersInSignFromResult);
    }

    @Override
    public void visit(CMP_I64 instruction) {
        Value valueA = _cpu.read(instruction.leftLocation());
        Value valueB = _cpu.read(instruction.rightLocation());
        final long a;
        final long b;
        if (valueA.kind() == Kind.REFERENCE || valueB.kind() == Kind.REFERENCE) {
            if (valueA.kind() != Kind.REFERENCE) {
                ProgramError.check(valueA.toLong() == 0L);
                valueA = ReferenceValue.NULL;
            }
            if (valueB.kind() != Kind.REFERENCE) {
                ProgramError.check(valueB.toLong() == 0L);
                valueB = ReferenceValue.NULL;
            }
            // Use arbitrary value for reference a.
            a = valueA.hashCode();

            if (valueA.asObject() == valueB.asObject()) {
                b = a;
            } else {
                b = ~a;
            }
        } else {
            a = valueA.toLong();
            b = valueB.toLong();
        }
        _cpu.set(Z, ICCOperand.XCC, a == b);
        _cpu.set(V, ICCOperand.XCC, ((a < 0) && (b >= 0) && (a - b >= 0)) || ((a >= 0) && (b < 0) && (a - b < 0)));
        _cpu.set(C, ICCOperand.XCC, Address.fromLong(a).lessThan(Address.fromLong(b)));
        _cpu.set(N, ICCOperand.XCC, (a - b) < 0);
    }

    @Override
    public void visit(DIV_I32 instruction) {
        final int a = _cpu.readInt(instruction.leftLocation());
        final int b = _cpu.readInt(instruction.rightLocation());
        _cpu.writeInt(instruction.destinationLocation(), a / b);
    }

    @Override
    public void visit(DIV_I64 instruction) {
        final long a = _cpu.readLong(instruction.leftLocation());
        final long b = _cpu.readLong(instruction.rightLocation());
        _cpu.writeLong(instruction.destinationLocation(), a / b);
    }

    @Override
    public void visit(BA instruction) {
        _cpu.gotoBlock(instruction.target());
    }

    private void conditionalBranch(SPARCEirConditionalBranch instruction, boolean condition) {
        if (condition) {
            _cpu.gotoBlock(instruction.target());
        } else {
            _cpu.gotoBlock(instruction.next());
        }
    }

    @Override
    public void visit(BRZ instruction) {
        conditionalBranch(instruction, _cpu.read(instruction.testedOperandLocation()).isZero());
    }

    @Override
    public void visit(BRNZ instruction) {
        conditionalBranch(instruction, !_cpu.read(instruction.testedOperandLocation()).isZero());
    }

    @Override
    public void visit(BRLZ instruction) {
        conditionalBranch(instruction, _cpu.read(instruction.testedOperandLocation()).asLong() < 0);
    }

    @Override
    public void visit(BRLEZ instruction) {
        conditionalBranch(instruction, _cpu.read(instruction.testedOperandLocation()).asLong() <= 0);
    }

    @Override
    public void visit(BRGEZ instruction) {
        conditionalBranch(instruction, _cpu.read(instruction.testedOperandLocation()).asLong() >= 0);
    }

    @Override
    public void visit(BRGZ instruction) {
        conditionalBranch(instruction, _cpu.read(instruction.testedOperandLocation()).asLong() > 0);
    }

    @Override
    public void visit(BE instruction) {
        conditionalBranch(instruction, testE(instruction.conditionCode()));
    }

    @Override
    public void visit(BNE instruction) {
        conditionalBranch(instruction, testNE(instruction.conditionCode()));
    }

    @Override
    public void visit(BL instruction) {
        conditionalBranch(instruction, testL(instruction.conditionCode()));
    }

    @Override
    public void visit(BLE instruction) {
        conditionalBranch(instruction, testLE(instruction.conditionCode()));
    }

    @Override
    public void visit(BGE instruction) {
        conditionalBranch(instruction, testGE(instruction.conditionCode()));
    }

    @Override
    public void visit(BG instruction) {
        conditionalBranch(instruction, testG(instruction.conditionCode()));
    }

    @Override
    public void visit(BLU instruction) {
        conditionalBranch(instruction,  testLU(instruction.conditionCode()));
    }

    @Override
    public void visit(BLEU instruction) {
        conditionalBranch(instruction, testLEU(instruction.conditionCode()));
    }

    @Override
    public void visit(BGEU instruction) {
        conditionalBranch(instruction, testGEU(instruction.conditionCode()));
    }

    @Override
    public void visit(BGU instruction) {
        conditionalBranch(instruction, testGU(instruction.conditionCode()));
    }

    @Override
    public void visit(FLAT_RETURN instruction) {
        // TODO Auto-generated method stub

    }

    @Override
    public void visit(FADD_S instruction) {
        final float a = _cpu.readFloat(instruction.leftLocation());
        final float b = _cpu.readFloat(instruction.rightLocation());
        _cpu.writeFloat(instruction.destinationLocation(), a + b);
    }

    @Override
    public void visit(FADD_D instruction) {
        final double a = _cpu.readDouble(instruction.leftLocation());
        final double b = _cpu.readDouble(instruction.rightLocation());
        _cpu.writeDouble(instruction.destinationLocation(), a + b);
    }

    @Override
    public void visit(FCMP_S instruction) {
        final float a = _cpu.readFloat(instruction.leftLocation());
        final float b = _cpu.readFloat(instruction.rightLocation());
        final FCCOperand fcc = instruction.selectedConditionCode();
        if (a == b) {
            _cpu.set(fcc, FCCValue.E);
        } else if (a < b) {
            _cpu.set(fcc, FCCValue.L);
        } else if (a > b) {
            _cpu.set(fcc, FCCValue.G);
        } else {
            assert Float.isNaN(a) || Float.isNaN(b);
            _cpu.set(fcc, FCCValue.U);
        }
    }

    @Override
    public void visit(FCMP_D instruction) {
        final double a = _cpu.readDouble(instruction.leftLocation());
        final double b = _cpu.readDouble(instruction.rightLocation());
        final FCCOperand fcc = instruction.selectedConditionCode();
        if (a == b) {
            _cpu.set(fcc, FCCValue.E);
        } else if (a < b) {
            _cpu.set(fcc, FCCValue.L);
        } else if (a > b) {
            _cpu.set(fcc, FCCValue.G);
        } else {
            assert Double.isNaN(a) || Double.isNaN(b);
            _cpu.set(fcc, FCCValue.U);
        }
    }

    @Override
    public void visit(FDIV_S instruction) {
        final float a = _cpu.readFloat(instruction.leftLocation());
        final float b = _cpu.readFloat(instruction.rightLocation());
        _cpu.writeFloat(instruction.destinationLocation(), a / b);
    }

    @Override
    public void visit(FDIV_D instruction) {
        final double a = _cpu.readDouble(instruction.leftLocation());
        final double b = _cpu.readDouble(instruction.rightLocation());
        _cpu.writeDouble(instruction.destinationLocation(), a / b);
    }

    @Override
    public void visit(FMUL_S instruction) {
        final float a = _cpu.readFloat(instruction.leftLocation());
        final float b = _cpu.readFloat(instruction.rightLocation());
        _cpu.writeFloat(instruction.destinationLocation(), a * b);
    }

    @Override
    public void visit(FMUL_D instruction) {
        final double a = _cpu.readDouble(instruction.leftLocation());
        final double b = _cpu.readDouble(instruction.rightLocation());
        _cpu.writeDouble(instruction.destinationLocation(), a * b);
    }

    @Override
    public void visit(FNEG_S instruction) {
        final float a = _cpu.readFloat(instruction.operandLocation());
        _cpu.writeFloat(instruction.operandLocation(), -a);

    }

    @Override
    public void visit(FNEG_D instruction) {
        final double a = _cpu.readDouble(instruction.operandLocation());
        _cpu.writeDouble(instruction.operandLocation(), -a);
    }

    @Override
    public void visit(FSUB_S instruction) {
        final float a = _cpu.readFloat(instruction.leftLocation());
        final float b = _cpu.readFloat(instruction.rightLocation());
        _cpu.writeFloat(instruction.destinationLocation(), a - b);
    }

    @Override
    public void visit(FSUB_D instruction) {
        final double a = _cpu.readDouble(instruction.leftLocation());
        final double b = _cpu.readDouble(instruction.rightLocation());
        _cpu.writeDouble(instruction.destinationLocation(), a - b);
    }

    @Override
    public void visit(FSTOD instruction) {
        final float f = _cpu.readFloat(instruction.sourceLocation());
        final double d = f;
        _cpu.writeDouble(instruction.destinationLocation(), d);
    }

    @Override
    public void visit(FDTOS instruction) {
        final double d = _cpu.readDouble(instruction.sourceLocation());
        final float f = (float) d;
        _cpu.writeFloat(instruction.destinationLocation(), f);
    }

    @Override
    public void visit(FITOS instruction) {
        final int i = _cpu.readInt(instruction.sourceLocation());
        final float f = i;
        _cpu.writeFloat(instruction.destinationLocation(), f);
    }

    @Override
    public void visit(FITOD instruction) {
        final int i = _cpu.readInt(instruction.sourceLocation());
        final double d = i;
        _cpu.writeDouble(instruction.destinationLocation(), d);
    }

    @Override
    public void visit(FXTOS instruction) {
        final long l = _cpu.readLong(instruction.sourceLocation());
        final float f = l;
        _cpu.writeFloat(instruction.destinationLocation(), f);
    }

    @Override
    public void visit(FXTOD instruction) {
        final long l = _cpu.readLong(instruction.sourceLocation());
        final double d = l;
        _cpu.writeDouble(instruction.destinationLocation(), d);
    }

    public void visit(FSTOI instruction) {
        // Float-value is converted into an integer value and stored in a float register.
        final float f = _cpu.readFloat(instruction.sourceLocation());
        final int i = (int) f;
        _cpu.writeFloat(instruction.destinationLocation(), i);
    }

    public void visit(FDTOI instruction) {
        // Double-value is converted into an integer value and stored in a float register.
        final double d = _cpu.readDouble(instruction.sourceLocation());
        final int i = (int) d;
        _cpu.writeFloat(instruction.destinationLocation(), i);
    }

    public void visit(FSTOX instruction) {
        // Float-value is converted first into an long value and stored in a double-precision float register.
        final float f = _cpu.readFloat(instruction.sourceLocation());
        final long l = (long) f;
        _cpu.writeDouble(instruction.destinationLocation(), l);
    }

    public void visit(FDTOX instruction) {
        // Double-value is converted first into an long value and stored in a double-precision float register.
        final double d = _cpu.readDouble(instruction.sourceLocation());
        final long l = (long) d;
        _cpu.writeDouble(instruction.destinationLocation(), l);
    }

    @Override
    public void visit(FLUSHW instruction) {
    }

    @Override
    public void visit(JMP_indirect instruction) {
        ProgramError.unexpected("indirect jump not implemented at EIR level - it should only occur during exception dispatching at target level");
    }

    @Override
    public void visit(MEMBAR instruction) {
    }

    @Override
    public void visit(SET_STACK_ADDRESS instruction) {
        final int sourceOffset = _cpu.offset(instruction.sourceOperand().location().asStackSlot());
        _cpu.write(instruction.destinationOperand().location(), new WordValue(_cpu.readFramePointer().plus(sourceOffset)));
    }

    @Override
    public void visit(MOV_I32 instruction) {
        final int a = _cpu.readInt(instruction.sourceLocation());
        _cpu.writeInt(instruction.destinationLocation(), a);
    }

    @Override
    public void visit(MOV_I64 instruction) {
        final long a = _cpu.readInt(instruction.sourceLocation());
        _cpu.writeLong(instruction.destinationLocation(), a);
    }

    @Override
    public void visit(MUL_I32 instruction) {
        final int a = _cpu.readInt(instruction.leftLocation());
        final int b = _cpu.readInt(instruction.rightLocation());
        _cpu.writeInt(instruction.destinationLocation(), a * b);
    }

    @Override
    public void visit(MUL_I64 instruction) {
        final long a = _cpu.readLong(instruction.leftLocation());
        final long b = _cpu.readLong(instruction.rightLocation());
        _cpu.writeLong(instruction.destinationLocation(), a * b);
    }

    @Override
    public void visit(NEG_I32 instruction) {
        final int a = _cpu.readInt(instruction.operandLocation());
        _cpu.writeInt(instruction.operandLocation(), -a);
    }

    @Override
    public void visit(NEG_I64 instruction) {
        final long a = _cpu.readLong(instruction.operandLocation());
        _cpu.writeLong(instruction.operandLocation(), -a);
    }

    @Override
    public void visit(NOT_I32 instruction) {
        final int operand = _cpu.readInt(instruction.operand().location());
        _cpu.writeInt(instruction.operand().location(), ~operand);
    }

    @Override
    public void visit(NOT_I64 instruction) {
        final long operand = _cpu.readLong(instruction.operand().location());
        _cpu.writeLong(instruction.operand().location(), ~operand);
    }

    @Override
    public void visit(OR_I32 instruction) {
        final int a = _cpu.readInt(instruction.leftLocation());
        final int b = _cpu.readInt(instruction.rightLocation());
        _cpu.writeInt(instruction.destinationLocation(), a | b);
    }

    @Override
    public void visit(OR_I64 instruction) {
        final long a = _cpu.readLong(instruction.leftLocation());
        final long b = _cpu.readLong(instruction.rightLocation());
        _cpu.writeLong(instruction.destinationLocation(), a | b);
    }

    @Override
    public void visit(RDPC instruction) {
        _cpu.write(instruction.operand().location(), new WordValue(Address.fromLong(_cpu.currentInstructionAddress().index())));
    }

    @Override
    public void visit(RET instruction) {
        if (cpu().usesRegisterWindow()) {
            ret((InstructionAddress) cpu().read(SPARCEirRegister.GeneralPurpose.I7).asObject());
            popFrame();
            final SPARCEirFrame currentFrame = (SPARCEirFrame) frame();
            currentFrame.registerWindow().restore(cpu());
        } else {
            Problem.unimplemented();
        }
    }

    @Override
    public void visit(SET_I32 instruction) {
        assert instruction.immediateOperand().isConstant();
        final int a = instruction.immediateOperand().value().asInt();
        _cpu.writeInt(instruction.operandLocation(), a);
    }

    @Override
    public void visit(SLL_I32 instruction) {
        final int number = _cpu.readInt(instruction.leftLocation());
        final int shift = _cpu.readByte(instruction.rightLocation());
        _cpu.writeInt(instruction.destinationLocation(), number << shift);
    }

    @Override
    public void visit(SLL_I64 instruction) {
        final long number = _cpu.readInt(instruction.leftLocation());
        final int shift = _cpu.readByte(instruction.rightLocation());
        _cpu.writeLong(instruction.destinationLocation(), number << shift);
    }

    @Override
    public void visit(SRA_I32 instruction) {
        final int number = _cpu.readInt(instruction.leftLocation());
        final int shift = _cpu.readByte(instruction.rightLocation());
        _cpu.writeInt(instruction.destinationLocation(), number >> shift);
    }

    @Override
    public void visit(SRA_I64 instruction) {
        final long number = _cpu.readInt(instruction.leftLocation());
        final int shift = _cpu.readByte(instruction.rightLocation());
        _cpu.writeLong(instruction.destinationLocation(), number >> shift);
    }

    @Override
    public void visit(SRL_I32 instruction) {
        final int number = _cpu.readInt(instruction.leftLocation());
        final int shift = _cpu.readByte(instruction.rightLocation());
        _cpu.writeInt(instruction.destinationLocation(), number >>> shift);
    }

    @Override
    public void visit(SRL_I64 instruction) {
        final long number = _cpu.readInt(instruction.leftLocation());
        final int shift = _cpu.readByte(instruction.rightLocation());
        _cpu.writeLong(instruction.destinationLocation(), number >>> shift);
    }

    @Override
    public void visit(SUB_I32 instruction) {
        final int a = _cpu.readInt(instruction.leftLocation());
        final int b = _cpu.readInt(instruction.rightLocation());
        _cpu.writeInt(instruction.destinationLocation(), a - b);
    }

    @Override
    public void visit(SUB_I64 instruction) {
        final long a = _cpu.readLong(instruction.leftLocation());
        final long b = _cpu.readLong(instruction.rightLocation());
        _cpu.writeLong(instruction.destinationLocation(), a - b);
    }

    @Override
    public void visit(SWITCH_I32 instruction) {
        final int a = _cpu.readInt(instruction.tag().location());

        for (int i = 0; i < instruction.matches().length; i++) {
            if (a == instruction.matches() [i].value().asInt()) {
                _cpu.gotoBlock(instruction.targets()[i]);
                return;
            }
        }

        _cpu.gotoBlock(instruction.defaultTarget());
    }

    @Override
    public void visit(XOR_I32 instruction) {
        final int a = _cpu.readInt(instruction.leftLocation());
        final int b = _cpu.readInt(instruction.rightLocation());
        _cpu.writeInt(instruction.destinationLocation(), a ^ b);

    }

    @Override
    public void visit(XOR_I64 instruction) {
        final long a = _cpu.readLong(instruction.leftLocation());
        final long b = _cpu.readLong(instruction.rightLocation());
        _cpu.writeLong(instruction.destinationLocation(), a ^ b);
    }

    @Override
    public void visit(ZERO instruction) {
        switch (instruction.kind().asEnum()) {
            case INT:
            case LONG:
            case WORD:
            case REFERENCE:
                _cpu.writeLong(instruction.operand().location(), 0L);
                break;
            case FLOAT:
                _cpu.writeFloat(instruction.operand().location(), 0F);
                break;
            case DOUBLE:
                _cpu.writeDouble(instruction.operand().location(), 0D);
                break;
            default:
                ProgramError.unknownCase();
                break;
        }
    }
}
