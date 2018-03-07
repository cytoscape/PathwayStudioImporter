package org.cytoscape.sample.internal.task;

import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Vector;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkFactory;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyTable;
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
	private CyNetworkManager netManager;
	private CyNetworkFactory netFactory;
	private CyNetworkNaming namingUtil;
	private CyNetworkViewFactory viewFactory;
	private CyNetworkViewManager viewManager;
	private LoadVizmapFileTaskFactory vizmapLoader;
	private VisualMappingManager vmm;
	private CyLayoutAlgorithmManager layoutManager;
	private Map<String, Integer> headerMap;
	private Map<String, CyNode> nodeMap;
	private Map<String, Class> typeMap;

	private TaskIterator iter;

	public PathwayStudiosImportTask(final File file, final CyServiceRegistrar serviceRegistrar, TaskIterator iter) {
		netManager = serviceRegistrar.getService(CyNetworkManager.class);
		namingUtil = serviceRegistrar.getService(CyNetworkNaming.class);
		netFactory = serviceRegistrar.getService(CyNetworkFactory.class);
		viewFactory = serviceRegistrar.getService(CyNetworkViewFactory.class);
		viewManager = serviceRegistrar.getService(CyNetworkViewManager.class);
		vizmapLoader = serviceRegistrar.getService(LoadVizmapFileTaskFactory.class);
		vmm = serviceRegistrar.getService(VisualMappingManager.class);
		layoutManager = serviceRegistrar.getService(CyLayoutAlgorithmManager.class);

		this.file = file;
		this.iter = iter;
	}

	public CyNetworkView buildCyNetworkView(CyNetwork net) {
		final CyNetworkView view = viewFactory.createNetworkView(net);
		viewManager.addNetworkView(view);
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

	private CyNode getNode(CyNetwork network, String name) {
		if (nodeMap.containsKey(name)) {
			return nodeMap.get(name);
		}
		CyNode node = network.addNode();
		nodeMap.put(name, node);
		return node;
	}

	private void parseTypes(CSVRecord record) {
		for (Entry<String, String> entry : record.toMap().entrySet()) {
			String val = entry.getValue();
			if ("Integer".equals(val)) {
				typeMap.put(entry.getKey(), Integer.class);
			} else if ("Float".equals(val)) {
				typeMap.put(entry.getKey(), Float.class);
			}else{
				typeMap.put(entry.getKey(), String.class);
			}
		}
	}

	private void addRecord(CyNetwork network, CSVRecord record) throws Exception {
		Map<String, String> map = record.toMap();
		String name1 = record.get("Entity 1 Name");
		String name2 = record.get("Entity 2 Name");

		CyNode node1 = getNode(network, name1), node2 = getNode(network, name2);
		CyEdge edge = network.addEdge(node1, node2, false);

		CyTable nodeTable = network.getDefaultNodeTable();
		CyTable edgeTable = network.getDefaultEdgeTable();

		for (String header : headerMap.keySet()) {
			Object value = getValue(header, map.get(header));
			String headerName = header.substring(9);
			if (header.startsWith("Entity 1")) {
				nodeTable.getRow(node1.getSUID()).set(headerName, value);
			} else if (header.startsWith("Entity 2")) {
				nodeTable.getRow(node2.getSUID()).set(headerName, value);
			} else if (header.startsWith("Relation")) {
				edgeTable.getRow(edge.getSUID()).set(headerName, value);
			} else {
				System.out.println("Unrecognized header: " + header);
			}
		}
	}

	private Object getValue(String header, String value) {
		Class c = typeMap.getOrDefault(header, String.class);
		if (c == Integer.class) {
			return Integer.parseInt(value);
		} else if (c == Double.class) {
			return Double.parseDouble(value);
		} else if (c == Float.class) {
			return Float.parseFloat(value);
		} else if (c == Boolean.class) {
			return Boolean.parseBoolean(value);
		}
		return value;
	}

	@Override
	public void run(TaskMonitor tm) throws Exception {

		tm.setTitle("Loading Pathway Studios File");
		tm.setProgress(0.0);
		tm.setStatusMessage("Loading file...");

		CyNetwork network = netFactory.createNetwork();

		FileReader reader = new FileReader(file);
		CSVParser parser = new CSVParser(reader, CSVFormat.EXCEL.withFirstRecordAsHeader());
		headerMap = parser.getHeaderMap();
		
		nodeMap = new HashMap<String, CyNode>();
		typeMap = new HashMap<String, Class>();
		typeMap.put("Relation # of Total References", Integer.class);
		createColumns(network);

		Iterator<CSVRecord> records = parser.iterator();
		int recordNum = 1; // TODO: Change to 0 when the second row is types
		while (records.hasNext()) {
			CSVRecord record = records.next();
			recordNum++;
			if (recordNum == 0) {
				parseTypes(record);
			} else {
				addRecord(network, record);
			}
		}
		parser.close();

		netManager.addNetwork(network);
		CyNetworkView view = buildCyNetworkView(network);

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

	@SuppressWarnings("unchecked")
	private void createColumns(CyNetwork network) {
		CyTable nodeTable = network.getDefaultNodeTable();
		CyTable edgeTable = network.getDefaultEdgeTable();

		for (String header : headerMap.keySet()) {
			String headerName = header.substring(9);
			if (header.startsWith("Entity")) {
				if (nodeTable.getColumn(headerName) == null)
					nodeTable.createColumn(headerName, typeMap.getOrDefault(header, String.class), false);
			} else if (header.startsWith("Relation")) {
				if (edgeTable.getColumn(headerName) == null)
					edgeTable.createColumn(headerName, typeMap.getOrDefault(header, String.class), false);
			}
		}
	}
}
