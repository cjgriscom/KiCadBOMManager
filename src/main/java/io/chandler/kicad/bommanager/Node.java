package io.chandler.kicad.bommanager;
import java.util.ArrayList;

public class Node {
	String type;
	ArrayList<Node> nodes = new ArrayList<Node>();
	
	public Node() {
		
	}
	
	public boolean hasType() {
		return type != null;
	}
	
	public void setType(String type) {
		this.type = type;
	}
	
	public void addParam(String text) {
		nodes.add(new StringNode(text));
	}
	
	public void addNode(Node node) {
		nodes.add(node);
	}
	
	@Override
	public String toString() {
		StringBuilder out = new StringBuilder();
		out.append("(");
		out.append(type);
		for (Node s : nodes) {
			out.append(" ");
			out.append(s.toString());
		}
		out.append(")\n");
		
		return out.toString();
	}
}
