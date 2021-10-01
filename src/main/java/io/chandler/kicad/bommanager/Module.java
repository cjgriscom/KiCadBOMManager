package io.chandler.kicad.bommanager;

public class Module {
	final String referenceL;
	final int referenceN;
	final String footprint;
	final String value;
	final double x, y, rot;
	final String layer;
	final boolean smd;
	
	public Module(Node src) {
		String reference = getType(src, "fp_text", 0, "reference", true).nodes.get(1).toString();
		referenceL = reference.replaceAll("\\d", "");
		referenceN = Integer.parseInt(reference.replaceAll("[A-z]", ""));
		footprint = src.nodes.get(0).toString();
		value = getType(src, "fp_text", 0, "value", true).nodes.get(1).toString();
		Node at = getType(src, "at", -1, null, true);
		x = Double.parseDouble(at.nodes.get(0).toString());
		y = Double.parseDouble(at.nodes.get(1).toString());
		rot = at.nodes.size() > 2 ? Double.parseDouble(at.nodes.get(2).toString()) : 0.0;
		layer = getType(src, "layer", -1, null, true).nodes.get(0).toString();
		smd = getType(src, "attr", 0, "smd", false) != null;
		
	}
	
	public Module(String referenceL, int referenceN, String footprint, String value,
			double x, double y, double rot,
			String layer,  boolean smd) {
		this.referenceL = referenceL;
		this.referenceN = referenceN;
		this.footprint = footprint;
		this.value = value;
		this.x = x; this.y = y; this.rot = rot;
		this.layer = layer;
		this.smd = smd;
	}
	
	private Node getType(Node src, String type, int paramMatchI, String paramMatch, boolean throwExp) {
		for (Node n : src.nodes) {
			if (n.type.equals(type)) {
				if (paramMatchI < 0) return n;
				if (paramMatch.equals(n.nodes.get(paramMatchI).toString())) return n;
			}
		}
		if (throwExp) throw new RuntimeException("Node " + type + " not found!");
		return null;
	}
	
	@Override
	public String toString() {
		return "Module: " + referenceL + referenceN + " - " + footprint + (smd ? " (smd)" : "") +
				"\n  at " + x + ", " + y + " (" + rot + ") " + layer +
				"\n  value " + value;
	}
}
