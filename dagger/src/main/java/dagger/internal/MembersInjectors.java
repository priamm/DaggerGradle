package dagger.internal;

import dagger.MembersInjector;
import javax.inject.Inject;

import static dagger.internal.Preconditions.checkNotNull;

public final class MembersInjectors {

  public static <T> T injectMembers(MembersInjector<T> membersInjector, T instance) {
    membersInjector.injectMembers(instance);
    return instance;
  }

  @SuppressWarnings("unchecked")
  public static <T> MembersInjector<T> noOp() {
    return (MembersInjector<T>) NoOpMembersInjector.INSTANCE;
  }

  private static enum NoOpMembersInjector implements MembersInjector<Object> {
    INSTANCE;

    @Override public void injectMembers(Object instance) {
      checkNotNull(instance);
    }
  }

  @SuppressWarnings("unchecked")
  public static <T> MembersInjector<T> delegatingTo(MembersInjector<? super T> delegate) {
    return (MembersInjector<T>) checkNotNull(delegate);
  }

  private MembersInjectors() {}
}
