package dagger.producers.monitoring.internal;

import dagger.producers.monitoring.ProductionComponentMonitor;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Provider;

public final class MonitorCache {
  private static final Logger logger = Logger.getLogger(MonitorCache.class.getName());

  private volatile ProductionComponentMonitor monitor;

  public ProductionComponentMonitor monitor(
      Provider<?> componentProvider,
      Provider<Set<ProductionComponentMonitor.Factory>> monitorFactorySetProvider) {
    ProductionComponentMonitor result = monitor;
    if (result == null) {
      synchronized (this) {
        result = monitor;
        if (result == null) {
          try {
            ProductionComponentMonitor.Factory factory =
                Monitors.delegatingProductionComponentMonitorFactory(
                    monitorFactorySetProvider.get());
            result = monitor = factory.create(componentProvider.get());
          } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "RuntimeException while constructing monitor factories.", e);
            result = monitor = ProductionComponentMonitor.noOp();
          }
        }
      }
    }
    return result;
  }
}
