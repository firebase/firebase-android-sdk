// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.database;

import static com.google.firebase.database.UnitTestHelpers.fromSingleQuotedString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import androidx.annotation.Keep;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.database.core.utilities.encoding.CustomClassMapper;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@org.junit.runner.RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class MapperTest {
  private static final double EPSILON = 0.00025f;

  private static class StringBean {
    private String value;

    public StringBean() {}

    public StringBean(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }

  private static class DoubleBean {
    private double value;

    public double getValue() {
      return value;
    }
  }

  private static class FloatBean {
    private float value;

    public float getValue() {
      return value;
    }
  }

  private static class LongBean {
    private long value;

    public long getValue() {
      return value;
    }
  }

  private static class IntBean {
    private int value;

    public int getValue() {
      return value;
    }
  }

  private static class BooleanBean {
    private boolean value;

    public boolean isValue() {
      return value;
    }
  }

  private static class ShortBean {
    private short value;

    public short getValue() {
      return value;
    }
  }

  private static class ByteBean {
    private byte value;

    public byte getValue() {
      return value;
    }
  }

  private static class CharBean {
    private char value;

    public char getValue() {
      return value;
    }
  }

  private static class IntArrayBean {
    private int[] values;

    public int[] getValues() {
      return this.values;
    }
  }

  private static class StringArrayBean {
    private String[] values;

    public String[] getValues() {
      return this.values;
    }
  }

  private static class XMLAndURLBean {
    private String XMLAndURL1;
    public String XMLAndURL2;

    public String getXMLAndURL1() {
      return XMLAndURL1;
    }

    public void setXMLAndURL1(String value) {
      this.XMLAndURL1 = value;
    }
  }

  private static class SetterBean {
    private String value;

    public String getValue() {
      return this.value;
    }

    public void setValue(String value) {
      this.value = "setter:" + value;
    }
  }

  private static class PrivateSetterBean {
    public String value;

    @Keep
    private void setValue(String value) {
      this.value = "setter:" + value;
    }
  }

  private static class GetterBean {
    private String value;

    public String getValue() {
      return "getter:" + this.value;
    }
  }

  private static class GetterPublicFieldBean {
    public String value;

    public String getValue() {
      return "getter:" + this.value;
    }
  }

  private static class GetterPublicFieldBeanCaseSensitive {
    public String valueCase;

    public String getValueCASE() {
      return "getter:" + this.valueCase;
    }
  }

  private static class CaseSensitiveGetterBean1 {
    private String value;

    public String getVALUE() {
      return this.value;
    }
  }

  private static class CaseSensitiveGetterBean2 {
    private String value;

    public String getvalue() {
      return this.value;
    }
  }

  private static class CaseSensitiveGetterBean3 {
    private String value;

    public String getVAlue() {
      return this.value;
    }
  }

  private static class CaseSensitiveGetterBean4 {
    private String value;

    public String getvaLUE() {
      return this.value;
    }
  }

  private static class CaseSensitiveSetterBean1 {
    private String value;

    public String getValue() {
      return this.value;
    }

    public void setValue(String value) {
      this.value = "setter:" + value;
    }

    public void setVAlue(String value) {
      this.value = "wrong setter!";
    }
  }

  private static class CaseSensitiveSetterBean2 {
    private String value;

    public String getValue() {
      return this.value;
    }

    public void setValue(String value) {
      this.value = "setter:" + value;
    }

    public void setvalue(String value) {
      this.value = "wrong setter!";
    }
  }

  private static class CaseSensitiveSetterBean3 {
    private String value;

    public String getValue() {
      return this.value;
    }

    public void setvalue(String value) {
      this.value = "setter:" + value;
    }
  }

  private static class CaseSensitiveSetterBean4 {
    private String value;

    public String getValue() {
      return this.value;
    }

    public void setVALUE(String value) {
      this.value = "setter:" + value;
    }
  }

  private static class CaseSensitiveSetterBean5 {
    private String value;

    public String getValue() {
      return this.value;
    }

    public void SETVALUE(String value) {
      this.value = "wrong setter!";
    }
  }

  private static class CaseSensitiveSetterBean6 {
    private String value;

    public String getValue() {
      return this.value;
    }

    public void setVaLUE(String value) {
      this.value = "setter:" + value;
    }
  }

  @SuppressWarnings("ConstantField")
  private static class CaseSensitiveFieldBean1 {
    public String VALUE;
  }

  private static class CaseSensitiveFieldBean2 {
    public String value;
  }

  private static class CaseSensitiveFieldBean3 {
    public String Value;
  }

  private static class CaseSensitiveFieldBean4 {
    public String valUE;
  }

  private static class WrongSetterBean {
    private String value;

    public String getValue() {
      return this.value;
    }

    public void setValue() {
      this.value = "wrong setter!";
    }

    public void setValue(String one, String two) {
      this.value = "wrong setter!";
    }
  }

  private static class WrongTypeBean {
    private Integer value;

    public String getValue() {
      return "" + this.value;
    }
  }

  private static class RecursiveBean {
    private StringBean bean;

    public StringBean getBean() {
      return this.bean;
    }
  }

  private static class ObjectBean {
    private Object value;

    public Object getValue() {
      return value;
    }
  }

  private static class GenericBean<B> {
    private B value;

    public B getValue() {
      return value;
    }
  }

  private static class DoubleGenericBean<A, B> {
    private A valueA;
    private B valueB;

    public A getValueA() {
      return valueA;
    }

    public B getValueB() {
      return valueB;
    }
  }

  private static class ListBean {
    private List<String> values;

    public List<String> getValues() {
      return this.values;
    }
  }

  private static class SetBean {
    private Set<String> values;

    public Set<String> getValues() {
      return this.values;
    }
  }

  private static class CollectionBean {
    private Collection<String> values;

    public Collection<String> getValues() {
      return this.values;
    }
  }

  private static class MapBean {
    private Map<String, String> values;

    public Map<String, String> getValues() {
      return this.values;
    }
  }

  private static class RecursiveListBean {
    private List<StringBean> values;

    public List<StringBean> getValues() {
      return this.values;
    }
  }

  private static class RecursiveMapBean {
    private Map<String, StringBean> values;

    public Map<String, StringBean> getValues() {
      return this.values;
    }
  }

  private static class IllegalKeyMapBean {
    private Map<Integer, StringBean> values;

    public Map<Integer, StringBean> getValues() {
      return this.values;
    }
  }

  private static class PublicFieldBean {
    public String value;
  }

  @ThrowOnExtraProperties
  private static class ThrowOnUnknownPropertiesBean {
    public String value;
  }

  @ThrowOnExtraProperties
  private static class PackageFieldBean {
    String value;
  }

  @ThrowOnExtraProperties
  @SuppressWarnings("unused") // Unused, but required for the test
  private static class PrivateFieldBean {
    private String value;
  }

  private static class PackageGetterBean {
    private String packageValue;
    private String publicValue;

    String getPackageValue() {
      return this.packageValue;
    }

    public String getPublicValue() {
      return this.publicValue;
    }
  }

  private static class ExcludedBean {
    @Exclude public String excludedField = "no-value";

    private String excludedGetter = "no-value";

    private String includedGetter = "no-value";

    @Exclude
    public String getExcludedGetter() {
      return this.excludedGetter;
    }

    public String getIncludedGetter() {
      return this.includedGetter;
    }
  }

  private static class ExcludedSetterBean {
    private String value;

    public String getValue() {
      return this.value;
    }

    @Exclude
    public void setValue(String value) {
      this.value = "wrong setter";
    }
  }

  private static class PropertyNameBean {

    @PropertyName("my_key")
    public String key;

    private String value;

    @PropertyName("my_value")
    public String getValue() {
      return this.value;
    }

    @PropertyName("my_value")
    public void setValue(String value) {
      this.value = value;
    }
  }

  private static class PublicPrivateFieldBean {
    public String value1;
    String value2;
    private String value3;
  }

  private static class TwoSetterBean {
    private String value;

    public String getValue() {
      return this.value;
    }

    public void setValue(String value) {
      this.value = "string:" + value;
    }

    public void setValue(Integer value) {
      this.value = "int:" + value;
    }
  }

  private static class TwoGetterBean {
    private String value;

    public String getValue() {
      return this.value;
    }

    public String getVALUE() {
      return this.value;
    }
  }

  private static class GetterArgumentsBean {
    private String value;

    public String getValue1() {
      return this.value + "1";
    }

    public void getValue2() {}

    public String getValue3(boolean flag) {
      return this.value + "3";
    }

    public String getValue4() {
      return this.value + "4";
    }
  }

  @SuppressWarnings("ConstantField")
  private static class UnicodeBean {
    private String 漢字;

    public String get漢字() {
      return this.漢字;
    }
  }

  private static class PublicConstructorBean {
    private String value;

    public PublicConstructorBean() {}

    public String getValue() {
      return this.value;
    }
  }

  private static class PrivateConstructorBean {
    private String value;

    private PrivateConstructorBean() {}

    public String getValue() {
      return this.value;
    }
  }

  private static class PackageConstructorBean {
    private String value;

    PackageConstructorBean() {}

    public String getValue() {
      return this.value;
    }
  }

  private static class ArgConstructorBean {
    private String value;

    public ArgConstructorBean(String value) {
      this.value = value;
    }

    public String getValue() {
      return this.value;
    }
  }

  private static class MultipleConstructorBean {
    private String value;

    public MultipleConstructorBean(String value) {
      this.value = "wrong-value";
    }

    public MultipleConstructorBean() {}

    public String getValue() {
      return this.value;
    }
  }

  private static class MultiBoundedMapBean<T extends String & Serializable> {
    private Map<String, T> values;

    public Map<String, T> getValues() {
      return values;
    }
  }

  private static class MultiBoundedMapHolderBean {
    private MultiBoundedMapBean<String> map;

    public MultiBoundedMapBean<String> getMap() {
      return map;
    }
  }

  private static class StaticFieldBean {
    public static String value1 = "static-value";
    public String value2;
  }

  private static class StaticMethodBean {
    private static String value1 = "static-value";
    public String value2;

    public static String getValue1() {
      return StaticMethodBean.value1;
    }

    public static void setValue1(String value1) {
      StaticMethodBean.value1 = value1;
    }
  }

  private static enum SimpleEnum {
    Foo,
    Bar;
  }

  private static enum ComplexEnum {
    One("one"),
    Two("two");

    private final String value;

    ComplexEnum(String value) {
      this.value = value;
    }

    public String getValue() {
      return this.value;
    }
  }

  private static class EnumBean {
    public SimpleEnum enumField;

    private SimpleEnum enumValue;

    public ComplexEnum complexEnum;

    public SimpleEnum getEnumValue() {
      return this.enumValue;
    }

    public void setEnumValue(SimpleEnum enumValue) {
      this.enumValue = enumValue;
    }
  }

  private static class BaseBean {
    // Public field on base class
    public String baseValue;

    // Value that is accessed through overridden methods in subclasses
    public String overrideValue;

    // Field that is package private in base class
    String packageBaseValue;

    // Private field that is used in getter/setter in base class
    private String baseMethodValue;

    // Private field that has field with same name in subclasses
    private String classPrivateValue;

    public String getClassPrivateValue() {
      return this.classPrivateValue;
    }

    public String getBaseMethod() {
      return this.baseMethodValue;
    }

    public String getPackageBaseValue() {
      return this.packageBaseValue;
    }

    public void setBaseMethod(String value) {
      this.baseMethodValue = value;
    }
  }

  private static class InheritedBean extends BaseBean {
    public String inheritedValue;

    private String inheritedMethodValue;

    private String classPrivateValue;

    @Override
    public String getClassPrivateValue() {
      return this.classPrivateValue;
    }

    public String getInheritedMethod() {
      return this.inheritedMethodValue;
    }

    public void setInheritedMethod(String value) {
      this.inheritedMethodValue = value;
    }

    public String getOverrideValue() {
      return this.overrideValue + "-inherited";
    }

    public void setOverrideValue(String value) {
      this.overrideValue = value + "-inherited";
    }
  }

  private static final class FinalBean extends InheritedBean {
    public String finalValue;

    private String finalMethodValue;

    private String classPrivateValue;

    @Override
    public String getClassPrivateValue() {
      return this.classPrivateValue;
    }

    public String getFinalMethod() {
      return this.finalMethodValue;
    }

    public void setFinalMethod(String value) {
      this.finalMethodValue = value;
    }

    @Override
    public String getOverrideValue() {
      return this.overrideValue + "-final";
    }

    @Override
    public void setOverrideValue(String value) {
      this.overrideValue = value + "-final";
    }
  }

  // Conflicting setters are not supported. When inheriting from a base class we require all
  // setters be an override of a base class
  private static class ConflictingSetterBean {
    public int value;

    // package private so override can be public
    void setValue(int value) {
      this.value = value;
    }
  }

  private static class ConflictingSetterSubBean extends ConflictingSetterBean {
    public void setValue(String value) {
      this.value = -1;
    }
  }

  private static class ConflictingSetterSubBean2 extends ConflictingSetterBean {
    public void setValue(Integer value) {
      this.value = -1;
    }
  }

  private static class NonConflictingSetterSubBean extends ConflictingSetterBean {
    @Override
    public void setValue(int value) {
      this.value = value * -1;
    }
  }

  private static class GenericSetterBaseBean<T> {
    public T value;

    void setValue(T value) {
      this.value = value;
    }
  }

  private static class ConflictingGenericSetterSubBean<T> extends GenericSetterBaseBean<T> {
    public void setValue(String value) {
      // wrong setter
    }
  }

  private static class NonConflictingGenericSetterSubBean extends GenericSetterBaseBean<String> {
    @Override
    public void setValue(String value) {
      this.value = "subsetter:" + value;
    }
  }

  private abstract static class GenericTypeIndicatorSubclass<T> extends GenericTypeIndicator<T> {}

  private abstract static class NonGenericTypeIndicatorSubclass
      extends GenericTypeIndicator<GenericBean<String>> {}

  private static class NonGenericTypeIndicatorConcreteSubclass
      extends GenericTypeIndicator<GenericBean<String>> {}

  private static class NonGenericTypeIndicatorSubclassConcreteSubclass
      extends GenericTypeIndicatorSubclass<GenericBean<String>> {}

  private static <T> T deserialize(String jsonString, Class<T> clazz) {
    Map<String, Object> json = fromSingleQuotedString(jsonString);
    return CustomClassMapper.convertToCustomClass(json, clazz);
  }

  private static <T> T deserialize(String jsonString, GenericTypeIndicator<T> typeIndicator) {
    Map<String, Object> json = fromSingleQuotedString(jsonString);
    return CustomClassMapper.convertToCustomClass(json, typeIndicator);
  }

  private static Object serialize(Object object) {
    return CustomClassMapper.convertToPlainJavaTypes(object);
  }

  private static void assertJson(String expected, Object actual) {
    assertEquals(fromSingleQuotedString(expected), actual);
  }

  @Test
  public void primitiveDeserializeString() {
    StringBean bean = deserialize("{'value': 'foo'}", StringBean.class);
    assertEquals("foo", bean.value);

    // Double
    try {
      deserialize("{'value': 1.1}", StringBean.class);
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    }

    // Int
    try {
      deserialize("{'value': 1}", StringBean.class);
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    }

    // Long
    try {
      deserialize("{'value': 1234567890123}", StringBean.class);
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    }

    // Boolean
    try {
      deserialize("{'value': true}", StringBean.class);
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    }
  }

  @Test
  public void primitiveDeserializeBoolean() {
    BooleanBean beanBoolean = deserialize("{'value': true}", BooleanBean.class);
    assertEquals(true, beanBoolean.value);

    // Double
    try {
      deserialize("{'value': 1.1}", BooleanBean.class);
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    }

    // Long
    try {
      deserialize("{'value': 1234567890123}", BooleanBean.class);
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    }

    // Int
    try {
      deserialize("{'value': 1}", BooleanBean.class);
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    }

    // String
    try {
      deserialize("{'value': 'foo'}", BooleanBean.class);
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    }
  }

  @Test
  public void primitiveDeserializeDouble() {
    DoubleBean beanDouble = deserialize("{'value': 1.1}", DoubleBean.class);
    assertEquals(1.1, beanDouble.value, EPSILON);

    // Int
    DoubleBean beanInt = deserialize("{'value': 1}", DoubleBean.class);
    assertEquals(1, beanInt.value, EPSILON);
    // Long
    DoubleBean beanLong = deserialize("{'value': 1234567890123}", DoubleBean.class);
    assertEquals(1234567890123L, beanLong.value, EPSILON);

    // Boolean
    try {
      deserialize("{'value': true}", DoubleBean.class);
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    }

    // String
    try {
      deserialize("{'value': 'foo'}", DoubleBean.class);
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    }
  }

  @Test
  public void primitiveDeserializeFloat() {
    FloatBean beanFloat = deserialize("{'value': 1.1}", FloatBean.class);
    assertEquals(1.1, beanFloat.value, EPSILON);

    // Int
    FloatBean beanInt = deserialize("{'value': 1}", FloatBean.class);
    assertEquals(1, beanInt.value, EPSILON);
    // Long
    FloatBean beanLong = deserialize("{'value': 1234567890123}", FloatBean.class);
    assertEquals(Long.valueOf(1234567890123L).floatValue(), beanLong.value, EPSILON);

    // Boolean
    try {
      deserialize("{'value': true}", FloatBean.class);
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    }

    // String
    try {
      deserialize("{'value': 'foo'}", FloatBean.class);
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    }
  }

  @Test
  public void primitiveDeserializeInt() {
    IntBean beanInt = deserialize("{'value': 1}", IntBean.class);
    assertEquals(1, beanInt.value);

    // Double
    IntBean beanDouble = deserialize("{'value': 1.1}", IntBean.class);
    assertEquals(1, beanDouble.value);
    ;

    // Large doubles
    try {
      deserialize("{'value': 1e10}", IntBean.class);
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    }

    // Long
    try {
      deserialize("{'value': 1234567890123}", IntBean.class);
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    }

    // Boolean
    try {
      deserialize("{'value': true}", IntBean.class);
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    }

    // String
    try {
      deserialize("{'value': 'foo'}", IntBean.class);
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    }
  }

  @Test
  public void primitiveDeserializeLong() {
    LongBean beanLong = deserialize("{'value': 1234567890123}", LongBean.class);
    assertEquals(1234567890123L, beanLong.value);

    // Int
    LongBean beanInt = deserialize("{'value': 1}", LongBean.class);
    assertEquals(1, beanInt.value);

    // Double
    LongBean beanDouble = deserialize("{'value': 1.1}", LongBean.class);
    assertEquals(1, beanDouble.value);
    ;

    // Large doubles
    try {
      deserialize("{'value': 1e300}", LongBean.class);
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    }

    // Boolean
    try {
      deserialize("{'value': true}", LongBean.class);
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    }

    // String
    try {
      deserialize("{'value': 'foo'}", LongBean.class);
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    }
  }

  @Test(expected = DatabaseException.class)
  public void primitiveDeserializeWrongTypeMap() {
    deserialize("{'value': {'foo': 'bar'}}", StringBean.class);
  }

  @Test(expected = DatabaseException.class)
  public void primitiveDeserializeWrongTypeList() {
    deserialize("{'value': ['foo']}", StringBean.class);
  }

  @Test
  public void publicFieldDeserialze() {
    PublicFieldBean bean = deserialize("{'value': 'foo'}", PublicFieldBean.class);
    assertEquals("foo", bean.value);
  }

  @Test
  public void publicPrivateFieldDeserialze() {
    PublicPrivateFieldBean bean =
        deserialize(
            "{'value1': 'foo', 'value2': 'bar', 'value3': 'baz'}", PublicPrivateFieldBean.class);
    assertEquals("foo", bean.value1);
    assertEquals(null, bean.value2);
    assertEquals(null, bean.value3);
  }

  @Test(expected = DatabaseException.class)
  public void packageFieldDeserialze() {
    deserialize("{'value': 'foo'}", PackageFieldBean.class);
  }

  @Test(expected = DatabaseException.class)
  public void privateFieldDeserialize() {
    deserialize("{'value': 'foo'}", PrivateFieldBean.class);
  }

  @Test
  public void packageGetterDeserialize() {
    PackageGetterBean bean =
        deserialize("{'publicValue': 'foo', 'packageValue': 'bar'}", PackageGetterBean.class);
    assertEquals("foo", bean.publicValue);
    assertNull(bean.packageValue);
  }

  @Test
  public void packageGetterSerialize() {
    PackageGetterBean bean = new PackageGetterBean();
    bean.packageValue = "foo";
    bean.publicValue = "bar";
    assertJson("{'publicValue': 'bar'}", serialize(bean));
  }

  @Test
  public void ignoreExtraProperties() {
    PublicFieldBean bean = deserialize("{'value': 'foo', 'unknown': 'bar'}", PublicFieldBean.class);
    assertEquals("foo", bean.value);
  }

  @Test(expected = DatabaseException.class)
  public void throwOnUnknownProperties() {
    deserialize("{'value': 'foo', 'unknown': 'bar'}", ThrowOnUnknownPropertiesBean.class);
  }

  @Test(expected = DatabaseException.class)
  public void twoSetterBean() {
    deserialize("{'value': 'foo'}", TwoSetterBean.class);
  }

  @Test
  public void XMLAndURLBean() {
    XMLAndURLBean bean =
        deserialize("{'xmlandURL1': 'foo', 'XMLAndURL2': 'bar'}", XMLAndURLBean.class);
    assertEquals("foo", bean.XMLAndURL1);
    assertEquals("bar", bean.XMLAndURL2);
  }

  @Test
  public void setterIsCalledWhenPresent() {
    SetterBean bean = deserialize("{'value': 'foo'}", SetterBean.class);
    assertEquals("setter:foo", bean.value);
  }

  @Test
  public void privateSetterIsCalledWhenPresent() {
    PrivateSetterBean bean = deserialize("{'value': 'foo'}", PrivateSetterBean.class);
    assertEquals("setter:foo", bean.value);
  }

  @Test(expected = DatabaseException.class)
  public void setterIsCaseSensitive1() {
    deserialize("{'value': 'foo'}", CaseSensitiveSetterBean1.class);
  }

  @Test(expected = DatabaseException.class)
  public void setterIsCaseSensitive2() {
    deserialize("{'value': 'foo'}", CaseSensitiveSetterBean2.class);
  }

  @Test
  public void caseSensitiveSetterIsCalledWhenPresent1() {
    CaseSensitiveSetterBean3 bean = deserialize("{'value': 'foo'}", CaseSensitiveSetterBean3.class);
    assertEquals("setter:foo", bean.value);
  }

  @Test
  public void caseSensitiveSetterIsCalledWhenPresent2() {
    CaseSensitiveSetterBean4 bean = deserialize("{'value': 'foo'}", CaseSensitiveSetterBean4.class);
    assertEquals("setter:foo", bean.value);
  }

  @Test
  public void caseSensitiveSetterIsCalledWhenPresent3() {
    CaseSensitiveSetterBean5 bean = deserialize("{'value': 'foo'}", CaseSensitiveSetterBean5.class);
    assertEquals("foo", bean.value);
  }

  @Test(expected = DatabaseException.class)
  public void caseSensitiveSetterMustHaveSameCaseAsSetter() {
    deserialize("{'value': 'foo'}", CaseSensitiveSetterBean6.class);
  }

  @Test
  public void wrongSetterIsNotCalledWhenPresent() {
    WrongSetterBean bean = deserialize("{'value': 'foo'}", WrongSetterBean.class);
    assertEquals("foo", bean.value);
  }

  @Test
  public void recursiveParsingWorks() {
    RecursiveBean bean = deserialize("{'bean': {'value': 'foo'}}", RecursiveBean.class);
    assertEquals("foo", bean.bean.value);
  }

  @Test
  public void beansCanContainLists() {
    ListBean bean = deserialize("{'values': ['foo', 'bar']}", ListBean.class);
    assertEquals(Arrays.asList("foo", "bar"), bean.values);
  }

  @Test
  public void beansCanContainMaps() {
    MapBean bean = deserialize("{'values': {'foo': 'bar'}}", MapBean.class);
    Map<String, Object> expected = fromSingleQuotedString("{'foo': 'bar'}");
    assertEquals(expected, bean.values);
  }

  @Test
  public void beansCanContainBeanLists() {
    RecursiveListBean bean = deserialize("{'values': [{'value': 'foo'}]}", RecursiveListBean.class);
    assertEquals(1, bean.values.size());
    assertEquals("foo", bean.values.get(0).value);
    ;
  }

  @Test
  public void beansCanContainBeanMaps() {
    RecursiveMapBean bean =
        deserialize("{'values': {'key': {'value': 'foo'}}}", RecursiveMapBean.class);
    assertEquals(1, bean.values.size());
    assertEquals("foo", bean.values.get("key").value);
    ;
  }

  @Test(expected = DatabaseException.class)
  public void beanMapsMustHaveStringKeys() {
    deserialize("{'values': {'1': 'bar'}}", IllegalKeyMapBean.class);
  }

  @Test
  public void serializeStringBean() {
    StringBean bean = new StringBean("foo");
    assertJson("{'value': 'foo'}", serialize(bean));
  }

  @Test
  public void serializeDoubleBean() {
    DoubleBean bean = new DoubleBean();
    bean.value = 1.1;
    assertJson("{'value': 1.1}", serialize(bean));
  }

  @Test
  public void serializeIntBean() {
    IntBean bean = new IntBean();
    bean.value = 1;
    assertJson("{'value': 1}", serialize(bean));
  }

  @Test
  public void serializeLongBean() {
    LongBean bean = new LongBean();
    bean.value = 1234567890123L;
    assertJson("{'value': 1234567890123}", serialize(bean));
  }

  @Test
  public void serializeBooleanBean() {
    BooleanBean bean = new BooleanBean();
    bean.value = true;
    assertJson("{'value': true}", serialize(bean));
  }

  @Test
  public void serializeFloatBean() {
    FloatBean bean = new FloatBean();
    bean.value = 0.5f;
    assertJson("{'value': 0.5}", serialize(bean));
  }

  @Test
  public void serializePublicFieldBean() {
    PublicFieldBean bean = new PublicFieldBean();
    bean.value = "foo";
    assertJson("{'value': 'foo'}", serialize(bean));
  }

  @Test(expected = DatabaseException.class)
  public void serializePrivateFieldBean() {
    PrivateFieldBean bean = new PrivateFieldBean();
    bean.value = "foo";
    serialize(bean);
  }

  @Test(expected = DatabaseException.class)
  public void serializePackageFieldBean() {
    PackageFieldBean bean = new PackageFieldBean();
    bean.value = "foo";
    serialize(bean);
  }

  @Test
  public void serializePublicPrivateFieldBean() {
    PublicPrivateFieldBean bean = new PublicPrivateFieldBean();
    bean.value1 = "foo";
    bean.value2 = "bar";
    bean.value3 = "baz";
    assertJson("{'value1': 'foo'}", serialize(bean));
  }

  @Test
  public void getterOverridesField() {
    GetterBean bean = new GetterBean();
    bean.value = "foo";
    assertJson("{'value': 'getter:foo'}", serialize(bean));
  }

  @Test
  public void getterOverridesPublicField() {
    GetterPublicFieldBean bean = new GetterPublicFieldBean();
    bean.value = "foo";
    assertJson("{'value': 'getter:foo'}", serialize(bean));
  }

  @Test(expected = DatabaseException.class)
  public void getterAndPublicFieldsConflictOnCaseSensitivity() {
    GetterPublicFieldBeanCaseSensitive bean = new GetterPublicFieldBeanCaseSensitive();
    bean.valueCase = "foo";
    serialize(bean);
  }

  @Test
  public void caseSensitveGetterBean1() {
    CaseSensitiveGetterBean1 bean = new CaseSensitiveGetterBean1();
    bean.value = "foo";
    assertJson("{'value': 'foo'}", serialize(bean));
  }

  @Test
  public void caseSensitveGetterBean2() {
    CaseSensitiveGetterBean2 bean = new CaseSensitiveGetterBean2();
    bean.value = "foo";
    assertJson("{'value': 'foo'}", serialize(bean));
  }

  @Test
  public void caseSensitveGetterBean3() {
    CaseSensitiveGetterBean3 bean = new CaseSensitiveGetterBean3();
    bean.value = "foo";
    assertJson("{'value': 'foo'}", serialize(bean));
  }

  @Test
  public void caseSensitveGetterBean4() {
    CaseSensitiveGetterBean4 bean = new CaseSensitiveGetterBean4();
    bean.value = "foo";
    assertJson("{'vaLUE': 'foo'}", serialize(bean));
  }

  @Test
  public void recursiveSerializingWorks() {
    RecursiveBean bean = new RecursiveBean();
    bean.bean = new StringBean("foo");
    assertJson("{'bean': {'value': 'foo'}}", serialize(bean));
  }

  @Test
  public void serializingListsWorks() {
    ListBean bean = new ListBean();
    bean.values = Arrays.asList("foo", "bar");
    assertJson("{'values': ['foo', 'bar']}", serialize(bean));
  }

  @Test
  public void serializingMapsWorks() {
    MapBean bean = new MapBean();
    bean.values = ImmutableMap.of("foo", "bar");
    assertJson("{'values': {'foo': 'bar'}}", serialize(bean));
  }

  @Test
  public void serializeListOfBeansWorks() {
    RecursiveListBean bean = new RecursiveListBean();
    bean.values = ImmutableList.of(new StringBean("foo"));
    assertJson("{'values': [{'value': 'foo'}]}", serialize(bean));
  }

  @Test
  public void serializeMapOfBeansWorks() {
    RecursiveMapBean bean = new RecursiveMapBean();
    bean.values = ImmutableMap.of("key", new StringBean("foo"));
    assertJson("{'values': {'key': {'value': 'foo'}}}", serialize(bean));
  }

  @Test(expected = DatabaseException.class)
  public void beanMapsMustHaveStringKeysForSerializing() {
    IllegalKeyMapBean bean = new IllegalKeyMapBean();
    bean.values = ImmutableMap.of(1, new StringBean("foo"));
    serialize(bean);
  }

  @Test(expected = DatabaseException.class)
  public void twoGettersThrows() {
    TwoGetterBean bean = new TwoGetterBean();
    bean.value = "foo";
    serialize(bean);
  }

  @Test
  public void serializeUPPERCASE() {
    XMLAndURLBean bean = new XMLAndURLBean();
    bean.XMLAndURL1 = "foo";
    bean.XMLAndURL2 = "bar";
    assertJson("{'xmlandURL1': 'foo', 'XMLAndURL2': 'bar'}", serialize(bean));
  }

  @Test
  public void onlySerializesGetterWithCorrectArguments() {
    GetterArgumentsBean bean = new GetterArgumentsBean();
    bean.value = "foo";
    assertJson("{'value1': 'foo1', 'value4': 'foo4'}", serialize(bean));
  }

  @Test
  public void roundTripCaseSensitiveFieldBean1() {
    CaseSensitiveFieldBean1 bean = new CaseSensitiveFieldBean1();
    bean.VALUE = "foo";
    assertJson("{'VALUE': 'foo'}", serialize(bean));
    CaseSensitiveFieldBean1 deserialized =
        deserialize("{'VALUE': 'foo'}", CaseSensitiveFieldBean1.class);
    assertEquals("foo", deserialized.VALUE);
  }

  @Test
  public void roundTripCaseSensitiveFieldBean2() {
    CaseSensitiveFieldBean2 bean = new CaseSensitiveFieldBean2();
    bean.value = "foo";
    assertJson("{'value': 'foo'}", serialize(bean));
    CaseSensitiveFieldBean2 deserialized =
        deserialize("{'value': 'foo'}", CaseSensitiveFieldBean2.class);
    assertEquals("foo", deserialized.value);
  }

  @Test
  public void roundTripCaseSensitiveFieldBean3() {
    CaseSensitiveFieldBean3 bean = new CaseSensitiveFieldBean3();
    bean.Value = "foo";
    assertJson("{'Value': 'foo'}", serialize(bean));
    CaseSensitiveFieldBean3 deserialized =
        deserialize("{'Value': 'foo'}", CaseSensitiveFieldBean3.class);
    assertEquals("foo", deserialized.Value);
  }

  @Test
  public void roundTripCaseSensitiveFieldBean4() {
    CaseSensitiveFieldBean4 bean = new CaseSensitiveFieldBean4();
    bean.valUE = "foo";
    assertJson("{'valUE': 'foo'}", serialize(bean));
    CaseSensitiveFieldBean4 deserialized =
        deserialize("{'valUE': 'foo'}", CaseSensitiveFieldBean4.class);
    assertEquals("foo", deserialized.valUE);
  }

  @Test
  public void roundTripUnicodeBean() {
    UnicodeBean bean = new UnicodeBean();
    bean.漢字 = "foo";
    assertJson("{'漢字': 'foo'}", serialize(bean));
    UnicodeBean deserialized = deserialize("{'漢字': 'foo'}", UnicodeBean.class);
    assertEquals("foo", deserialized.漢字);
  }

  @Test(expected = DatabaseException.class)
  public void shortsCantBeSerialized() {
    ShortBean bean = new ShortBean();
    bean.value = 1;
    serialize(bean);
  }

  @Test(expected = DatabaseException.class)
  public void bytesCantBeSerialized() {
    ByteBean bean = new ByteBean();
    bean.value = 1;
    serialize(bean);
  }

  @Test(expected = DatabaseException.class)
  public void charsCantBeSerialized() {
    CharBean bean = new CharBean();
    bean.value = 1;
    serialize(bean);
  }

  @Test(expected = DatabaseException.class)
  public void intArraysCantBeSerialized() {
    IntArrayBean bean = new IntArrayBean();
    bean.values = new int[] {1};
    serialize(bean);
  }

  @Test(expected = DatabaseException.class)
  public void objectArraysCantBeSerialized() {
    StringArrayBean bean = new StringArrayBean();
    bean.values = new String[] {"foo"};
    serialize(bean);
  }

  @Test(expected = DatabaseException.class)
  public void shortsCantBeDeserialized() {
    deserialize("{'value': 1}", ShortBean.class);
  }

  @Test(expected = DatabaseException.class)
  public void bytesCantBeDeserialized() {
    deserialize("{'value': 1}", ByteBean.class);
  }

  @Test(expected = DatabaseException.class)
  public void charsCantBeDeserialized() {
    deserialize("{'value': '1'}", CharBean.class);
  }

  @Test(expected = DatabaseException.class)
  public void intArraysCantBeDeserialized() {
    deserialize("{'values': [1]}", IntArrayBean.class);
  }

  @Test(expected = DatabaseException.class)
  public void objectArraysCantBeDeserialized() {
    deserialize("{'values': ['foo']}", StringArrayBean.class);
  }

  @Test
  public void publicConstructorCanBeDeserialized() {
    PublicConstructorBean bean = deserialize("{'value': 'foo'}", PublicConstructorBean.class);
    assertEquals("foo", bean.value);
  }

  @Test
  public void privateConstructorCanBeDeserialized() {
    PrivateConstructorBean bean = deserialize("{'value': 'foo'}", PrivateConstructorBean.class);
    assertEquals("foo", bean.value);
  }

  @Test(expected = DatabaseException.class)
  public void argConstructorCantBeDeserialized() {
    deserialize("{'value': 'foo'}", ArgConstructorBean.class);
  }

  @Test
  public void packageConstructorCanBeDeserialized() {
    PackageConstructorBean bean = deserialize("{'value': 'foo'}", PackageConstructorBean.class);
    assertEquals("foo", bean.value);
  }

  @Test
  public void multipleConstructorsCanBeDeserialized() {
    MultipleConstructorBean bean = deserialize("{'value': 'foo'}", MultipleConstructorBean.class);
    assertEquals("foo", bean.value);
  }

  @Test
  public void objectAcceptsAnyObject() {
    ObjectBean stringValue = deserialize("{'value': 'foo'}", ObjectBean.class);
    assertEquals("foo", stringValue.value);
    ObjectBean listValue = deserialize("{'value': ['foo']}", ObjectBean.class);
    assertEquals(Collections.singletonList("foo"), listValue.value);
    ObjectBean mapValue = deserialize("{'value': {'foo':'bar'}}", ObjectBean.class);
    assertEquals(fromSingleQuotedString("{'foo':'bar'}"), mapValue.value);
    String complex =
        "{'value': {'foo':['bar', ['baz'], {'bam': 'qux'}]}, " + "'other':{'a': ['b']}}";
    ObjectBean complexValue = deserialize(complex, ObjectBean.class);
    assertEquals(fromSingleQuotedString(complex).get("value"), complexValue.value);
  }

  @Test
  public void objectClassCanBePassedInAtTopLevel() {
    assertEquals("foo", CustomClassMapper.convertToCustomClass("foo", Object.class));
    assertEquals(1, CustomClassMapper.convertToCustomClass(1, Object.class));
    assertEquals(1L, CustomClassMapper.convertToCustomClass(1L, Object.class));
    assertEquals(true, CustomClassMapper.convertToCustomClass(true, Object.class));
    assertEquals(1.1, CustomClassMapper.convertToCustomClass(1.1, Object.class));
    List<String> fooList = Collections.singletonList("foo");
    assertEquals(fooList, CustomClassMapper.convertToCustomClass(fooList, Object.class));
    Map<String, String> fooMap = Collections.singletonMap("foo", "bar");
    assertEquals(fooMap, CustomClassMapper.convertToCustomClass(fooMap, Object.class));
  }

  @Test
  public void primitiveClassesCanBePassedInTopLevel() {
    assertEquals("foo", CustomClassMapper.convertToCustomClass("foo", String.class));
    assertEquals((Integer) 1, CustomClassMapper.convertToCustomClass(1, Integer.class));
    assertEquals((Long) 1L, CustomClassMapper.convertToCustomClass(1L, Long.class));
    assertEquals(true, CustomClassMapper.convertToCustomClass(true, Boolean.class));
    assertEquals((Double) 1.1, CustomClassMapper.convertToCustomClass(1.1, Double.class));
  }

  @Test(expected = DatabaseException.class)
  public void passingInListTopLevelThrows() {
    CustomClassMapper.convertToCustomClass(Collections.singletonList("foo"), List.class);
  }

  @Test(expected = DatabaseException.class)
  public void passingInMapTopLevelThrows() {
    CustomClassMapper.convertToCustomClass(Collections.singletonMap("foo", "bar"), Map.class);
  }

  @Test(expected = DatabaseException.class)
  public void passingInCharacterTopLevelThrows() {
    CustomClassMapper.convertToCustomClass('1', Character.class);
  }

  @Test(expected = DatabaseException.class)
  public void passingInShortTopLevelThrows() {
    CustomClassMapper.convertToCustomClass(1, Short.class);
  }

  @Test(expected = DatabaseException.class)
  public void passingInByteTopLevelThrows() {
    CustomClassMapper.convertToCustomClass(1, Byte.class);
  }

  @Test(expected = DatabaseException.class)
  public void passingInGenericBeanTopLevelThrows() {
    deserialize("{'value': 'foo'}", GenericBean.class);
  }

  @Test
  public void collectionsCanBeSerializedWhenList() {
    CollectionBean bean = new CollectionBean();
    bean.values = Collections.singletonList("foo");
    assertJson("{'values': ['foo']}", serialize(bean));
  }

  @Test(expected = DatabaseException.class)
  public void collectionsCantBeSerializedWhenSet() {
    CollectionBean bean = new CollectionBean();
    bean.values = Collections.singleton("foo");
    serialize(bean);
  }

  @Test(expected = DatabaseException.class)
  public void collectionsCantBeDeserialized() {
    deserialize("{'values': ['foo']}", CollectionBean.class);
  }

  @Test
  public void allowNullEverywhere() {
    assertNull(CustomClassMapper.convertToCustomClass(null, Integer.class));
    assertNull(CustomClassMapper.convertToCustomClass(null, String.class));
    assertNull(CustomClassMapper.convertToCustomClass(null, Double.class));
    assertNull(CustomClassMapper.convertToCustomClass(null, Long.class));
    assertNull(CustomClassMapper.convertToCustomClass(null, Boolean.class));
    assertNull(CustomClassMapper.convertToCustomClass(null, StringBean.class));
    assertNull(CustomClassMapper.convertToCustomClass(null, Object.class));
    assertNull(CustomClassMapper.convertToCustomClass(null, new GenericTypeIndicator<String>() {}));
    assertNull(
        CustomClassMapper.convertToCustomClass(
            null, new GenericTypeIndicator<Map<String, String>>() {}));
  }

  @Test
  public void parsingGenericBeansSupportedUsingGenericTypeIndicator() {
    GenericBean<String> stringBean =
        deserialize("{'value': 'foo'}", new GenericTypeIndicator<GenericBean<String>>() {});
    assertEquals("foo", stringBean.value);

    GenericBean<Map<String, String>> mapBean =
        deserialize(
            "{'value': {'foo': 'bar'}}",
            new GenericTypeIndicator<GenericBean<Map<String, String>>>() {});
    assertEquals(Collections.singletonMap("foo", "bar"), mapBean.value);

    GenericBean<List<String>> listBean =
        deserialize("{'value': ['foo']}", new GenericTypeIndicator<GenericBean<List<String>>>() {});
    assertEquals(Collections.singletonList("foo"), listBean.value);

    GenericBean<GenericBean<String>> recursiveBean =
        deserialize(
            "{'value': {'value': 'foo'}}",
            new GenericTypeIndicator<GenericBean<GenericBean<String>>>() {});
    assertEquals("foo", recursiveBean.value.value);

    DoubleGenericBean<String, Integer> doubleBean =
        deserialize(
            "{'valueA': 'foo', 'valueB': 1}",
            new GenericTypeIndicator<DoubleGenericBean<String, Integer>>() {});
    assertEquals("foo", doubleBean.valueA);
    assertEquals((Integer) 1, doubleBean.valueB);
  }

  @Test
  public void serializingGenericBeansSupported() {
    GenericBean<String> stringBean = new GenericBean<>();
    stringBean.value = "foo";
    assertJson("{'value': 'foo'}", serialize(stringBean));

    GenericBean<Map<String, String>> mapBean = new GenericBean<>();
    mapBean.value = Collections.singletonMap("foo", "bar");
    assertJson("{'value': {'foo': 'bar'}}", serialize(mapBean));

    GenericBean<List<String>> listBean = new GenericBean<>();
    listBean.value = Collections.singletonList("foo");
    assertJson("{'value': ['foo']}", serialize(listBean));

    GenericBean<GenericBean<String>> recursiveBean = new GenericBean<>();
    recursiveBean.value = new GenericBean<>();
    recursiveBean.value.value = "foo";
    assertJson("{'value': {'value': 'foo'}}", serialize(recursiveBean));

    DoubleGenericBean<String, Integer> doubleBean = new DoubleGenericBean<>();
    doubleBean.valueA = "foo";
    doubleBean.valueB = 1;
    assertJson("{'valueA': 'foo', 'valueB': 1}", serialize(doubleBean));
  }

  @Test(expected = DatabaseException.class)
  public void deserializingWrongTypeThrows() {
    deserialize("{'value': 'foo'}", WrongTypeBean.class);
  }

  @Test
  public void serializingWrongTypeWorks() {
    WrongTypeBean bean = new WrongTypeBean();
    bean.value = 1;
    assertJson("{'value': '1'}", serialize(bean));
  }

  @Test(expected = DatabaseException.class)
  public void extendingGenericTypeIndicatorIsForbidden1() {
    deserialize("{'value': 'foo'}", new GenericTypeIndicatorSubclass<GenericBean<String>>() {});
  }

  @Test(expected = DatabaseException.class)
  public void extendingGenericTypeIndicatorIsForbidden2() {
    deserialize("{'value': 'foo'}", new NonGenericTypeIndicatorSubclass() {});
  }

  @Test(expected = DatabaseException.class)
  public void extendingGenericTypeIndicatorIsForbidden3() {
    deserialize("{'value': 'foo'}", new NonGenericTypeIndicatorSubclassConcreteSubclass());
  }

  @Test
  public void subclassingGenericTypeIndicatorIsAllowed() {
    GenericBean<String> bean =
        deserialize("{'value': 'foo'}", new NonGenericTypeIndicatorConcreteSubclass());
    assertEquals("foo", bean.value);
  }

  @Test
  public void usingWildcardInGenericTypeIndicatorIsAllowed() {
    Map<String, String> fooMap = Collections.singletonMap("foo", "bar");
    assertEquals(
        fooMap,
        CustomClassMapper.convertToCustomClass(
            fooMap, new GenericTypeIndicator<Map<String, ? extends String>>() {}));
  }

  @Test(expected = DatabaseException.class)
  public void usingLowerBoundWildcardsIsForbidden() {
    Map<String, String> fooMap = Collections.singletonMap("foo", "bar");
    CustomClassMapper.convertToCustomClass(
        fooMap, new GenericTypeIndicator<Map<String, ? super String>>() {});
  }

  @Test
  public void multiBoundedWildcardsUsesTheFirst() {
    Map<String, Object> source =
        Collections.singletonMap(
            "map", Collections.singletonMap("values", Collections.singletonMap("foo", "bar")));
    MultiBoundedMapHolderBean bean =
        CustomClassMapper.convertToCustomClass(source, MultiBoundedMapHolderBean.class);
    Map<String, String> expected = Collections.singletonMap("foo", "bar");
    assertEquals(expected, bean.map.values);
  }

  @Test(expected = DatabaseException.class)
  public void unknownTypeParametersNotSupported() {
    deserialize("{'value': 'foo'}", new GenericTypeIndicatorSubclass<GenericBean<?>>() {});
  }

  @Test(expected = DatabaseException.class)
  public void unknownTypeParametersSupportedIfBoundedByKnownType() {
    GenericBean<? extends String> bean =
        deserialize(
            "{'value': 'foo'}",
            new GenericTypeIndicatorSubclass<GenericBean<? extends String>>() {});
    assertEquals("foo", bean.value);
  }

  @Test
  public void excludedFieldsAreExcluded() {
    ExcludedBean bean = new ExcludedBean();
    assertJson("{'includedGetter': 'no-value'}", serialize(bean));
  }

  @Test
  public void excludedFieldsAreNotParsed() {
    ExcludedBean bean =
        deserialize(
            "{'includedGetter': 'foo', 'excludedField': 'bar', " + "'excludedGetter': 'qux'}",
            ExcludedBean.class);
    assertEquals("no-value", bean.excludedField);
    assertEquals("no-value", bean.excludedGetter);
    assertEquals("foo", bean.includedGetter);
  }

  @Test
  public void excludedSettersAreIgnored() {
    ExcludedSetterBean bean = deserialize("{'value': 'foo'}", ExcludedSetterBean.class);
    assertEquals("foo", bean.value);
  }

  @Test
  public void propertyNamesAreSerialized() {
    PropertyNameBean bean = new PropertyNameBean();
    bean.key = "foo";
    bean.setValue("bar");

    assertJson("{'my_key': 'foo', 'my_value': 'bar'}", serialize(bean));
  }

  @Test
  public void propertyNamesAreParsed() {
    PropertyNameBean bean =
        deserialize("{'my_key': 'foo', 'my_value': 'bar'}", PropertyNameBean.class);
    assertEquals("foo", bean.key);
    assertEquals("bar", bean.getValue());
  }

  @Test
  public void staticFieldsAreNotParsed() {
    StaticFieldBean bean = deserialize("{'value1': 'foo', 'value2': 'bar'}", StaticFieldBean.class);
    assertEquals("static-value", StaticFieldBean.value1);
    assertEquals("bar", bean.value2);
  }

  @Test
  public void staticFieldsAreNotSerialized() {
    StaticFieldBean bean = new StaticFieldBean();
    bean.value2 = "foo";
    assertJson("{'value2': 'foo'}", serialize(bean));
  }

  @Test
  public void staticSettersAreNotUsed() {
    StaticMethodBean bean =
        deserialize("{'value1': 'foo', 'value2': 'bar'}", StaticMethodBean.class);
    assertEquals("static-value", StaticMethodBean.value1);
    assertEquals("bar", bean.value2);
  }

  @Test
  public void staticMethodsAreNotSerialized() {
    StaticMethodBean bean = new StaticMethodBean();
    bean.value2 = "foo";
    assertJson("{'value2': 'foo'}", serialize(bean));
  }

  @Test
  public void enumsAreSerialized() {
    EnumBean bean = new EnumBean();
    bean.enumField = SimpleEnum.Bar;
    bean.complexEnum = ComplexEnum.One;
    bean.setEnumValue(SimpleEnum.Foo);

    assertJson("{'enumField': 'Bar', 'enumValue': 'Foo', 'complexEnum': 'One'}", serialize(bean));
  }

  @Test
  public void enumsAreParsed() {
    String json = "{'enumField': 'Bar', 'enumValue': 'Foo', 'complexEnum': 'One'}";
    EnumBean bean = deserialize(json, EnumBean.class);
    assertEquals(bean.enumField, SimpleEnum.Bar);
    assertEquals(bean.enumValue, SimpleEnum.Foo);
    assertEquals(bean.complexEnum, ComplexEnum.One);
  }

  @Test
  public void enumsCanBeParsedToNull() {
    String json = "{'enumField': null}";
    EnumBean bean = deserialize(json, EnumBean.class);
    assertNull(bean.enumField);
    assertNull(bean.enumValue);
    assertNull(bean.complexEnum);
  }

  @Test(expected = DatabaseException.class)
  public void throwsOnUnmatchedEnums() {
    String json = "{'enumField': 'Unavailable', 'enumValue': 'Foo', 'complexEnum': 'One'}";
    deserialize(json, EnumBean.class);
  }

  @Test
  public void inheritedFieldsAndGettersAreSerialized() {
    FinalBean bean = new FinalBean();
    bean.finalValue = "final-value";
    bean.inheritedValue = "inherited-value";
    bean.baseValue = "base-value";
    bean.overrideValue = "override-value";
    bean.classPrivateValue = "private-value";
    bean.packageBaseValue = "package-base-value";
    bean.setFinalMethod("final-method");
    bean.setInheritedMethod("inherited-method");
    bean.setBaseMethod("base-method");
    assertJson(
        "{'baseValue': 'base-value', "
            + "'baseMethod': 'base-method', "
            + "'classPrivateValue': 'private-value', "
            + "'finalMethod': 'final-method', "
            + "'finalValue': 'final-value', "
            + "'inheritedMethod': 'inherited-method', "
            + "'inheritedValue': 'inherited-value', "
            + "'overrideValue': 'override-value-final', "
            + "'packageBaseValue': 'package-base-value'}",
        serialize(bean));
  }

  @Test
  public void inheritedFieldsAndSettersAreParsed() {
    String bean =
        "{'baseValue': 'base-value', "
            + "'baseMethod': 'base-method', "
            + "'classPrivateValue': 'private-value', "
            + "'finalMethod': 'final-method', "
            + "'finalValue': 'final-value', "
            + "'inheritedMethod': 'inherited-method', "
            + "'inheritedValue': 'inherited-value', "
            + "'overrideValue': 'override-value', "
            + "'packageBaseValue': 'package-base-value'}";
    FinalBean finalBean = deserialize(bean, FinalBean.class);
    assertEquals("base-value", finalBean.baseValue);
    assertEquals("inherited-value", finalBean.inheritedValue);
    assertEquals("final-value", finalBean.finalValue);
    assertEquals("base-method", finalBean.getBaseMethod());
    assertEquals("inherited-method", finalBean.getInheritedMethod());
    assertEquals("final-method", finalBean.getFinalMethod());
    assertEquals("override-value-final", finalBean.overrideValue);
    assertEquals("private-value", finalBean.classPrivateValue);
    assertNull(((InheritedBean) finalBean).classPrivateValue);
    assertNull(((BaseBean) finalBean).classPrivateValue);

    InheritedBean inheritedBean = deserialize(bean, InheritedBean.class);
    assertEquals("base-value", inheritedBean.baseValue);
    assertEquals("inherited-value", inheritedBean.inheritedValue);
    assertEquals("base-method", inheritedBean.getBaseMethod());
    assertEquals("inherited-method", inheritedBean.getInheritedMethod());
    assertEquals("override-value-inherited", inheritedBean.overrideValue);
    assertEquals("private-value", inheritedBean.classPrivateValue);
    assertNull(((BaseBean) inheritedBean).classPrivateValue);

    BaseBean baseBean = deserialize(bean, BaseBean.class);
    assertEquals("base-value", baseBean.baseValue);
    assertEquals("base-method", baseBean.getBaseMethod());
    assertEquals("override-value", baseBean.overrideValue);
    assertEquals("private-value", baseBean.classPrivateValue);
  }

  @Test(expected = DatabaseException.class)
  public void settersFromSubclassConflictsWithBaseClass() {
    ConflictingSetterSubBean bean = new ConflictingSetterSubBean();
    bean.value = 1;
    serialize(bean);
  }

  @Test(expected = DatabaseException.class)
  public void settersFromSubclassConflictsWithBaseClass2() {
    ConflictingSetterSubBean2 bean = new ConflictingSetterSubBean2();
    bean.value = 1;
    serialize(bean);
  }

  @Test
  public void settersCanOverridePrimitiveSettersSerializing() {
    NonConflictingSetterSubBean bean = new NonConflictingSetterSubBean();
    bean.value = 1;
    assertJson("{'value': 1}", serialize(bean));
  }

  @Test
  public void settersCanOverridePrimitiveSettersParsing() {
    NonConflictingSetterSubBean bean =
        deserialize("{'value': 2}", NonConflictingSetterSubBean.class);
    // sub-bean converts to negative value
    assertEquals(-2, bean.value);
  }

  @Test(expected = DatabaseException.class)
  public void genericSettersFromSubclassConflictsWithBaseClass() {
    ConflictingGenericSetterSubBean<String> bean = new ConflictingGenericSetterSubBean<>();
    bean.value = "hello";
    serialize(bean);
  }

  // This should work, but generics and subclassing are tricky to get right. For now we will just
  // throw and we can add support for generics & subclassing if it becomes a high demand feature
  @Test(expected = DatabaseException.class)
  public void settersCanOverrideGenericSettersParsingNot() {
    NonConflictingGenericSetterSubBean bean =
        deserialize("{'value': 'value'}", NonConflictingGenericSetterSubBean.class);
    assertEquals("subsetter:value", bean.value);
  }
}
