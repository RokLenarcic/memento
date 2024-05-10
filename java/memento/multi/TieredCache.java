package memento.multi;

import clojure.lang.ArraySeq;
import clojure.lang.IFn;
import clojure.lang.IPersistentMap;
import clojure.lang.ISeq;
import memento.base.ICache;
import memento.base.Segment;

public class TieredCache extends MultiCache {
    public TieredCache(ICache cache, ICache upstream, IPersistentMap conf, Object absent) {
        super(cache, upstream, conf, absent);
    }

    @Override
    public Object cached(Segment segment, ISeq args) {
        return cache.cached(new Segment(new AskUpstream(segment), segment.getKeyFn(), segment.getId(), segment.getConf()), args);
    }

    private class AskUpstream implements IFn {

        private Segment segment;

        public AskUpstream(Segment segment) {
            this.segment = segment;
        }

        @Override
        public Object call() {
            return upstream.cached(segment, ArraySeq.create());
        }

        @Override
        public void run() {
            upstream.cached(segment, ArraySeq.create());
        }

        @Override
        public Object invoke() {
            return upstream.cached(segment, ArraySeq.create());
        }

        @Override
        public Object invoke(Object arg1) {
            return upstream.cached(segment, ArraySeq.create(arg1));
        }

        @Override
        public Object invoke(Object arg1, Object arg2) {
            return upstream.cached(segment, ArraySeq.create(arg1, arg2));
        }

        @Override
        public Object invoke(Object arg1, Object arg2, Object arg3) {
            return upstream.cached(segment, ArraySeq.create(arg1, arg2, arg3));
        }

        @Override
        public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4) {
            return upstream.cached(segment, ArraySeq.create(arg1, arg2, arg3, arg4));
        }

        @Override
        public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5) {
            return upstream.cached(segment, ArraySeq.create(arg1, arg2, arg3, arg4, arg5));
        }

        @Override
        public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6) {
            return upstream.cached(segment, ArraySeq.create(arg1, arg2, arg3, arg4, arg5, arg6));
        }

        @Override
        public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7) {
            return upstream.cached(segment, ArraySeq.create(arg1, arg2, arg3, arg4, arg5, arg6, arg7));
        }

        @Override
        public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8) {
            return upstream.cached(segment, ArraySeq.create(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8));
        }

        @Override
        public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9) {
            return upstream.cached(segment, ArraySeq.create(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9));
        }

        @Override
        public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10) {
            return upstream.cached(segment, ArraySeq.create(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10));
        }

        @Override
        public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11) {
            return upstream.cached(segment, ArraySeq.create(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11));
        }

        @Override
        public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11, Object arg12) {
            return upstream.cached(segment, ArraySeq.create(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12));
        }

        @Override
        public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13) {
            return upstream.cached(segment, ArraySeq.create(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13));
        }

        @Override
        public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14) {
            return upstream.cached(segment, ArraySeq.create(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14));
        }

        @Override
        public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14, Object arg15) {
            return upstream.cached(segment, ArraySeq.create(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15));
        }

        @Override
        public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14, Object arg15, Object arg16) {
            return upstream.cached(segment, ArraySeq.create(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16));
        }

        @Override
        public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14, Object arg15, Object arg16, Object arg17) {
            return upstream.cached(segment, ArraySeq.create(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17));
        }

        @Override
        public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14, Object arg15, Object arg16, Object arg17, Object arg18) {
            return upstream.cached(segment, ArraySeq.create(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18));
        }

        @Override
        public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14, Object arg15, Object arg16, Object arg17, Object arg18, Object arg19) {
            return upstream.cached(segment, ArraySeq.create(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18, arg19));
        }

        @Override
        public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14, Object arg15, Object arg16, Object arg17, Object arg18, Object arg19, Object arg20) {
            return upstream.cached(segment, ArraySeq.create(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18, arg19, arg20));
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
            return upstream.cached(segment, ArraySeq.create(allArgs));
        }

        @Override
        public Object applyTo(ISeq arglist) {
            return upstream.cached(segment, arglist);
        }

    }
}
