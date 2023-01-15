package memento.multi;

import clojure.lang.*;
import memento.base.ICache;
import memento.base.Segment;

public class ConsultingCache extends MultiCache {
    public ConsultingCache(ICache cache, ICache upstream, IPersistentMap conf, Object absent) {
        super(cache, upstream, conf, absent);
    }

    @Override
    public Object cached(Segment segment, ISeq args) {
        return cache.cached(new Segment(new UpstreamOrCalc(segment), segment.getKeyFn(), segment.getId()), args);
    }

    private class UpstreamOrCalc implements IFn {

        private Segment segment;

        public UpstreamOrCalc(Segment segment) {
            this.segment = segment;
        }
        @Override
        public Object call() {
            ISeq s = ArraySeq.create();
            Object up = upstream.ifCached(segment, s);
            return up == absent ? AFn.applyToHelper(segment.getF(), s) : up;
        }

        @Override
        public void run() {
            ISeq s = ArraySeq.create();
            Object up = upstream.ifCached(segment, s);
            if (up == absent) {
                AFn.applyToHelper(segment.getF(), s);
            }
        }

        @Override
        public Object invoke() {
            ISeq s = ArraySeq.create();
            Object up = upstream.ifCached(segment, s);
            return up == absent ? AFn.applyToHelper(segment.getF(), s) : up;
        }

        @Override
        public Object invoke(Object arg1) {
            ISeq s = ArraySeq.create(arg1);
            Object up = upstream.ifCached(segment, s);
            return up == absent ? AFn.applyToHelper(segment.getF(), s) : up;
        }

        @Override
        public Object invoke(Object arg1, Object arg2) {
            ISeq s = ArraySeq.create(arg1, arg2);
            Object up = upstream.ifCached(segment, s);
            return up == absent ? AFn.applyToHelper(segment.getF(), s) : up;
        }

        @Override
        public Object invoke(Object arg1, Object arg2, Object arg3) {
            ISeq s = ArraySeq.create(arg1, arg2, arg3);
            Object up = upstream.ifCached(segment, s);
            return up == absent ? AFn.applyToHelper(segment.getF(), s) : up;
        }

        @Override
        public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4) {
            ISeq s = ArraySeq.create(arg1, arg2, arg3, arg4);
            Object up = upstream.ifCached(segment, s);
            return up == absent ? AFn.applyToHelper(segment.getF(), s) : up;
        }

        @Override
        public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5) {
            ISeq s = ArraySeq.create(arg1, arg2, arg3, arg4, arg5);
            Object up = upstream.ifCached(segment, s);
            return up == absent ? AFn.applyToHelper(segment.getF(), s) : up;
        }

        @Override
        public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6) {
            ISeq s = ArraySeq.create(arg1, arg2, arg3, arg4, arg5, arg6);
            Object up = upstream.ifCached(segment, s);
            return up == absent ? AFn.applyToHelper(segment.getF(), s) : up;
        }

        @Override
        public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7) {
            ISeq s = ArraySeq.create(arg1, arg2, arg3, arg4, arg5, arg6, arg7);
            Object up = upstream.ifCached(segment, s);
            return up == absent ? AFn.applyToHelper(segment.getF(), s) : up;
        }

        @Override
        public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8) {
            ISeq s = ArraySeq.create(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8);
            Object up = upstream.ifCached(segment, s);
            return up == absent ? AFn.applyToHelper(segment.getF(), s) : up;
        }

        @Override
        public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9) {
            ISeq s = ArraySeq.create(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9);
            Object up = upstream.ifCached(segment, s);
            return up == absent ? AFn.applyToHelper(segment.getF(), s) : up;
        }

        @Override
        public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10) {
            ISeq s = ArraySeq.create(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10);
            Object up = upstream.ifCached(segment, s);
            return up == absent ? AFn.applyToHelper(segment.getF(), s) : up;
        }

        @Override
        public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11) {
            ISeq s = ArraySeq.create(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11);
            Object up = upstream.ifCached(segment, s);
            return up == absent ? AFn.applyToHelper(segment.getF(), s) : up;
        }

        @Override
        public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11, Object arg12) {
            ISeq s = ArraySeq.create(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12);
            Object up = upstream.ifCached(segment, s);
            return up == absent ? AFn.applyToHelper(segment.getF(), s) : up;
        }

        @Override
        public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13) {
            ISeq s = ArraySeq.create(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13);
            Object up = upstream.ifCached(segment, s);
            return up == absent ? AFn.applyToHelper(segment.getF(), s) : up;
        }

        @Override
        public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14) {
            ISeq s = ArraySeq.create(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14);
            Object up = upstream.ifCached(segment, s);
            return up == absent ? AFn.applyToHelper(segment.getF(), s) : up;
        }

        @Override
        public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14, Object arg15) {
            ISeq s = ArraySeq.create(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15);
            Object up = upstream.ifCached(segment, s);
            return up == absent ? AFn.applyToHelper(segment.getF(), s) : up;
        }

        @Override
        public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14, Object arg15, Object arg16) {
            ISeq s = ArraySeq.create(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16);
            Object up = upstream.ifCached(segment, s);
            return up == absent ? AFn.applyToHelper(segment.getF(), s) : up;
        }

        @Override
        public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14, Object arg15, Object arg16, Object arg17) {
            ISeq s = ArraySeq.create(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17);
            Object up = upstream.ifCached(segment, s);
            return up == absent ? AFn.applyToHelper(segment.getF(), s) : up;
        }

        @Override
        public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14, Object arg15, Object arg16, Object arg17, Object arg18) {
            ISeq s = ArraySeq.create(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18);
            Object up = upstream.ifCached(segment, s);
            return up == absent ? AFn.applyToHelper(segment.getF(), s) : up;
        }

        @Override
        public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14, Object arg15, Object arg16, Object arg17, Object arg18, Object arg19) {
            ISeq s = ArraySeq.create(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18, arg19);
            Object up = upstream.ifCached(segment, s);
            return up == absent ? AFn.applyToHelper(segment.getF(), s) : up;
        }

        @Override
        public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14, Object arg15, Object arg16, Object arg17, Object arg18, Object arg19, Object arg20) {
            ISeq s = ArraySeq.create(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16, arg17, arg18, arg19, arg20);
            Object up = upstream.ifCached(segment, s);
            return up == absent ? AFn.applyToHelper(segment.getF(), s) : up;
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
            ISeq s = ArraySeq.create(allArgs);
            Object up = upstream.ifCached(segment, s);
            return up == absent ? AFn.applyToHelper(segment.getF(), s) : up;
        }

        @Override
        public Object applyTo(ISeq arglist) {
            Object up = upstream.ifCached(segment, arglist);
            return up == absent ? AFn.applyToHelper(segment.getF(), arglist) : up;
        }

    }
}
