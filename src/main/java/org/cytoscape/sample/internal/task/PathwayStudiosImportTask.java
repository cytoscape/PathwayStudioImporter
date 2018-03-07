package org.cytoscape.sample.internal.task;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkFactory;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.model.CyNode;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.session.CyNetworkNaming;
import org.cytoscape.task.read.LoadVizmapFileTaskFactory;
import org.cytoscape.view.layout.CyLayoutAlgorithm;
import org.cytoscape.view.layout.CyLayoutAlgorithmManager;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.CyNetworkViewFactory;
import org.cytoscape.view.model.CyNetworkViewManager;
import org.cytoscape.view.model.View;
import org.cytoscape.view.vizmap.VisualMappingManager;
import org.cytoscape.view.vizmap.VisualStyle;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.TaskIterator;
import org.cytoscape.work.TaskMonitor;

public class PathwayStudiosImportTask extends AbstractTask {
	private File file;
	private CyNetworkManager netMgr;
	private CyNetworkFactory cnf;
	private CyNetworkNaming namingUtil;
	private CyNetworkViewFactory networkViewFactory;
	private CyNetworkViewManager networkViewManager;
	private LoadVizmapFileTaskFactory vizmapLoader;
	private VisualMappingManager vmm;
	private CyLayoutAlgorithmManager layoutManager;
	
	private TaskIterator iter;

	public PathwayStudiosImportTask(final File file, final CyServiceRegistrar serviceRegistrar, TaskIterator iter) {
		netMgr = serviceRegistrar.getService(CyNetworkManager.class);
		namingUtil = serviceRegistrar.getService(CyNetworkNaming.class);
		cnf = serviceRegistrar.getService(CyNetworkFactory.class);
		networkViewFactory = serviceRegistrar.getService(CyNetworkViewFactory.class);
		networkViewManager = serviceRegistrar.getService(CyNetworkViewManager.class);
		vizmapLoader = serviceRegistrar.getService(LoadVizmapFileTaskFactory.class);
		vmm = serviceRegistrar.getService(VisualMappingManager.class);
		layoutManager = serviceRegistrar.getService(CyLayoutAlgorithmManager.class);
		
		this.file = file;
		this.iter = iter;
	}

	public CyNetworkView buildCyNetworkView(CyNetwork net) {
		final CyNetworkView view = networkViewFactory.createNetworkView(net);
		networkViewManager.addNetworkView(view);
		InputStream f = getClass().getResourceAsStream("/pathway_style.xml");

		Set<VisualStyle> vsSet = vizmapLoader.loadStyles(f);
		for (VisualStyle s : vsSet) {
			vmm.addVisualStyle(s);
			vmm.setVisualStyle(s, view);

			// s.apply(view);
		}
		view.updateView();

		return view;
	}

	@Override
	public void run(TaskMonitor tm) throws Exception {

		tm.setTitle("Loading Pathway Studios Workbook");
		tm.setProgress(0.0);
		tm.setStatusMessage("Loading excel file...");

		Workbook workbook = null;

		// Load Spreadsheet data for preview.
		InputStream is = new FileInputStream(file);
		
		try {
			workbook = WorkbookFactory.create(is);
		} catch (InvalidFormatException e) {
			e.printStackTrace();
			throw new IllegalArgumentException("Could not read Excel file.  Maybe the file is broken?");
		} finally {
			if (is != null)
				is.close();
		}

		tm.setProgress(0.20);
		tm.setStatusMessage("Reading Node and Interaction Table...");
		// Create an empty network
		CyNetwork myNet = cnf.createNetwork();
		String name = namingUtil.getSuggestedNetworkTitle(file.getName().substring(0, file.getName().lastIndexOf('.')));
		myNet.getRow(myNet).set("name", name);
		PathwayStudiosWorkbookReader reader = new PathwayStudiosWorkbookReader(myNet, workbook);
		reader.read();

		tm.setProgress(0.80);
		tm.setStatusMessage("Creating View...");
		netMgr.addNetwork(myNet);
		CyNetworkView view = buildCyNetworkView(myNet);

		Set<View<CyNode>> views = new HashSet<View<CyNode>>();
		for (View<CyNode> vn : view.getNodeViews())
			views.add(vn);
		CyLayoutAlgorithm algor = layoutManager.getDefaultLayout();
		boolean ready = algor.isReady(view, algor.createLayoutContext(), views, "name");
		if (ready) {
			TaskIterator ti = algor.createTaskIterator(view, algor.createLayoutContext(), views, "name");
			iter.append(ti);
		}
	}

}
