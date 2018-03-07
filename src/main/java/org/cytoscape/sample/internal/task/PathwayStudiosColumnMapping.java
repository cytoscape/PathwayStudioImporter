package org.cytoscape.sample.internal.task;

import java.util.ArrayList;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

public class PathwayStudiosColumnMapping {
	private ColumnInfo[] columns;

	public PathwayStudiosColumnMapping(Sheet sheet) {
		readHeaderInfo(sheet);
	}

	private void readHeaderInfo(Sheet sheet) {
		Row headerRow = sheet.getRow(0);
		Row typeRow = sheet.getRow(1);

		ArrayList<ColumnInfo> columnList = new ArrayList<ColumnInfo>();

		Cell cell = headerRow.getCell(0);

		for (int i = 0; cell != null; i++, cell = headerRow.getCell(i)) {
			String name = cell.getStringCellValue();
			String typeStr = typeRow.getCell(i).getStringCellValue();
			columnList.add(new ColumnInfo(name, typeStr));
		}

		columns = new ColumnInfo[columnList.size()];
		columnList.toArray(columns);
	}

	public int columnCount() {
		return columns.length;
	}

	public String headerAt(int col) {
		return columns[col].getName();
	}

	public Class<?> dataTypeAt(int col) {
		return columns[col].getType();
	}

	public boolean isList(int col) {
		return columns[col].isList();
	}

	private class ColumnInfo {
		private String name;
		private Class<?> dataType;
		private boolean isList;

		public ColumnInfo(String name, String typeStr) {
			this.name = name;
			if (typeStr.startsWith("List")) {
				isList = true;
				typeStr = typeStr.substring(5);
			}
			switch (typeStr) {
			case "Double":
				dataType = Double.class;
				break;
			case "Boolean":
				dataType = Boolean.class;
				break;
			case "Integer":
				dataType = Integer.class;
				break;
			default:
				dataType = String.class;
			}
		}

		public String getName() {
			return name;
		}

		public Class<?> getType() {
			return dataType;
		}

		public boolean isList() {
			return isList;
		}
	}
}
