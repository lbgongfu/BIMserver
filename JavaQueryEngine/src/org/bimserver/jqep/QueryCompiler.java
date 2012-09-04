package org.bimserver.jqep;

/******************************************************************************
 * Copyright (C) 2009-2012  BIMserver.org
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *****************************************************************************/

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

import org.bimserver.models.store.CompileResult;
import org.bimserver.models.store.RunResult;
import org.bimserver.models.store.StoreFactory;
import org.bimserver.plugins.VirtualClassLoader;
import org.bimserver.plugins.VirtualFile;
import org.bimserver.plugins.VirtualFileManager;
import org.bimserver.plugins.serializers.IfcModelInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QueryCompiler {
	private static final Logger LOGGER = LoggerFactory.getLogger(QueryCompiler.class);
	private static String libPath = System.getProperty("java.class.path");
	private final ClassLoader classLoader;
	private final JavaFileManager pluginFileManager;

	public QueryCompiler() {
		this.classLoader = getClass().getClassLoader();
		pluginFileManager = ToolProvider.getSystemJavaCompiler().getStandardFileManager(null, null, null);
	}
	
	public QueryCompiler(ClassLoader classLoader, JavaFileManager pluginFileManager) {
		this.classLoader = classLoader;
		this.pluginFileManager = pluginFileManager;
	}
	
	public static void addJarFolder(File libDir) {
		if (libDir.exists() && libDir.isDirectory()) {
			for (File file : libDir.listFiles()) {
				if (file.getName().endsWith(".jar")) {
					libPath += file.getAbsolutePath() + File.pathSeparator;
				}
			}
		}
		LOGGER.debug("libPath: " + libPath);
	}
	
	private void getJavaFiles(List<VirtualFile> fileList, VirtualFile baseDir) {
		for (VirtualFile f : baseDir.listFiles()) {
			if (f.isDirectory()) {
				getJavaFiles(fileList, f);
			} else {
				if (f.getName().endsWith(".java")) {
					fileList.add(f);
				}
			}
		}
	}

	public RunResult run(String code, IfcModelInterface model) {
		RunResult runResult = StoreFactory.eINSTANCE.createRunResult();
		runResult.setRunOke(true);
		try {
			StringWriter out = new StringWriter();
			QueryInterface queryInterface = createQueryInterface(code);
			queryInterface.query(model, new PrintWriter(out));
			runResult.setOutput("Executing...\n\n" + out + "\n" + "Execution complete");
			runResult.setRunOke(true);
		} catch (CompileException e) {
			runResult.setRunOke(false);
			runResult.getErrors().add(e.getMessage());
		}
		return runResult;
	}

	private QueryInterface createQueryInterface(String code) throws CompileException {
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		if (compiler == null) {
			throw new CompileException("JDK needed for compile tasks");
		}
		VirtualFile baseDir = new VirtualFile(null, null);
		VirtualFile file = baseDir.createFile("org" + File.separator + "bimserver" + File.separator + "jqep" + File.separator + "Query.java");
		file.setStringContent(code);
		VirtualFileManager myFileManager = new VirtualFileManager(pluginFileManager, classLoader, baseDir);

		List<VirtualFile> fileList = new ArrayList<VirtualFile>();
		getJavaFiles(fileList, baseDir);
		List<VirtualFile> compilationUnits = baseDir.getAllJavaFileObjects();

		List<String> options = new ArrayList<String>();
		options.add("-cp");
		options.add(libPath);
//		options.add("-target");
//		options.add("7");

		DiagnosticCollector<JavaFileObject> diagnosticsCollector = new DiagnosticCollector<JavaFileObject>();
		boolean success = true;
		compiler.getTask(null, myFileManager, diagnosticsCollector, options, null, compilationUnits).call();
		List<Diagnostic<? extends JavaFileObject>> diagnostics = diagnosticsCollector.getDiagnostics();
		for (Diagnostic<? extends JavaFileObject> d : diagnostics) {
			if (d.getKind() == Kind.ERROR) {
				success = false;
				throw new CompileException(d.getMessage(Locale.ENGLISH));
			} else if (d.getKind() == Kind.WARNING) {
				throw new CompileException(d.getMessage(Locale.ENGLISH));
			}
		}
		if (success) {
			VirtualClassLoader loader = new VirtualClassLoader(classLoader, baseDir);
			try {
				Class<?> loadClass = loader.loadClass("org.bimserver.jqep.Query");
				QueryInterface newInstance = (QueryInterface) loadClass.newInstance();
				return newInstance;
			} catch (ClassNotFoundException e) {
				LOGGER.error("", e);
			} catch (InstantiationException e) {
				LOGGER.error("", e);
			} catch (IllegalAccessException e) {
				LOGGER.error("", e);
			}
		}
		return null;
	}
	
	public CompileResult compile(String code) {
		CompileResult compileResult = StoreFactory.eINSTANCE.createCompileResult();
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		if (compiler == null) {
			compileResult.setCompileOke(false);
			compileResult.getErrors().add("JDK needed for compile tasks");
			return compileResult;
		}
		JavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
		VirtualFile baseDir = new VirtualFile(null, null);
		VirtualFile file = baseDir.createFile("org" + File.separator + "bimserver" + File.separator + "jqep" + File.separator + "Query.java");
		file.setStringContent(code);
		VirtualFileManager myFileManager = new VirtualFileManager(fileManager, classLoader, baseDir);

		List<VirtualFile> fileList = new ArrayList<VirtualFile>();
		getJavaFiles(fileList, baseDir);
		List<VirtualFile> compilationUnits = baseDir.getAllJavaFileObjects();

		List<String> options = new ArrayList<String>();
		options.add("-cp");
		options.add(libPath);
//		options.add("-target");
//		options.add("7");

		DiagnosticCollector<JavaFileObject> diagnosticsCollector = new DiagnosticCollector<JavaFileObject>();
		compiler.getTask(null, myFileManager, diagnosticsCollector, options, null, compilationUnits).call();
		List<Diagnostic<? extends JavaFileObject>> diagnostics = diagnosticsCollector.getDiagnostics();
		compileResult.setCompileOke(true);
		for (Diagnostic<? extends JavaFileObject> d : diagnostics) {
			if (d.getKind() == Kind.ERROR) {
				compileResult.setCompileOke(false);
				compileResult.getErrors().add(d.getMessage(Locale.ENGLISH));
			} else if (d.getKind() == Kind.WARNING) {
				compileResult.getErrors().add(d.getMessage(Locale.ENGLISH));
			}
		}
		return compileResult;
	}
}