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
package com.sun.max.vm.monitor.modal.sync;

import com.sun.max.annotate.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.monitor.*;
import com.sun.max.vm.monitor.modal.modehandlers.inflated.*;
import com.sun.max.vm.monitor.modal.sync.JavaMonitorManager.*;
import com.sun.max.vm.object.*;
import com.sun.max.vm.reference.*;
import com.sun.max.vm.thread.*;

/**
 * Base class for <code>JavaMonitor</code>'s managed by <code>JavaMonitorManager</code>.
 *
 * @author Simon Wilkinson
 */
abstract class AbstractJavaMonitor implements ManagedMonitor {

    private Object _boundObject;
    protected volatile VmThread _ownerThread;
    protected int _recursionCount;
    private Word _displacedMiscWord;

    protected BindingProtection _bindingProtection;
    private Word _preGCLockWord;

    private static final FieldActor _displacedMiscWordFieldActor = FieldActor.findInstance(AbstractJavaMonitor.class, "_displacedMiscWord");

    // Support for direct linked lists of JavaMonitors.
    private ManagedMonitor _next;

    protected AbstractJavaMonitor() {
        _bindingProtection = BindingProtection.PRE_ACQUIRE;
    }

    public abstract void monitorEnter();

    public abstract void monitorExit();

    public abstract void monitorWait(long timeoutMilliSeconds) throws InterruptedException;

    public abstract void monitorNotify(boolean all);

    public abstract void monitorPrivateAcquire(VmThread owner, int lockQty);

    public abstract void monitorPrivateRelease();

    public abstract void allocate();

    public final boolean isOwnedBy(VmThread thread) {
        return _ownerThread == thread;
    }

    public final Word displacedMisc() {
        return _displacedMiscWord;
    }

    public final void setDisplacedMisc(Word lockWord) {
        _displacedMiscWord = lockWord;
    }

    public final Word compareAndSwapDisplacedMisc(Word suspectedValue, Word newValue) {
        return Reference.fromJava(this).compareAndSwapWord(_displacedMiscWordFieldActor.offset(), suspectedValue, newValue);
    }

    public void reset() {
        _boundObject = null;
        _ownerThread = null;
        _recursionCount = 0;
        _displacedMiscWord = Word.zero();
        _preGCLockWord = Word.zero();
        _bindingProtection = BindingProtection.PRE_ACQUIRE;
    }

    public final void setBoundObject(Object object) {
        _boundObject = object;
    }

    public final Object boundObject() {
        return _boundObject;
    }

    public final boolean isBound() {
        return _boundObject != null;
    }

    public final boolean isHardBound() {
        return isBound() && ObjectAccess.readMisc(_boundObject).equals(InflatedMonitorLockWord64.boundFromMonitor(this));
    }

    public final void preGCPrepare() {
        _preGCLockWord = InflatedMonitorLockWord64.boundFromMonitor(this);
    }

    public final boolean requiresPostGCRefresh() {
        return isBound() && ObjectAccess.readMisc(_boundObject).equals(_preGCLockWord);
    }

    public final void refreshBoundObject() {
        ObjectAccess.writeMisc(_boundObject, InflatedMonitorLockWord64.boundFromMonitor(this));
    }

    public final BindingProtection bindingProtection() {
        return _bindingProtection;
    }

    public final void setBindingProtection(BindingProtection deflationState) {
        _bindingProtection = deflationState;
    }

    @INLINE
    public final ManagedMonitor next() {
        return _next;
    }

    @INLINE
    public final void setNext(ManagedMonitor next) {
        _next = next;
    }

    public void dump() {
        Log.print(ObjectAccess.readClassActor(this).name().string());
        Log.print(" boundTo=");
        Log.print(boundObject() == null ? "null" : ObjectAccess.readClassActor(boundObject()).name().string());
        Log.print(" owner=");
        Log.print(_ownerThread == null ? "null" : _ownerThread.getName());
        Log.print(" recursion=");
        Log.print(_recursionCount);
        Log.print(" binding=");
        Log.print(_bindingProtection.name());
    }

    protected void traceStartMonitorEnter(final VmThread currentThread) {
        if (Monitor.traceMonitors()) {
            final boolean lockDisabledSafepoints = Log.lock();
            Log.print("Acquiring monitor for ");
            Log.print(currentThread.getName());
            Log.print(" [");
            dump();
            Log.println("]");
            Log.unlock(lockDisabledSafepoints);
        }
    }

    protected void traceEndMonitorEnter(final VmThread currentThread) {
        if (Monitor.traceMonitors()) {
            final boolean lockDisabledSafepoints = Log.lock();
            Log.print("Acquired monitor for ");
            Log.print(currentThread.getName());
            Log.print(" [");
            dump();
            Log.println("]");
            Log.unlock(lockDisabledSafepoints);
        }
    }

    protected void traceStartMonitorExit(final VmThread currentThread) {
        if (Monitor.traceMonitors()) {
            final boolean lockDisabledSafepoints = Log.lock();
            Log.print("Releasing monitor for ");
            Log.print(currentThread.getName());
            Log.print(" [");
            dump();
            Log.println("]");
            Log.unlock(lockDisabledSafepoints);
        }
    }

    protected void traceEndMonitorExit(final VmThread currentThread) {
        if (Monitor.traceMonitors()) {
            final boolean lockDisabledSafepoints = Log.lock();
            Log.print("Released monitor for ");
            Log.print(currentThread.getName());
            Log.print(" [");
            dump();
            Log.println("]");
            Log.unlock(lockDisabledSafepoints);
        }
    }

    protected void traceStartMonitorWait(final VmThread currentThread) {
        if (Monitor.traceMonitors()) {
            final boolean lockDisabledSafepoints = Log.lock();
            Log.print("Start wait on monitor for ");
            Log.print(currentThread.getName());
            Log.print(" [");
            dump();
            Log.println("]");
            Log.unlock(lockDisabledSafepoints);
        }
    }

    protected void traceEndMonitorWait(final VmThread currentThread, final boolean interrupted, boolean timedOut) {
        if (Monitor.traceMonitors()) {
            final boolean lockDisabledSafepoints = Log.lock();
            Log.print("End wait on monitor for ");
            Log.print(currentThread.getName());
            Log.print(" [");
            dump();
            if (interrupted) {
                Log.print(" *interrupted*");
            } else if (timedOut) {
                Log.print(" *timed-out*");
            }
            Log.println("]");
            Log.unlock(lockDisabledSafepoints);
        }
    }

    protected void traceEndMonitorNotify(final VmThread currentThread) {
        if (Monitor.traceMonitors()) {
            final boolean lockDisabledSafepoints = Log.lock();
            Log.print("End notify monitor for ");
            Log.print(currentThread.getName());
            Log.print(" [");
            dump();
            Log.println("]");
            Log.unlock(lockDisabledSafepoints);
        }
    }

    protected void traceStartMonitorNotify(final VmThread currentThread) {
        if (Monitor.traceMonitors()) {
            final boolean lockDisabledSafepoints = Log.lock();
            Log.print("Start notify monitor for ");
            Log.print(currentThread.getName());
            Log.print(" [");
            dump();
            Log.println("]");
            Log.unlock(lockDisabledSafepoints);
        }
    }
}
