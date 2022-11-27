package dagger.internal;

import javax.inject.Provider;

public final class DelegateFactory<T> implements Factory<T> {
  private Provider<T> delegate;

  @Override
  public T get() {
    if (delegate == null) {
      throw new IllegalStateException();
    }
    return delegate.get();
  }

  public void setDelegatedProvider(Provider<T> delegate) {
    if (delegate == null) {
      throw new IllegalArgumentException();
    }
    if (this.delegate != null) {
      throw new IllegalStateException();
    }
    this.delegate = delegate;
  }
}

