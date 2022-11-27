package moxy.sample;

import android.app.Application;
import android.location.LocationManager;
import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;

import static android.content.Context.LOCATION_SERVICE;

@Module
public class DemoApplicationModule {
  private final Application application;

  public DemoApplicationModule(Application application) {
    this.application = application;
  }

  @Provides @Singleton Application application() {
    return application;
  }

  @Provides @Singleton LocationManager provideLocationManager() {
    return (LocationManager) application.getSystemService(LOCATION_SERVICE);
  }
}