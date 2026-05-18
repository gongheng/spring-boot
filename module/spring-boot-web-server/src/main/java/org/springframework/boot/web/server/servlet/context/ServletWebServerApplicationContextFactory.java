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

package org.springframework.boot.web.server.servlet.context;

import org.jspecify.annotations.Nullable;

import org.springframework.aot.AotDetector;
import org.springframework.boot.ApplicationContextFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.web.context.servlet.ApplicationServletEnvironment;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * {@link ApplicationContextFactory} registered in {@code spring.factories} to support
 * {@link AnnotationConfigServletWebServerApplicationContext} and
 * {@link ServletWebServerApplicationContext}.
 *
 * <p><b>Servlet Web应用的ApplicationContext创建工厂：</b>
 * <pre>
 * 调用时机：
 * SpringApplication.createApplicationContext() [第579行]
 *   ↓
 * applicationContextFactory.create(WebApplicationType.SERVLET)
 *   ↓
 * ServletWebServerApplicationContextFactory.create() [第50行]
 *   ↓
 * createContext() [第54行]
 *
 * 创建的ApplicationContext类型：
 * - 非AOT环境：AnnotationConfigServletWebServerApplicationContext
 * - AOT环境：ServletWebServerApplicationContext
 *
 * 继承关系：
 * AnnotationConfigServletWebServerApplicationContext
 *   → extends ServletWebServerApplicationContext
 *     → extends GenericWebApplicationContext
 *       → extends GenericApplicationContext 【关键！继承GenericApplicationContext】
 *         → extends AbstractApplicationContext
 *
 * 为什么使用GenericApplicationContext？
 * ✅ 支持注解配置（@Configuration、@Component等）
 * ✅ 不需要多次刷新BeanFactory
 * ✅ BeanFactory在构造时就创建好
 * ✅ 性能更优（refresh()时不需要重新创建BeanFactory）
 * </pre>
 *
 * @author Phillip Webb
 * @author Andy Wilkinson
 */
class ServletWebServerApplicationContextFactory implements ApplicationContextFactory {

	@Override
	public @Nullable Class<? extends ConfigurableEnvironment> getEnvironmentType(
			@Nullable WebApplicationType webApplicationType) {
		return (webApplicationType != WebApplicationType.SERVLET) ? null : ApplicationServletEnvironment.class;
	}

	@Override
	public @Nullable ConfigurableEnvironment createEnvironment(@Nullable WebApplicationType webApplicationType) {
		return (webApplicationType != WebApplicationType.SERVLET) ? null : new ApplicationServletEnvironment();
	}

	@Override
	public @Nullable ConfigurableApplicationContext create(@Nullable WebApplicationType webApplicationType) {
		return (webApplicationType != WebApplicationType.SERVLET) ? null : createContext();
	}

	private ConfigurableApplicationContext createContext() {
		if (!AotDetector.useGeneratedArtifacts()) {
			return new AnnotationConfigServletWebServerApplicationContext();
		}
		return new ServletWebServerApplicationContext();
	}

}
