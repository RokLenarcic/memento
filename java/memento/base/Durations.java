package memento.base;

import clojure.lang.ISeq;
import clojure.lang.Keyword;
import clojure.lang.Seqable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Durations {

    /**
     * (def timeunits
     *   "Timeunits keywords"
     *   {:ns TimeUnit/NANOSECONDS
     *    :us TimeUnit/MICROSECONDS
     *    :ms TimeUnit/MILLISECONDS
     *    :s TimeUnit/SECONDS
     *    :m TimeUnit/MINUTES
     *    :h TimeUnit/HOURS
     *    :d TimeUnit/DAYS})
     */
    private static final Map<Object, TimeUnit> timeunits = init();

    private static Map<Object, TimeUnit> init() {
        Map<Object, TimeUnit> m = new HashMap<>();
        m.put(Keyword.intern("ns"), TimeUnit.NANOSECONDS);
        m.put(Keyword.intern("us"), TimeUnit.MICROSECONDS);
        m.put(Keyword.intern("ms"), TimeUnit.MILLISECONDS);
        m.put(Keyword.intern("s"), TimeUnit.SECONDS);
        m.put(Keyword.intern("m"), TimeUnit.MINUTES);
        m.put(Keyword.intern("h"), TimeUnit.HOURS);
        m.put(Keyword.intern("d"), TimeUnit.DAYS);
        return m;
    }

    public static long nanos(Object o) {
        if (o instanceof Number) {
            return TimeUnit.SECONDS.toNanos(((Number) o).longValue());
        } else {
            ISeq s = o instanceof ISeq ? (ISeq) o : ((Seqable)o).seq();
            long amt = ((Number)s.first()).longValue();
            return timeunits.get(s.next().first()).toNanos(amt);
        }
    }

    public static long millis(Object o) {
        if (o instanceof Number) {
            return TimeUnit.SECONDS.toMillis(((Number) o).longValue());
        } else {
            ISeq s = o instanceof ISeq ? (ISeq) o : ((Seqable)o).seq();
            long amt = ((Number)s.first()).longValue();
            return timeunits.get(s.next().first()).toMillis(amt);
        }
    }

    public static final Keyword fadeKw = Keyword.intern("memento.core", "fade");
    public static final Keyword ttlKw = Keyword.intern("memento.core", "ttl");

}
