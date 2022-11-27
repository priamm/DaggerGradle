package dagger.internal;

public final class InstanceFactory<T> implements Factory<T> {
  public static <T> Factory<T> create(T instance) {
    if (instance == null) {
      throw new NullPointerException();
    }
    return new InstanceFactory<T>(instance);
  }

  private final T instance;

  private InstanceFactory(T instance) {
    this.instance = instance;
  }

  @Override
  public T get() {
    return instance;
  }
}
