package io.github.adamw7.tools.code.format;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.TextEdit;

public class UnusedImportsRemover implements ImportRemoverIfc {

	@Override
	public String removeUnused(String code) {
		CompilationUnit unit = parse(code);
		Set<String> referencedNames = collectReferencedNames(unit);
		return rewriteWithoutUnusedImports(code, unit, referencedNames);
	}

	private CompilationUnit parse(String code) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(code.toCharArray());
		return (CompilationUnit) parser.createAST(null);
	}

	private Set<String> collectReferencedNames(CompilationUnit unit) {
		Set<String> referencedNames = new HashSet<>();
		unit.accept(new ReferencedNamesVisitor(referencedNames));
		return referencedNames;
	}

	private String rewriteWithoutUnusedImports(String code, CompilationUnit unit, Set<String> referencedNames) {
		ASTRewrite rewrite = ASTRewrite.create(unit.getAST());
		for (ImportDeclaration importDeclaration : imports(unit)) {
			removeIfUnused(rewrite, importDeclaration, referencedNames);
		}
		return applyRewrite(code, rewrite);
	}

	@SuppressWarnings("unchecked")
	private List<ImportDeclaration> imports(CompilationUnit unit) {
		return unit.imports();
	}

	private void removeIfUnused(ASTRewrite rewrite, ImportDeclaration importDeclaration, Set<String> referencedNames) {
		if (isUnused(importDeclaration, referencedNames)) {
			rewrite.remove(importDeclaration, null);
		}
	}

	private boolean isUnused(ImportDeclaration importDeclaration, Set<String> referencedNames) {
		return !importDeclaration.isOnDemand() && !referencedNames.contains(importedName(importDeclaration));
	}

	private String importedName(ImportDeclaration importDeclaration) {
		Name name = importDeclaration.getName();
		if (name instanceof QualifiedName qualifiedName) {
			return qualifiedName.getName().getIdentifier();
		}
		return ((SimpleName) name).getIdentifier();
	}

	private String applyRewrite(String code, ASTRewrite rewrite) {
		IDocument document = new Document(code);
		try {
			TextEdit edits = rewrite.rewriteAST(document, null);
			edits.apply(document);
		} catch (BadLocationException e) {
			throw new FormatException(e);
		}
		return document.get();
	}
}
