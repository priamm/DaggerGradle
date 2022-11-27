package dagger.producers.monitoring;

import com.google.common.base.Stopwatch;
import com.google.common.base.Ticker;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

final class TimingProducerMonitor extends ProducerMonitor {
  private final ProducerTimingRecorder recorder;
  private final Stopwatch stopwatch;
  private final Stopwatch componentStopwatch;
  private long startNanos = -1;

  TimingProducerMonitor(
      ProducerTimingRecorder recorder, Ticker ticker, Stopwatch componentStopwatch) {
    this.recorder = recorder;
    this.stopwatch = Stopwatch.createUnstarted(ticker);
    this.componentStopwatch = componentStopwatch;
  }

  @Override
  public void methodStarting() {
    startNanos = componentStopwatch.elapsed(NANOSECONDS);
    stopwatch.start();
  }

  @Override
  public void methodFinished() {
    long durationNanos = stopwatch.elapsed(NANOSECONDS);
    recorder.recordMethod(startNanos, durationNanos);
  }

  @Override
  public void succeeded(Object o) {
    long latencyNanos = stopwatch.elapsed(NANOSECONDS);
    recorder.recordSuccess(latencyNanos);
  }

  @Override
  public void failed(Throwable t) {
    if (stopwatch.isRunning()) {
      long latencyNanos = stopwatch.elapsed(NANOSECONDS);
      recorder.recordFailure(t, latencyNanos);
    } else {
      recorder.recordSkip(t);
    }
  }
}
