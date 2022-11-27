package dagger.producers.internal;

import com.google.common.util.concurrent.ListenableFuture;
import dagger.producers.Producer;

public abstract class AbstractProducer<T> implements Producer<T> {
  private volatile ListenableFuture<T> instance = null;

  protected abstract ListenableFuture<T> compute();

  @Override
  public final ListenableFuture<T> get() {
    ListenableFuture<T> result = instance;
    if (result == null) {
      synchronized (this) {
        result = instance;
        if (result == null) {
          instance = result = compute();
          if (result == null) {
            throw new NullPointerException("compute returned null");
          }
        }
      }
    }
    return result;
  }
}
