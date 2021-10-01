package io.chandler.kicad.bommanager;

import static io.chandler.kicad.bommanager.ImportAssociations.Idx.I_DESIG;
import static io.chandler.kicad.bommanager.ImportAssociations.Idx.I_FOOT;
import static io.chandler.kicad.bommanager.ImportAssociations.Idx.I_VAL;

import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import io.chandler.kicad.bommanager.ImportAssociations.Idx;

public class ExportAssociations {
	
	public static void main(String[] args) throws Exception {
		Scanner in = new Scanner(System.in);
		
		
		System.out.println("Enter XYRS to edit filename: ");
		
		
		File kicadPCB = new File(in.nextLine().trim());
		
		if (!kicadPCB.exists()) System.exit(1);
		
		/*   
		
		/home/cjgriscom/Xact/Toradex/ToradexCarrier/ToradexCarrier.rpt.xyrs
		/home/cjgriscom/Xact/Toradex/ToradexCarrier/ToradexCarrier.assoc.txt
		 * 
		 */
		

		System.out.println("Enter corresponding ASSOC filename: ");

		File assocFile = new File(in.nextLine().trim());
		
		Map<String, String> mpns = new HashMap<>();
		
		List<String> lines = Files.readAllLines(kicadPCB.toPath());
		
		List<String> linesAssoc = Files.readAllLines(assocFile.toPath());
		
		File outFile = kicadPCB.getParentFile().toPath().resolve(kicadPCB.getName() + ".EDIT.assoc.xyrs").toFile();
		outFile.delete();
		PrintStream out = new PrintStream(outFile);
		

		for (String s : linesAssoc) {
			String[] spl = s.split("\t");
			
			if (spl.length < 3) {
				System.out.println("Skip Line " + s);
			} else {
				String mpn = spl[2];
				String foot = spl[0];
				String val = spl[1];
				
				String old = mpns.put(foot + "\t" + val, mpn);
				if (old != null && !old.equals(mpn)) {
					System.err.println("CONFLICT");
					System.err.println("   Was: " + "?" + ": " + foot + " / " + val + " ---> " + old);
					System.err.println("   Now: " + "?" + ": " + foot + " / " + val + " ---> " + mpn);
				}
			}
		}
		
		for (String s : lines) {
			String[] spl = s.split("\t");
			
			if (spl.length < Idx.values().length - 2) {
				System.out.println("Skip Line " + s);
			} else {
				String desig = spl[I_DESIG.ordinal()];
				String foot = spl[I_FOOT.ordinal()];
				String val = spl[I_VAL.ordinal()];
				
				for (int i = 0; i < Idx.values().length - 2; i++) {
					out.print(spl[i]);
					out.print('\t');
				}
				
				String mpn = mpns.get(foot + "\t" + val);
				if (mpn == null) {
					System.err.println("   No Assoc for: " + desig + ": " + foot + " / " + val + " ---> " + mpn);
					out.println("1");
				} else {
					out.println("1\t" + mpn);
				}
			}
		}
		
		in.close();
		
		out.close();
	}
}
