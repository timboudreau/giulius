/*
 * The MIT License
 *
 * Copyright 2021 Mastfrog Technologies.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.mastfrog.jarmerge.builtin;

import com.mastfrog.jarmerge.MergeLog;
import com.mastfrog.jarmerge.builtin.MergePlexusComponents.PlexusComponentsCoalescer;
import com.mastfrog.jarmerge.support.AbstractCoalescer;
import com.mastfrog.jarmerge.support.AbstractJarFilter;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *
 * @author Tim Boudreau
 */
public class MergePlexusComponents extends AbstractJarFilter<PlexusComponentsCoalescer> {

    private static final String PLEXUS_COMPONENTS_FILE = "META-INF/plexus/components.xml";
    private PlexusComponentsCoalescer coa;

    @Override
    public String description() {
        return "Merges maven plugin components found in " + PLEXUS_COMPONENTS_FILE + " in "
                + "JARs in order to correctly combine archives containing Maven plug-ins.";
    }

    @Override
    public PlexusComponentsCoalescer findCoalescer(String path, Path inJar, JarEntry entry, MergeLog log) {
        if (PLEXUS_COMPONENTS_FILE.equals(path)) {
            if (coa == null) {
                coa = new PlexusComponentsCoalescer(zeroDates());
            }
            return coa;
        }
        return null;
    }

    static final class PlexusComponentsCoalescer extends AbstractCoalescer {

        private final List<Node> allNodes = new ArrayList<>();

        public PlexusComponentsCoalescer(boolean z) {
            super(PLEXUS_COMPONENTS_FILE, z);
        }

        @Override
        protected boolean read(Path jar, JarEntry entry, JarFile file, InputStream in, MergeLog log) throws Exception {
            allNodes.addAll(readPlexusComponents(jar.getFileName().toString(), in));
            return true;
        }

        @Override
        protected void write(JarEntry entry, JarOutputStream out, MergeLog log) throws Exception {
            out.write(assemblePlexusComponents(allNodes).getBytes(UTF_8));
        }

        private Collection<? extends Node> readPlexusComponents(String jar, InputStream in) throws Exception {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setValidating(false);
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(in);
            doc.getDocumentElement().normalize();

            XPathFactory fac = XPathFactory.newInstance();
            XPath xpath = fac.newXPath();
            XPathExpression findComponents = xpath.compile(
                    "/component-set/components/component");
            NodeList nl = (NodeList) findComponents.evaluate(doc, XPathConstants.NODESET);

            List<Node> nodes = new ArrayList<>();
            for (int i = 0; i < nl.getLength(); i++) {
                Node n = nl.item(i);
                nodes.add(n);
            }

            return nodes;
        }

        private String assemblePlexusComponents(List<Node> all) throws Exception {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setValidating(false);
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();

            Document doc = dBuilder.newDocument();
            Node root = doc.createElement("component-set");
            Node sub = doc.createElement("components");
            root.appendChild(sub);
            doc.appendChild(root);
            for (Node n : all) {
                Node adopted = doc.adoptNode(n);
                sub.appendChild(adopted);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            StreamResult res = new StreamResult(out);
            TransformerFactory tFactory
                    = TransformerFactory.newInstance();
            Transformer transformer = tFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            transformer.setOutputProperty(OutputKeys.STANDALONE, "yes");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
//            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", Integer.toString(4));
            transformer.transform(new DOMSource(doc), res);

            String result = new String(out.toByteArray(), "UTF-8");
            return result;
        }
    }
}
