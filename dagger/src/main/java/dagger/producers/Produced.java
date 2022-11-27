package dagger.producers;

import com.google.common.base.Objects;
import dagger.internal.Beta;
import java.util.concurrent.ExecutionException;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

@Beta
@CheckReturnValue
public abstract class Produced<T> {

  public abstract T get() throws ExecutionException;

  @Override
  public abstract boolean equals(Object o);

  @Override
  public abstract int hashCode();

  public static <T> Produced<T> successful(@Nullable T value) {
    return new Successful<T>(value);
  }

  public static <T> Produced<T> failed(Throwable throwable) {
    return new Failed<T>(checkNotNull(throwable));
  }

  private static final class Successful<T> extends Produced<T> {
    @Nullable private final T value;

    private Successful(@Nullable T value) {
      this.value = value;
    }

    @Override public T get() {
      return value;
    }

    @Override public boolean equals(Object o) {
      if (o == this) {
        return true;
      } else if (o instanceof Successful) {
        Successful<?> that = (Successful<?>) o;
        return Objects.equal(this.value, that.value);
      } else {
        return false;
      }
    }

    @Override public int hashCode() {
      return value == null ? 0 : value.hashCode();
    }
  }

  private static final class Failed<T> extends Produced<T> {
    private final Throwable throwable;

    private Failed(Throwable throwable) {
      this.throwable = checkNotNull(throwable);
    }

    @Override public T get() throws ExecutionException {
      throw new ExecutionException(throwable);
    }

    @Override public boolean equals(Object o) {
      if (o == this) {
        return true;
      } else if (o instanceof Failed) {
        Failed<?> that = (Failed<?>) o;
        return this.throwable.equals(that.throwable);
      } else {
        return false;
      }
    }

    @Override public int hashCode() {
      return throwable.hashCode();
    }
  }

  private Produced() {}
}
