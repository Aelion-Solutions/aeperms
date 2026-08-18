package net.beteax.aeperm.common.history;

public final class ActingContext {

    private static final ThreadLocal<Actor> CURRENT = new ThreadLocal<>();

    private ActingContext() {
    }

    public static Actor current() {
        Actor actor = CURRENT.get();
        return actor == null ? Actor.API : actor;
    }

    public static void run(Actor actor, Runnable action) {
        Actor previous = CURRENT.get();
        CURRENT.set(actor == null ? Actor.API : actor);
        try {
            action.run();
        } finally {
            restore(previous);
        }
    }

    public static int call(Actor actor, java.util.function.IntSupplier action) {
        Actor previous = CURRENT.get();
        CURRENT.set(actor == null ? Actor.API : actor);
        try {
            return action.getAsInt();
        } finally {
            restore(previous);
        }
    }

    @FunctionalInterface
    public interface ThrowingInt {
        int get() throws Exception;
    }

    public static int callThrowing(Actor actor, ThrowingInt action) throws Exception {
        Actor previous = CURRENT.get();
        CURRENT.set(actor == null ? Actor.API : actor);
        try {
            return action.get();
        } finally {
            restore(previous);
        }
    }

    public static void actingAs(String pluginName, Runnable action) {
        run(Actor.api(pluginName), action);
    }

    private static void restore(Actor previous) {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }
}
