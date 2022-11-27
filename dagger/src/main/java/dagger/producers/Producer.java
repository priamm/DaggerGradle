package dagger.producers;

import dagger.internal.Beta;
import com.google.common.util.concurrent.ListenableFuture;

import javax.annotation.CheckReturnValue;

@Beta
public interface Producer<T> {
  @CheckReturnValue
  ListenableFuture<T> get();
}
