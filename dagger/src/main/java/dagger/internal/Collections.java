package dagger.internal;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

final class Collections {
  private Collections() {
  }

  static <E> LinkedHashSet<E> newLinkedHashSetWithExpectedSize(int expectedSize) {
    return new LinkedHashSet<E>(calculateInitialCapacity(expectedSize), 1f);
  }

  static <K, V> LinkedHashMap<K, V> newLinkedHashMapWithExpectedSize(int expectedSize) {
    return new LinkedHashMap<K, V>(calculateInitialCapacity(expectedSize), 1f);
  }

  private static int calculateInitialCapacity(int expectedSize) {
    return (expectedSize < 3)
        ? expectedSize + 1
        : (expectedSize < (1 << (Integer.SIZE - 2)))
            ? expectedSize + expectedSize / 3
            : Integer.MAX_VALUE;
  }
}
