import com.google.firebase.encoders.FieldDescriptor;
import com.google.firebase.encoders.ObjectEncoder;
import com.google.firebase.encoders.ObjectEncoderContext;
import com.google.firebase.encoders.config.Configurator;
import com.google.firebase.encoders.config.EncoderConfig;
import java.io.IOException;
import java.lang.Override;
import java.lang.SuppressWarnings;

@SuppressWarnings("KotlinInternal")
public final class AutoSimpleClassEncoder implements Configurator {
  public static final int CODEGEN_VERSION = 2;

  public static final Configurator CONFIG = new AutoSimpleClassEncoder();

  private AutoSimpleClassEncoder() {
  }

  @Override
  public void configure(EncoderConfig<?> cfg) {
    cfg.registerEncoder(SimpleClass.class, SimpleClassEncoder.INSTANCE);
  }

  private static final class SimpleClassEncoder implements ObjectEncoder<SimpleClass> {
    static final SimpleClassEncoder INSTANCE = new SimpleClassEncoder();

    private static final FieldDescriptor INT_DESCRIPTOR = FieldDescriptor.of("int");

    private static final FieldDescriptor BOOL_DESCRIPTOR = FieldDescriptor.of("bool");

    private static final FieldDescriptor MAP_DESCRIPTOR = FieldDescriptor.of("map");

    private static final FieldDescriptor FOO_DESCRIPTOR = FieldDescriptor.of("foo");

    @Override
    public void encode(SimpleClass value, ObjectEncoderContext ctx) throws IOException {
      ctx.add(INT_DESCRIPTOR, value.getInt());
      ctx.add(BOOL_DESCRIPTOR, value.isBool());
      ctx.add(MAP_DESCRIPTOR, value.getMap());
      ctx.add(FOO_DESCRIPTOR, value.getField());
    }
  }
}
