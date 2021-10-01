package io.chandler.kicad.bommanager;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Scanner;
import java.util.Stack;

public class KiCADPCB {
	final Node root;
	
	static final double CONVX = 1/.0254; // mm to mil
	static final double CONVY = 1/.0254; // mm to mil
	
	public KiCADPCB(File file) throws IOException {
		root = parsePCB(file);
	}
	
	private static String getValueFromReference(File src, String reference) throws IOException {
		//grep "reference \"C12\"" -R . | grep kicad_sch
		String dir = src.toPath().toAbsolutePath().getParent().toString();
		Process p = new ProcessBuilder("bash", "-c", "grep \"reference \\\"" + reference + "\\\"\" -R " + dir + " | grep sch").start();
		Scanner in = new Scanner(p.getInputStream());
		try { if (p.waitFor() != 0) throw new IOException("Could not get value from ref " + reference);
		} catch (InterruptedException e) { e.printStackTrace(); }
		String out = in.nextLine();
		String srch;
		out = out.substring(out.indexOf(srch="(value \"") + srch.length());
		out = out.substring(0, out.indexOf("\") ("));
		in.close();
		return out;
	}
	
	public static void cvtRpt(File file) throws IOException {
		//  /home/cjgriscom/Xact/Toradex/ToradexCarrier/ToradexCarrier.rpt
		File outFile = file.toPath().resolveSibling(file.getName() + ".xyrs").toFile();
		outFile.delete();
		PrintStream out = new PrintStream(outFile);
		Scanner in = new Scanner(file);
		
		String curModule = null;
		String footprint = "";
		String value = "";
		double x = 0, y = 0, rot = 0;
		String layer = "";
		boolean smd = false;
		boolean shouldBeSmd = false;
		boolean padIsSmd = false;

		double x_min = Double.MAX_VALUE, y_min = Double.MAX_VALUE;
		double x_max = Double.MIN_VALUE, y_max = Double.MIN_VALUE;
		
		boolean inPad = false;
		while (in.hasNextLine()) {
			String[] line = in.nextLine().trim().split("\\s+");
			if (line.length == 0) continue;
			//System.out.println(line[0]);
			if (line[0].equals("$MODULE")) {
				curModule = line[1];
				x_min = Double.MAX_VALUE;
				y_min = Double.MAX_VALUE;
				x_max = Double.MIN_VALUE;
				y_max = Double.MIN_VALUE;
				layer = "?";
				smd = false;
				x = 0;
				y = 0;
				rot = 0;
				footprint = "?";
				value = "?";
				shouldBeSmd = false;
				padIsSmd = false;
				
			} else if (line[0].equals("$EndMODULE")) {
				if (!smd && shouldBeSmd) {
					System.err.println(curModule + " should be SMD?");
				}
				if (x_max == Double.MIN_VALUE) {
					System.out.println("Skipping " + curModule);
					continue;
				}
				double width = x_max - x_min;
				double height= y_max - y_min;
				double offsetX = (x_max + x_min) / 2;
				double offsetY = (y_max + y_min) / 2;
				double offsetAbs = Math.hypot(offsetX, offsetY);
				double offsetAng = Math.atan2(offsetY, offsetX);
				double cx = x + offsetAbs * Math.cos(offsetAng - Math.toRadians(rot));
				double cy = y + offsetAbs * Math.sin(offsetAng - Math.toRadians(rot));
				
				//System.err.printf("w %.2f h %.2f ox %.2f oy %.2f oABS %.2f oANG %.0f rot %.0f cx %.2f cy %.2f x %.2f y %.2f\n", width,height,offsetX,offsetY,offsetAbs, Math.toDegrees(offsetAng), rot, cx,cy,x,y);
				
				y = -y;
				cy = -cy;
				
				if (Math.abs(cx - x) > 0.01 || Math.abs(cy - y) > 0.01) {
					System.err.printf("Centroid doesn't match: adv=(%.2f, %.2f) act=(%.2f, %.2f) \n", x,y, cx,cy);
					if (!smd || curModule.startsWith("J")) {
						System.err.println("Corrected through-hole part centroid " + curModule);
						x = cx;
						y = cy;
					}
				}
				
				String s = String.format("%s\t%.2f\t%.2f\t%.2f\t%s\t%s\t%.2f\t%.2f\t%s\t%s", curModule, x* CONVX, y* CONVY, rot, layer, smd ? "1":"2", width* CONVX, height* CONVY, value, footprint);
				System.out.println(s);
				out.println(s);
			} else if (line[0].equals("$PAD")) {
				inPad = true;
				padIsSmd = true;
			} else if (line[0].equals("$EndPAD")) {
				inPad = false;
				if (padIsSmd) shouldBeSmd = true;
			} else if (line[0].equals("position")) {
				double px = Double.parseDouble(line[1]);
				double py = Double.parseDouble(line[2]);
				
				if (inPad) {
					double pw = Double.parseDouble(line[4]);
					double ph = Double.parseDouble(line[5]);
					double pr = Double.parseDouble(line[7]);
					if (((int)Math.round(Math.abs(pr))) % 180 == 0) {
						// ok
					} else if (((int)Math.round(Math.abs(pr))) % 180 == 90) {
						double tmp = pw;
						pw = ph;
						ph = tmp;
					} else {
						in.close();
						out.close();
						throw new RuntimeException("Unusable pad rotation " + pr);
					}
					//System.out.println("  pad " + px + " " + py + " " + pw + " " + ph);
					x_max = Math.max(x_max, px + pw/2);
					y_max = Math.max(y_max, py + ph/2);
					x_min = Math.min(x_min, px - pw/2);
					y_min = Math.min(y_min, py - ph/2);
					
				} else {
					double pr = Double.parseDouble(line[4]);
					x = px; y = py; rot = pr;
				}
			} else if (line[0].equals("layer")) {
				if (line[1].equalsIgnoreCase("front")) layer = "top";
				if (line[1].equalsIgnoreCase("back")) layer = "bottom";
			} else if (line[0].equals("attribut")) {
				if (line[1].equals("smd")) smd = true;
			} else if (line[0].equals("footprint")) {
				footprint = line[1];
				value = getValueFromReference(file, curModule);
			} else if (line[0].equals("drill")) {
				if (Double.parseDouble(line[1]) != 0) padIsSmd = false;
			}
			
		}
		
		in.close();
		out.close();
	}
	
	private Node parsePCB(File file) throws IOException {
		InputStream in = new FileInputStream(file);
		
		Node node = new Node();
		boolean quoteMode = false;
		Stack<Node> stack = new Stack<>();
		StringBuilder charBuffer = new StringBuilder();
		int c;
		while (true) {
			c = in.read();
			if (c == -1) break;
			//System.out.print(c);
			if (c == '\"') {
				quoteMode = ! quoteMode;
				charBuffer.append((char)c);
			} else if (quoteMode) {
				charBuffer.append((char)c);
			} else if (Character.isWhitespace(c)) {
				if (charBuffer.length() > 0) {
					if (node.hasType()) node.addParam(charBuffer.toString());
					else node.setType(charBuffer.toString());
					charBuffer = new StringBuilder();
				}
			} else if (c == '(') {
				stack.push(node);
				node = new Node();
			} else if (c == ')') {
				if (charBuffer.length() > 0) {
					if (node.hasType()) node.addParam(charBuffer.toString());
					else node.setType(charBuffer.toString());
					charBuffer = new StringBuilder();
				}
				Node cur = node;
				node = stack.pop();
				node.addNode(cur);
			} else {
				charBuffer.append((char)c);
			}
		}
		in.close();
		return node.nodes.get(0);
		
	}
}
