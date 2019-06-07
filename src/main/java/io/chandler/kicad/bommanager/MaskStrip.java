package io.chandler.kicad.bommanager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.TreeSet;

import com.quirkygaming.errorlib.ErrorHandler;
import com.quirkygaming.propertydb.InitializationToken;
import com.quirkygaming.propertydb.PropertyDB;
import com.quirkygaming.propertylib.MutableProperty;

public class MaskStrip {
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
		
		if (!new File(kicadFile.get()).exists()) System.exit(1);
		
		System.out.println("Loading...\n\n");
		PropertyDB.forceSave(db);
		
		TreeSet<String> keepRefs = new TreeSet<String>();
		System.out.println("Enter list of designators to keep, enter 'FIN' when done.");
		String keep = "";
		while (!(keep = in.nextLine().trim()).equals("FIN")) {
			keepRefs.add(keep);
		}
		System.out.println("OK!");
		
		KiCADPCB pcb = new KiCADPCB(new File(kicadFile.get()));
		
		TreeMap<String, ArrayList<Module>> modules = new TreeMap<>();
		
		Iterator<Node> rootNodes = pcb.root.nodes.iterator();
		while (rootNodes.hasNext()) {
			Node n = rootNodes.next();
			if (n.type.equals("module")) {
				Module m = new Module(n);
				
				if (!keepRefs.contains(m.referenceL+m.referenceN)) rootNodes.remove();
			}
		}
		
		PrintStream pcbout = new PrintStream(new FileOutputStream(new File(new File(kicadFile.get()).getParentFile(),"MASKLESS.kicad_pcb")));
		pcbout.println(pcb.root.toString());
		pcbout.close();
		
		in.close();
		PropertyDB.closeDatabase(db);
	}
	
}
