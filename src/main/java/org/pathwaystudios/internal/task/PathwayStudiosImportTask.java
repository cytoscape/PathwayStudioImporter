package org.pathwaystudios.internal.task;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkFactory;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.service.util.CyServiceRegistrar;
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
import org.cytoscape.work.TaskMonitor;

public class PathwayStudiosImportTask extends AbstractTask {
	private File file;
	private CyNetworkManager netManager;
	private CyNetworkFactory netFactory;
	private CyNetworkViewFactory viewFactory;
	private CyNetworkViewManager viewManager;
	private LoadVizmapFileTaskFactory vizmapLoader;
	private VisualMappingManager vmm;
	private CyLayoutAlgorithmManager layoutManager;
	private Map<String, Integer> headerMap;
	private Map<String, CyNode> nodeMap;
	private Map<String, String> typeMap;


	public PathwayStudiosImportTask(final File file, final CyServiceRegistrar serviceRegistrar) {
		netManager = serviceRegistrar.getService(CyNetworkManager.class);
		netFactory = serviceRegistrar.getService(CyNetworkFactory.class);
		viewFactory = serviceRegistrar.getService(CyNetworkViewFactory.class);
		viewManager = serviceRegistrar.getService(CyNetworkViewManager.class);
		vizmapLoader = serviceRegistrar.getService(LoadVizmapFileTaskFactory.class);
		vmm = serviceRegistrar.getService(VisualMappingManager.class);
		layoutManager = serviceRegistrar.getService(CyLayoutAlgorithmManager.class);

		this.file = file;
	}

	public CyNetworkView buildCyNetworkView(CyNetwork net) {
		final CyNetworkView view = viewFactory.createNetworkView(net);
		viewManager.addNetworkView(view);

		VisualStyle vs = null;
		for (VisualStyle style : vmm.getAllVisualStyles()) {
			if (style.getTitle().startsWith("Pathway Studios Style")) {
				vs = style;
				break;
			}
		}
		if (vs == null) {
			InputStream f = getClass().getResourceAsStream("/pathway_style.xml");
			Set<VisualStyle> vsSet = vizmapLoader.loadStyles(f);
			vs = vsSet.iterator().next();
			vmm.addVisualStyle(vs);
			vs.setTitle("Pathway Studios Style");
		}
		
		vmm.setVisualStyle(vs, view);
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

	private void addRecord(CyNetwork network, CSVRecord record) {
		Map<String, String> map = record.toMap();
		String name1 = record.get("Entity 1 Name");
		String name2 = record.get("Entity 2 Name");

		CyNode node1 = getNode(network, name1), node2 = getNode(network, name2);
		CyEdge edge = network.addEdge(node1, node2, false);

		CyRow row;

		for (String header : headerMap.keySet()) {
			if (header.startsWith("Entity 1")) {
				row = network.getDefaultNodeTable().getRow(node1.getSUID());
			} else if (header.startsWith("Entity 2")) {
				row = network.getDefaultNodeTable().getRow(node2.getSUID());
			} else if (header.startsWith("Relation")) {
				row = network.getDefaultEdgeTable().getRow(edge.getSUID());
			} else {
				System.out.println("Unrecognized header: " + header);
				continue;
			}
			try {
				setValue(row, header, map.get(header));
			} catch (Exception e) {
				System.out.println("Failed to set " + header + " to " + map.get(header) + " as " + typeMap.get(header));
			}
		}
	}

	private void setValue(CyRow row, String header, String valueStr) throws Exception {
		if (valueStr.isEmpty())
			return;
		String type = typeMap.getOrDefault(header, "String");
		String headerName = header.substring(9);
		Object value = valueStr;
		if (type.endsWith("List")) {
			if (type.startsWith("Integer")) {
				ArrayList<Integer> nums = new ArrayList<Integer>();
				for (String s : valueStr.split(";")) {
					nums.add(Integer.parseInt(s));
				}
				value = nums;
			} else if (type.startsWith("Float")) {
				ArrayList<Double> nums = new ArrayList<Double>();
				for (String s : valueStr.split(";")) {
					nums.add(Double.parseDouble(s));
				}
				value = nums;
			} else {
				ArrayList<String> strs = new ArrayList<String>();
				for (String s : valueStr.split(";")) {
					strs.add(s);
				}
				value = strs;
			}
		} else if (type.startsWith("Integer")) {
			value = Integer.parseInt(valueStr);
		} else if (type.startsWith("Float")) {
			value = Double.parseDouble(valueStr);
		}

		row.set(headerName, value);
	}

	@Override
	public void run(TaskMonitor tm) throws IOException {
		if (file == null || !file.isFile()) {
			return;
		}
		
		tm.setTitle("Loading Pathway Studios File");
		tm.setProgress(0.0);
		tm.setStatusMessage("Loading file...");

		CyNetwork network = netFactory.createNetwork();

		FileReader reader = new FileReader(file);
		CSVParser parser = new CSVParser(reader, CSVFormat.EXCEL.withFirstRecordAsHeader());
		headerMap = parser.getHeaderMap();

		nodeMap = new HashMap<String, CyNode>();

		Iterator<CSVRecord> records = parser.iterator();
		if (!records.hasNext()) {
			parser.close();
			reader.close();
			return;
		}
		typeMap = records.next().toMap();
		createColumns(network);

		while (records.hasNext()) {
			CSVRecord record = records.next();
			addRecord(network, record);
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
			insertTasksAfterCurrentTask(algor.createTaskIterator(view, algor.createLayoutContext(), views, "name"));
		}
	}

	@SuppressWarnings("rawtypes")
	public Class getType(String type) {
		if (type.startsWith("Float"))
			return Double.class;
		else if (type.startsWith("Integer"))
			return Integer.class;
		return String.class;
	}

	@SuppressWarnings("unchecked")
	private void createColumn(CyTable table, String fullName) {
		// remove "Entity # " or "Relation "
		String name = fullName.substring(9);
		String type = typeMap.get(fullName);

		if (table.getColumn(name) == null) {
			if (type.endsWith("List")) {
				table.createListColumn(name, getType(type), false);
			} else {
				table.createColumn(name, getType(type), false);
			}
		}
	}

	private void createColumns(CyNetwork network) {
		CyTable nodeTable = network.getDefaultNodeTable();
		CyTable edgeTable = network.getDefaultEdgeTable();

		for (String header : headerMap.keySet()) {
			if (header.startsWith("Entity")) {
				createColumn(nodeTable, header);
			} else if (header.startsWith("Relation")) {
				createColumn(edgeTable, header);
			}
		}
	}
}
