package dagger.producers;

import dagger.internal.Beta;
import java.util.concurrent.ExecutionException;

@Beta
public interface Produced<T> {
  T get() throws ExecutionException;
}
