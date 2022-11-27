package dagger.producers.internal;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.producers.Produced;
import dagger.producers.Producer;
import dagger.producers.monitoring.ProducerMonitor;
import java.util.Set;
import javax.inject.Provider;

import static com.google.common.base.Preconditions.checkNotNull;

public final class Producers {

  public static <T> ListenableFuture<Produced<T>> createFutureProduced(ListenableFuture<T> future) {
    return Futures.catchingAsync(
        Futures.transform(
            future,
            new Function<T, Produced<T>>() {
              @Override
              public Produced<T> apply(final T value) {
                return Produced.successful(value);
              }
            }),
        Throwable.class,
        Producers.<T>futureFallbackForProduced());

  }

  private static final AsyncFunction<Throwable, Produced<Object>> FUTURE_FALLBACK_FOR_PRODUCED =
      new AsyncFunction<Throwable, Produced<Object>>() {
        @Override
        public ListenableFuture<Produced<Object>> apply(Throwable t) throws Exception {
          Produced<Object> produced = Produced.failed(t);
          return Futures.immediateFuture(produced);
        }
      };

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static <T> AsyncFunction<Throwable, Produced<T>> futureFallbackForProduced() {
    return (AsyncFunction) FUTURE_FALLBACK_FOR_PRODUCED;
  }

  public static <T> ListenableFuture<Set<T>> createFutureSingletonSet(ListenableFuture<T> future) {
    return Futures.transform(future, new Function<T, Set<T>>() {
      @Override public Set<T> apply(T value) {
        return ImmutableSet.of(value);
      }
    });
  }

  public static <T> Producer<T> producerFromProvider(final Provider<T> provider) {
    checkNotNull(provider);
    return new AbstractProducer<T>() {
      @Override
      protected ListenableFuture<T> compute(ProducerMonitor unusedMonitor) {
        return Futures.immediateFuture(provider.get());
      }
    };
  }

  public static <T> Producer<T> immediateProducer(final T value) {
    return new Producer<T>() {
      @Override
      public ListenableFuture<T> get() {
        return Futures.immediateFuture(value);
      }
    };
  }

  public static <T> Producer<T> immediateFailedProducer(final Throwable throwable) {
    return new Producer<T>() {
      @Override
      public ListenableFuture<T> get() {
        return Futures.immediateFailedFuture(throwable);
      }
    };
  }

  private Producers() {}
}
