package dagger.internal;

import java.util.Map;
import java.util.Map.Entry;
import javax.inject.Provider;

import static dagger.internal.Collections.newLinkedHashMapWithExpectedSize;
import static java.util.Collections.unmodifiableMap;

public final class MapFactory<K, V> implements Factory<Map<K, V>> {
  private final Map<K, Provider<V>> contributingMap;

  private MapFactory(Map<K, Provider<V>> map) {
    this.contributingMap = unmodifiableMap(map);
  }

  public static <K, V> MapFactory<K, V> create(Provider<Map<K, Provider<V>>> mapProviderFactory) {
    Map<K, Provider<V>> map = mapProviderFactory.get();
    return new MapFactory<K, V>(map);
  }

  @Override
  public Map<K, V> get() {
    Map<K, V> result = newLinkedHashMapWithExpectedSize(contributingMap.size());
    for (Entry<K, Provider<V>> entry: contributingMap.entrySet()) {
      result.put(entry.getKey(), entry.getValue().get());
    }
    return unmodifiableMap(result);
  }
}
