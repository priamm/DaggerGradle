package dagger.producers.internal;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.internal.Beta;
import dagger.producers.Producer;
import dagger.producers.monitoring.ProducerMonitor;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

@Beta
public final class MapOfProducerProducer<K, V> extends AbstractProducer<Map<K, Producer<V>>> {
  private static final MapOfProducerProducer<Object, Object> EMPTY =
      new MapOfProducerProducer<Object, Object>(ImmutableMap.<Object, Producer<Object>>of());

  private final ImmutableMap<K, Producer<V>> contributingMap;

  public static <K, V> Builder<K, V> builder(int size) {
    return new Builder<K, V>(size);
  }

  @SuppressWarnings("unchecked")
  public static <K, V> MapOfProducerProducer<K, V> empty() {
    return (MapOfProducerProducer<K, V>) EMPTY;
  }

  private MapOfProducerProducer(ImmutableMap<K, Producer<V>> contributingMap) {
    this.contributingMap = contributingMap;
  }

  @Override
  public ListenableFuture<Map<K, Producer<V>>> compute(ProducerMonitor unusedMonitor) {
    return Futures.<Map<K, Producer<V>>>immediateFuture(contributingMap);
  }

  public static final class Builder<K, V> {
    private final ImmutableMap.Builder<K, Producer<V>> mapBuilder;

    private Builder(int size) {
      this.mapBuilder = ImmutableMap.builder();
    }

    public MapOfProducerProducer<K, V> build() {
      return new MapOfProducerProducer<K, V>(mapBuilder.build());
    }

    public Builder<K, V> put(K key, Producer<V> producerOfValue) {
      checkNotNull(key, "key");
      checkNotNull(producerOfValue, "producer of value");
      mapBuilder.put(key, producerOfValue);
      return this;
    }
  }
}
