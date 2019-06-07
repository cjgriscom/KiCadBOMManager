package io.chandler.kicad.bommanager;
public class StringNode extends Node {
	public StringNode(String type) {
		this.type = type;
	}
	
	@Override
	public String toString() {
		return type;
	}
}
