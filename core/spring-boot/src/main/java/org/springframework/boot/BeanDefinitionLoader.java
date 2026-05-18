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

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import groovy.lang.Closure;
import org.jspecify.annotations.Nullable;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.groovy.GroovyBeanDefinitionReader;
import org.springframework.beans.factory.support.AbstractBeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.filter.AbstractTypeHierarchyTraversingFilter;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * Loads bean definitions from underlying sources, including XML and JavaConfig. Acts as a
 * simple facade over {@link AnnotatedBeanDefinitionReader},
 * {@link XmlBeanDefinitionReader} and {@link ClassPathBeanDefinitionScanner}. See
 * {@link SpringApplication} for the types of sources that are supported.
 *
 * @author Phillip Webb
 * @author Vladislav Kisel
 * @author Sebastien Deleuze
 * @see #setBeanNameGenerator(BeanNameGenerator)
 */
class BeanDefinitionLoader {

	private static final Pattern GROOVY_CLOSURE_PATTERN = Pattern.compile(".*\\$_.*closure.*");

	private final Object[] sources;

	private final AnnotatedBeanDefinitionReader annotatedReader;

	private final AbstractBeanDefinitionReader xmlReader;

	private final @Nullable BeanDefinitionReader groovyReader;

	private final ClassPathBeanDefinitionScanner scanner;

	private @Nullable ResourceLoader resourceLoader;

	/**
	 * Create a new {@link BeanDefinitionLoader} that will load beans into the specified
	 * {@link BeanDefinitionRegistry}.
	 * @param registry the bean definition registry that will contain the loaded beans
	 * @param sources the bean sources
	 */
	BeanDefinitionLoader(BeanDefinitionRegistry registry, Object... sources) {
		Assert.notNull(registry, "'registry' must not be null");
		Assert.notEmpty(sources, "'sources' must not be empty");
		this.sources = sources;
		this.annotatedReader = new AnnotatedBeanDefinitionReader(registry);
		this.xmlReader = new XmlBeanDefinitionReader(registry);
		this.groovyReader = isGroovyPresent() ? new GroovyBeanDefinitionReader(registry) : null;
		this.scanner = new ClassPathBeanDefinitionScanner(registry);
		this.scanner.addExcludeFilter(new ClassExcludeFilter(sources));
	}

	/**
	 * Set the bean name generator to be used by the underlying readers and scanner.
	 * @param beanNameGenerator the bean name generator
	 */
	void setBeanNameGenerator(BeanNameGenerator beanNameGenerator) {
		this.annotatedReader.setBeanNameGenerator(beanNameGenerator);
		this.scanner.setBeanNameGenerator(beanNameGenerator);
		this.xmlReader.setBeanNameGenerator(beanNameGenerator);
	}

	/**
	 * Set the resource loader to be used by the underlying readers and scanner.
	 * @param resourceLoader the resource loader
	 */
	void setResourceLoader(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
		this.scanner.setResourceLoader(resourceLoader);
		this.xmlReader.setResourceLoader(resourceLoader);
	}

	/**
	 * Set the environment to be used by the underlying readers and scanner.
	 * @param environment the environment
	 */
	void setEnvironment(ConfigurableEnvironment environment) {
		this.annotatedReader.setEnvironment(environment);
		this.scanner.setEnvironment(environment);
		this.xmlReader.setEnvironment(environment);
	}

	/**
	 * Load the sources into the reader.
	 *
	 * <p><b>【在refresh()之前注册主配置类】</b>
	 *
	 * <p>这个方法在SpringApplication.prepareContext()阶段被调用（第489行），
	 * 在refresh()执行之前将主配置类注册到BeanFactory。
	 *
	 * <p><b>调用时机：</b>
	 * <pre>
	 * SpringApplication.run() 【第304行】
	 *   ↓
	 * prepareContext() 【第320行】
	 *   ↓
	 * load(context, sources) 【第489行】
	 *   ↓
	 * BeanDefinitionLoader.load() 【本方法，第128行】
	 *   ↓
	 * annotatedReader.register(source) 【第162行】
	 *   ↓
	 * 注册主配置类到BeanFactory
	 * </pre>
	 *
	 * <p><b>对于SpringBoot应用（如TodoApplication）：</b>
	 * <ul>
	 * <li>source = TodoApplication.class</li>
	 * <li>为TodoApplication创建BeanDefinition</li>
	 * <li>注册到BeanFactory</li>
	 * <li>这样ConfigurationClassPostProcessor执行时就能找到这个配置类</li>
	 * </ul>
	 *
	 * <p><b>为什么在refresh()之前注册？</b>
	 * <pre>
	 * 1. ApplicationContext构造时 → 注册5个内部Bean定义
	 * 2. prepareContext()时 → 注册1个主配置类Bean定义
	 *    【这就是本方法的作用】
	 * 3. refresh()执行时
	 *    → invokeBeanFactoryPostProcessors()
	 *      → ConfigurationClassPostProcessor.processConfigBeanDefinitions()
	 *        → 从这6个Bean定义中找到配置类
	 *        → 扫描组件，加载更多Bean定义
	 * </pre>
	 */
	void load() {
		for (Object source : this.sources) {
			load(source);
		}
	}

	private void load(Object source) {
		Assert.notNull(source, "'source' must not be null");
		if (source instanceof Class<?> type) {
			load(type);
			return;
		}
		if (source instanceof Resource resource) {
			load(resource);
			return;
		}
		if (source instanceof Package pack) {
			load(pack);
			return;
		}
		if (source instanceof CharSequence sequence) {
			load(sequence);
			return;
		}
		throw new IllegalArgumentException("Invalid source type " + source.getClass());
	}

	private void load(Class<?> source) {
		if (this.groovyReader != null && GroovyBeanDefinitionSource.class.isAssignableFrom(source)) {
			// Any GroovyLoaders added in beans{} DSL can contribute beans here
			GroovyBeanDefinitionSource loader = BeanUtils.instantiateClass(source, GroovyBeanDefinitionSource.class);
			((GroovyBeanDefinitionReader) this.groovyReader).beans(loader.getBeans());
		}
		if (isEligible(source)) {
			// ⭐️ 【关键步骤】注册主配置类到BeanFactory
			//
			// 对于SpringBoot应用，这里会注册：
			// - TodoApplication.class（主配置类，带有@SpringBootApplication注解）
			//
			// 调用链：
			// AnnotatedBeanDefinitionReader.register(TodoApplication.class)
			//   ↓
			// registerBean() 【创建BeanDefinition】
			//   ↓
			// BeanDefinitionReaderUtils.registerBeanDefinition() 【注册到BeanFactory】
			//
			// 这样ConfigurationClassPostProcessor执行时（第389行）就能：
			// 1. 通过registry.getBeanDefinitionNames()获取到这个Bean定义
			// 2. 检查它是否是配置类（checkConfigurationClassCandidate）
			// 3. 如果是配置类，解析它的注解（@SpringBootApplication、@ComponentScan等）
			// 4. 扫描组件，加载更多Bean定义
			this.annotatedReader.register(source);
		}
	}

	private void load(Resource source) {
		String filename = source.getFilename();
		Assert.state(filename != null, "Source has no filename");
		if (filename.endsWith(".groovy")) {
			if (this.groovyReader == null) {
				throw new BeanDefinitionStoreException("Cannot load Groovy beans without Groovy on classpath");
			}
			this.groovyReader.loadBeanDefinitions(source);
		}
		else {
			this.xmlReader.loadBeanDefinitions(source);
		}
	}

	private void load(Package source) {
		this.scanner.scan(source.getName());
	}

	private void load(CharSequence source) {
		String resolvedSource = this.scanner.getEnvironment().resolvePlaceholders(source.toString());
		// Attempt as a Class
		try {
			load(ClassUtils.forName(resolvedSource, null));
			return;
		}
		catch (IllegalArgumentException | ClassNotFoundException ex) {
			// swallow exception and continue
		}
		// Attempt as Resources
		if (loadAsResources(resolvedSource)) {
			return;
		}
		// Attempt as package
		Package packageResource = findPackage(resolvedSource);
		if (packageResource != null) {
			load(packageResource);
			return;
		}
		throw new IllegalArgumentException("Invalid source '" + resolvedSource + "'");
	}

	private boolean loadAsResources(String resolvedSource) {
		boolean foundCandidate = false;
		Resource[] resources = findResources(resolvedSource);
		for (Resource resource : resources) {
			if (isLoadCandidate(resource)) {
				foundCandidate = true;
				load(resource);
			}
		}
		return foundCandidate;
	}

	private boolean isGroovyPresent() {
		return ClassUtils.isPresent("groovy.lang.MetaClass", null);
	}

	private Resource[] findResources(String source) {
		ResourceLoader loader = (this.resourceLoader != null) ? this.resourceLoader
				: new PathMatchingResourcePatternResolver();
		try {
			if (loader instanceof ResourcePatternResolver resolver) {
				return resolver.getResources(source);
			}
			return new Resource[] { loader.getResource(source) };
		}
		catch (IOException ex) {
			throw new IllegalStateException("Error reading source '" + source + "'");
		}
	}

	private boolean isLoadCandidate(@Nullable Resource resource) {
		if (resource == null || !resource.exists()) {
			return false;
		}
		if (resource instanceof ClassPathResource classPathResource) {
			// A simple package without a '.' may accidentally get loaded as an XML
			// document if we're not careful. The result of getInputStream() will be
			// a file list of the package content. We double-check here that it's not
			// actually a package.
			String path = classPathResource.getPath();
			if (path.indexOf('.') == -1) {
				try {
					return getClass().getClassLoader().getDefinedPackage(path) == null;
				}
				catch (Exception ex) {
					// Ignore
				}
			}
		}
		return true;
	}

	private @Nullable Package findPackage(CharSequence source) {
		Package pkg = getClass().getClassLoader().getDefinedPackage(source.toString());
		if (pkg != null) {
			return pkg;
		}
		try {
			// Attempt to find a class in this package
			ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(getClass().getClassLoader());
			Resource[] resources = resolver
				.getResources(ClassUtils.convertClassNameToResourcePath(source.toString()) + "/*.class");
			for (Resource resource : resources) {
				String filename = resource.getFilename();
				Assert.state(filename != null, "No filename available");
				String className = StringUtils.stripFilenameExtension(filename);
				load(Class.forName(source + "." + className));
				break;
			}
		}
		catch (Exception ex) {
			// swallow exception and continue
		}
		return getClass().getClassLoader().getDefinedPackage(source.toString());
	}

	/**
	 * Check whether the bean is eligible for registration.
	 * @param type candidate bean type
	 * @return true if the given bean type is eligible for registration, i.e. not a groovy
	 * closure nor an anonymous class
	 */
	private boolean isEligible(Class<?> type) {
		return !(type.isAnonymousClass() || isGroovyClosure(type) || hasNoConstructors(type));
	}

	private boolean isGroovyClosure(Class<?> type) {
		return GROOVY_CLOSURE_PATTERN.matcher(type.getName()).matches();
	}

	private boolean hasNoConstructors(Class<?> type) {
		Constructor<?>[] constructors = type.getDeclaredConstructors();
		return ObjectUtils.isEmpty(constructors);
	}

	/**
	 * Simple {@link TypeFilter} used to ensure that specified {@link Class} sources are
	 * not accidentally re-added during scanning.
	 */
	private static class ClassExcludeFilter extends AbstractTypeHierarchyTraversingFilter {

		private final Set<String> classNames = new HashSet<>();

		ClassExcludeFilter(Object... sources) {
			super(false, false);
			for (Object source : sources) {
				if (source instanceof Class<?> classSource) {
					this.classNames.add(classSource.getName());
				}
			}
		}

		@Override
		protected boolean matchClassName(String className) {
			return this.classNames.contains(className);
		}

	}

	/**
	 * Source for Bean definitions defined in Groovy.
	 */
	@FunctionalInterface
	protected interface GroovyBeanDefinitionSource {

		Closure<?> getBeans();

	}

}
