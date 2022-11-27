package dagger.producers.monitoring;

import dagger.producers.Produces;
import dagger.producers.ProductionComponent;

public abstract class ProductionComponentMonitor {

  public abstract ProducerMonitor producerMonitorFor(ProducerToken token);

  private static final ProductionComponentMonitor NO_OP =
      new ProductionComponentMonitor() {
        @Override
        public ProducerMonitor producerMonitorFor(ProducerToken token) {
          return ProducerMonitor.noOp();
        }
      };

  public static ProductionComponentMonitor noOp() {
    return NO_OP;
  }

  public abstract static class Factory {

    public abstract ProductionComponentMonitor create(Object component);

    private static final Factory NO_OP =
        new Factory() {
          @Override
          public ProductionComponentMonitor create(Object component) {
            return ProductionComponentMonitor.noOp();
          }
        };

    public static Factory noOp() {
      return NO_OP;
    }
  }
}
