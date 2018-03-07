package org.cytoscape.sample.internal.task;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.sample.internal.util.ExcelCellParser;

public class PathwayStudiosWorkbookReader {
	private final CyNetwork network;
	private final CyTable nodeTable, edgeTable;
	private Map<Object, CyNode> nodeMap;
	private final Sheet nodeSheet, interactionSheet;

	public PathwayStudiosWorkbookReader(CyNetwork network, Workbook workbook) {
		nodeMap = new LinkedHashMap<>();
		this.nodeSheet = workbook.getSheetAt(0);
		this.interactionSheet = workbook.getSheetAt(1);
		this.network = network;
		this.nodeTable = network.getDefaultNodeTable();
		this.edgeTable = network.getDefaultEdgeTable();
	}

	public void read() {
		parseNodes();
		parseEdges();
	}

	public void parseNodes() {
		PathwayStudiosColumnMapping mapping = new PathwayStudiosColumnMapping(nodeSheet);

		for (int rowId = 2; rowId < nodeSheet.getLastRowNum() + 1; rowId++) {
			Row r = nodeSheet.getRow(rowId);
			if (r == null)
				break;
			Cell c = r.getCell(0);
			if (c == null || c.getStringCellValue().isEmpty()){
				break;
			}
			CyNode node = parseRowNode(r);
			for (int col = 0; col < mapping.columnCount(); col++) {
				Object val = ExcelCellParser.getCellValue(r.getCell(col));
				if (val != null) {
					if (nodeTable.getColumn(mapping.headerAt(col)) == null) {
						if (mapping.isList(col)) {
							nodeTable.createListColumn(mapping.headerAt(col), mapping.dataTypeAt(col), false);
						} else {
							nodeTable.createColumn(mapping.headerAt(col), mapping.dataTypeAt(col), false);
						}
					}
					val = ExcelCellParser.parseValue(String.valueOf(val), mapping.dataTypeAt(col), mapping.isList(col));
					network.getRow(node).set(mapping.headerAt(col), val);
				}
			}
		}
	}

	public void parseEdges() {
		int i = 0;
		PathwayStudiosColumnMapping mapping = new PathwayStudiosColumnMapping(interactionSheet);
		for (int rowId = 2; i < interactionSheet.getLastRowNum() + 1; rowId++) {
			Row r = interactionSheet.getRow(rowId);
			if (r == null)
				break;
			Cell c = r.getCell(0);
			if (c == null || c.getStringCellValue().isEmpty()) {
				break;
			}
			
			
			CyEdge edge = parseRowEdge(r);
			if (edge == null){
				continue;
			}
			for (int col = 0; col < mapping.columnCount(); col++) {
				Object val = ExcelCellParser.getCellValue(r.getCell(col));
				if (mapping.headerAt(col).equals("Effect") && val == null)
					val = edge.isDirected() ? "directed" : "";
				if (val != null) {
					if (edgeTable.getColumn(mapping.headerAt(col)) == null) {
						if (mapping.isList(col)) {
							edgeTable.createListColumn(mapping.headerAt(col), mapping.dataTypeAt(col), true);

						} else {
							edgeTable.createColumn(mapping.headerAt(col), mapping.dataTypeAt(col), true);
						}
					}
					val = ExcelCellParser.parseValue(String.valueOf(val), mapping.dataTypeAt(col), mapping.isList(col));
					network.getRow(edge).set(mapping.headerAt(col), val);
				}
			}

		}
	}

	private CyNode parseRowNode(Row r) {
		CyNode node = network.addNode();
		CyRow row = network.getRow(node);
		String name = r.getCell(0).getStringCellValue();
		row.set("name", name);
		nodeMap.put(name, node);
		return node;
	}

	private CyEdge parseRowEdge(Row r) {
		String nodes = r.getCell(0).getStringCellValue();
		String[] pair = nodes.split(":");
		String[] nodeArr = pair[1].trim().split("[-+\\|>]+");
		String nameA = nodeArr[0].trim(), nameB = nodeArr[1].trim();

		if (!nodeMap.containsKey(nameA)) {
			System.out.println("MISSING NODE " + nameA);
			return null;
		}
		if (!nodeMap.containsKey(nameB)) {
			System.out.println("MISSING NODE " + nameB);
			return null;
		}

		CyNode nodeA = nodeMap.get(nameA), nodeB = nodeMap.get(nameB);
		boolean isDirected = !pair[1].contains("----");
		CyEdge edge = network.addEdge(nodeA, nodeB, isDirected);
		network.getRow(edge).set(CyEdge.INTERACTION, pair[0]);
		String edgeName = nameA + " (" + pair[0] + ") " + nameB;
		network.getRow(edge).set(CyNetwork.NAME, edgeName);
		

		return edge;
	}
}
