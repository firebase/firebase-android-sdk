package com.google.firebase.firestore.util;

import static com.google.firebase.firestore.testutil.TestUtil.fromSingleQuotedString;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.PropertyName;
import com.google.firebase.firestore.ThrowOnExtraProperties;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Eran Leshem
 *
 * @noinspection JUnitMalformedDeclaration*/
class BaseRecordMapperTest {
  public record StringBean(String value) {}

  public record DoubleBean(double value) {}

  public record FloatBean(float value) {}

  public record LongBean(long value) {}

  public record IntBean(int value) {}

  public record BooleanBean(boolean value) {}

  public record ShortBean(short value) {}

  public record ByteBean(byte value) {}

  public record CharBean(char value) {}

  public record IntArrayBean(int[] values) {}

  public record StringArrayBean(String[] values) {}

  public record XMLAndURLBean(String XMLAndURL) {}

  public record CaseSensitiveFieldBean1(String VALUE) {}

  public record CaseSensitiveFieldBean2(String value) {}

  public record CaseSensitiveFieldBean3(String Value) {}

  public record CaseSensitiveFieldBean4(String valUE) {}

  public record NestedBean(StringBean bean) {}

  public record ObjectBean(Object value) {}

  public record GenericBean<B>(B value) {}

  public record DoubleGenericBean<A, B>(A valueA, B valueB) {}

  public record ListBean(List<String> values) {}

  public record SetBean(Set<String> values) {}

  public record CollectionBean(Collection<String> values) {}

  public record MapBean(Map<String, String> values) {}

  /**
   * This form is not terribly useful in Java, but Kotlin Maps are immutable and are rewritten into
   * this form (b/67470108 has more details).
   */
  public record UpperBoundedMapBean(Map<String, ? extends Date> values) {}

  public record MultiBoundedMapBean<T extends Date & Serializable>(Map<String, T> values) {}

  public record MultiBoundedMapHolderBean(MultiBoundedMapBean<Date> map) {}

  public record UnboundedMapBean(Map<String, ?> values) {}

  public record UnboundedTypeVariableMapBean<T>(Map<String, T> values) {}

  public record UnboundedTypeVariableMapHolderBean(UnboundedTypeVariableMapBean<String> map) {}

  public record NestedListBean(List<StringBean> values) {}

  public record NestedMapBean(Map<String, StringBean> values) {}

  public record IllegalKeyMapBean(Map<Integer, StringBean> values) {}

  @ThrowOnExtraProperties
  public record ThrowOnUnknownPropertiesBean(String value) {}

  @ThrowOnExtraProperties
  public record NoFieldBean() {}

  public record PropertyNameBean(
      @PropertyName("my_key") String key, @PropertyName("my_value") String value) {}

  @SuppressWarnings({"NonAsciiCharacters"})
  public record UnicodeBean(String 漢字) {}

  public record AllCapsDefaultHandlingBean(String UUID) {}

  public record AllCapsWithPropertyName(@PropertyName("uuid") String UUID) {}

  // Bean definitions with @DocumentId applied to wrong type.
  public record FieldWithDocumentIdOnWrongTypeBean(@DocumentId Integer intField) {}

  public record PropertyWithDocumentIdOnWrongTypeBean(
      @PropertyName("intField") @DocumentId int intField) {}

  public record DocumentIdOnStringField(@DocumentId String docId) {}

  public record DocumentIdOnStringFieldAsProperty(
      @PropertyName("docIdProperty") @DocumentId String docId,
      @PropertyName("anotherProperty") int someOtherProperty) {}

  public record DocumentIdOnNestedObjects(
      @PropertyName("nestedDocIdHolder") DocumentIdOnStringField nestedDocIdHolder) {}

  public record CustomConstructorBean(String value) {
    public CustomConstructorBean() {
      this("value");
    }
  }

  public record ConflictingConstructorBean(String value, int i) {
    public ConflictingConstructorBean(int i, String value) {
      this(value, i);
    }
  }

  private static final double EPSILON = 0.0003;

  @Test
  public void primitiveDeserializeString() {
    var bean = deserialize("{'value': 'foo'}", StringBean.class);
    assertEquals("foo", bean.value());

    // Double
    try {
      deserialize("{'value': 1.1}", StringBean.class);
      fail("Should throw");
    } catch (RuntimeException e) { // ignore
    }

    // Int
    try {
      deserialize("{'value': 1}", StringBean.class);
      fail("Should throw");
    } catch (RuntimeException e) { // ignore
    }

    // Long
    try {
      deserialize("{'value': 1234567890123}", StringBean.class);
      fail("Should throw");
    } catch (RuntimeException e) { // ignore
    }

    // Boolean
    try {
      deserialize("{'value': true}", StringBean.class);
      fail("Should throw");
    } catch (RuntimeException e) { // ignore
    }
  }

  @Test
  public void primitiveDeserializeBoolean() {
    var beanBoolean = deserialize("{'value': true}", BooleanBean.class);
    assertEquals(true, beanBoolean.value());

    // Double
    try {
      deserialize("{'value': 1.1}", BooleanBean.class);
      fail("Should throw");
    } catch (RuntimeException e) { // ignore
    }

    // Long
    try {
      deserialize("{'value': 1234567890123}", BooleanBean.class);
      fail("Should throw");
    } catch (RuntimeException e) { // ignore
    }

    // Int
    try {
      deserialize("{'value': 1}", BooleanBean.class);
      fail("Should throw");
    } catch (RuntimeException e) { // ignore
    }

    // String
    try {
      deserialize("{'value': 'foo'}", BooleanBean.class);
      fail("Should throw");
    } catch (RuntimeException e) { // ignore
    }
  }

  @Test
  public void primitiveDeserializeDouble() {
    var beanDouble = deserialize("{'value': 1.1}", DoubleBean.class);
    assertEquals(1.1, beanDouble.value(), EPSILON);

    // Int
    var beanInt = deserialize("{'value': 1}", DoubleBean.class);
    assertEquals(1, beanInt.value(), EPSILON);
    // Long
    var beanLong = deserialize("{'value': 1234567890123}", DoubleBean.class);
    assertEquals(1234567890123L, beanLong.value(), EPSILON);

    // Boolean
    try {
      deserialize("{'value': true}", DoubleBean.class);
      fail("Should throw");
    } catch (RuntimeException e) { // ignore
    }

    // String
    try {
      deserialize("{'value': 'foo'}", DoubleBean.class);
      fail("Should throw");
    } catch (RuntimeException e) { // ignore
    }
  }

  @Test
  public void primitiveDeserializeFloat() {
    var beanFloat = deserialize("{'value': 1.1}", FloatBean.class);
    assertEquals(1.1, beanFloat.value(), EPSILON);

    // Int
    var beanInt = deserialize(Collections.singletonMap("value", 1), FloatBean.class);
    assertEquals(1, beanInt.value(), EPSILON);
    // Long
    var beanLong = deserialize(Collections.singletonMap("value", 1234567890123L), FloatBean.class);
    assertEquals((float) 1234567890123L, beanLong.value(), EPSILON);

    // Boolean
    try {
      deserialize("{'value': true}", FloatBean.class);
      fail("Should throw");
    } catch (RuntimeException e) { // ignore
    }

    // String
    try {
      deserialize("{'value': 'foo'}", FloatBean.class);
      fail("Should throw");
    } catch (RuntimeException e) { // ignore
    }
  }

  @Test
  public void primitiveDeserializeInt() {
    var beanInt = deserialize("{'value': 1}", IntBean.class);
    assertEquals(1, beanInt.value());

    // Double
    var beanDouble = deserialize("{'value': 1.1}", IntBean.class);
    assertEquals(1, beanDouble.value());

    // Large doubles
    try {
      deserialize("{'value': 1e10}", IntBean.class);
      fail("Should throw");
    } catch (RuntimeException e) { // ignore
    }

    // Long
    try {
      deserialize("{'value': 1234567890123}", IntBean.class);
      fail("Should throw");
    } catch (RuntimeException e) { // ignore
    }

    // Boolean
    try {
      deserialize("{'value': true}", IntBean.class);
      fail("Should throw");
    } catch (RuntimeException e) { // ignore
    }

    // String
    try {
      deserialize("{'value': 'foo'}", IntBean.class);
      fail("Should throw");
    } catch (RuntimeException e) { // ignore
    }
  }

  @Test
  public void primitiveDeserializeLong() {
    var beanLong = deserialize("{'value': 1234567890123}", LongBean.class);
    assertEquals(1234567890123L, beanLong.value());

    // Int
    var beanInt = deserialize("{'value': 1}", LongBean.class);
    assertEquals(1, beanInt.value());

    // Double
    var beanDouble = deserialize("{'value': 1.1}", LongBean.class);
    assertEquals(1, beanDouble.value());

    // Large doubles
    try {
      deserialize("{'value': 1e300}", LongBean.class);
      fail("Should throw");
    } catch (RuntimeException e) { // ignore
    }

    // Boolean
    try {
      deserialize("{'value': true}", LongBean.class);
      fail("Should throw");
    } catch (RuntimeException e) { // ignore
    }

    // String
    try {
      deserialize("{'value': 'foo'}", LongBean.class);
      fail("Should throw");
    } catch (RuntimeException e) { // ignore
    }
  }

  @Test
  public void primitiveDeserializeWrongTypeMap() {
    var expectedExceptionMessage =
        ".* Failed to convert value of type .*Map to String \\(found in field 'value'\\).*";
    Throwable exception =
        assertThrows(
            RuntimeException.class,
            () -> deserialize("{'value': {'foo': 'bar'}}", StringBean.class));
    assertTrue(exception.getMessage().matches(expectedExceptionMessage));
  }

  @Test
  public void primitiveDeserializeWrongTypeList() {
    assertExceptionContains(
        "Failed to convert value of type java.util.ArrayList to String"
            + " (found in field 'value')",
        () -> deserialize("{'value': ['foo']}", StringBean.class));
  }

  @Test
  public void noFieldDeserialize() {
    assertExceptionContains(
        "No properties to serialize found on class "
            + "com.google.firebase.firestore.util.BaseRecordMapperTest$NoFieldBean",
        () -> deserialize("{'value': 'foo'}", NoFieldBean.class));
  }

  @Test
  public void throwOnUnknownProperties() {
    assertExceptionContains(
        "No accessor for unknown found on class "
            + "com.google.firebase.firestore.util.BaseRecordMapperTest$ThrowOnUnknownPropertiesBean",
        () ->
            deserialize("{'value': 'foo', 'unknown': 'bar'}", ThrowOnUnknownPropertiesBean.class));
  }

  @Test
  public void XMLAndURLBean() {
    var bean = deserialize("{'XMLAndURL': 'foo'}", XMLAndURLBean.class);
    assertEquals("foo", bean.XMLAndURL());
  }

  @Test
  public void allCapsSerializesToUppercaseByDefault() {
    var bean = new AllCapsDefaultHandlingBean("value");
    assertJson("{'UUID': 'value'}", serialize(bean));
    var deserialized = deserialize("{'UUID': 'value'}", AllCapsDefaultHandlingBean.class);
    assertEquals("value", deserialized.UUID());
  }

  @Test
  public void allCapsWithPropertyNameSerializesToLowercase() {
    var bean = new AllCapsWithPropertyName("value");
    assertJson("{'uuid': 'value'}", serialize(bean));
    var deserialized = deserialize("{'uuid': 'value'}", AllCapsWithPropertyName.class);
    assertEquals("value", deserialized.UUID());
  }

  @Test
  public void nestedParsingWorks() {
    var bean = deserialize("{'bean': {'value': 'foo'}}", NestedBean.class);
    assertEquals("foo", bean.bean().value());
  }

  @Test
  public void beansCanContainLists() {
    var bean = deserialize("{'values': ['foo', 'bar']}", ListBean.class);
    assertEquals(Arrays.asList("foo", "bar"), bean.values());
  }

  @Test
  public void beansCanContainMaps() {
    var bean = deserialize("{'values': {'foo': 'bar'}}", MapBean.class);
    var expected = fromSingleQuotedString("{'foo': 'bar'}");
    assertEquals(expected, bean.values());
  }

  @Test
  public void beansCanContainUpperBoundedMaps() {
    var date = new Date(1491847082123L);
    var source = map("values", map("foo", date));
    var bean = convertToCustomClass(source, UpperBoundedMapBean.class);
    var expected = map("foo", date);
    assertEquals(expected, bean.values());
  }

  @Test
  public void beansCanContainMultiBoundedMaps() {
    var date = new Date(1491847082123L);
    var source = map("map", map("values", map("foo", date)));
    var bean = convertToCustomClass(source, MultiBoundedMapHolderBean.class);

    var expected = map("foo", date);
    assertEquals(expected, bean.map().values());
  }

  @Test
  public void beansCanContainUnboundedMaps() {
    var bean = deserialize("{'values': {'foo': 'bar'}}", UnboundedMapBean.class);
    var expected = map("foo", "bar");
    assertEquals(expected, bean.values());
  }

  @Test
  public void beansCanContainUnboundedTypeVariableMaps() {
    var source = map("map", map("values", map("foo", "bar")));
    var bean = convertToCustomClass(source, UnboundedTypeVariableMapHolderBean.class);

    var expected = map("foo", "bar");
    assertEquals(expected, bean.map().values());
  }

  @Test
  public void beansCanContainNestedUnboundedMaps() {
    var bean = deserialize("{'values': {'foo': {'bar': 'baz'}}}", UnboundedMapBean.class);
    var expected = map("foo", map("bar", "baz"));
    assertEquals(expected, bean.values());
  }

  @Test
  public void beansCanContainBeanLists() {
    var bean = deserialize("{'values': [{'value': 'foo'}]}", NestedListBean.class);
    assertEquals(1, bean.values().size());
    assertEquals("foo", bean.values().get(0).value());
  }

  @Test
  public void beansCanContainBeanMaps() {
    var bean = deserialize("{'values': {'key': {'value': 'foo'}}}", NestedMapBean.class);
    assertEquals(1, bean.values().size());
    assertEquals("foo", bean.values().get("key").value());
  }

  @Test
  public void beanMapsMustHaveStringKeys() {
    assertExceptionContains(
        "Only Maps with string keys are supported, but found Map with key type class "
            + "java.lang.Integer (found in field 'values')",
        () -> deserialize("{'values': {'1': 'bar'}}", IllegalKeyMapBean.class));
  }

  @Test
  public void serializeStringBean() {
    var bean = new StringBean("foo");
    assertJson("{'value': 'foo'}", serialize(bean));
  }

  @Test
  public void serializeDoubleBean() {
    var bean = new DoubleBean(1.1);
    assertJson("{'value': 1.1}", serialize(bean));
  }

  @Test
  public void serializeIntBean() {
    var bean = new IntBean(1);
    assertJson("{'value': 1}", serialize(Collections.singletonMap("value", 1)));
  }

  @Test
  public void serializeLongBean() {
    var bean = new LongBean(1234567890123L);
    assertJson(
        "{'value': 1.234567890123E12}",
        serialize(Collections.singletonMap("value", 1.234567890123E12)));
  }

  @Test
  public void serializeBooleanBean() {
    var bean = new BooleanBean(true);
    assertJson("{'value': true}", serialize(bean));
  }

  @Test
  public void serializeFloatBean() {
    var bean = new FloatBean(0.5f);

    // We don't use assertJson as it converts all floating point numbers to Double.
    Assert.assertEquals(map("value", 0.5f), serialize(bean));
  }

  @Test
  public void serializePrivateFieldBean() {
    final var bean = new NoFieldBean();
    assertExceptionContains(
        "No properties to serialize found on class "
            + "com.google.firebase.firestore.util.BaseRecordMapperTest$NoFieldBean",
        () -> serialize(bean));
  }

  @Test
  public void nestedSerializingWorks() {
    var bean = new NestedBean(new StringBean("foo"));
    assertJson("{'bean': {'value': 'foo'}}", serialize(bean));
  }

  @Test
  public void serializingListsWorks() {
    var bean = new ListBean(Arrays.asList("foo", "bar"));
    assertJson("{'values': ['foo', 'bar']}", serialize(bean));
  }

  @Test
  public void serializingMapsWorks() {
    var bean = new MapBean(new HashMap<>());
    bean.values().put("foo", "bar");
    assertJson("{'values': {'foo': 'bar'}}", serialize(bean));
  }

  @Test
  public void serializingUpperBoundedMapsWorks() {
    var date = new Date(1491847082123L);
    var bean = new UpperBoundedMapBean(Map.of("foo", date));
    var expected = map("values", map("foo", new Date(date.getTime())));
    assertEquals(expected, serialize(bean));
  }

  @Test
  public void serializingMultiBoundedObjectsWorks() {
    var date = new Date(1491847082123L);

    var values = new HashMap<String, Date>();
    values.put("foo", date);

    var holder = new MultiBoundedMapHolderBean(new MultiBoundedMapBean<>(values));

    var expected = map("map", map("values", map("foo", new Date(date.getTime()))));
    assertEquals(expected, serialize(holder));
  }

  @Test
  public void serializeListOfBeansWorks() {
    var stringBean = new StringBean("foo");

    var bean = new NestedListBean(new ArrayList<>());
    bean.values().add(stringBean);

    assertJson("{'values': [{'value': 'foo'}]}", serialize(bean));
  }

  @Test
  public void serializeMapOfBeansWorks() {
    var stringBean = new StringBean("foo");

    var bean = new NestedMapBean(new HashMap<>());
    bean.values().put("key", stringBean);

    assertJson("{'values': {'key': {'value': 'foo'}}}", serialize(bean));
  }

  @Test
  public void beanMapsMustHaveStringKeysForSerializing() {
    var stringBean = new StringBean("foo");

    final var bean = new IllegalKeyMapBean(new HashMap<>());
    bean.values().put(1, stringBean);

    assertExceptionContains(
        "Maps with non-string keys are not supported (found in field 'values')",
        () -> serialize(bean));
  }

  @Test
  public void serializeUPPERCASE() {
    var bean = new XMLAndURLBean("foo");
    assertJson("{'XMLAndURL': 'foo'}", serialize(bean));
  }

  @Test
  public void roundTripCaseSensitiveFieldBean1() {
    var bean = new CaseSensitiveFieldBean1("foo");
    assertJson("{'VALUE': 'foo'}", serialize(bean));
    var deserialized = deserialize("{'VALUE': 'foo'}", CaseSensitiveFieldBean1.class);
    assertEquals("foo", deserialized.VALUE());
  }

  @Test
  public void roundTripCaseSensitiveFieldBean2() {
    var bean = new CaseSensitiveFieldBean2("foo");
    assertJson("{'value': 'foo'}", serialize(bean));
    var deserialized = deserialize("{'value': 'foo'}", CaseSensitiveFieldBean2.class);
    assertEquals("foo", deserialized.value());
  }

  @Test
  public void roundTripCaseSensitiveFieldBean3() {
    var bean = new CaseSensitiveFieldBean3("foo");
    assertJson("{'Value': 'foo'}", serialize(bean));
    var deserialized = deserialize("{'Value': 'foo'}", CaseSensitiveFieldBean3.class);
    assertEquals("foo", deserialized.Value());
  }

  @Test
  public void roundTripCaseSensitiveFieldBean4() {
    var bean = new CaseSensitiveFieldBean4("foo");
    assertJson("{'valUE': 'foo'}", serialize(bean));
    var deserialized = deserialize("{'valUE': 'foo'}", CaseSensitiveFieldBean4.class);
    assertEquals("foo", deserialized.valUE());
  }

  @Test
  public void roundTripUnicodeBean() {
    var bean = new UnicodeBean("foo");
    assertJson("{'漢字': 'foo'}", serialize(bean));
    var deserialized = deserialize("{'漢字': 'foo'}", UnicodeBean.class);
    assertEquals("foo", deserialized.漢字());
  }

  @Test
  public void shortsCantBeSerialized() {
    final var bean = new ShortBean((short) 1);
    assertExceptionContains(
        "Numbers of type Short are not supported, please use an int, long, float or double (found in field 'value')",
        () -> serialize(bean));
  }

  @Test
  public void bytesCantBeSerialized() {
    final var bean = new ByteBean((byte) 1);
    assertExceptionContains(
        "Numbers of type Byte are not supported, please use an int, long, float or double (found in field 'value')",
        () -> serialize(bean));
  }

  @Test
  public void charsCantBeSerialized() {
    final var bean = new CharBean((char) 1);
    assertExceptionContains(
        "Characters are not supported, please use Strings (found in field 'value')",
        () -> serialize(bean));
  }

  @Test
  public void intArraysCantBeSerialized() {
    final var bean = new IntArrayBean(new int[] {1});
    assertExceptionContains(
        "Serializing Arrays is not supported, please use Lists instead "
            + "(found in field 'values')",
        () -> serialize(bean));
  }

  @Test
  public void objectArraysCantBeSerialized() {
    final var bean = new StringArrayBean(new String[] {"foo"});
    assertExceptionContains(
        "Serializing Arrays is not supported, please use Lists instead "
            + "(found in field 'values')",
        () -> serialize(bean));
  }

  @Test
  public void shortsCantBeDeserialized() {
    assertExceptionContains(
        "Deserializing values to short is not supported (found in field 'value')",
        () -> deserialize("{'value': 1}", ShortBean.class));
  }

  @Test
  public void bytesCantBeDeserialized() {
    assertExceptionContains(
        "Deserializing values to byte is not supported (found in field 'value')",
        () -> deserialize("{'value': 1}", ByteBean.class));
  }

  @Test
  public void charsCantBeDeserialized() {
    assertExceptionContains(
        "Deserializing values to char is not supported (found in field 'value')",
        () -> deserialize("{'value': '1'}", CharBean.class));
  }

  @Test
  public void intArraysCantBeDeserialized() {
    assertExceptionContains(
        "Converting to Arrays is not supported, please use Lists instead (found in field 'values')",
        () -> deserialize("{'values': [1]}", IntArrayBean.class));
  }

  @Test
  public void objectArraysCantBeDeserialized() {
    assertExceptionContains(
        "Could not deserialize object. Converting to Arrays is not supported, please use Lists "
            + "instead (found in field 'values')",
        () -> deserialize("{'values': ['foo']}", StringArrayBean.class));
  }

  @Test
  public void objectAcceptsAnyObject() {
    var stringValue = deserialize("{'value': 'foo'}", ObjectBean.class);
    assertEquals("foo", stringValue.value());
    var listValue = deserialize("{'value': ['foo']}", ObjectBean.class);
    assertEquals(Collections.singletonList("foo"), listValue.value());
    var mapValue = deserialize("{'value': {'foo':'bar'}}", ObjectBean.class);
    Assert.assertEquals(fromSingleQuotedString("{'foo':'bar'}"), mapValue.value());
    var complex = "{'value': {'foo':['bar', ['baz'], {'bam': 'qux'}]}, 'other':{'a': ['b']}}";
    var complexValue = deserialize(complex, ObjectBean.class);
    Assert.assertEquals(fromSingleQuotedString(complex).get("value"), complexValue.value());
  }

  @Test
  public void passingInGenericBeanTopLevelThrows() {
    assertExceptionContains(
        "Class com.google.firebase.firestore.util.BaseRecordMapperTest$GenericBean has generic type parameters",
        () -> deserialize("{'value': 'foo'}", GenericBean.class));
  }

  @Test
  public void collectionsCanBeSerializedWhenList() {
    var bean = new CollectionBean(Collections.singletonList("foo"));
    assertJson("{'values': ['foo']}", serialize(bean));
  }

  @Test
  public void collectionsCantBeSerializedWhenSet() {
    final var bean = new CollectionBean(Collections.singleton("foo"));
    assertExceptionContains(
        "Serializing Collections is not supported, please use Lists instead "
            + "(found in field 'values')",
        () -> serialize(bean));
  }

  @Test
  public void collectionsCantBeDeserialized() {
    assertExceptionContains(
        "Collections are not supported, please use Lists instead (found in field 'values')",
        () -> deserialize("{'values': ['foo']}", CollectionBean.class));
  }

  @Test
  public void serializingGenericBeansSupported() {
    var stringBean = new GenericBean<String>("foo");
    assertJson("{'value': 'foo'}", serialize(stringBean));

    var mapBean = new GenericBean<Map<String, String>>(Collections.singletonMap("foo", "bar"));
    assertJson("{'value': {'foo': 'bar'}}", serialize(mapBean));

    var listBean = new GenericBean<List<String>>(Collections.singletonList("foo"));
    assertJson("{'value': ['foo']}", serialize(listBean));

    var recursiveBean = new GenericBean<GenericBean<String>>(new GenericBean<>("foo"));
    assertJson("{'value': {'value': 'foo'}}", serialize(recursiveBean));

    var doubleBean = new DoubleGenericBean<String, Double>("foo", 1.0);
    assertJson("{'valueB': 1.0, 'valueA': 'foo'}", serialize(doubleBean));
  }

  @Test
  public void propertyNamesAreSerialized() {
    var bean = new PropertyNameBean("foo", "bar");

    assertJson("{'my_key': 'foo', 'my_value': 'bar'}", serialize(bean));
  }

  @Test
  public void propertyNamesAreParsed() {
    var bean = deserialize("{'my_key': 'foo', 'my_value': 'bar'}", PropertyNameBean.class);
    assertEquals("foo", bean.key());
    assertEquals("bar", bean.value());
  }

  @Test
  public void documentIdAnnotateWrongTypeThrows() {
    final var expectedErrorMessage = "instead of String or DocumentReference";
    assertExceptionContains(
        expectedErrorMessage, () -> serialize(new FieldWithDocumentIdOnWrongTypeBean(100)));
    assertExceptionContains(
        expectedErrorMessage,
        () -> deserialize("{'intField': 1}", FieldWithDocumentIdOnWrongTypeBean.class));

    assertExceptionContains(
        expectedErrorMessage, () -> serialize(new PropertyWithDocumentIdOnWrongTypeBean(100)));
    assertExceptionContains(
        expectedErrorMessage,
        () -> deserialize("{'intField': 1}", PropertyWithDocumentIdOnWrongTypeBean.class));
  }

  @Test
  public void customConstructorRoundTrip() {
    final var bean = new CustomConstructorBean("foo");
    assertEquals(
        bean,
        CustomClassMapper.convertToCustomClass(serialize(bean), CustomConstructorBean.class, null));
  }

  @Test
  public void conflictingConstructorCantBeSerialized() {
    final var bean = new ConflictingConstructorBean("foo", 5);
    assertExceptionContains(
        "Multiple constructors match set of components for record "
            + "com.google.firebase.firestore.util.BaseRecordMapperTest$ConflictingConstructorBean",
        () -> serialize(bean));
  }

  @Test
  public void conflictingConstructorCantBeDeserialized() {
    assertExceptionContains(
        "Multiple constructors match set of components for record "
            + "com.google.firebase.firestore.util.BaseRecordMapperTest$ConflictingConstructorBean",
        () -> deserialize(map("foo", 5), ConflictingConstructorBean.class));
  }

  static <T> T deserialize(String jsonString, Class<T> clazz) {
    return deserialize(jsonString, clazz, /* docRef= */ null);
  }

  static <T> T deserialize(Map<String, Object> json, Class<T> clazz) {
    return deserialize(json, clazz, /* docRef= */ null);
  }

  static <T> T deserialize(String jsonString, Class<T> clazz, DocumentReference docRef) {
    var json = fromSingleQuotedString(jsonString);
    return CustomClassMapper.convertToCustomClass(json, clazz, docRef);
  }

  static <T> T deserialize(Map<String, Object> json, Class<T> clazz, DocumentReference docRef) {
    return CustomClassMapper.convertToCustomClass(json, clazz, docRef);
  }

  static Object serialize(Object object) {
    return CustomClassMapper.convertToPlainJavaTypes(object);
  }

  private static void assertJson(String expected, Object actual) {
    Assert.assertEquals(fromSingleQuotedString(expected), actual);
  }

  static void assertExceptionContains(String partialMessage, Runnable run) {
    try {
      run.run();
      fail("Expected exception not thrown");
    } catch (RuntimeException e) {
      assertTrue(e.getMessage().contains(partialMessage));
    }
  }

  private static <T> T convertToCustomClass(
      Object object, Class<T> clazz, DocumentReference docRef) {
    return CustomClassMapper.convertToCustomClass(object, clazz, docRef);
  }

  static <T> T convertToCustomClass(Object object, Class<T> clazz) {
    return CustomClassMapper.convertToCustomClass(object, clazz, null);
  }
}
