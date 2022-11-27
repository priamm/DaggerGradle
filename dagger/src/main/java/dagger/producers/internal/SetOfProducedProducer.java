package dagger.producers.internal;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.producers.Produced;
import dagger.producers.Producer;
import dagger.producers.monitoring.ProducerMonitor;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public final class SetOfProducedProducer<T> extends AbstractProducer<Set<Produced<T>>> {
  public static <T> Producer<Set<T>> create() {
    return SetProducer.create();
  }

  @SafeVarargs
  public static <T> Producer<Set<Produced<T>>> create(Producer<Set<T>>... producers) {
    return new SetOfProducedProducer<T>(ImmutableSet.copyOf(producers));
  }

  private final ImmutableSet<Producer<Set<T>>> contributingProducers;

  private SetOfProducedProducer(ImmutableSet<Producer<Set<T>>> contributingProducers) {
    this.contributingProducers = contributingProducers;
  }

  @Override
  public ListenableFuture<Set<Produced<T>>> compute(ProducerMonitor unusedMonitor) {
    List<ListenableFuture<Produced<Set<T>>>> futureProducedSets =
        new ArrayList<ListenableFuture<Produced<Set<T>>>>(contributingProducers.size());
    for (Producer<Set<T>> producer : contributingProducers) {
      ListenableFuture<Set<T>> futureSet = producer.get();
      if (futureSet == null) {
        throw new NullPointerException(producer + " returned null");
      }
      futureProducedSets.add(Producers.createFutureProduced(futureSet));
    }
    return Futures.transform(
        Futures.allAsList(futureProducedSets),
        new Function<List<Produced<Set<T>>>, Set<Produced<T>>>() {
          @Override
          public Set<Produced<T>> apply(List<Produced<Set<T>>> producedSets) {
            ImmutableSet.Builder<Produced<T>> builder = ImmutableSet.builder();
            for (Produced<Set<T>> producedSet : producedSets) {
              try {
                Set<T> set = producedSet.get();
                if (set == null) {
                  builder.add(
                      Produced.<T>failed(
                          new NullPointerException(
                              "Cannot contribute a null set into a producer set binding when it's"
                                  + " injected as Set<Produced<T>>.")));
                } else {
                  for (T value : set) {
                    if (value == null) {
                      builder.add(
                          Produced.<T>failed(
                              new NullPointerException(
                                  "Cannot contribute a null element into a producer set binding"
                                      + " when it's injected as Set<Produced<T>>.")));
                    } else {
                      builder.add(Produced.successful(value));
                    }
                  }
                }
              } catch (ExecutionException e) {
                builder.add(Produced.<T>failed(e.getCause()));
              }
            }
            return builder.build();
          }
        });
  }
}
