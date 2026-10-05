package com.example.weekly;

import org.w3c.dom.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.util.*;

final class Xml {
    static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    static final String R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    static final String REL = "http://schemas.openxmlformats.org/package/2006/relationships";
    static Document parse(byte[] bytes) {
        try {
            var f = DocumentBuilderFactory.newInstance(); f.setNamespaceAware(true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return f.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
        } catch (Exception e) { throw new IllegalArgumentException("Word XML 損壞或包含不支援的外部實體", e); }
    }
    static byte[] bytes(Node node) {
        try {
            var f = TransformerFactory.newInstance(); f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            var t = f.newTransformer(); t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            var out = new ByteArrayOutputStream(); t.transform(new DOMSource(node), new StreamResult(out)); return out.toByteArray();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    static List<Element> children(Node n) {
        var list = new ArrayList<Element>();
        for (Node c=n.getFirstChild(); c!=null; c=c.getNextSibling()) if (c instanceof Element e) list.add(e);
        return list;
    }
    static List<Element> all(Node n, String ns, String name) {
        var list = new ArrayList<Element>();
        if (n instanceof Element e && ns.equals(e.getNamespaceURI()) && name.equals(e.getLocalName())) list.add(e);
        NodeList nodes = n instanceof Document d ? d.getElementsByTagNameNS(ns, name) : ((Element)n).getElementsByTagNameNS(ns,name);
        for (int i=0;i<nodes.getLength();i++) list.add((Element)nodes.item(i)); return list;
    }
    static Element first(Node n, String name) { return all(n, W, name).stream().findFirst().orElse(null); }
    static String text(Node n) { var b = new StringBuilder(); for (var t : all(n,W,"t")) b.append(t.getTextContent()); return b.toString(); }
    static String val(Element e) { return e == null ? "" : e.getAttributeNS(W,"val"); }
    static void val(Element e, String value) { e.setAttributeNS(W,"w:val",value); }
}
