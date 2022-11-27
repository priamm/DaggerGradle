package dagger.producers.monitoring;

import dagger.producers.Produces;

import static com.google.common.base.Preconditions.checkNotNull;

public final class ProducerToken {
  private final Class<?> classToken;

  private ProducerToken(Class<?> classToken) {
    this.classToken = classToken;
  }

  public static ProducerToken create(Class<?> classToken) {
    return new ProducerToken(checkNotNull(classToken));
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    } else if (o instanceof ProducerToken) {
      ProducerToken that = (ProducerToken) o;
      return this.classToken.equals(that.classToken);
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return classToken.hashCode();
  }

  @Override
  public String toString() {
    return classToken.toString();
  }
}
