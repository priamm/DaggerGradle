package dagger.producers;

import dagger.internal.Beta;
import com.google.common.util.concurrent.ListenableFuture;

@Beta
public interface Producer<T> {
  ListenableFuture<T> get();
}
