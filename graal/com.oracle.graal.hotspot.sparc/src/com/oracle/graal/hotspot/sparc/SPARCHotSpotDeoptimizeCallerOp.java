/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.graal.hotspot.sparc;

import static com.oracle.graal.sparc.SPARC.*;
import static com.oracle.graal.asm.sparc.SPARCMacroAssembler.*;
import static com.oracle.graal.hotspot.HotSpotBackend.*;
import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.sparc.*;
import com.oracle.graal.lir.asm.*;

/**
 * Removes the current frame and tail calls the uncommon trap routine.
 */
@Opcode("DEOPT_CALLER")
final class SPARCHotSpotDeoptimizeCallerOp extends SPARCHotSpotEpilogueOp {

    private final DeoptimizationAction action;
    private final DeoptimizationReason reason;

    SPARCHotSpotDeoptimizeCallerOp(DeoptimizationAction action, DeoptimizationReason reason) {
        this.action = action;
        this.reason = reason;
    }

    @Override
    public void emitCode(TargetMethodAssembler tasm, SPARCMacroAssembler masm) {
        leaveFrame(tasm);

// SPARCHotSpotBackend backend = (SPARCHotSpotBackend)
// HotSpotGraalRuntime.graalRuntime().getBackend();
// final boolean isStub = true;
// HotSpotFrameContext frameContext = backend.new HotSpotFrameContext(isStub);
// frameContext.enter(tasm);

        HotSpotGraalRuntime runtime = graalRuntime();
        Register thread = runtime.getRuntime().threadRegister();

        Register scratch = g5;
        new Mov(tasm.runtime.encodeDeoptActionAndReason(action, reason), scratch).emit(masm);
        new Stw(scratch, new SPARCAddress(thread, runtime.getConfig().pendingDeoptimizationOffset)).emit(masm);

        SPARCCall.indirectJmp(tasm, masm, scratch, tasm.runtime.lookupForeignCall(UNCOMMON_TRAP));

// frameContext.leave(tasm);
    }
}
