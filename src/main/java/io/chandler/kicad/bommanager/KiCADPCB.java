package io.chandler.kicad.bommanager;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Stack;

public class KiCADPCB {
	final Node root;
	
	public KiCADPCB(File file) throws IOException {
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
		root = node.nodes.get(0);
		
		in.close();
	}
}
