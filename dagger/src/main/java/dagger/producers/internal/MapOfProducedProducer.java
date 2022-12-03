package dagger.producers.internal;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.internal.Beta;
import dagger.producers.Produced;
import dagger.producers.Producer;
import dagger.producers.monitoring.ProducerMonitor;
import java.util.List;
import java.util.Map;

@Beta
public final class MapOfProducedProducer<K, V> extends AbstractProducer<Map<K, Produced<V>>> {
  private final Producer<Map<K, Producer<V>>> mapProducerProducer;

  private MapOfProducedProducer(Producer<Map<K, Producer<V>>> mapProducerProducer) {
    this.mapProducerProducer = mapProducerProducer;
  }

  public static <K, V> MapOfProducedProducer<K, V> create(
      Producer<Map<K, Producer<V>>> mapProducerProducer) {
    return new MapOfProducedProducer<K, V>(mapProducerProducer);
  }

  @Override
  public ListenableFuture<Map<K, Produced<V>>> compute(ProducerMonitor unusedMonitor) {
    return Futures.transformAsync(
        mapProducerProducer.get(),
        new AsyncFunction<Map<K, Producer<V>>, Map<K, Produced<V>>>() {
          @Override
          public ListenableFuture<Map<K, Produced<V>>> apply(final Map<K, Producer<V>> map) {
            return Futures.transform(
                Futures.allAsList(
                    Iterables.transform(
                        map.entrySet(), MapOfProducedProducer.<K, V>entryUnwrapper())),
                new Function<List<Map.Entry<K, Produced<V>>>, Map<K, Produced<V>>>() {
                  @Override
                  public Map<K, Produced<V>> apply(List<Map.Entry<K, Produced<V>>> entries) {
                    return ImmutableMap.copyOf(entries);
                  }
                });
          }
        });
  }

  private static final Function<
          Map.Entry<Object, Producer<Object>>,
          ListenableFuture<Map.Entry<Object, Produced<Object>>>>
      ENTRY_UNWRAPPER =
          new Function<
              Map.Entry<Object, Producer<Object>>,
              ListenableFuture<Map.Entry<Object, Produced<Object>>>>() {
            @Override
            public ListenableFuture<Map.Entry<Object, Produced<Object>>> apply(
                final Map.Entry<Object, Producer<Object>> entry) {
              return Futures.transform(
                  Producers.createFutureProduced(entry.getValue().get()),
                  new Function<Produced<Object>, Map.Entry<Object, Produced<Object>>>() {
                    @Override
                    public Map.Entry<Object, Produced<Object>> apply(Produced<Object> value) {
                      return Maps.immutableEntry(entry.getKey(), value);
                    }
                  });
            }
          };

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static <K, V>
      Function<Map.Entry<K, Producer<V>>, ListenableFuture<Map.Entry<K, Produced<V>>>>
          entryUnwrapper() {
    return (Function<Map.Entry<K, Producer<V>>, ListenableFuture<Map.Entry<K, Produced<V>>>>)
        (Function) ENTRY_UNWRAPPER;
  }
}
