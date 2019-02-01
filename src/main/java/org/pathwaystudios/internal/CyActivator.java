 package org.pathwaystudios.internal;

import org.cytoscape.work.TaskFactory;

import org.osgi.framework.BundleContext;
import org.pathwaystudios.internal.task.PathwayStudiosImportTaskFactory;
import org.cytoscape.service.util.AbstractCyActivator;
import org.cytoscape.service.util.CyServiceRegistrar;

import java.util.Properties;

public class CyActivator extends AbstractCyActivator {
	public CyActivator() {
		super();
	}

	public void start(BundleContext bc) {
        final CyServiceRegistrar serviceRegistrar = getService(bc, CyServiceRegistrar.class);
        
		PathwayStudiosImportTaskFactory factory = new PathwayStudiosImportTaskFactory(serviceRegistrar);
		Properties props = new Properties();
		props.setProperty("preferredMenu", "Apps");
		props.setProperty("title", "Pathway Studio Import");
		registerService(bc, factory, TaskFactory.class, props);

	}
}
