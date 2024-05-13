package memento.caffeine;

import clojure.lang.IObj;
import clojure.lang.IPersistentMap;
import memento.base.Durations;

/**
 * Enables variable expiry of entries in the cache. Cache entries can have individual expiries. If interface
 * invocations return nil, then preexisting ttl and fade settings apply.
 */
public interface Expiry {
    /**
     * Return a duration of when entry expires after write, if nil is returned then ttl and fade settings are used
     * @param conf conf map
     * @param k key
     * @param v value
     * @return a duration, either an integer, representing seconds or a vector of 2 values
     */
    Object ttl(IPersistentMap conf, Object k, Object v);
    /**
     * Return a duration of when entry expires after access, if nil is returned then ttl and fade settings are used
     * @param conf conf map
     * @param k key
     * @param v value
     * @return a duration, either an integer, representing seconds or a vector of 2 values
     */
    Object fade(IPersistentMap conf, Object k, Object v);

    Expiry META_VAL_EXP = new Expiry() {

        @Override
        public Object ttl(IPersistentMap conf, Object k, Object v) {
            if (v instanceof IObj) {
                IPersistentMap meta = ((IObj) v).meta();
                Object ttl = meta.valAt(Durations.ttlKw);
                if (ttl != null) {
                    return ttl;
                }
                return meta.valAt(Durations.fadeKw);
            }
            return null;
        }

        @Override
        public Object fade(IPersistentMap conf, Object k, Object v) {
            if (v instanceof IObj) {
                IPersistentMap meta = ((IObj) v).meta();
                return meta.valAt(Durations.fadeKw);
            }
            return null;
        }
    };
}
