package dagger.producers.monitoring;

import dagger.producers.Produces;
import dagger.producers.ProductionComponent;

public abstract class ProducerTimingRecorder {

  public void recordMethod(long startedNanos, long durationNanos) {}

  public void recordSuccess(long latencyNanos) {}

  public void recordFailure(Throwable exception, long latencyNanos) {}

  public void recordSkip(Throwable exception) {}

  public static ProducerTimingRecorder noOp() {
    return NO_OP;
  }

  private static final ProducerTimingRecorder NO_OP = new ProducerTimingRecorder() {};
}
