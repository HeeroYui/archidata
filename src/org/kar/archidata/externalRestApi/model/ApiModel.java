package org.kar.archidata.externalRestApi.model;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApiModel {
	static final Logger LOGGER = LoggerFactory.getLogger(ApiModel.class);

	Class<?> originClass;
	Method orignMethod;

	// Name of the REST end-point name
	public String restEndPoint;
	// Type of the request:
	public RestTypeRequest restTypeRequest;
	// Description of the API
	public String description;
	// need to generate the progression of stream (if possible)
	boolean needGenerateProgress;

	// List of types returned by the API
	public List<ClassModel> returnTypes = new ArrayList<>();;
	// Name of the API (function name)
	public String name;
	// list of all parameters (/{key}/...
	public Map<String, ClassModel> parameters = new HashMap<>();
	// list of all query (?key...)
	public Map<String, ClassModel> queries = new HashMap<>();

	// Possible input type of the REST API
	public List<String> consumes = new ArrayList<>();
	// Possible output type of the REST API
	public List<String> produces = new ArrayList<>();

	private void updateReturnTypes(final Method method, final ModelGroup previousModel) throws Exception {
		// get return type from the user specification:
		final Class<?>[] returnTypeModel = ApiTool.apiAnnotationGetAsyncType(method);
		LOGGER.info("Get return Type async = {}", returnTypeModel);
		if (returnTypeModel != null) {
			if (returnTypeModel.length == 0) {
				throw new IOException("Create a @AsyncType with empty elements ...");
			}
			for (final Class<?> clazz : returnTypeModel) {
				if (clazz == Void.class || clazz == void.class) {
					this.returnTypes.add(previousModel.add(Void.class));
				} else if (clazz == List.class) {
					throw new IOException("Unmanaged List.class in @AsyncType.");
				} else if (clazz == Map.class) {
					throw new IOException("Unmanaged Map.class in @AsyncType.");
				} else {
					this.returnTypes.add(previousModel.add(clazz));
				}
			}
			return;
		}

		final Class<?> returnTypeModelRaw = method.getReturnType();
		LOGGER.info("Get return Type RAW = {}", returnTypeModelRaw.getCanonicalName());
		if (returnTypeModelRaw == Map.class) {
			LOGGER.warn("Model Map");
			final ParameterizedType listType = (ParameterizedType) method.getGenericReturnType();
			this.returnTypes.add(new ClassMapModel(listType, previousModel));
			return;
		} else if (returnTypeModelRaw == List.class) {
			LOGGER.warn("Model List");
			final ParameterizedType listType = (ParameterizedType) method.getGenericReturnType();
			this.returnTypes.add(new ClassListModel(listType, previousModel));
			return;
		} else {
			LOGGER.warn("Model Object");
			this.returnTypes.add(previousModel.add(returnTypeModelRaw));
		}
		LOGGER.warn("List of returns elements:");
		for (final ClassModel elem : this.returnTypes) {
			LOGGER.warn("    - {}", elem);
		}
	}

	public ApiModel(final Class<?> clazz, final Method method, final String baseRestEndPoint,
			final List<String> consume, final List<String> produce, final ModelGroup previousModel) throws Exception {
		this.originClass = clazz;
		this.orignMethod = method;

		final String methodPath = ApiTool.apiAnnotationGetPath(method);
		final String methodType = ApiTool.apiAnnotationGetTypeRequest(method);
		final String methodName = method.getName();

		this.description = ApiTool.apiAnnotationGetOperationDescription(method);
		this.consumes = ApiTool.apiAnnotationGetConsumes2(consume, method);
		this.produces = ApiTool.apiAnnotationProduces2(produce, method);
		LOGGER.trace("    [{}] {} => {}/{}", methodType, methodName, baseRestEndPoint, methodPath);
		this.needGenerateProgress = ApiTool.apiAnnotationTypeScriptProgress(method);

		updateReturnTypes(method, previousModel);
		LOGGER.trace("         return: {}", this.returnTypes.size());
		for (final ClassModel elem : this.returnTypes) {
			LOGGER.trace("             - {}", elem);
		}

		final Map<String, List<ClassModel>> queryParams = new HashMap<>();
		final Map<String, List<ClassModel>> pathParams = new HashMap<>();
		final Map<String, List<ClassModel>> formDataParams = new HashMap<>();
		final List<ClassModel> emptyElement = new ArrayList<>();
		// LOGGER.info(" Parameters:");
		for (final Parameter parameter : method.getParameters()) {
			// Security context are internal parameter (not available from API)
			if (ApiTool.apiAnnotationIsContext(parameter)) {
				continue;
			}
			final Class<?> parameterType = parameter.getType();
			final List<ClassModel> parameterModel = new ArrayList<>();
			final Class<?>[] asyncType = ApiTool.apiAnnotationGetAsyncType(parameter);
			if (asyncType != null) {
				for (final Class<?> elem : asyncType) {
					parameterModel.add(new ClassListModel(elem, previousModel));
				}
			} else if (parameterType == List.class) {
				final Type parameterrizedType = parameter.getParameterizedType();
				parameterModel.add(ClassModel.getModelBase(parameterType, parameterrizedType, previousModel));
			} else if (parameterType == Map.class) {
				final Type parameterrizedType = parameter.getParameterizedType();
				parameterModel.add(ClassModel.getModelBase(parameterType, parameterrizedType, previousModel));
			} else {
				parameterModel.add(ClassModel.getModel(parameterType, previousModel));
			}

			final String pathParam = ApiTool.apiAnnotationGetPathParam(parameter);
			final String queryParam = ApiTool.apiAnnotationGetQueryParam(parameter);
			final String formDataParam = ApiTool.apiAnnotationGetFormDataParam(parameter);
			if (queryParam != null) {
				queryParams.put(queryParam, parameterModel);
			} else if (pathParam != null) {
				pathParams.put(pathParam, parameterModel);
			} else if (formDataParam != null) {
				formDataParams.put(formDataParam, parameterModel);
			} else {
				emptyElement.addAll(parameterModel);
			}
		}
	}
}
