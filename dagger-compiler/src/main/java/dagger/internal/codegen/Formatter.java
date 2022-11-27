package dagger.internal.codegen;

import com.google.common.base.Function;

abstract class Formatter<T> implements Function<T, String> {

  public abstract String format(T object);

  @SuppressWarnings("javadoc")
  @Deprecated
  @Override final public String apply(T object) {
    return format(object);
  }
}
