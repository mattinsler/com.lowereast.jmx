package com.lowereast.jmx;

import com.google.inject.Guice;
import com.lowereast.jmx.guice.JmxModule;

public class Test {
	public interface HelloMXBean {
		String sayHello(String name);
	}
	
	public static class Hello implements HelloMXBean {
		public String sayHello(String name) {
			return "Hello " + name;
		}
	}
	
	public static void main(String[] args) {
		JMXManager.manage(Guice.createInjector(new JmxModule() {
			@Override
			protected void configureJmx() {
				register(Hello.class);
			}
		}));
		
		try {
			System.out.println("Press any key to exit");
			System.in.read();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
