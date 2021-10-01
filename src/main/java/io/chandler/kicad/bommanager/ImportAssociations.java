package io.chandler.kicad.bommanager;

import static io.chandler.kicad.bommanager.ImportAssociations.Idx.I_DESIG;
import static io.chandler.kicad.bommanager.ImportAssociations.Idx.I_FOOT;
import static io.chandler.kicad.bommanager.ImportAssociations.Idx.I_MPN;
import static io.chandler.kicad.bommanager.ImportAssociations.Idx.I_VAL;

import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;

public class ImportAssociations {
	public static enum Idx { 
		I_DESIG,
		I_X,
		I_Y,
		I_ORI,
		I_SIDE,
		I_SMD,
		I_W,
		I_H,
		I_VAL,
		I_FOOT,
		I_DNP,
		I_MPN,
	}
	
	public static void main(String[] args) throws Exception {
		Scanner in = new Scanner(System.in);
		
		
		System.out.println("Enter XYRS filename: ");
		
		
		File kicadPCB = new File(in.nextLine().trim());
		
		if (!kicadPCB.exists()) System.exit(1);
		
		/* 
		
		/home/cjgriscom/Xact/Toradex/ToradexCarrier/CurrentBOM.txt
		/home/cjgriscom/Xact/Toradex/ToradexCarrier/ToradexCarrier.assoc.txt
		 */
		

		System.out.println("Enter dest ASSOC filename: ");

		File assocFile = new File(in.nextLine().trim());
		assocFile.delete();
		PrintStream assocs = new PrintStream(assocFile);
		
		Map<String, String> mpns = new HashMap<>();
		
		List<String> lines = Files.readAllLines(kicadPCB.toPath());
		
		for (String s : lines) {
			String[] spl = s.split("\t");
			
			if (spl.length < Idx.values().length) {
				System.out.println("Skip Line " + s);
			} else {
				String desig = spl[I_DESIG.ordinal()];
				String mpn = spl[I_MPN.ordinal()];
				String foot = spl[I_FOOT.ordinal()];
				String val = spl[I_VAL.ordinal()];
				
				String old = mpns.put(foot + "\t" + val, mpn);
				if (old != null && !old.equals(mpn)) {
					System.err.println("CONFLICT");
					System.err.println("   Was: " + desig + ": " + foot + " / " + val + " ---> " + old);
					System.err.println("   Now: " + desig + ": " + foot + " / " + val + " ---> " + mpn);
				}
			}
		}
		
		for (Entry<String, String> ent : mpns.entrySet()) {
			assocs.println(ent.getKey() + "\t" + ent.getValue());
		}
		
		in.close();
		
		assocs.close();
	}
}
