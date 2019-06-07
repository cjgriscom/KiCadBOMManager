package io.chandler.kicad.bommanager;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;

import org.json.JSONObject;

import com.quirkygaming.errorlib.ErrorHandler;
import com.quirkygaming.propertydb.InitializationToken;
import com.quirkygaming.propertydb.PropertyDB;
import com.quirkygaming.propertylib.MutableProperty;
import com.squareup.okhttp.FormEncodingBuilder;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

public class BOMExport {
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
		
		KiCADPCB pcb = new KiCADPCB(kicadPCB);
		
		TreeMap<String, ArrayList<Module>> modules = new TreeMap<>();
		
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
		
		for (Entry<String, ArrayList<Module>> entries : modules.entrySet()) {
			System.out.println(entries.getValue().size() +"x " + entries.getKey());
		}
		
		System.out.println(modules.size() + " unique items");
		System.out.println();
		
		MutableProperty<TreeMap<String, String>> idPartNumCache = 
				PropertyDB.initiateProperty(
						kicadPCB.toPath().getParent().toFile(), 
						kicadPCB.toPath().getFileName().toString() + "_componentsDB", 1, new TreeMap<>(), ErrorHandler.throwAll());

		
		System.out.println("Loaded " + idPartNumCache.get().size() + " cached part numbers.");
		
		// Clear unused
		Iterator<String> cacheIterator = idPartNumCache.get().keySet().iterator();
		while (cacheIterator.hasNext()) {
			String cached = cacheIterator.next();
			if (!modules.keySet().contains(cached)) {
				System.out.println("Detected unused part num " + cached + ": " + idPartNumCache.get().get(cached));
				System.out.println("Remove? (y/N)");
				String resp = in.nextLine();
				if (resp.startsWith("y")) {
					cacheIterator.remove();
					System.out.println("Removed.");
				}
			}
		} //2x18: S2011EC-18-ND  ext: SAM1206-04-ND
		PropertyDB.forceSave(db);
		
		TreeSet<String> unknownAssocs = new TreeSet<>();
		unknownAssocs.addAll(modules.keySet());
		unknownAssocs.removeAll(idPartNumCache.get().keySet());
		
		System.out.println("Unmapped associations: " + unknownAssocs.size());
		
		System.out.println("Edit association table? (y/N)");
		
		if (in.nextLine().toLowerCase().startsWith("y")) {
			System.out.println("Edit part number associations: ");
			System.out.println();
			
			boolean lockResp = false;
			String shop = "";
			
			for (String s : modules.keySet()) {
				System.out.println();
				System.out.println(modules.get(s).size() + "x " + s);
				if (idPartNumCache.get().containsKey(s)) System.out.println("Current assoc: " + idPartNumCache.get().get(s));
				else System.out.println("Unmapped.");
				
				if (!lockResp) {
					System.out.print("(K)eep, (c)lear, (q)uit, (d)igikey, (m)ouser, (a)rrow, (n)ewark, macro(f)ab? ");
					String resp = in.nextLine().trim();
					if (resp.length() > 0 && Character.isUpperCase(resp.charAt(0))) lockResp = true;
					resp = resp.toLowerCase();
					shop = "";
					if      (resp.startsWith("d")) shop = "Digikey";
					else if (resp.startsWith("m")) shop = "Mouser";
					else if (resp.startsWith("a")) shop = "Arrow";
					else if (resp.startsWith("n")) shop = "Newark";
					else if (resp.startsWith("f")) shop = "Macrofab";
					else if (resp.startsWith("c")) {
						idPartNumCache.get().remove(s);
					} else if (resp.startsWith("q")) {
						break;
					}
				}
				
				if (!shop.isEmpty()) {
					System.out.print("Part number: ");
					String partNum = in.nextLine().trim().replaceAll("[^\\x00-\\x7F]", "");
					
					idPartNumCache.get().put(s, shop + "|" + partNum);
				}
				idPartNumCache.update();
				PropertyDB.forceSave(db);
			}
			idPartNumCache.update();
			PropertyDB.forceSave(db);
		}
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
		exportRohs(in, documentRoot, modules, idPartNumCache.get());
		//exportDigikey(in, documentRoot, modules, idPartNumCache.get());
		//exportSolder(in, documentRoot, modules, idPartNumCache.get());
		exportMacrofab(in, documentRoot, modules, idPartNumCache.get());
		
		in.close();
		PropertyDB.closeDatabase(db);
	}
	
	private static void exportRohs(Scanner in, Path documentRoot, TreeMap<String, ArrayList<Module>> modules, TreeMap<String, String> idPartNumCache) throws FileNotFoundException {

		System.out.println("Exporting RoHs certs?");
		String rohs = in.nextLine();
		if (!rohs.toLowerCase().trim().startsWith("y")) return;
		
		Path rohsp = documentRoot.resolveSibling("rohs_certs.csv");
		rohsp.toFile().delete();
		PrintStream expOut = new PrintStream(new FileOutputStream(rohsp.toFile()));

		csvCell(expOut, "Reference Designator");
		csvCell(expOut, "Manufacturer");
		csvCell(expOut, "Manufacturer Part Number");
		csvCell(expOut, "Digi-Key Part Number");
		csvCell(expOut, "Quantity");
		csvCell(expOut, "Roh's Cert");
		csvNextRow(expOut);
		
		for (ArrayList<Module> m : modules.values()) {
			String uuid = getModuleID(m.get(0));

			String digiPN = "";
			String manufPN = "";
			String manufName = "";
			AtomicReference<String> rohsCert = new AtomicReference<>("");
			
			if (idPartNumCache.containsKey(uuid)) {
				String[] partNum = idPartNumCache.get(uuid).replaceAll("[^\\x00-\\x7F]", "").split("\\|");
				if (partNum[0].equals("Digikey")) {
					digiPN = partNum[1];
					try {
						JSONObject digikeyInfo = digikeyLookup(in, digiPN);
						System.out.println(digikeyInfo);
						manufPN = digikeyInfo.getJSONObject("PartDetails").getString("ManufacturerPartNumber");
						manufName = digikeyInfo.getJSONObject("PartDetails").getJSONObject("ManufacturerName").getString("Text");
					} catch (Exception e) {
						System.err.println(e.getMessage());
					}
				} else {
					manufName = partNum[0];
					manufPN = partNum[1];
					try {
						JSONObject digikeyInfo = digikeyLookup(in, partNum[1]);
						System.out.println(digikeyInfo);
						digiPN = digikeyInfo.getJSONObject("PartDetails").getString("DigiKeyPartNumber");
						manufName = digikeyInfo.getJSONObject("PartDetails").getJSONObject("ManufacturerName").getString("Text");
						
						if (digikeyInfo.getJSONObject("PartDetails").has("MediaLinks")) {
							digikeyInfo.getJSONObject("PartDetails").getJSONArray("MediaLinks").forEach((mediaLink) -> {
								if (((JSONObject)mediaLink).getString("Title").trim().equalsIgnoreCase("RoHS Cert")) {
									rohsCert.set(((JSONObject)mediaLink).getString("Url").trim());
								}
							});
						}
					} catch (Exception e) {
						System.err.println(e.getMessage());
					}
				}
			}
			for (Module model : m) {
				
				
				String reference;
				
				TreeSet<Integer> designators = new TreeSet<>();
				for (Module mm : m) designators.add(mm.referenceN);
				StringBuilder desigString = new StringBuilder();
				boolean first = true;
				for (int i : designators) {
					if (!first) desigString.append(' ');
					desigString.append(model.referenceL + i);
					first = false;
				}
				reference = desigString.toString();
				
				csvCell(expOut, reference);
				csvCell(expOut, manufName);
				csvCell(expOut, manufPN);
				csvCell(expOut, digiPN);
				csvCell(expOut, m.size() + "");
				csvCell(expOut, rohsCert.get());
				csvNextRow(expOut);
				
				break; // Just process first item
			}
			
			
		}
		System.out.println(rohsp);
	}
	
	private static void exportDigikey(Scanner in, Path documentRoot, TreeMap<String, ArrayList<Module>> modules, TreeMap<String, String> idPartNumCache) throws FileNotFoundException {

		System.out.println("Exporting Digikey BOM...");
		System.out.print("Enter manual quantities? (y/N)");
		String manualQuant = in.nextLine();
		Path digikey = documentRoot.resolveSibling("bom_digikey.csv");
		digikey.toFile().delete();
		PrintStream digikeyOut = new PrintStream(new FileOutputStream(digikey.toFile()));
		
		csvCell(digikeyOut, "Digi-Key Part Number");
		csvCell(digikeyOut, "Customer Reference");
		csvCell(digikeyOut, "Quantity");
		csvNextRow(digikeyOut);
		
		int processed = 0;
		for (ArrayList<Module> m : modules.values()) {
			Module model = m.get(0);
			String uuid = getModuleID(model);
			
			if (!idPartNumCache.containsKey(uuid)) continue;
			String[] partNum = idPartNumCache.get(uuid).replaceAll("[^\\x00-\\x7F]", "").split("\\|");
			if (partNum[0].equals("Digikey")) {
				TreeSet<Integer> designators = new TreeSet<>();
				for (Module mm : m) designators.add(mm.referenceN);
				StringBuilder desigString = new StringBuilder();
				desigString.append(model.referenceL);
				for (int i : designators) {
					desigString.append(' ');
					desigString.append(i);
				}
				
				
				csvCell(digikeyOut, partNum[1]);
				csvCell(digikeyOut, desigString.toString());
				if (manualQuant.toLowerCase().trim().startsWith("y")) {
					System.out.println(m.size() + "x " + model);
					System.out.println("  "+partNum[1]);
					System.out.print("Quantity: ");
					csvCell(digikeyOut, in.nextLine());
				} else {
					csvCell(digikeyOut, m.size() + "");
				}
				csvNextRow(digikeyOut);
				processed++;
			}
			
			
		}
		int unprocessed = modules.size() - processed;
		if (unprocessed > 0) System.out.println("Warning: " + unprocessed + " unprocessed line items");
		System.out.println(digikey);
	}

	private static void exportMacrofab(Scanner in, Path documentRoot, TreeMap<String, ArrayList<Module>> modules, TreeMap<String, String> idPartNumCache) throws IOException {

		System.out.println("Exporting Macrofab Assembly...");
		Path export = documentRoot.resolveSibling("bom_macrofab.csv");
		export.toFile().delete();
		PrintStream expOut = new PrintStream(new FileOutputStream(export.toFile()));
		
		System.out.print("Collate components? (y/N)");
		boolean collate = in.nextLine().trim().toLowerCase().startsWith("y");

		csvCell(expOut, "Reference Designator");
		csvCell(expOut, "Manufacturer");
		csvCell(expOut, "Manufacturer Part Number");
		csvCell(expOut, "Digi-Key Part Number");
		if (collate) csvCell(expOut, "Quantity");
		csvNextRow(expOut);
		
		for (ArrayList<Module> m : modules.values()) {
			String uuid = getModuleID(m.get(0));

			String digiPN = "";
			String manufPN = "";
			String manufName = "";
			
			if (idPartNumCache.containsKey(uuid)) {
				String[] partNum = idPartNumCache.get(uuid).replaceAll("[^\\x00-\\x7F]", "").split("\\|");
				if (partNum[0].equals("Digikey")) {
					JSONObject digikeyInfo = digikeyLookup(in, digiPN = partNum[1]);
					System.out.println(digikeyInfo);
					manufPN = digikeyInfo.getJSONObject("PartDetails").getString("ManufacturerPartNumber");
					manufName = digikeyInfo.getJSONObject("PartDetails").getJSONObject("ManufacturerName").getString("Text");
				} else {
					manufPN = partNum[1];
				}
			}
			for (Module model : m) {
				
				
				String reference;
				
				if (collate) {
					TreeSet<Integer> designators = new TreeSet<>();
					for (Module mm : m) designators.add(mm.referenceN);
					StringBuilder desigString = new StringBuilder();
					boolean first = true;
					for (int i : designators) {
						if (!first) desigString.append(' ');
						desigString.append(model.referenceL + i);
						first = false;
					}
					reference = desigString.toString();
				} else {
					reference = model.referenceL + model.referenceN;
				}

				csvCell(expOut, reference);
				csvCell(expOut, manufName);
				csvCell(expOut, manufPN);
				csvCell(expOut, digiPN);
				if (collate) csvCell(expOut, m.size() + "");
				csvNextRow(expOut);
				
				if (collate) break; // Just process first item
			}
			
			
		}
		System.out.println(export);
	}

	private static void exportSolder(Scanner in, Path documentRoot, TreeMap<String, ArrayList<Module>> modules, TreeMap<String, String> idPartNumCache) throws IOException {

		System.out.println("Exporting Solder Assembly...");
		Path export = documentRoot.resolveSibling("bom_solder.csv");
		export.toFile().delete();
		PrintStream expOut = new PrintStream(new FileOutputStream(export.toFile()));

		csvCell(expOut, "Part Number");
		csvCell(expOut, "Value");
		csvCell(expOut, "Quantity");
		csvCell(expOut, "Reference Designator");
		csvNextRow(expOut);
		
		for (ArrayList<Module> m : modules.values()) {
			String digiPN = "";
			
			for (Module model : m) {
				String uuid = getModuleID(model);

				if (idPartNumCache.containsKey(uuid)) {
					String[] partNum = idPartNumCache.get(uuid).replaceAll("[^\\x00-\\x7F]", "").split("\\|");

					digiPN = partNum[1];
				}

				String value = model.value;
				
				String reference;
				
				TreeSet<Integer> designators = new TreeSet<>();
				for (Module mm : m) designators.add(mm.referenceN);
				StringBuilder desigString = new StringBuilder();
				boolean first = true;
				for (int i : designators) {
					if (!first) desigString.append(' ');
					desigString.append(model.referenceL + i);
					first = false;
				}
				reference = desigString.toString();
				

				csvCell(expOut, digiPN);
				csvCell(expOut, value);
				csvCell(expOut, m.size() + "");
				csvCell(expOut, reference);
				csvNextRow(expOut);
				
				break; // Just process first item
			}
			
			
		}
		System.out.println(export);
	}

	static String clientSecret = "eF3pD1cJ3fV7lA0sT8iK5pR1fP4xL3cN6iY6bP2mU7oP5qD1cB";
	static String auth = null;
	private static JSONObject digikeyLookup(Scanner in, String pn) throws IOException {
		if (auth == null) {
			System.out.println("\n\n -- DIGIKEY AUTHENTICATION --");
			System.out.println("Login to Digikey here: https://sso.digikey.com/as/authorization.oauth2?response_type=code&client_id=ec47c1d0-aa80-401e-8d53-2af97a646aca&redirect_uri=https://xactmetal.com");
			System.out.print("Paste the returned code here: ");
			auth = in.nextLine();

			OkHttpClient client = new OkHttpClient();
			Request request = new Request.Builder()
			  .url("https://sso.digikey.com/as/token.oauth2")
			  .post(new FormEncodingBuilder()
					  .add("code", auth)
					  .add("client_id", "ec47c1d0-aa80-401e-8d53-2af97a646aca")
					  .add("redirect_uri", "https://xactmetal.com")
					  .add("client_secret", clientSecret)
					  .add("grant_type", "authorization_code")
					  .build())
			  .build();

			Response response = client.newCall(request).execute();
			
			auth = new JSONObject(response.body().string()).getString("access_token");
		}
		
		OkHttpClient client = new OkHttpClient();

		MediaType mediaType = MediaType.parse("application/json");
		RequestBody body = RequestBody.create(mediaType, "{\"Part\":\""+pn+"\",\"IncludeAllAssociatedProducts\":\"false\",\"IncludeAllForUseWithProducts\":\"false\"}");
		Request request = new Request.Builder()
		  .url("https://api.digikey.com/services/partsearch/v2/partdetails")
		  .post(body)
		  .addHeader("x-ibm-client-id", "ec47c1d0-aa80-401e-8d53-2af97a646aca")
		  .addHeader("x-digikey-locale-currency", "usd")
		  .addHeader("authorization", auth)
		  .addHeader("content-type", "application/json")
		  .addHeader("accept", "application/json")
		  .build();

		Response response = client.newCall(request).execute();
		return new JSONObject(response.body().string());
	}
	
	private static boolean first = true;
	private static void csvCell(PrintStream csv, String string) {
		if (!first) csv.print(",");
		string = string.replaceAll("\"", "'"); // TODO
		csv.print("\"");
		csv.print(string);
		csv.print("\"");
		first = false;
	}

	private static void csvNextRow(PrintStream csv) {
		first = true;
		csv.println();
	}

}
