package memento.caffeine;

public interface Joinable {
    Object join() throws Throwable;
    Object getNow(Object valueIfAbsent) throws Throwable;
}
