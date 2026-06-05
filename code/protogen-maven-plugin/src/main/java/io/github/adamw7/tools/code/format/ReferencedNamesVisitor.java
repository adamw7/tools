package io.github.adamw7.tools.code.format;

import java.util.Set;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;

class ReferencedNamesVisitor extends ASTVisitor {

	private final Set<String> referencedNames;

	ReferencedNamesVisitor(Set<String> referencedNames) {
		this.referencedNames = referencedNames;
	}

	@Override
	public boolean visit(ImportDeclaration importDeclaration) {
		return false;
	}

	@Override
	public boolean visit(PackageDeclaration packageDeclaration) {
		return false;
	}

	@Override
	public boolean visit(SimpleName simpleName) {
		referencedNames.add(simpleName.getIdentifier());
		return true;
	}
}
