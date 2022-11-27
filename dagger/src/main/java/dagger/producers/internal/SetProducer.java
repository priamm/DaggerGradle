package dagger.producers.internal;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.producers.Producer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class SetProducer<T> extends AbstractProducer<Set<T>> {

  public static <T> Producer<Set<T>> create(
      @SuppressWarnings("unchecked") Producer<Set<T>>... producers) {
    return new SetProducer<T>(ImmutableSet.copyOf(producers));
  }

  private final Set<Producer<Set<T>>> contributingProducers;

  private SetProducer(Set<Producer<Set<T>>> contributingProducers) {
    this.contributingProducers = contributingProducers;
  }

  @Override
  public ListenableFuture<Set<T>> compute() {
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
