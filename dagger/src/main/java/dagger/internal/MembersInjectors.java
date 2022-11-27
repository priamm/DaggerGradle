package dagger.internal;

import dagger.MembersInjector;
import javax.inject.Inject;

public final class MembersInjectors {

  @SuppressWarnings("unchecked")
  public static <T> MembersInjector<T> noOp() {
    return (MembersInjector<T>) NoOpMembersInjector.INSTANCE;
  }

  private static enum NoOpMembersInjector implements MembersInjector<Object> {
    INSTANCE;

    @Override public void injectMembers(Object instance) {
      if (instance == null) {
        throw new NullPointerException();
      }
    }
  }

  @SuppressWarnings("unchecked")
  public static <T> MembersInjector<T> delegatingTo(MembersInjector<? super T> delegate) {
    return (MembersInjector<T>) delegate;
  }

  private MembersInjectors() {}
}
