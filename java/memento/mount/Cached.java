package memento.mount;

import clojure.lang.IFn;

public interface Cached {
    IMountPoint getMp();
    IFn getOriginalFn();
}
