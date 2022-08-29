package com.kevin.apt.core;

import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Names;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaCompiler;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author kevin lau (双鹰)
 */
public class ModelProcessor extends AbstractProcessor {

    private Filer filer;

    /**
     * Element操作类
     */
    private Elements mElementUtils;

    private TreeMaker treeMaker;

    private JavacTrees trees;

    protected Names names;


    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        filer = processingEnv.getFiler();
        mElementUtils = processingEnv.getElementUtils();
        trees = JavacTrees.instance(processingEnv);
        Context context = ((JavacProcessingEnvironment) processingEnv).getContext();
        treeMaker = TreeMaker.instance(context);
        this.names = Names.instance(context);
        super.init(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (annotations == null || annotations.size() == 0) {
            return false;
        }
        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(Model.class);
        Set<String> filed = new HashSet<>();
        for (Element element : elements) {
            TypeElement typeElement = (TypeElement) element;
            final List<? extends Element> enclosedElements = typeElement.getEnclosedElements();
            for (Element enclosedElement : enclosedElements) {
                if (enclosedElement.getKind().equals(ElementKind.FIELD)) {
                    VariableElement variableElement = (VariableElement) enclosedElement;
                    filed.add(variableElement.getSimpleName().toString());
                }
            }
            JCTree.JCCompilationUnit compilationUnit = (JCTree.JCCompilationUnit) trees.getPath(element).getCompilationUnit();

            JCTree tree = trees.getTree(element);
            tree.accept(new TreeTranslator() {
                @Override
                public void visitClassDef(JCTree.JCClassDecl jcClassDecl) {
                    com.sun.tools.javac.util.List<JCTree.JCVariableDecl> jcVariableDeclList = com.sun.tools.javac.util.List.nil();
                    for (JCTree def : jcClassDecl.defs) {
                        if (def.getKind().equals(Tree.Kind.VARIABLE)) {
                            JCTree.JCVariableDecl variableDecl = (JCTree.JCVariableDecl) def;
                            jcVariableDeclList = jcVariableDeclList.append(variableDecl);
                        }
                    }
                    jcVariableDeclList.forEach(t->{
                        jcClassDecl.defs = jcClassDecl.defs.append(makeStaticField(t));
                    });
                    super.visitClassDef(jcClassDecl);
                }
            });
            addQueryModel(element, filed);
        }
        return true;
    }

    private JCTree.JCVariableDecl makeStaticField(JCTree.JCVariableDecl variableDecl) {
        String name = variableDecl.getName().toString();
        return treeMaker.VarDef(
                //访问标志
                treeMaker.Modifiers(Flags.PUBLIC + Flags.STATIC),
                names.fromString( name+ "_field"),
                treeMaker.Ident(names.fromString("String")),
                treeMaker.Literal(name)
        );
    }

    private void addQueryModel(Element element, Set<String> fileds) {
        // 获取节点包信息
        String packageName = mElementUtils.getPackageOf(element).getQualifiedName().toString();
        // 获取节点类信息（如：MainActivity）
        String className = element.getSimpleName().toString();

        //FieldSpec
        final TypeSpec.Builder builder = TypeSpec.classBuilder("Q" + className)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        for (String fi : fileds) {
            FieldSpec field = FieldSpec.builder(String.class, fi + "_filed")
                    .addModifiers(Modifier.PUBLIC, Modifier.FINAL, Modifier.STATIC)
                    .initializer("$S", fi)
                    .build();
            builder.addField(field);

        }
        TypeSpec typeSpec = builder.build();
        JavaFile javaFile = JavaFile.builder(packageName + ".qo", typeSpec)
                .build();
        try {
            javaFile.writeTo(System.out);
            javaFile.writeTo(filer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton(Model.class.getCanonicalName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public Set<String> getSupportedOptions() {
        return super.getSupportedOptions();
    }
}
