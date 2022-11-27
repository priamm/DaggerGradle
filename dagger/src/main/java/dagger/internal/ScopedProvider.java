package dagger.internal;

import javax.inject.Provider;

public final class ScopedProvider<T> implements Provider<T> {
  private static final Object UNINITIALIZED = new Object();

  private final Factory<T> factory;
  private volatile Object instance = UNINITIALIZED;

  private ScopedProvider(Factory<T> factory) {
    assert factory != null;
    this.factory = factory;
  }

  @SuppressWarnings("unchecked")
  @Override
  public T get() {
    Object result = instance;
    if (result == UNINITIALIZED) {
      synchronized (this) {
        result = instance;
        if (result == UNINITIALIZED) {
          instance = result = factory.get();
        }
      }
    }
    return (T) result;
  }

  public static <T> Provider<T> create(Factory<T> factory) {
    if (factory == null) {
      throw new NullPointerException();
    }
    return new ScopedProvider<T>(factory);
  }
}
