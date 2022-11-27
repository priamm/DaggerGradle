package dagger.internal.codegen;

final class KeyFormatter extends Formatter<Key> {

  @Override public String format(Key request) {
    StringBuilder builder = new StringBuilder();
    if (request.qualifier().isPresent()) {
      builder.append(request.qualifier());
      builder.append(' ');
    }
    builder.append(request.type());
    return builder.toString();
  }
}
