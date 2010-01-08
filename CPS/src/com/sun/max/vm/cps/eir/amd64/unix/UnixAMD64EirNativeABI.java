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
package com.sun.max.vm.cps.eir.amd64.unix;

import static com.sun.max.vm.runtime.VMRegister.Role.*;

import com.sun.max.annotate.*;
import com.sun.max.asm.amd64.*;
import com.sun.max.collect.*;
import com.sun.max.vm.*;
import com.sun.max.vm.compiler.*;
import com.sun.max.vm.compiler.target.*;
import com.sun.max.vm.cps.eir.amd64.*;
import com.sun.max.vm.jni.*;

/**
 * The native ABI used by the Solaris OS.
 *
 * @author Bernd Mathiske
 */
public class UnixAMD64EirNativeABI extends UnixAMD64EirCFunctionABI {

    @HOSTED_ONLY
    public UnixAMD64EirNativeABI(VMConfiguration vmConfiguration) {
        super(vmConfiguration, false);
        final TargetABI<AMD64GeneralRegister64, AMD64XMMRegister> originalTargetABI = super.targetABI();
        final RegisterRoleAssignment<AMD64GeneralRegister64, AMD64XMMRegister> registerRoleAssignment =
            new RegisterRoleAssignment<AMD64GeneralRegister64, AMD64XMMRegister>(
                            originalTargetABI.registerRoleAssignment(),
                            ABI_FRAME_POINTER,
                            originalTargetABI.registerRoleAssignment().integerRegisterActingAs(CPU_FRAME_POINTER));
        initTargetABI(new TargetABI<AMD64GeneralRegister64, AMD64XMMRegister>(originalTargetABI, registerRoleAssignment, CallEntryPoint.C_ENTRY_POINT));
    }

    /**
     * All the {@link #allocatableRegisters()} registers need to be saved across a call to a native function.
     * Even though the platform ABI may force a callee to save the any non-caller saved registers, the
     * GC has no way of knowing where they were save. As such, they need to be saved explicitly by the
     * {@linkplain NativeStubGenerator native stub} around the native function call. The reference map
     * generated by the compiler will inform the GC which of the saved registers contain references.
     */
    @Override
    public PoolSet<AMD64EirRegister> callerSavedRegisters() {
        return allocatableRegisters();
    }

}
