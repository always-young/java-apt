package com.kevin.apt.core.demo;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeTranslator;

public class DemoTreeTranslator extends TreeTranslator {

    @Override
    public void visitClassDef(JCTree.JCClassDecl jcClassDecl) {
        //更改 jcClassDecl.defs来更改子节点
        super.visitClassDef(jcClassDecl);
    }

    @Override
    public void visitMethodDef(JCTree.JCMethodDecl jcMethodDecl) {
        //jcMethodDecl.params 更改参数
        // jcMethodDecl.body 更改方法体
        // jcMethodDecl.restype 更改返回值
        super.visitMethodDef(jcMethodDecl);
    }

    @Override
    public void visitVarDef(JCTree.JCVariableDecl jcVariableDecl) {
        //jcVariableDecl.init 更改初始化语句
        //jcVariableDecl.name 更改名称
        //jcVariableDecl.type 更改类型
        super.visitVarDef(jcVariableDecl);
    }
}
