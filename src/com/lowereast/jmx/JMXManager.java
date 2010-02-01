package com.lowereast.jmx;

import java.rmi.registry.LocateRegistry;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.MBeanServerInvocationHandler;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXConnectorServerMBean;
import javax.management.remote.JMXServiceURL;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.name.Named;
import com.lowereast.jmx.guice.JmxModule;

public final class JMXManager {
	private static MBeanServer _beanServer;

	private static class Manager {
		private final Injector _injector;

		private final int _rmiPort;
		private final String _urlPath;
		private final boolean _isRmiEnabled;
		private final Set<JmxModule.Registration> _registrations;

		@Inject
		Manager(Injector injector, @Named("JMXRegistrations") Set<JmxModule.Registration> registrations, @Named("RMIServerPort") int rmiPort, @Named("RMIServerUrlPath") String urlPath) {
			_injector = injector;
			_rmiPort = rmiPort;
			_urlPath = urlPath;
			_registrations = registrations;
			_isRmiEnabled = (rmiPort != -1);
		}

		private MBeanServer getBeanServer() {
			if (_beanServer == null) {
				try {
					_beanServer = MBeanServerFactory.createMBeanServer();
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}

			return _beanServer;
		}

		private void startRmiServer(MBeanServer beanServer) {
			try {
				LocateRegistry.createRegistry(_rmiPort);
				
				JMXServiceURL url = new JMXServiceURL("service:jmx:rmi://localhost/jndi/rmi://localhost:" + _rmiPort + "/" + _urlPath);
				System.out.println(url);
				JMXConnectorServer connectorServer = JMXConnectorServerFactory.newJMXConnectorServer(url, null, beanServer);

				 ObjectName connectorServerName = ObjectName.getInstance("connectors", "protocol", "rmi");
				 beanServer.registerMBean(connectorServer, connectorServerName);

				 connectorServer.start();

				 beanServer.invoke(connectorServerName, "start", null, null);

				 ((JMXConnectorServerMBean)MBeanServerInvocationHandler.newProxyInstance(beanServer, connectorServerName, JMXConnectorServerMBean.class, true)).start();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		private void registerBeans(MBeanServer beanServer) {
			for (JmxModule.Registration registration : _registrations) {
				try {
					ObjectName name = ObjectName.getInstance(registration.domain, "type", registration.type);
					beanServer.registerMBean(_injector.getInstance(registration.beanClass), name);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		}

		public void start() {
			if (_registrations.size() == 0)
				return;

			MBeanServer beanServer = getBeanServer();
			registerBeans(beanServer);

			if (_isRmiEnabled)
				startRmiServer(beanServer);
		}
	}

	public static void manage(Injector injector) {
		injector.getInstance(Manager.class).start();
	}
}
