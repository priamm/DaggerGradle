package dagger.producers.monitoring;

import dagger.producers.Produces;
import java.util.Objects;
import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

public final class ProducerToken {
  @Nullable private final Class<?> classToken;
  @Nullable private final String methodName;

  private ProducerToken(@Nullable Class<?> classToken, @Nullable String methodName) {
    this.classToken = classToken;
    this.methodName = methodName;
  }

  public static ProducerToken create(Class<?> classToken) {
    return new ProducerToken(checkNotNull(classToken), null);
  }

  public static ProducerToken create(String methodName) {
    return new ProducerToken(null, checkNotNull(methodName));
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    } else if (o instanceof ProducerToken) {
      ProducerToken that = (ProducerToken) o;
      return Objects.equals(this.classToken, that.classToken)
          && Objects.equals(this.methodName, that.methodName);
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= Objects.hashCode(this.classToken);
    h *= 1000003;
    h ^= Objects.hashCode(this.methodName);
    return h;
  }

  @Override
  public String toString() {
    if (methodName != null) {
      return methodName;
    } else if (classToken != null) {
      return classToken.toString();
    } else {
      throw new IllegalStateException();
    }
  }
}
