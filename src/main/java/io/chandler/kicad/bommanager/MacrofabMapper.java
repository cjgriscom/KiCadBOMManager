package io.chandler.kicad.bommanager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;

import com.quirkygaming.errorlib.ErrorHandler;
import com.quirkygaming.propertydb.InitializationToken;
import com.quirkygaming.propertydb.PropertyDB;
import com.quirkygaming.propertylib.MutableProperty;

public class MacrofabMapper {
	static final TreeSet<String> ignoreAll = new TreeSet<>();
	static final TreeSet<String> ignoreValueOnRef = new TreeSet<>();
	
	static {
		ignoreValueOnRef.add("FID");
		ignoreValueOnRef.add("J");
		ignoreAll.add("H");
		ignoreAll.add("HOLE");
		ignoreAll.add("LOGO");
	}
	
	static String getModuleID(Module m) {
		String value = m.value;
		boolean ignoreValue = ignoreValueOnRef.contains(m.referenceL);
		if (m.value.startsWith("!")) {
			value = value.substring(1);
			ignoreValue = false;
		}
		
		if (ignoreValue) {
			return "Ref " + m.referenceL + "\n  Footprint: " + m.footprint;
		} else {
			return "Ref " + m.referenceL + "\n  Value: " + value + "\n  Footprint: " +  m.footprint;
		}
	}
	
	public static void main(String[] args) throws IOException {
		Scanner in = new Scanner(System.in);
		
		InitializationToken db = PropertyDB.initializeDB(1000);
		
		MutableProperty<String> kicadFile = 
				PropertyDB.initiateProperty(new File("kicadFile.pdb"), "kicadFile", 1, "", ErrorHandler.throwAll());
		if (new File(kicadFile.get()).exists()) {
			System.out.println("Loading KiCAD PCB: " + kicadFile.get());
			System.out.print("Change? (y/N)");
			if (in.nextLine().toLowerCase().startsWith("y")) {
				System.out.println("Enter filename: ");
				kicadFile.set(in.nextLine());
			}
		} else {
			System.out.println("Enter KiCAD PCB filename: ");
			kicadFile.set(in.nextLine());
		}
		
		File kicadPCB = new File(kicadFile.get());
		
		if (!kicadPCB.exists()) System.exit(1);
		
		System.out.println("Loading...\n\n");
		PropertyDB.forceSave(db);
		
		
		TreeMap<String, ArrayList<Module>> modules = new TreeMap<>();
		
		if (kicadPCB.toString().endsWith(".rpt")) {
			KiCADPCB.cvtRpt(kicadPCB);
			System.exit(0);
		} else {
			KiCADPCB pcb = new KiCADPCB(kicadPCB);
			
			for (Node n : pcb.root.nodes) {
				if (n.type.equals("module")) {
					Module m = new Module(n);
					
					boolean ignore = ignoreAll.contains(m.referenceL);
					if (ignore) continue;
	
					String uuid = getModuleID(m);
					
					
					if (!modules.containsKey(uuid)) modules.put(uuid, new ArrayList<>());
					modules.get(uuid).add(m);
				}
			}
		}
		
		for (Entry<String, ArrayList<Module>> entries : modules.entrySet()) {
			System.out.println(entries.getValue().size() +"x " + entries.getKey());
		}
		
		System.out.println(modules.size() + " unique items");
		System.out.println();
		
		TreeMap<String, String[]> idPartNumCache = new TreeMap<>();
		CSVParser p = CSVParser.parse(
				kicadPCB.toPath().resolveSibling(kicadPCB.toPath().getFileName().toString() + "_cached_db.csv").toFile(),
				StandardCharsets.UTF_8, CSVFormat.EXCEL);
		p.stream().skip(1).forEach((record) -> {
			String[] parts = new String[]{"", "","","","","",""};
			for (int i = 1; i < record.size(); i++) parts[i-1] = record.get(i);
			idPartNumCache.put(record.get(0), parts);
		});
		
		System.out.println("Loaded " + idPartNumCache.size() + " cached part numbers.");
		
		// Clear unused
		Iterator<String> cacheIterator = idPartNumCache.keySet().iterator();
		while (cacheIterator.hasNext()) {
			String cached = cacheIterator.next();
			if (!modules.keySet().contains(cached)) {
				System.out.println("Detected unused part num " + cached);
			}
		} //2x18: S2011EC-18-ND  ext: SAM1206-04-ND
		PropertyDB.forceSave(db);
		
		TreeSet<String> unknownAssocs = new TreeSet<>();
		unknownAssocs.addAll(modules.keySet());
		unknownAssocs.removeAll(idPartNumCache.keySet());
		
		System.out.println("Unmapped associations: " + unknownAssocs.size());
		
		System.out.println("");
		
		System.out.println("Input ignore list? (y/N) ");
		String ignore = in.nextLine();
		if (ignore.startsWith("y")) {
			TreeSet<String> delRefs = new TreeSet<String>();
			System.out.println("Enter list of designators to delete, enter 'FIN' when done.");
			String del = "";
			while (!(del = in.nextLine().trim()).equals("FIN")) {
				delRefs.add(del);
			}
			
			for (ArrayList<Module> ml : modules.values()) {
				Iterator<Module> moduleIter = ml.iterator();
				while (moduleIter.hasNext()) {
					Module m = moduleIter.next();
					if (delRefs.contains(m.referenceL + m.referenceN)) moduleIter.remove();
				}
			}
			
			int size = modules.size();
			Iterator<Entry<String, ArrayList<Module>>> modEntryIter = modules.entrySet().iterator();
			while (modEntryIter.hasNext()) {
				Entry<String, ArrayList<Module>> entry = modEntryIter.next();
				if (entry.getValue().isEmpty()) modEntryIter.remove();
			}
			System.out.println("Removed " + (size - modules.size()) + " line items!");
		}
		
		Path documentRoot = new File(kicadFile.get()).toPath();
		exportMacrofab(in, documentRoot, modules, idPartNumCache);
		
		in.close();
		PropertyDB.closeDatabase(db);
	}

	private static void exportMacrofab(Scanner in, Path documentRoot, TreeMap<String, ArrayList<Module>> modules, TreeMap<String, String[]> idPartNumCache) throws IOException {

		System.out.println("Exporting Macrofab Assembly...");
		Path export = documentRoot.resolveSibling("bom_macrofab.csv");
		export.toFile().delete();
		PrintStream expOut = new PrintStream(new FileOutputStream(export.toFile()));
		CSVPrinter printer = new CSVPrinter(expOut, CSVFormat.EXCEL);

		printer.printRecord(
				"Designator", "Populate",
				"Manufacturer 1", "MPN 1",
				"Manufacturer 2", "MPN 2",
				"Manufacturer 3", "MPN 3");
		
		for (ArrayList<Module> m : modules.values()) {
			String uuid = getModuleID(m.get(0));
			
			String[] cells = new String[]{"", "", "", "", "", "", ""};
			
			if (idPartNumCache.containsKey(uuid)) {
				cells = idPartNumCache.get(uuid);
				
				// .replaceAll("[^\\x00-\\x7F]", "");
			}
			for (Module model : m) {
				
				
				String reference = model.referenceL + model.referenceN;
				printer.print(reference);
				for (String s : cells) printer.print(s);
				printer.println();
			}
			
			
		}
		printer.close();
		System.out.println(export);
	}


}
