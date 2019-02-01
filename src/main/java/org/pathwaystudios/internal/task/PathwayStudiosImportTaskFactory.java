package org.pathwaystudios.internal.task;

import java.io.File;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;

import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.work.AbstractTaskFactory;
import org.cytoscape.work.TaskIterator;

public class PathwayStudiosImportTaskFactory extends AbstractTaskFactory {

	private final CyServiceRegistrar servicer;

	public PathwayStudiosImportTaskFactory(final CyServiceRegistrar serviceRegistrar) {
		servicer = serviceRegistrar;
	}

	public static String getExtension(File f) {
		String ext = null;
		String s = f.getName();
		int i = s.lastIndexOf('.');

		if (i > 0 && i < s.length() - 1) {
			ext = s.substring(i + 1).toLowerCase();
		}
		return ext;
	}

	@Override
	public TaskIterator createTaskIterator() {
		JFileChooser chooser = new JFileChooser();
		FileFilter filter = new FileFilter() {
			@Override
			public String getDescription() {
				return "Relation Table file (.csv)";
			}

			@Override
			public boolean accept(File f) {
				if (f.isDirectory())
					return true;
				String ext = getExtension(f);
				if (ext.startsWith("csv")) {
					return true;
				}
				return false;
			}
		};
		chooser.setFileFilter(filter);
		File file = null;
		if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
			file = chooser.getSelectedFile();
		}
		
		PathwayStudiosImportTask psit = new PathwayStudiosImportTask(file, servicer);
		TaskIterator iter = new TaskIterator(psit);
		
		return iter;
	}

	@Override
	public boolean isReady() {
		return true;
	}

}
