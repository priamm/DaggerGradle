package dagger.producers.internal;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.producers.Producer;
import dagger.producers.monitoring.ProducerMonitor;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class SetProducer<T> extends AbstractProducer<Set<T>> {
  private static final Producer<Set<Object>> EMPTY_PRODUCER =
      new Producer<Set<Object>>() {
        @Override
        public ListenableFuture<Set<Object>> get() {
          return Futures.<Set<Object>>immediateFuture(ImmutableSet.<Object>of());
        }
      };

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static <T> Producer<Set<T>> create() {
    return (Producer<Set<T>>) (Producer) EMPTY_PRODUCER;
  }

  public static <T> Producer<Set<T>> create(Producer<Set<T>> producer) {
    return producer;
  }

  @SafeVarargs
  public static <T> Producer<Set<T>> create(Producer<Set<T>>... producers) {
    return new SetProducer<T>(ImmutableSet.copyOf(producers));
  }

  private final Set<Producer<Set<T>>> contributingProducers;

  private SetProducer(Set<Producer<Set<T>>> contributingProducers) {
    super();
    this.contributingProducers = contributingProducers;
  }

  @Override
  public ListenableFuture<Set<T>> compute(ProducerMonitor unusedMonitor) {
    List<ListenableFuture<Set<T>>> futureSets =
        new ArrayList<ListenableFuture<Set<T>>>(contributingProducers.size());
    for (Producer<Set<T>> producer : contributingProducers) {
      ListenableFuture<Set<T>> futureSet = producer.get();
      if (futureSet == null) {
        throw new NullPointerException(producer + " returned null");
      }
      futureSets.add(futureSet);
    }
    return Futures.transform(Futures.allAsList(futureSets), new Function<List<Set<T>>, Set<T>>() {
      @Override public Set<T> apply(List<Set<T>> sets) {
        ImmutableSet.Builder<T> builder = ImmutableSet.builder();
        for (Set<T> set : sets) {
          builder.addAll(set);
        }
        return builder.build();
      }
    });
  }
}
