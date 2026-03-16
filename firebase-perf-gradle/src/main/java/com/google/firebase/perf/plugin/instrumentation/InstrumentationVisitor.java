/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.perf.plugin.instrumentation;

import com.google.firebase.perf.plugin.FirebasePerfPlugin;
import com.google.firebase.perf.plugin.instrumentation.annotation.AnnotatedMethodAdapter;
import com.google.firebase.perf.plugin.instrumentation.annotation.AnnotatedMethodInstrumentationFactory;
import com.google.firebase.perf.plugin.instrumentation.exceptions.AlreadyPerfInstrumentedException;
import com.google.firebase.perf.plugin.instrumentation.model.AnnotationInfo;
import com.google.firebase.perf.plugin.instrumentation.model.ClassInfo;
import com.google.firebase.perf.plugin.instrumentation.network.NetworkObjectInstrumentation;
import com.google.firebase.perf.plugin.instrumentation.network.NetworkObjectInstrumentationFactory;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;
import org.slf4j.Logger;

/**
 * Using ASM, a bytecode manipulation framework, the apk code is searched to find the functions that
 * were defined in the instrumentation configs:
 *
 * <p>see {@link InstrumentationConfigFactory#annotatedMethodConfigs} and {@link
 * InstrumentationConfigFactory#networkObjectConfigs}.
 *
 * <p>The appropriate instrumentation (annotation or network) is then called:
 *
 * <p>see {@link com.google.firebase.perf.plugin.instrumentation.annotation} and {@link
 * com.google.firebase.perf.plugin.instrumentation.network}.
 */
public class InstrumentationVisitor extends ClassVisitor {

  private static final Logger logger = FirebasePerfPlugin.getLogger();

  private static final boolean LOG_VISITS = false;
  private static final boolean LOG_INSTRUMENTATION = true;

  private final ClassVisitor classVisitor;
  private final InstrumentationConfig instrConfig;

  private final ClassInfo classInfo = new ClassInfo();
  private final InstrumentationContext instrContext = new InstrumentationContext();

  private boolean ending;

  public InstrumentationVisitor(
      final int asmApiVersion,
      final ClassVisitor classVisitor,
      final InstrumentationConfig instrConfig) {
    super(asmApiVersion, classVisitor);

    this.classVisitor = classVisitor;
    this.instrConfig = instrConfig;
  }

  @Override
  public void visit(
      final int version,
      final int access,
      final String className,
      final String signature,
      final String superName,
      final String[] interfaces) {

    super.visit(version, access, className, signature, superName, interfaces);

    if (LOG_VISITS) {
      logger.debug("VISITING CLASS " + className);
      logger.debug(
          "signature: "
              + signature
              + ", superName: "
              + superName
              + ", interfaces: "
              + (interfaces != null && interfaces.length > 0 ? interfaces[0] : ""));
    }

    classInfo.type = Type.getObjectType(className);
    classInfo.interfaces = interfaces;
    instrContext.classMap.put("name", classInfo.type.getClassName());
  }

  @Override
  public void visitOuterClass(final String owner, final String className, final String classDesc) {
    if (LOG_VISITS) {
      logger.debug(
          "visitOuterClass(): owner: "
              + owner
              + ", className: "
              + className
              + ", classDesc: "
              + classDesc);
    }

    super.visitOuterClass(owner, className, classDesc);
  }

  @Override
  public AnnotationVisitor visitAnnotation(final String classDesc, final boolean visible) {
    if (LOG_VISITS) {
      logger.debug("visitAnnotation(): desc: " + classDesc + ", visible: " + visible);
    }

    final AnnotationInfo annotationInfo = new AnnotationInfo();
    annotationInfo.type = Type.getType(classDesc);
    classInfo.annotations.add(annotationInfo);

    final AnnotationVisitor annotationVisitor = super.visitAnnotation(classDesc, visible);

    return new FirebasePerfAnnotationVisitor(api, annotationVisitor, annotationInfo);
  }

  @Override
  public void visitAttribute(final Attribute attribute) {
    if (LOG_VISITS) {
      logger.debug("visitAttribute(): type: " + attribute.type);
    }

    super.visitAttribute(attribute);

    if (!ending && attribute instanceof PerfInstrumentedAttribute) {
      throw new AlreadyPerfInstrumentedException();
    }
  }

  @Override
  public MethodVisitor visitMethod(
      final int access,
      final String methodName,
      final String methodDesc,
      final String signature,
      final String[] exceptions) {

    if (LOG_VISITS) {
      logger.debug(
          "visitMethod(): methodName: "
              + methodName
              + ", methodDesc: "
              + methodDesc
              + ", signature: "
              + signature);
    }

    instrContext.methodMap.clear();

    final MethodVisitor rootMethodVisitor =
        classVisitor.visitMethod(access, methodName, methodDesc, signature, exceptions);

    return new FirebasePerfMethodVisitor(
        classInfo.type.getDescriptor(),
        api,
        rootMethodVisitor,
        access,
        methodName,
        methodDesc,
        instrConfig);
  }

  @Override
  public void visitInnerClass(
      final String name, final String outerName, final String innerName, final int access) {

    if (LOG_VISITS) {
      logger.debug(
          "visitInnerClass() name: "
              + name
              + ", outerName: "
              + outerName
              + ", innerName: "
              + innerName
              + ", access: "
              + access);
    }

    super.visitInnerClass(name, outerName, innerName, access);
  }

  @Override
  public void visitEnd() {
    if (LOG_VISITS) {
      logger.debug("LEAVING CLASS " + classInfo.type.getClassName());
    }

    ending = true;
    visitAttribute(new PerfInstrumentedAttribute(/* extra */ "instrumented"));

    super.visitEnd();
  }

  private class FirebasePerfMethodVisitor extends AdviceAdapter {

    private final InstrumentationConfig instrConfig;
    private final String perfClassDesc;
    private final String perfMethodName;
    private final String perfMethodDesc;

    private final List<AnnotatedMethodAdapter> annotatedMethodAdapters = new ArrayList<>();

    protected FirebasePerfMethodVisitor(
        final String perfClassDesc,
        final int api,
        final MethodVisitor methodVisitor,
        final int access,
        final String perfMethodName,
        final String perfMethodDesc,
        final InstrumentationConfig instrConfig) {

      super(api, methodVisitor, access, perfMethodName, perfMethodDesc);

      this.instrConfig = instrConfig;
      this.perfClassDesc = perfClassDesc;
      this.perfMethodName = perfMethodName;
      this.perfMethodDesc = perfMethodDesc;
    }

    @Override
    public AnnotationVisitor visitAnnotationDefault() {
      if (LOG_VISITS) {
        logger.debug("visitAnnotationDefault()");
      }

      final AnnotationVisitor annotationVisitor = super.visitAnnotationDefault();
      final AnnotationInfo annotationInfo = new AnnotationInfo();

      return new FirebasePerfAnnotationVisitor(api, annotationVisitor, annotationInfo);
    }

    @Override
    public AnnotationVisitor visitAnnotation(final String classDesc, final boolean visible) {
      if (LOG_VISITS) {
        logger.debug("visitAnnotation(): classDesc: " + classDesc + ", visible: " + visible);
      }

      AnnotationVisitor annotationVisitor = super.visitAnnotation(classDesc, visible);
      final AnnotationInfo annotationInfo = new AnnotationInfo();

      annotationVisitor = new FirebasePerfAnnotationVisitor(api, annotationVisitor, annotationInfo);

      // To trap annotated methods, we can't use a regular MethodVisitor
      // wrapper because annotations are visited after the method
      // is visited.  So we have another type of factory that
      // generates instrumentation that we'll call on method enter
      // and exit within this MethodVisitor just like AdviceAdapter.
      final List<AnnotatedMethodInstrumentationFactory> annotatedMethodFactoryList =
          instrConfig.getAnnotatedMethodInstrumentationFactories(classDesc);

      if (annotatedMethodFactoryList != null) {
        if (LOG_INSTRUMENTATION) {
          logger.debug(
              "Instrumenting annotation: "
                  + perfClassDesc
                  + ", perfMethodName: "
                  + perfMethodName
                  + ", perfMethodDesc: "
                  + perfMethodDesc);
        }

        for (final AnnotatedMethodInstrumentationFactory annotatedMethodFactory :
            annotatedMethodFactoryList) {
          // The AnnotationInfo passed to the factory here *will*
          // contain info after this method complete, but not right now.
          annotatedMethodAdapters.add(
              annotatedMethodFactory.newAnnotatedMethodInstrumentation(
                  instrContext,
                  /* adviceAdapter */ this,
                  annotationInfo,
                  perfMethodName,
                  perfMethodDesc));
        }
      }

      return annotationVisitor;
    }

    @Override
    protected void onMethodEnter() {
      super.onMethodEnter();

      for (final AnnotatedMethodAdapter annotationInstr : annotatedMethodAdapters) {
        logger.debug(
            "Apply annotation instrumentation: "
                + perfClassDesc
                + ", perfMethodName: "
                + perfMethodName
                + ", perfMethodDesc: "
                + perfMethodDesc);

        annotationInstr.onMethodEnter();
      }
    }

    @Override
    protected void onMethodExit(final int opcode) {
      super.onMethodExit(opcode);

      for (final AnnotatedMethodAdapter annotationInstr : annotatedMethodAdapters) {
        annotationInstr.onMethodExit();
      }
    }

    @Override
    public void visitMethodInsn(
        final int opcode,
        final String owner,
        final String name,
        final String desc,
        final boolean itf) {

      if (LOG_VISITS) {
        logger.debug(
            "visitMethodInsn(): opcode: "
                + opcode
                + ", owner: "
                + owner
                + ", name: "
                + name
                + ". desc: "
                + desc);
      }

      final NetworkObjectInstrumentationFactory networkObjectFactory =
          instrConfig.getNetworkObjectInstrumentationFactory(owner, name, desc);

      NetworkObjectInstrumentation networkObjectInstr = null;

      if (networkObjectFactory != null) {
        networkObjectInstr = networkObjectFactory.newObjectInstrumentation(owner, name, desc);

        if (LOG_INSTRUMENTATION) {
          logger.debug(
              "Instrumenting return obj [owner: "
                  + owner
                  + ", name: "
                  + name
                  + ", desc: "
                  + desc
                  + "] with: "
                  + networkObjectInstr);
        }
      }

      if (networkObjectInstr != null) {
        networkObjectInstr.injectBefore(mv);

        if (!networkObjectInstr.replaceMethod(mv, opcode)) {
          super.visitMethodInsn(opcode, owner, name, desc, itf);
        }

        networkObjectInstr.injectAfter(mv);

      } else {
        super.visitMethodInsn(opcode, owner, name, desc, itf);
      }
    }
  }

  private static class FirebasePerfAnnotationVisitor extends AnnotationVisitor {

    private final AnnotationInfo annotationInfo;

    public FirebasePerfAnnotationVisitor(
        final int api,
        final AnnotationVisitor annotationVisitor,
        final AnnotationInfo annotationInfo) {

      super(api, annotationVisitor);
      this.annotationInfo = annotationInfo;
    }

    @Override
    public void visit(final String name, final Object value) {
      if (LOG_VISITS) {
        logger.debug("annotation name: " + name + ", value: " + value);
      }

      annotationInfo.values.put(name, value);
      super.visit(name, value);
    }
  }
}
