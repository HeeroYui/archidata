package org.kar.archidata.externalRestApi.model;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class ClassModel {
	protected Class<?> originClasses = null;
	protected List<ClassModel> dependencyModels = new ArrayList<>();
	
	public Class<?> getOriginClasses() {
		return this.originClasses;
	}
	
	protected boolean isCompatible(final Class<?> clazz) {
		return this.originClasses == clazz;
	}
	
	public List<ClassModel> getDependencyModels() {
		return this.dependencyModels;
	}
	
	public static ClassModel getModel(final Type type, final ModelGroup previousModel) throws IOException {
		if (type instanceof final ParameterizedType paramType) {
			final Type[] typeArguments = paramType.getActualTypeArguments();
			if (paramType.getRawType() == List.class) {
				return new ClassListModel(typeArguments[0], previousModel);
			}
			if (paramType.getRawType() == Map.class) {
				return new ClassMapModel(typeArguments[0], typeArguments[1], previousModel);
			}
			throw new IOException("Fail to manage parametrized type...");
		}
		return previousModel.add((Class<?>) type);
	}
	
	public static ClassModel getModelBase(
			final Class<?> clazz,
			final Type parameterizedType,
			final ModelGroup previousModel) throws IOException {
		/*
		if (clazz == List.class) {
			return new ClassListModel((ParameterizedType) parameterizedType, previousModel);
		}
		if (clazz == Map.class) {
			return new ClassMapModel((ParameterizedType) parameterizedType, previousModel);
		}
		return previousModel.add(clazz);
		*/
		return getModel(parameterizedType, previousModel);
	}
	
	public static ClassModel getModel(final Class<?> type, final ModelGroup previousModel) throws IOException {
		if (type == List.class) {
			throw new IOException("Fail to manage parametrized type...");
		}
		if (type == Map.class) {
			throw new IOException("Fail to manage parametrized type...");
		}
		return previousModel.add(type);
	}
	
	public abstract void analyze(final ModelGroup group) throws Exception;
	
	public abstract Set<ClassModel> getAlls();

	public List<String> getReadOnlyField() {
		return List.of();
	}
	
}
