package memento.base;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

public class LockoutTag {
    private UUID id;
    private CountDownLatch latch;

    public LockoutTag(UUID id) {
        this.id = id;
        this.latch = new CountDownLatch(1);
    }

    public LockoutTag() {
        this(UUID.randomUUID());
    }

    public UUID getId() {
        return id;
    }

    public CountDownLatch getLatch() {
        return latch;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LockoutTag that = (LockoutTag) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
