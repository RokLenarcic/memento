package memento.multi;

import clojure.lang.IPersistentMap;
import clojure.lang.ISeq;
import memento.base.ICache;
import memento.base.Segment;

public abstract class MultiCache implements ICache {
    protected final ICache cache;
    protected final ICache upstream;
    private final IPersistentMap conf;
    protected final Object absent;

    public MultiCache(ICache cache, ICache upstream, IPersistentMap conf, Object absent) {
        this.cache = cache;
        this.upstream = upstream;
        this.conf = conf;
        this.absent = absent;
    }

    @Override
    public IPersistentMap conf() {
        return conf;
    }

    @Override
    public Object ifCached(Segment segment, ISeq args) {
        Object v = cache.ifCached(segment, args);
        return v == absent ? upstream.ifCached(segment, args) : v;
    }

    @Override
    public ICache invalidate(Segment segment) {
        cache.invalidate(segment);
        upstream.invalidate(segment);
        return this;
    }

    @Override
    public ICache invalidate(Segment segment, ISeq args) {
        cache.invalidate(segment, args);
        upstream.invalidate(segment, args);
        return this;
    }

    @Override
    public ICache invalidateAll() {
        cache.invalidateAll();
        upstream.invalidateAll();
        return this;
    }

    @Override
    public ICache invalidateId(Object id) {
        cache.invalidateId(id);
        upstream.invalidateId(id);
        return this;
    }

    @Override
    public ICache addEntries(Segment segment, IPersistentMap argsToVals) {
        cache.addEntries(segment, argsToVals);
        return this;
    }

    @Override
    public IPersistentMap asMap() {
        return cache.asMap();
    }

    @Override
    public IPersistentMap asMap(Segment segment) {
        return cache.asMap(segment);
    }
}
