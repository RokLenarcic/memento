package memento.mount;

import clojure.lang.*;
import memento.base.ICache;
import memento.base.Segment;

public class CachedMultiFn extends MultiFn implements IMountPoint, Cached, IObj {
    private final Object reloadGuard;
    private final MultiFn originalFn;
    private final Segment segment;

    @Override
    public IPersistentMap asMap() {
        return mp.asMap();
    }

    @Override
    public Object cached(ISeq args) {
        return mp.cached(args);
    }

    @Override
    public Object ifCached(ISeq args) {
        return mp.ifCached(args);
    }

    @Override
    public Object getTags() {
        return mp.getTags();
    }

    @Override
    public Object handleEvent(Object event) {
        return mp.handleEvent(event);
    }

    @Override
    public ICache invalidate(ISeq args) {
        return mp.invalidate(args);
    }

    @Override
    public ICache invalidateAll() {
        return mp.invalidateAll();
    }

    @Override
    public ICache mountedCache() {
        return mp.mountedCache();
    }

    @Override
    public Segment segment() {
        return mp.segment();
    }

    @Override
    public ICache addEntries(IPersistentMap argsToVals) {
        return mp.addEntries(argsToVals);
    }

    private final String name;
    private final IMountPoint mp;
    private final IPersistentMap meta;

    public CachedMultiFn(String name, Object reloadGuard, IMountPoint mp, IPersistentMap meta, MultiFn originalFn) {
        super(name, originalFn.dispatchFn, originalFn.defaultDispatchVal, originalFn.hierarchy);
        this.reloadGuard = reloadGuard;
        this.mp = mp;
        this.name = name;
        this.meta = meta;
        this.originalFn = originalFn;
        this.segment = mp.segment();
    }

    @Override
    public IPersistentMap meta() {
        return meta;
    }

    @Override
    public IObj withMeta(IPersistentMap meta) {
        return new CachedMultiFn(name, reloadGuard, mp, meta, originalFn);
    }

    @Override
    public Object call() {
        return mp.mountedCache().cached(segment, ArraySeq.create());
    }

    @Override
    public void run() {
        mp.mountedCache().cached(segment, ArraySeq.create());
    }

    @Override
    public Object invoke() {
        return mp.mountedCache().cached(segment, ArraySeq.create());
    }

    @Override
    public Object invoke(Object arg1) {
        return mp.mountedCache().cached(segment, ArraySeq.create(arg1));
    }

    @Override
    public Object invoke(Object arg1, Object arg2) {
        return mp.mountedCache().cached(segment, ArraySeq.create(arg1, arg2));
    }

    @Override
    public Object invoke(Object arg1, Object arg2, Object arg3) {
        return mp.mountedCache().cached(segment, ArraySeq.create(arg1, arg2, arg3));
    }

    @Override
    public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4) {
        return mp.mountedCache().cached(segment, ArraySeq.create(arg1, arg2, arg3, arg4));
    }

    @Override
    public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5) {
        return mp.mountedCache().cached(segment, ArraySeq.create(arg1, arg2, arg3, arg4, arg5));
    }

    @Override
    public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6) {
        return mp.mountedCache().cached(segment, ArraySeq.create(arg1, arg2, arg3, arg4, arg5, arg6));
    }

    @Override
    public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7) {
        return mp.mountedCache().cached(segment, ArraySeq.create(arg1, arg2, arg3, arg4, arg5, arg6, arg7));
    }

    @Override
    public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8) {
        return mp.mountedCache().cached(segment, ArraySeq.create(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8));
    }

    @Override
    public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9) {
        return mp.mountedCache().cached(segment, ArraySeq.create(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9));
    }

    @Override
    public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10) {
        return mp.mountedCache().cached(segment, ArraySeq.create(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10));
    }

    @Override
    public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11) {
        return mp.mountedCache().cached(segment, ArraySeq.create(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11));
    }

    @Override
    public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11, Object arg12) {
        return mp.mountedCache().cached(segment, ArraySeq.create(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12));
    }

    @Override
    public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13) {
        return mp.mountedCache().cached(segment, ArraySeq.create(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13));
    }

    @Override
    public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14) {
        return mp.mountedCache().cached(segment, ArraySeq.create(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14));
    }

    @Override
    public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14, Object arg15) {
        return mp.mountedCache().cached(segment, ArraySeq.create(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15));
    }

    @Override
    public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14, Object arg15, Object arg16) {
        return mp.mountedCache().cached(segment, ArraySeq.create(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16));
    }

    @Override
    public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14, Object arg15, Object arg16, Object arg17) {
        return mp.mountedCache().cached(segment, ArraySeq.create(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17));
    }

    @Override
    public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14, Object arg15, Object arg16, Object arg17, Object arg18) {
        return mp.mountedCache().cached(segment, ArraySeq.create(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18));
    }

    @Override
    public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14, Object arg15, Object arg16, Object arg17, Object arg18, Object arg19) {
        return mp.mountedCache().cached(segment, ArraySeq.create(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18, arg19));
    }

    @Override
    public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14, Object arg15, Object arg16, Object arg17, Object arg18, Object arg19, Object arg20) {
        return mp.mountedCache().cached(segment, ArraySeq.create(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18, arg19, arg20));
    }

    @Override
    public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14, Object arg15, Object arg16, Object arg17, Object arg18, Object arg19, Object arg20, Object... args) {
        Object[] allArgs = new Object[20 + args.length];
        System.arraycopy(args, 0, allArgs, 20, args.length);
        allArgs[0] = arg1;
        allArgs[1] = arg2;
        allArgs[2] = arg3;
        allArgs[3] = arg4;
        allArgs[4] = arg5;
        allArgs[5] = arg6;
        allArgs[6] = arg7;
        allArgs[7] = arg8;
        allArgs[8] = arg9;
        allArgs[9] = arg10;
        allArgs[10] = arg11;
        allArgs[11] = arg12;
        allArgs[12] = arg13;
        allArgs[13] = arg14;
        allArgs[14] = arg15;
        allArgs[15] = arg16;
        allArgs[16] = arg17;
        allArgs[17] = arg18;
        allArgs[18] = arg19;
        allArgs[19] = arg20;
        return mp.mountedCache().cached(segment, ArraySeq.create(allArgs));
    }

    @Override
    public Object applyTo(ISeq arglist) {
        return mp.mountedCache().cached(segment, arglist);
    }

    public IMountPoint getMp() {
        return mp;
    }

    public IFn getOriginalFn() {
        return originalFn;
    }

    @Override
    public String toString() {
        return "CachedMultiFn{" +
                "originalFn=" + originalFn +
                ", segment=" + segment +
                ", mp=" + mp +
                ", meta=" + meta +
                '}';
    }

    public Segment getSegment() {
        return segment;
    }

    @Override
    public MultiFn addMethod(Object dispatchVal, IFn method) {
        originalFn.addMethod(dispatchVal, method);
        return this;
    }

    @Override
    public IFn getMethod(Object dispatchVal) {
        return originalFn.getMethod(dispatchVal);
    }

    @Override
    public IPersistentMap getMethodTable() {
        return originalFn == null ? PersistentHashMap.EMPTY : originalFn.getMethodTable();
    }

    @Override
    public IPersistentMap getPreferTable() {
        return originalFn.getPreferTable();
    }

    @Override
    public MultiFn preferMethod(Object dispatchValX, Object dispatchValY) {
        originalFn.preferMethod(dispatchValX, dispatchValY);
        return this;
    }

    @Override
    public MultiFn removeMethod(Object dispatchVal) {
        originalFn.removeMethod(dispatchVal);
        return this;
    }

    @Override
    public MultiFn reset() {
        originalFn.reset();
        return this;
    }
}
