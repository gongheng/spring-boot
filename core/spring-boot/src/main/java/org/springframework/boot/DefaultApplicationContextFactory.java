/*
 * Copyright 2012-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot;

import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import org.springframework.aot.AotDetector;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.lang.Contract;

/**
 * Default {@link ApplicationContextFactory} implementation that will create an
 * appropriate context for the {@link WebApplicationType}.
 *
 * <p><b>默认ApplicationContext创建策略：</b>
 * <pre>
 * 工作原理：
 * 1. 使用SpringFactoriesLoader加载所有ApplicationContextFactory实现
 * 2. 按优先级调用每个factory的create()方法
 * 3. 返回第一个非null的ApplicationContext
 *
 * ApplicationContextFactory实现顺序：
 * - ServletWebServerApplicationContextFactory (SERVLET类型)
 * - ReactiveWebServerApplicationContextFactory (REACTIVE类型)
 * - DefaultApplicationContextFactory.default实现 (兜底)
 *
 * 创建的ApplicationContext类型：
 * - SERVLET: AnnotationConfigServletWebServerApplicationContext
 * - REACTIVE: AnnotationConfigReactiveWebServerApplicationContext
 * - NONE（非Web）: AnnotationConfigApplicationContext
 *
 * 所有这些ApplicationContext都继承GenericApplicationContext：
 * ✅ BeanFactory在构造时创建（DefaultListableBeanFactory）
 * ✅ refresh()时只设置序列化ID，不重新创建BeanFactory
 * ✅ 支持编程式注册Bean定义
 * ✅ 适合注解驱动和SpringBoot的自动配置
 * </pre>
 *
 * @author Phillip Webb
 */
class DefaultApplicationContextFactory implements ApplicationContextFactory {

	// Method reference is not detected with correct nullability
	@SuppressWarnings("NullAway")
	@Override
	public @Nullable Class<? extends ConfigurableEnvironment> getEnvironmentType(
			@Nullable WebApplicationType webApplicationType) {
		return getFromSpringFactories(webApplicationType, ApplicationContextFactory::getEnvironmentType, null);
	}

	// Method reference is not detected with correct nullability
	@SuppressWarnings("NullAway")
	@Override
	public @Nullable ConfigurableEnvironment createEnvironment(@Nullable WebApplicationType webApplicationType) {
		return getFromSpringFactories(webApplicationType, ApplicationContextFactory::createEnvironment, null);
	}

	// Method reference is not detected with correct nullability
	@SuppressWarnings("NullAway")
	@Override
	public ConfigurableApplicationContext create(@Nullable WebApplicationType webApplicationType) {
		try {
			return getFromSpringFactories(webApplicationType, ApplicationContextFactory::create,
					this::createDefaultApplicationContext);
		}
		catch (Exception ex) {
			throw new IllegalStateException("Unable create a default ApplicationContext instance, "
					+ "you may need a custom ApplicationContextFactory", ex);
		}
	}

	private ConfigurableApplicationContext createDefaultApplicationContext() {
		if (!AotDetector.useGeneratedArtifacts()) {
			return new AnnotationConfigApplicationContext();
		}
		return new GenericApplicationContext();
	}

	@Contract("_, _, !null -> !null")
	private <T> @Nullable T getFromSpringFactories(@Nullable WebApplicationType webApplicationType,
			BiFunction<ApplicationContextFactory, @Nullable WebApplicationType, @Nullable T> action,
			@Nullable Supplier<T> defaultResult) {
		for (ApplicationContextFactory candidate : SpringFactoriesLoader.loadFactories(ApplicationContextFactory.class,
				getClass().getClassLoader())) {
			T result = action.apply(candidate, webApplicationType);
			if (result != null) {
				return result;
			}
		}
		return (defaultResult != null) ? defaultResult.get() : null;
	}

}
