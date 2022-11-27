package dagger.internal;

import dagger.Lazy;
import javax.inject.Provider;

public final class DoubleCheckLazy<T> implements Lazy<T> {
  private static final Object UNINITIALIZED = new Object();

  private final Provider<T> provider;
  private volatile Object instance = UNINITIALIZED;

  private DoubleCheckLazy(Provider<T> provider) {
    assert provider != null;
    this.provider = provider;
  }

  @SuppressWarnings("unchecked")
  @Override
  public T get() {
    Object result = instance;
    if (result == UNINITIALIZED) {
      synchronized (this) {
        result = instance;
        if (result == UNINITIALIZED) {
          instance = result = provider.get();
        }
      }
    }
    return (T) result;
  }

  public static <T> Lazy<T> create(Provider<T> provider) {
    if (provider == null) {
      throw new NullPointerException();
    }
    return new DoubleCheckLazy<T>(provider);
  }
}
