package moxy.sample.ui;

import dagger.Component;
import moxy.sample.AbstractActivityComponent;
import moxy.sample.ActivityModule;
import moxy.sample.ApplicationComponent;
import moxy.sample.PerActivity;

@PerActivity
@Component(dependencies = ApplicationComponent.class, modules = ActivityModule.class)
public interface HomeComponent extends AbstractActivityComponent {
  void inject(HomeActivity homeActivity);
  void inject(HomeFragment homeFragment);
}
