package com.etheric.testsupport;

import io.quarkus.vertx.VertxContextSupport;
import io.smallrye.mutiny.Uni;

import java.util.function.Supplier;

public final class TestSupport {

    private TestSupport() {
    }

    public static <T> T await(Supplier<Uni<T>> supplier) {
        try {
            return VertxContextSupport.subscribeAndAwait(supplier);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T await(Uni<T> uni) {
        return await(() -> uni);
    }

    public static void awaitVoid(Supplier<Uni<?>> supplier) {
        try {
            VertxContextSupport.subscribeAndAwait(() -> supplier.get().replaceWithVoid());
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static void awaitVoid(Uni<?> uni) {
        awaitVoid(() -> uni);
    }
}
