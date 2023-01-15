package memento.multi;

import clojure.lang.IPersistentMap;
import clojure.lang.ISeq;
import memento.base.ICache;
import memento.base.Segment;

public class DaisyChainCache extends MultiCache {

    public DaisyChainCache(ICache cache, ICache upstream, IPersistentMap conf, Object absent) {
        super(cache, upstream, conf, absent);
    }

    @Override
    public Object cached(Segment segment, ISeq args) {
        Object c = cache.ifCached(segment, args);
        return c == absent ? upstream.cached(segment, args) : c;
    }
}
