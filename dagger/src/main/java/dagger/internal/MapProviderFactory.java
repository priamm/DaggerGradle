package dagger.internal;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Provider;

import static dagger.internal.Collections.newLinkedHashMapWithExpectedSize;
import static java.util.Collections.unmodifiableMap;

public final class MapProviderFactory<K, V> implements Factory<Map<K, Provider<V>>> {
  private static final MapProviderFactory<Object, Object> EMPTY =
      new MapProviderFactory<Object, Object>(Collections.<Object, Provider<Object>>emptyMap());

  private final Map<K, Provider<V>> contributingMap;

  public static <K, V> Builder<K, V> builder(int size) {
    return new Builder<K, V>(size);
  }

  @SuppressWarnings("unchecked")
  public static <K, V> MapProviderFactory<K, V> empty() {
    return (MapProviderFactory<K, V>) EMPTY;
  }

  private MapProviderFactory(Map<K, Provider<V>> contributingMap) {
    this.contributingMap = unmodifiableMap(contributingMap);
  }

  @Override
  public Map<K, Provider<V>> get() {
    return this.contributingMap;
  }

  public static final class Builder<K, V> {
    private final LinkedHashMap<K, Provider<V>> mapBuilder;

    private Builder(int size) {
      this.mapBuilder = newLinkedHashMapWithExpectedSize(size);
    }

    public MapProviderFactory<K, V> build() {
      return new MapProviderFactory<K, V>(this.mapBuilder);
    }

    public Builder<K, V> put(K key, Provider<V> providerOfValue) {
      if (key == null) {
        throw new NullPointerException("The key is null");
      }
      if (providerOfValue == null) {
        throw new NullPointerException("The provider of the value is null");
      }

      this.mapBuilder.put(key, providerOfValue);
      return this;
    }
  }
}
