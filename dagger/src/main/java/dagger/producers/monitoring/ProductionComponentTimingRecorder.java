package dagger.producers.monitoring;

import dagger.producers.Produces;
import dagger.producers.ProductionComponent;

public interface ProductionComponentTimingRecorder {

  ProducerTimingRecorder producerTimingRecorderFor(ProducerToken token);

  public interface Factory {
    ProductionComponentTimingRecorder create(Object component);
  }
}
