package com.uber.backend.common.id;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Pluggable ID generation — inject Clock/IdGenerator for deterministic tests.
 */
public interface IdGenerator {

    String nextId(String prefix);

    static IdGenerator uuid() {
        return prefix -> prefix + "-" + UUID.randomUUID();
    }

    static IdGenerator sequential() {
        AtomicLong counter = new AtomicLong(1);
        return prefix -> prefix + "-" + counter.getAndIncrement();
    }
}
