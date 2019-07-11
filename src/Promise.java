import java.util.ArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class Promise<T, E extends Throwable> {
    private ArrayList<Resolver<T>> resolvers = new ArrayList<>();
    private ArrayList<Rejecter<E>> rejecters = new ArrayList<>();

    public interface Resolver<T> {
        void resolve(T result);
    }

    public interface Rejecter<E extends Throwable> {
        void reject(E error);
    }

    public interface Action<T, E extends Throwable> {
        void execute(Resolver<T> resolver, Rejecter<E> rejecter) throws E;

    }

    public interface Executor<T, E extends Throwable> {
        void execute(Action<T, E> action, Resolver<T> resolver, Rejecter<E> rejecter);
    }

    public static class AsyncExecutor<T, E extends Throwable> implements Executor<T, E> {
        @Override
        public void execute(Action<T, E> action, Resolver<T> resolver, Rejecter<E> rejecter) {
            new Thread(() -> {
                try {
                    action.execute(resolver, rejecter);
                } catch (Throwable e) {
                    onExceptionThrown(action, resolver, rejecter, (E) e);
                }
            }).start();
        }

        protected void onExceptionThrown(Action<T, E> action, Resolver<T> resolver, Rejecter<E> rejecter, E exception) {
            rejecter.reject(exception);
        }
    }

    public static class SyncExecutor<T, E extends Throwable> implements Executor<T, E> {
        @Override
        public void execute(Action<T, E> action, Resolver<T> resolver, Rejecter<E> rejecter) {
            try {
                action.execute(resolver, rejecter);
            } catch (Throwable e) {
                onExceptionThrown(action, resolver, rejecter, (E) e);
            }
        }
        protected void onExceptionThrown(Action<T, E> action, Resolver<T> resolver, Rejecter<E> rejecter, E exception) {
            rejecter.reject(exception);
        }
    }

    private interface NonThrowableExecutor<T, E extends Exception> extends Executor<T, E> {
        final class ExceptionThrownException extends RuntimeException {
            private Throwable exception;
            protected ExceptionThrownException(Throwable e) {
                super("Exception thrown in non-throwable executor: " + e.toString());
                exception = e;
            }

            public Throwable getException() {
                return exception;
            }
        }
    }

    public static class AsyncNonThrowableExecutor<T, E extends Exception>
            extends AsyncExecutor<T, E> implements NonThrowableExecutor<T, E> {
        @Override
        public void onExceptionThrown(Action<T, E> action, Resolver<T> resolver, Rejecter<E> rejecter, E exception) {
            throw new ExceptionThrownException(exception);
        }
    }

    public static class SyncNonThrowableExecutor<T, E extends Exception>
            extends SyncExecutor<T, E> implements NonThrowableExecutor<T, E> {
        @Override
        public void onExceptionThrown(Action<T, E> action, Resolver<T> resolver, Rejecter<E> rejecter, E exception) {
            throw new ExceptionThrownException(exception);
        }
    }

    public static final <T, E extends Throwable> Executor<T, E> getDefaultExecutor() {
        return new AsyncExecutor<>();
    }

    public Promise(Action<T, E> action, Executor<T, E> executor) {
        Resolver<T> resolver = result -> resolvers.forEach(resolver1 -> resolver1.resolve(result));
        Rejecter<E> rejecter = error -> rejecters.forEach(rejecter1 -> rejecter1.reject(error));
        executor.execute(action, resolver, rejecter);
    }

    public Promise(Action<T, E> action) {
        this(action, getDefaultExecutor());
    }

    public Promise<T, E> then(Resolver<T> resolver) {
        resolvers.add(resolver);
        return this;
    }

    public Promise<T, E> except(Rejecter<E> rejecter) {
        rejecters.add(rejecter);
        return this;
    }
    public static final class Builder<T, E extends Exception> {
        private Action<T, E> action;
        private Executor<T, E> executor;
        public static final<T, E extends Exception> Builder<T, E> instance() {
            return new Builder<>();
        }
        public Builder<T, E> setAction(Action<T, E> action) {
            this.action = action;
            return this;
        }
        public Builder<T, E> setAction(Consumer<Resolver<T>> action) {
            this.action = (resolver, rejecter) -> action.accept(resolver);
            return this;
        }
        public Builder<T, E> setExecutor(Executor<T, E> executor) {
            this.executor = executor;
            return this;
        }
        public Promise<T, E> build() {
            if (action != null) {
                return executor == null ? new Promise<>(action) : new Promise<>(action, executor);
            } else {
                throw new IncorrectBuildConfigurationException("executor is null");
            }
        }
        public static final class IncorrectBuildConfigurationException extends RuntimeException {
            private IncorrectBuildConfigurationException(String message) {
                super(message);
            }
        }
    }
}
