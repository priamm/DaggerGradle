package dagger.producers.monitoring;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.producers.Producer;
import dagger.producers.Produces;

public abstract class ProducerMonitor {

  public void requested() {}

  public void methodStarting() {}

  public void methodFinished() {}

  public void succeeded(Object o) {}

  public void failed(Throwable t) {}

  public <T> void addCallbackTo(ListenableFuture<T> future) {
    Futures.addCallback(
        future,
        new FutureCallback<T>() {
          @Override
          public void onSuccess(T value) {
            succeeded(value);
          }

          @Override
          public void onFailure(Throwable t) {
            failed(t);
          }
        });
  }

  private static final ProducerMonitor NO_OP =
      new ProducerMonitor() {
        @Override
        public <T> void addCallbackTo(ListenableFuture<T> future) {

        }
      };

  public static ProducerMonitor noOp() {
    return NO_OP;
  }
}
