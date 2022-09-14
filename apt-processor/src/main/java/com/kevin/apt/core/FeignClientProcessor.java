package com.kevin.apt.core;

import com.sun.source.util.Trees;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Names;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Set;

@SupportedAnnotationTypes({FeignClientProcessor.FeignClient})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class FeignClientProcessor extends AbstractProcessor {
    static final String FeignClient = "org.springframework.cloud.openfeign.FeignClient";
    static final String RequestMapping = "org.springframework.web.bind.annotation.RequestMapping";
    static final String PostMapping = "org.springframework.web.bind.annotation.PostMapping";
    static final String PutMapping = "org.springframework.web.bind.annotation.PutMapping";
    static final String GetMapping = "org.springframework.web.bind.annotation.GetMapping";
    static final String DeleteMapping = "org.springframework.web.bind.annotation.DeleteMapping";
    static final String PatchMapping = "org.springframework.web.bind.annotation.PatchMapping";
    static final String RequestBody = "org.springframework.web.bind.annotation.RequestBody";
    static final String RequestParam = "org.springframework.web.bind.annotation.RequestParam";
    static final String PathVariable = "org.springframework.web.bind.annotation.PathVariable";
    static final String RequestAttribute = "org.springframework.web.bind.annotation.RequestAttribute";

    private Trees trees;
    private TreeMaker treeMaker;
    private Names names;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        JavacProcessingEnvironment jpe = unwrapped(processingEnv);
        this.trees = Trees.instance(jpe);
        this.treeMaker = TreeMaker.instance(jpe.getContext());
        this.names = Names.instance(jpe.getContext());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        annotations.forEach(annotation -> {
            final Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(annotation);
            elements.forEach(element -> {
                if (element.getKind().isInterface()) {
                    JCTree tree = (JCTree) trees.getTree(element);
                    JCTree.JCCompilationUnit unit = (JCTree.JCCompilationUnit) trees.getPath(element).getCompilationUnit();
                    OpenFeignTranslator translator = new OpenFeignTranslator(treeMaker, names);
                    tree.accept(translator);
                    unit.defs = unit.defs.appendList(translator.imports);
                }
            });
        });
        return true;
    }

    private static class OpenFeignTranslator extends TreeTranslator {
        private final TreeMaker treeMaker;
        private final Names names;
        private List<JCTree> imports;
        private List<String> methods;
        private String url;

        private OpenFeignTranslator(TreeMaker treeMaker, Names names) {
            this.treeMaker = treeMaker;
            this.names = names;
            this.imports = List.nil();
            this.methods = List.nil();
        }

        @Override
        public void visitClassDef(JCTree.JCClassDecl jcClassDecl) {
            this.url = generatePath(jcClassDecl);
            super.visitClassDef(jcClassDecl);
//            // OpenFeign新版本不支持RequestMapping注解,URL全部放到方法上
//            List<JCTree.JCAnnotation> annotations = jcClassDecl.mods.annotations;
//            if (null == findAnnotation(RequestMapping, annotations)) {
//                addImport(RequestMapping);
//                jcClassDecl.mods.annotations = annotations.append(
//                        treeMaker.Annotation(treeMaker.Ident(names.fromString(getSimpleName(RequestMapping))),
//                                List.of(treeMaker.Assign(treeMaker.Ident(names.fromString("value")), treeMaker.Literal(generatePath(jcClassDecl))))));
//            }
            this.result = jcClassDecl;
        }

        @Override
        public void visitMethodDef(JCTree.JCMethodDecl jcMethodDecl) {
            super.visitMethodDef(jcMethodDecl);
            List<JCTree.JCAnnotation> annotations = jcMethodDecl.mods.annotations;
            if (null == findAnnotation(RequestMapping, annotations) &&
                    null == findAnnotation(PutMapping, annotations) &&
                    null == findAnnotation(PostMapping, annotations) &&
                    null == findAnnotation(GetMapping, annotations) &&
                    null == findAnnotation(DeleteMapping, annotations) &&
                    null == findAnnotation(PatchMapping, annotations)) {
                addImport(PostMapping);
                jcMethodDecl.mods.annotations = annotations.append(
                        treeMaker.Annotation(treeMaker.Ident(names.fromString(getSimpleName(PostMapping))),
                                List.of(treeMaker.Assign(treeMaker.Ident(names.fromString("value")), treeMaker.Literal(generatePath(jcMethodDecl))))));
                if (null != jcMethodDecl.params && jcMethodDecl.params.size() == 1) {
                    JCTree.JCVariableDecl jcVariableDecl = jcMethodDecl.params.get(0);
                    annotations = jcVariableDecl.mods.annotations;
                    if (null == findAnnotation(RequestBody, annotations) &&
                            null == findAnnotation(RequestParam, annotations) &&
                            null == findAnnotation(PathVariable, annotations) &&
                            null == findAnnotation(RequestAttribute, annotations)) {
                        addImport(RequestBody);
                        jcVariableDecl.mods.annotations = annotations.append(
                                treeMaker.Annotation(treeMaker.Ident(names.fromString(getSimpleName(RequestBody))), List.nil()));
                    }
                }
            }
        }

        private void addImport(String qualifiedName) {
            imports = imports.append(
                    treeMaker.Import(treeMaker.Select(treeMaker.Ident(names.fromString(getPackageName(qualifiedName)))
                            , names.fromString(getSimpleName(qualifiedName))), false));
        }

        private String generatePath(JCTree.JCClassDecl jcClassDecl) {
            String feignName = "open-feign";
            JCTree.JCAnnotation openFeign = findAnnotation(FeignClient, jcClassDecl.mods.annotations);
            if (null != openFeign) {
                String name = findAnnotationValue("value", openFeign);
                if (null == name || name.length() == 0) {
                    name = findAnnotationValue("name", openFeign);
                }
                if (null != name && name.length() > 0) {
                    feignName = name;
                }
            }
            String simpleName = jcClassDecl.name.toString();
            return "/api/gaia/" + feignName + "/" + simpleName;
        }

        private String generatePath(JCTree.JCMethodDecl jcMethodDecl) {
            String method = jcMethodDecl.getName().toString();
            String path = method;
            long count = methods.stream().filter(m -> m.equals(method)).count();
            if (count > 0) {
                path = path + count;
            }
            methods = methods.append(method);
            return url + "/" + path;
        }

        private JCTree.JCAnnotation findAnnotation(String annotationSymbol, List<JCTree.JCAnnotation> annotations) {
            if (null == annotations) {
                return null;
            }
            for (JCTree.JCAnnotation annotation : annotations) {
                if (annotation.annotationType instanceof JCTree.JCIdent &&
                        annotationSymbol.equals(((JCTree.JCIdent) annotation.annotationType).sym.getQualifiedName().toString())) {
                    return annotation;
                }
            }
            return null;
        }

        private String findAnnotationValue(String key, JCTree.JCAnnotation annotation) {
            if (null == annotation.args) {
                return null;
            }
            for (JCTree.JCExpression expression : annotation.args) {
                if (expression instanceof JCTree.JCAssign && key.equals(((JCTree.JCAssign) expression).lhs.toString())) {
                    return ((JCTree.JCLiteral) ((JCTree.JCAssign) expression).rhs).value.toString();
                }
            }
            return null;
        }

        private String getPackageName(String qualifiedName) {
            return qualifiedName.substring(0, qualifiedName.lastIndexOf("."));
        }

        private String getSimpleName(String qualifiedName) {
            return qualifiedName.substring(qualifiedName.lastIndexOf(".") + 1);
        }
    }

    private JavacProcessingEnvironment unwrapped(ProcessingEnvironment pe) {
        if (pe instanceof JavacProcessingEnvironment) {
            return (JavacProcessingEnvironment) pe;
        }
        JavacProcessingEnvironment delegate = getJavaProxyDelegateIfNecessary(pe);
        if (delegate == null) {
            throw new IllegalStateException("not found JavacProcessingEnvironment");
        }
        return delegate;
    }

    private JavacProcessingEnvironment getJavaProxyDelegateIfNecessary(ProcessingEnvironment pe) {
        try {
            InvocationHandler handler = Proxy.getInvocationHandler(pe);
            java.lang.reflect.Field delegateField = null;
            Class<?> handlerClass = handler.getClass();
            while (handlerClass != null) {
                try {
                    delegateField = handlerClass.getDeclaredField("val$delegateTo");
                    break;
                } catch (NoSuchFieldException e) {
                    // no field, ignore
                }
                handlerClass = handlerClass.getSuperclass();
            }
            if (delegateField == null) {
                return null;
            }
            delegateField.setAccessible(true);
            ProcessingEnvironment delegate = (ProcessingEnvironment) delegateField.get(handler);
            if (delegate != null) {
                return unwrapped(delegate);
            }
        } catch (Exception e) {
            // not a idea proxy, ignore
        }
        return null;
    }
}
