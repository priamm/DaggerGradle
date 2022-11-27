package dagger.producers.internal;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.FutureFallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import dagger.producers.Produced;
import dagger.producers.Producer;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import javax.inject.Provider;

import static com.google.common.base.Preconditions.checkNotNull;

public final class Producers {

  public static <T> ListenableFuture<Produced<T>> createFutureProduced(ListenableFuture<T> future) {
    return Futures.withFallback(
        Futures.transform(future, new Function<T, Produced<T>>() {
          @Override public Produced<T> apply(final T value) {
            return new Produced<T>() {
              @Override public T get() {
                return value;
              }
            };
          }
        }), Producers.<T>futureFallbackForProduced());

  }

  private static final FutureFallback<Produced<Object>> FUTURE_FALLBACK_FOR_PRODUCED =
      new FutureFallback<Produced<Object>>() {
    @Override public ListenableFuture<Produced<Object>> create(final Throwable t) {
      Produced<Object> produced = new Produced<Object>() {
        @Override public Object get() throws ExecutionException {
          throw new ExecutionException(t);
        }
      };
      return Futures.immediateFuture(produced);
    }
  };

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static <T> FutureFallback<Produced<T>> futureFallbackForProduced() {
    return (FutureFallback) FUTURE_FALLBACK_FOR_PRODUCED;
  }

  public static <T> ListenableFuture<Set<T>> createFutureSingletonSet(ListenableFuture<T> future) {
    return Futures.transform(future, new Function<T, Set<T>>() {
      @Override public Set<T> apply(T value) {
        return ImmutableSet.of(value);
      }
    });
  }

  public static <T> ListenableFuture<T> submitToExecutor(Callable<T> callable, Executor executor) {
    ListenableFutureTask<T> future = ListenableFutureTask.create(callable);
    executor.execute(future);
    return future;
  }

  public static <T> Producer<T> producerFromProvider(final Provider<T> provider) {
    checkNotNull(provider);
    return new AbstractProducer<T>() {
      @Override protected ListenableFuture<T> compute() {
        return Futures.immediateFuture(provider.get());
      }
    };
  }

  private Producers() {}
}
