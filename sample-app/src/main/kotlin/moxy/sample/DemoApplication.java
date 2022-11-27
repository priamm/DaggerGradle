package moxy.sample;

import android.app.Application;
import android.location.LocationManager;
import javax.inject.Singleton;
import javax.inject.Inject;

public class DemoApplication extends Application {
  private ApplicationComponent applicationComponent;

  @Inject LocationManager locationManager;

  @Override public void onCreate() {
    super.onCreate();
    applicationComponent = DaggerApplicationComponent.builder()
        .demoApplicationModule(new DemoApplicationModule(this))
        .build();
  }

  public ApplicationComponent component() {
    return applicationComponent;
  }
}
