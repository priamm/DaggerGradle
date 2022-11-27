package dagger.internal;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

final class Collections {

  private static final int MAX_POWER_OF_TWO = 1 << (Integer.SIZE - 2);

  private Collections() {
  }

  static <E> LinkedHashSet<E> newLinkedHashSetWithExpectedSize(int expectedSize) {
    return new LinkedHashSet<E>(calculateInitialCapacity(expectedSize));
  }

  static <K, V> LinkedHashMap<K, V> newLinkedHashMapWithExpectedSize(int expectedSize) {
    return new LinkedHashMap<K, V>(calculateInitialCapacity(expectedSize));
  }

  private static int calculateInitialCapacity(int expectedSize) {
    if (expectedSize < 3) {
      return expectedSize + 1;
    }
    if (expectedSize < MAX_POWER_OF_TWO) {
      return (int) (expectedSize / 0.75F + 1.0F);
    }
    return Integer.MAX_VALUE;
  }
}
