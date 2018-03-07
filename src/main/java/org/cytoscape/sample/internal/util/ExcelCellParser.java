package org.cytoscape.sample.internal.util;

import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;

public class ExcelCellParser {
	private static final String DELIMITER = ";";

	public static Object getCellValue(Cell c) {
		if (c == null) {
			return null;
		}
		switch (c.getCellType()) {
		case Cell.CELL_TYPE_STRING:
			return c.getStringCellValue();
		case Cell.CELL_TYPE_NUMERIC:
			return c.getNumericCellValue();
		case Cell.CELL_TYPE_BOOLEAN:
			return c.getBooleanCellValue();
		}
		return null;
	}

	private static Object parseValue(String val, Class<?> cls) {
		if (cls == String.class) {
			return String.valueOf(val);
		} else if (cls == Boolean.class) {
			return Boolean.valueOf(val);
		} else if (cls == Double.class) {
			return Double.valueOf(val);
		} else if (cls == Integer.class) {
			if (val.contains("."))
				val = val.substring(0, val.lastIndexOf("."));
			return Integer.valueOf(val);
		} else if (cls == Long.class) {
			return Long.valueOf(val);
		}
		return val;
	}

	public static Object parseValue(String val, Class<?> cls, boolean isList) {
		if (isList) {
			List<Object> list = new ArrayList<Object>();
			for (String s : val.split(DELIMITER)) {
				list.add(parseValue(s, cls));
			}
			return list;

		}
		return parseValue(val, cls);
	}
}
