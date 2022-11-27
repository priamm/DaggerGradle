package dagger.producers.internal;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.internal.Beta;
import dagger.producers.Producer;
import dagger.producers.monitoring.ProducerMonitor;
import java.util.List;
import java.util.Map;

@Beta
public final class MapProducer<K, V> extends AbstractProducer<Map<K, V>> {
  private final Producer<Map<K, Producer<V>>> mapProducerProducer;

  private MapProducer(Producer<Map<K, Producer<V>>> mapProducerProducer) {
    this.mapProducerProducer = mapProducerProducer;
  }

  public static <K, V> MapProducer<K, V> create(Producer<Map<K, Producer<V>>> mapProducerProducer) {
    return new MapProducer<K, V>(mapProducerProducer);
  }

  @Override
  public ListenableFuture<Map<K, V>> compute(ProducerMonitor unusedMonitor) {
    return Futures.transformAsync(
        mapProducerProducer.get(),
        new AsyncFunction<Map<K, Producer<V>>, Map<K, V>>() {
          @Override
          public ListenableFuture<Map<K, V>> apply(final Map<K, Producer<V>> map) {
            return Futures.transform(
                Futures.allAsList(
                    Iterables.transform(map.entrySet(), MapProducer.<K, V>entryUnwrapper())),
                new Function<List<Map.Entry<K, V>>, Map<K, V>>() {
                  @Override
                  public Map<K, V> apply(List<Map.Entry<K, V>> entries) {
                    return ImmutableMap.copyOf(entries);
                  }
                });
          }
        });
  }

  private static final Function<
          Map.Entry<Object, Producer<Object>>, ListenableFuture<Map.Entry<Object, Object>>>
      ENTRY_UNWRAPPER =
          new Function<
              Map.Entry<Object, Producer<Object>>, ListenableFuture<Map.Entry<Object, Object>>>() {
            @Override
            public ListenableFuture<Map.Entry<Object, Object>> apply(
                final Map.Entry<Object, Producer<Object>> entry) {
              return Futures.transform(
                  entry.getValue().get(),
                  new Function<Object, Map.Entry<Object, Object>>() {
                    @Override
                    public Map.Entry<Object, Object> apply(Object value) {
                      return Maps.immutableEntry(entry.getKey(), value);
                    }
                  });
            }
          };

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static <K, V>
      Function<Map.Entry<K, Producer<V>>, ListenableFuture<Map.Entry<K, V>>> entryUnwrapper() {
    return (Function<Map.Entry<K, Producer<V>>, ListenableFuture<Map.Entry<K, V>>>)
        (Function) ENTRY_UNWRAPPER;
  }
}
