package dagger.producers.monitoring;

import com.google.common.base.Stopwatch;
import com.google.common.base.Ticker;
import dagger.internal.Beta;

@Beta
public final class TimingProductionComponentMonitor extends ProductionComponentMonitor {
  private final ProductionComponentTimingRecorder recorder;
  private final Ticker ticker;
  private final Stopwatch stopwatch;

  TimingProductionComponentMonitor(ProductionComponentTimingRecorder recorder, Ticker ticker) {
    this.recorder = recorder;
    this.ticker = ticker;
    this.stopwatch = Stopwatch.createStarted(ticker);
  }

  @Override
  public ProducerMonitor producerMonitorFor(ProducerToken token) {
    return new TimingProducerMonitor(recorder.producerTimingRecorderFor(token), ticker, stopwatch);
  }

  public static final class Factory extends ProductionComponentMonitor.Factory {
    private final ProductionComponentTimingRecorder.Factory recorderFactory;
    private final Ticker ticker;

    public Factory(ProductionComponentTimingRecorder.Factory recorderFactory) {
      this(recorderFactory, Ticker.systemTicker());
    }

    Factory(ProductionComponentTimingRecorder.Factory recorderFactory, Ticker ticker) {
      this.recorderFactory = recorderFactory;
      this.ticker = ticker;
    }

    @Override
    public ProductionComponentMonitor create(Object component) {
      return new TimingProductionComponentMonitor(recorderFactory.create(component), ticker);
    }
  }
}
