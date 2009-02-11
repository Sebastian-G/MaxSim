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
package com.sun.max.vm.monitor.modal.sync.nat;

import com.sun.max.annotate.*;
import com.sun.max.memory.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.*;
import com.sun.max.vm.runtime.*;

/**
 * Java wrapper for a native condition variable.
 *
 * @author Simon Wilkinson
 */
public final class ConditionVariable {

    @CONSTANT_WHEN_NOT_ZERO
    private Pointer _condition = Pointer.zero();

    private static int _size;

    static {
        new CriticalNativeMethod(ConditionVariable.class, "nativeConditionSize");
        new CriticalNativeMethod(ConditionVariable.class, "nativeConditionInitialize");
        new CriticalNativeMethod(ConditionVariable.class, "nativeConditionWait");
        new CriticalNativeMethod(ConditionVariable.class, "nativeConditionSize");
    }

    @C_FUNCTION
    private static native int nativeConditionSize();

    @C_FUNCTION
    private static native void nativeConditionInitialize(Pointer condition);

    @C_FUNCTION
    private static native boolean nativeConditionNotify(Pointer condition, boolean all);

    /**
     * This must be a fully informative JNI call, not a C_FUNCTION, because it can block and the stack walker needs to know the last Java frame.
     *
     * @return whether waiting succeeded, i.e. no error and no interrupt occurred
     */
    private static native boolean nativeConditionWait(Pointer mutex, Pointer condition, long timeoutMilliSeconds);

    public static void initialize() {
        assert MaxineVM.hostOrTarget().phase() == MaxineVM.Phase.PRIMORDIAL;
        _size = nativeConditionSize();
    }

    public ConditionVariable() {
    }

    /**
     * Performs native allocation for this <code>ConditionVariable</code>.
     */
    public void allocate() {
        _condition =  Memory.mustAllocate(_size);
        nativeConditionInitialize(_condition);
    }

    public boolean requiresAllocation() {
        return _condition.isZero();
    }

    /**
     * Causes the current thread to wait until this <code>ConditionVariable</code>
     * is signaled via {@link #threadNotify(boolean all) threadNotify()}.
     * If a timeout > 0 is specified, then the thread may return after this timeout
     * has elapsed without being signaled.
     *
     * The current thread must own the given mutex when calling this method, otherwise the
     * results are undefined. Before blocking, the current thread will release the mutex.
     * On return, the current thread is guaranteed to own the mutex.
     *
     * The thread may return early if its <code>java.lang.Thread</code> is interrupted via
     * {@link java.lang.Thread#interrupt() interrupt()} whilst blocking.
     * In this case, this method returns returns true. In all other cases it returns false.
     *
     * @param mutex the mutex on which to block
     * @param timeoutMilliSeconds the maximum time to block. No timeout is used if timeoutMilliSeconds == 0.
     * @return true if the current thread was interrupted whilst blocking; false otherwise
     */
    @INLINE
    public boolean threadWait(Mutex mutex, long timeoutMilliSeconds) {
        return nativeConditionWait(mutex.asPointer(), _condition, timeoutMilliSeconds);
    }

    /**
     * Causes one or all of the threads {@link #threadWait(Mutex mutex, long timeoutMilliSeconds) waiting} on this <code>ConditionVariable</code> to
     * wake-up.
     *
     * @param all notify all threads
     * @return true if an error occured in native code; false otherwise
     */
    @INLINE
    public boolean threadNotify(boolean all) {
        return nativeConditionNotify(_condition, all);
    }

    /**
     * Returns a pointer to this <code>ConditionVariable</code>'s native data structure.
     * @return
     */
    @INLINE
    public Pointer asPointer() {
        return _condition;
    }

    @Override
    public void finalize() throws Throwable {
        super.finalize();
        Memory.deallocate(_condition);
    }
}
