package dagger.producers.internal;

import com.google.common.util.concurrent.ListenableFuture;
import dagger.producers.Producer;
import dagger.producers.monitoring.ProducerMonitor;
import dagger.producers.monitoring.ProducerToken;
import dagger.producers.monitoring.ProductionComponentMonitor;
import dagger.producers.monitoring.internal.Monitors;

import static com.google.common.base.Preconditions.checkNotNull;

import javax.annotation.Nullable;
import javax.inject.Provider;

public abstract class AbstractProducer<T> implements Producer<T> {
  private final Provider<ProductionComponentMonitor> monitorProvider;
  @Nullable private final ProducerToken token;
  private volatile ListenableFuture<T> instance = null;

  protected AbstractProducer() {
    this(Monitors.noOpProductionComponentMonitorProvider(), null);
  }

  protected AbstractProducer(
      Provider<ProductionComponentMonitor> monitorProvider, @Nullable ProducerToken token) {
    this.monitorProvider = checkNotNull(monitorProvider);
    this.token = token;
  }

  protected abstract ListenableFuture<T> compute(ProducerMonitor monitor);

  @Override
  public final ListenableFuture<T> get() {
    ListenableFuture<T> result = instance;
    if (result == null) {
      synchronized (this) {
        result = instance;
        if (result == null) {
          ProducerMonitor monitor = monitorProvider.get().producerMonitorFor(token);
          monitor.requested();
          instance = result = compute(monitor);
          if (result == null) {
            throw new NullPointerException("compute returned null");
          }
          monitor.addCallbackTo(result);
        }
      }
    }
    return result;
  }
}
