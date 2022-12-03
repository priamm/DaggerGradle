package dagger.internal;

import dagger.Provides;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Scope;

public interface Factory<T> extends Provider<T> {
}
