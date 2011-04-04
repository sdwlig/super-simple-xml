/** SsxTest.java
 * Copyright 2010, 2011 Stephen D. Williams, OptimaLogic, and Client
 * License: Apache 2.0
 */

package org.javaglue.ssx;
import org.javaglue.*;

@SuppressWarnings("unused")
public class SsxTest {
    public static void main(String args[]) {
        test();
    }
    public static void test() {
        try {
            Ssx ssx = new Ssx();
            Ssx.Xml fx;
            Ssx.setDebug(true, false); // Turns on debug/verbose internal Ssx trace.
            fx = ssx.parse("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><test><!DOCTYPE greeting [ <!ELEMENT greeting (#PCDATA)> ]>this <![CDATA[ is <nice!> ]]> ok?</test>");
            ssx.message("DTD / CDATA:"+fx.toXml());
            ssx.message("Parsed:"+fx/*.getNode("test")*/.toXml());
            // ssx.setParseType(true, false, true); // Both SParse and default/native.
            fx = ssx.parse("feed", feed, true);
            fx = ssx.parse("feed pass 2", feed, true); // Just to compare second pass speed
            ssx.message("Parsed:"+fx.toXml());
            
            for (Ssx.Xml entry = fx.getNode(/*"http://www.w3.org/2005/Atom:*/"entry"); entry != null; entry = entry.nextSameName()) {
                System.out.println("Entry: "+entry.toXml());
                for (Ssx.Xml link = entry.getNode("link"); link != null; link = link.nextSameName()) {
                    ssx.message("type "+link.get("@type"));
                    ssx.message("href "+link.get("@href"));
                }
            }
            
            ssx.message("Done with feed.\n\n\n");
            Ssx.Xml xml = ssx.parse("mini msg", "<msg start=\"45\" end='50'><fname>Bob|&amp;|&lt;|&gt;|&apos;|&quot;|&#x20;|</fname><!-- whatever... <ha!> --><age type=\"int\">55</age><data><row>one</row><row>two</row></data></msg>", true);
            // ssx.map("fname=entry/fname,");         // Not working yet
            String nodeName = xml.name();        // Returns name of element.
            String fname = xml.get("fname");        // Returns value of "fname" element relative to current location.
            ssx.message("fname:"+fname);
            int start = xml.getInt("@start", -1);        // Returns value of "start" attribute as an int, with a default of -1 if not found.
            ssx.message("@start:"+Integer.toString(start));
            int age = xml.getInt("age", -1);        // Returns value of "age" element as an int, with a default of -1 if not found.
            ssx.message("age:"+Integer.toString(age));
            String atype = xml.get("age@type");  // Returns the value of attribute "type" on element "age".
            String atype2 = xml.get("age/type"); // Returns element "type" which is a child of "age",
            // OR attribute "type" of age if an element doesn't exist.
            Ssx.Xml data = xml.getNode("data");        // Returns new Xml node for child element "data".
            String dataString = xml.toXml();        // Returns the XML fragment for current node, including children.
            String dataString2 = data.toXml();
            Ssx.Xml row = data.getNode("row");
            Ssx.Xml rowb = xml.getNode("data/row");
            String row1 = row.get();
            row = row.nextSameName();        // Returns next element at the current level of the same name.
            // next() returns next element of any name.
            String row2 = row.get();
            
            ssx.message("\nParsing feed:");
            Ssx ssx2 = new Ssx();
            
        } catch (Exception e) { e.printStackTrace(); }
    }
    
    // Example from: http://www.lexcycle.com/developer
    static final String feed = 
        "<feed xmlns=\"http://www.w3.org/2005/Atom\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n" +
        // "<feed  xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n" +
        "  <title>Online Catalog</title>\n" +
        "  <id>urn:uuid:09aeccc1-c633-aa48-22ab-000052cbd81c</id>\n" +
        "  <updated>2008-09-12T00:44:20+00:00</updated>\n" +
        "  <link rel=\"self\" type=\"application/atom+xml\" href=\"http://www.billybobsbooks.com/catalog/top.atom\"/>\n" +
        "  <link rel=\"search\" title=\"Search Billy Bob's Books\" type=\"application/atom+xml\" href=\"http://www.billybobsbooks.com/catalog/search.php?search={searchTerms}\"/>\n" +
        "  <author>\n" +
        "    <name>Billy Bob</name>\n" +
        "    <uri>http://www.billybobsbooks.com</uri>\n" +
        "    <email>billybob@billybobsbooks.com</email>\n" +
        "  </author>\n" +
        "  <entry>\n" +
        "    <title>1984</title>\n" +
        "    <content type=\"xhtml\">\n" +
        "      <div xmlns:t=\"http://www.w3.org/1999/xhtml\"> Published: 1949 Subject: Novels Language: en</div>\n" +
        "    </content>\n" +
        "    <id>urn:billybobsbooks:1166</id>\n" +
        "    <author>\n" +
        "      <name>Orwell, George</name>\n" +
        "    </author>\n" +
        "    <updated>2008-09-12T00:44:20+00:00</updated>\n" +
        "    <link type=\"application/epub+zip\" href=\"http://www.billybobsbooks.com/book/1984.epub\"/>\n" +
        "    <link rel=\"x-stanza-cover-image-thumbnail\" type=\"image/png\" href=\"http://www.billybobsbooks.com/book/1984.png\"/>\n" +
        "    <link rel=\"x-stanza-cover-image\" type=\"image/png\" href=\"http://www.billybobsbooks.com/book/1984.png\"/>\n" +
        "  </entry>\n" +
        "  <entry>\n" +
        "    <title>The Art of War</title>\n" +
        "    <content type=\"xhtml\">\n" +
        "      <div xmlns:t=\"http://www.w3.org/1999/xhtml\">Published: -500 Subject: Non-Fiction Language: en</div>\n" +
        "    </content>\n" +
        "    <id>urn:billybobsbooks:168</id>\n" +
        "    <author>\n" +
        "      <name>Sun Tzu</name>\n" +
        "    </author>\n" +
        "    <updated>2008-09-12T00:44:20+00:00</updated>\n" +
        "    <link type=\"application/epub+zip\" href=\"http://www.billybobsbooks.com/book/artofwar.epub\"/>\n" +
        "    <link rel=\"x-stanza-cover-image-thumbnail\" type=\"image/png\" href=\"http://www.billybobsbooks.com/book/artofwar.png\"/>\n" +
        "    <link rel=\"x-stanza-cover-image\" type=\"image/png\" href=\"http://www.billybobsbooks.com/book/artofwar.png\"/>\n" +
        "  </entry>\n" +
        "</feed>\n"
        ;
    
}
