package io.github.adamw7.tools.code.gen;

public abstract class AbstractStatements {

	protected final Methods methods;
	protected final String optionalIfcName;
	protected final String optionalImplName;
	protected final String header;
	protected final ClassInfo info;

	public AbstractStatements(ClassInfo info, TypeMappings typeMappings, String header) {
		this.info = info;
		methods = new Methods(typeMappings, info.name());
		this.optionalIfcName = info.name() + "OptionalIfc";
		this.optionalImplName = info.name() + "OptionalImpl";		
		this.header = header;
	}

}
