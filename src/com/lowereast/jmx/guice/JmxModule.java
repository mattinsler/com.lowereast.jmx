package com.lowereast.jmx.guice;

import javax.management.JMX;
import javax.management.MBeanServerConnection;
import javax.management.MXBean;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

public abstract class JmxModule extends AbstractModule {
	public static class Registration {
		public Class<?> beanClass;
		public Class<?> implementationClass;
		public String type;
		public String domain;
		
		private void init(Binder binder, Class<?> beanClass, Class<?> implementationClass) {
			this.beanClass = beanClass;
			this.implementationClass = implementationClass;
			
			// domain is defaulted to the bean's package name
			this.domain = this.beanClass.getPackage().getName();
			
			// type name is defaulted to the bean implementation's type name
			this.type = this.beanClass.getSimpleName();
			
			binder.bind((Class)this.beanClass).to(this.implementationClass).asEagerSingleton();
		}

		public Registration(Binder binder, Class<?> implementationClass) {
			if (implementationClass.isInterface())
				throw new IllegalArgumentException("Implementation class must be a class, not an interface");
			
			Class<?> beanClass = null;
			for (Class<?> i : implementationClass.getInterfaces()) {
				if (i.getName().endsWith("MBean") || i.getName().endsWith("MXBean") || (i.isAnnotationPresent(MXBean.class) && i.getAnnotation(MXBean.class).value())) {
					if (beanClass != null)
						throw new IllegalArgumentException("Implementation class must only implement one bean interface when registered this way");
					beanClass = i;
				}
			}
			
			init(binder, beanClass, implementationClass);
		}
		public Registration(Binder binder, Class<?> beanClass, Class<?> implementationClass) {
			init(binder, beanClass, implementationClass);
		}
	}
	
	public static class Builder {
		private final Registration _registration;
		
		Builder(Binder binder, Registration registration) {
			_registration = registration;
		}
		public Builder toDomain(String domain) {
			_registration.domain = domain;
			return this;
		}
		public Builder asType(String type) {
			_registration.type = type;
			return this;
		}
	}
	
	protected Builder register(Class<?> jmxImplementationClass) {
		Multibinder<Registration> jmxBinder = Multibinder.newSetBinder(binder(), Registration.class, Names.named("JMXRegistrations"));
		Registration registration = new Registration(binder(), jmxImplementationClass);
		jmxBinder.addBinding().toInstance(registration);
		
		return new Builder(binder(), registration);
	}
	
	protected <T> Builder register(Class<T> jmxBeanClass, Class<? extends T> jmxImplementationClass) {
		Multibinder<Registration> jmxBinder = Multibinder.newSetBinder(binder(), Registration.class, Names.named("JMXRegistrations"));
		Registration registration = new Registration(binder(), jmxBeanClass, jmxImplementationClass);
		jmxBinder.addBinding().toInstance(registration);
		
		return new Builder(binder(), registration);
	}

	@Inject
	@Provides
	@Singleton
	MBeanServerConnection provideMBeanServerConnection(@Named("RMIClientPort") int rmiPort, @Named("RMIClientUrlPath") String urlPath) throws Exception {
		JMXServiceURL url = new JMXServiceURL("service:jmx:rmi://localhost/jndi/rmi://localhost:" + rmiPort + "/" + urlPath);
		JMXConnector connector = JMXConnectorFactory.connect(url, null);
		return connector.getMBeanServerConnection();
	}
	
	private static class JmxProxyProvider<T> implements Provider<T> {
		private final Class<T> _interfaceClass;
		private Provider<MBeanServerConnection> _connectionProvider;
		JmxProxyProvider(Class<T> interfaceClass) {
			_interfaceClass = interfaceClass;
		}
		
		@Inject
		void setBeanServerConnectionProvider(Provider<MBeanServerConnection> connectionProvider) {
			_connectionProvider = connectionProvider;
		}

		public T get() {
			try {
				MBeanServerConnection connection = _connectionProvider.get();
				ObjectName name = ObjectName.getInstance(_interfaceClass.getPackage().getName(), "type", _interfaceClass.getSimpleName());
				return JMX.newMBeanProxy(connection, name, _interfaceClass);
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		}
	}

	protected <T> void proxy(Class<T> jmxBeanClass) {
		bind(jmxBeanClass).toProvider(new JmxProxyProvider<T>(jmxBeanClass));
	}
	
	private int _rmiServerPort = -1;
	private String _urlServerPath = "";
	private int _rmiClientPort = -1;
	private String _urlClientPath = "";
	
	@Override
	protected void configure() {
		bindConstant().annotatedWith(Names.named("RMIServerPort")).to(_rmiServerPort);
		bindConstant().annotatedWith(Names.named("RMIServerUrlPath")).to(_urlServerPath);
		bindConstant().annotatedWith(Names.named("RMIClientPort")).to(_rmiClientPort);
		bindConstant().annotatedWith(Names.named("RMIClientUrlPath")).to(_urlClientPath);
		configureJmx();
	}
	
	protected abstract void configureJmx();
	
	public JmxModule withRMIServer(int port, String urlPath) {
		_rmiServerPort = port;
		_urlServerPath = urlPath;
		return this;
	}
	
	public JmxModule withRMIClient(int port, String urlPath) {
		_rmiClientPort = port;
		_urlClientPath = urlPath;
		return this;
	}
}
