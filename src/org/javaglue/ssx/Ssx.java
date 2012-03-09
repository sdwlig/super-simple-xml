/**
 * Ssx.java Copyright 2010, 2011, 2012 Stephen D. Williams,
 * OptimaLogic, and Client sdw@lig.net http://sdw.st License: Apache
 * 2.0
 * 
 * This is a minimal but fully conforming (with exceptions below) XML parser.
 */

/*
 * Current limitations: To be improved: Once namespaces are defined, they remain active for the rest
 * of the parse. Parse but ignore comments currently. Might want to expose. Lexical events are not
 * fully implemented. Currently doesn't support modification. Possible improvements probably not
 * desired: Only support basic set of entitities. Parse but ignore DTDs, PIs.
 */

package org.javaglue.ssx;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.text.ParseException;
// The parser can use the local SAX parser, or the built-in SAX parser.
// For the non-native SAX mode, the following imports are only used for interfaces. Could be
// duplicated inline if needed.
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.DTDHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;


/**
 * The goal of Ssx/Xml is to make application use of XML as simple as possible.
 */
public class Ssx {
  // Enums turn out to be slow on Android 1.6...
  // enum S {
  // Top, LT, Xml, Start, StartRest, StartFinish, AttrName, AttrEqual, AttrVal, AttrNoQ, AttrSingle,
// AttrDouble, MaybeChars, Chars, Comment, End, EndEnd; }
  static final int Top = 0, LT = 1, Xml = 2, Elem = 3, ElemRest = 4, ElemFinish = 5, AttrName = 6,
      AttrEqual = 7, AttrVal = 8, AttrNoQ = 9, AttrSingle = 10, AttrDouble = 11, MaybeChars = 12,
      Chars = 13, Comment = 14, End = 15, EndEnd = 16, DocType = 17, CData = 18;

  /**
   * A fast Sax Parser in 240 lines.
   */
  class SParser implements XMLReader {
    ContentHandler ch;
    ErrorHandler eh;
    Map<String, Boolean> features = new TreeMap<String, Boolean>(/* 10 */);
    Map<String, Object> properties = new TreeMap<String, Object>(/* 10 */);

    public ContentHandler getContentHandler() {
      return ch;
    }

    public DTDHandler getDTDHandler() {
      return null;
    }

    public EntityResolver getEntityResolver() {
      return null;
    }

    public ErrorHandler getErrorHandler() {
      return eh;
    }

    public boolean getFeature(String name) {
      return features.get(name);
    }

    public Object getProperty(String name) {
      return properties.get(name);
    }

    public void parse(InputSource input) throws SAXException, IOException {
      java.io.Reader reader = input.getCharacterStream();
      if (reader != null) {
        StringBuilder sb = new StringBuilder(1024);
        char[] cb = new char[8192];
        int rn = 0;
        while (rn != -1) {
          rn = reader.read(cb, 0, cb.length);
          sb.append(cb, 0, rn);
        }
        parse(sb.toString());
      } else {
        java.io.InputStream is = input.getByteStream();
        if (is == null) throw new IOException(); // !!
        parse(slurp(is));
      }
    }

    public int x, xlen;
    public char[] xmlChars = null;
    int state, periodicCheck = 256;
    boolean inXmlDecl;
    String ns, ln, qn;
    SAttributes atts;
    boolean eatSpace, space;

    // Amortized, lightweight growable character array. Call stretch() every periodicCheck appends
// to avoid running out of space.
    class Cheapcs {
      char[] s;
      int i;

      public Cheapcs(int d) {
        s = new char[d];
        i = 0;
      }

      // If we're getting close, double it.
      public void stretch() {
        if (i + periodicCheck * 2 > s.length) {
          char[] t = new char[s.length * 2];
          System.arraycopy(s, 0, t, 0, i);
          s = t;
        }
      }
    }

    Cheapcs aname = new Cheapcs(periodicCheck * 4);
    Cheapcs ename = new Cheapcs(periodicCheck * 4);
    Cheapcs aval = new Cheapcs(periodicCheck * 4);
    Cheapcs chs = new Cheapcs(periodicCheck * 32);
    int startOfElement; // Pulled by Ssx.startElement to know where element started.

    // Abuse the interface here: literal string rather than systemId...
    // Handle: <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN"
// "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
    public void parse(String xs) throws SAXException {
      if (xs == null) {
        message("Ssx.parse() passed null string.");
        return;
      }
      xmlChars = xs.toCharArray();
      char[] xsc = xmlChars;
      x = 0;
      xlen = xsc.length;
      state = Top;
      inXmlDecl = false;
      atts = null;
      eatSpace = true;

      char c;
      boolean notStarted = true;
      int periodic = 0;
      int docTypeNest = 0;
      try { // This originally had very condensed formatting for a reason. Read each state like a sentence.
        for (; x < xlen; x++) {
          if (x > periodic + periodicCheck) {
            ename.stretch();
            aname.stretch();
            aval.stretch();
            chs.stretch();
            periodic = x;
          }
          c = xsc[x];
          if ((c == ' ' || c == '\t' || c == '\r' || c == '\n')) space = true;
          else space = false;
          if (space && eatSpace) continue;
          switch (state) {
            case Top:
              if (c == '<') {
                state = LT;
                startOfElement = x;
              }
              break;// XmlDecl or top element
            case LT:
              if (notStarted) {
                ch.startDocument();
                npush("_xml_");
                notStarted = false;
              }
              if (c == '!') {
                if (xsc[x + 1] == '-' && xsc[x + 2] == '-') {
                  x = x + 2;
                  state = Comment;
                  eatSpace = false;
                } else if (cmatch(xsc, x + 1, "DOCTYPE")) {
                  x = x + 8;
                  state = DocType;
                  docTypeNest = 1;
                }
              } else if (c == '?') {
                inXmlDecl = true;
                state = Xml;
              } else if (c == '/') {
                state = End;
              } else {
                if (c == '&') x = appendEntity(ename, xsc, x);
                else ename.s[ename.i++] = c;
                state = Elem;
                eatSpace = false;
              }
              break;
            case Xml:
              if (c == 'x' && xsc[x + 1] == 'm' && xsc[x + 2] == 'l') {
                x += 2;
                state = ElemRest;
              } else throw new SAXException("Only support: \"<?xml ...\"\n" + toXmlAll());
              break;
            case Elem:
              if (space || c == '>' || c == '/') {
                if (space) state = ElemRest;
                else {
                  if (c == '/') {
                    state = ElemFinish;
                    eatSpace = true;
                  } else {
                    state = MaybeChars;
                    startElement(atts);
                    npush(ename);
                    ratts();
                    re();
                  }
                }
              } else if (c == '&') x = appendEntity(ename, xsc, x);
              else ename.s[ename.i++] = c;
              break;
            case ElemRest: // Everything after the element name
              if (inXmlDecl && c == '?') {
                if (xsc[x + 1] != '>')
                  throw new SAXException("Malformed XML decl end, expected '>' after '?'\n"
                      + toXmlAll());
                x++; /* ch.startDocument(); notStarted=false; */
                state = Top;
                inXmlDecl = false;
              } else if (c == '>') {
                state = MaybeChars;
                startElement(atts);
                npush(ename);
                ratts();
                re();
              } else if (c == '/') {
                state = ElemFinish;
              } else {
                eatSpace = false;
                state = AttrName;
                if (c == '&') x = appendEntity(aname, xsc, x);
                else aname.s[aname.i++] = c;
              }
              break;
            case ElemFinish:
              if (c == '>') {
                state = MaybeChars;
                startElement(atts); /* npush(ename); npop(ename); */
                endElement();
                ratts();
                re();
              } else throw new SAXException("ElemFinish no '>'.\n" + toXmlAll());
              break;
            case AttrName:
              if (space) {
                eatSpace = true;
                state = AttrEqual;
              } else if (c == '=') {
                if (aname.i == 0) throw new SAXException("Attribute must have name.\n+toXmlAll()");
                eatSpace = true;
                state = AttrVal;
              } else if (c == '&') x = appendEntity(aname, xsc, x);
              else aname.s[aname.i++] = c;
              break;
            case AttrEqual:
              if (c == '=') {
                state = AttrVal;
              } else throw new SAXException("Need '=' after attribute name.\n" + toXmlAll());
              break;
            case AttrVal:
              eatSpace = false;
              if (c == '\'') {
                state = AttrSingle;
              } else if (c == '"') {
                state = AttrDouble;
              } else {
                if (c == '&') x = appendEntity(aval, xsc, x);
                else aval.s[aval.i++] = c;
                state = AttrNoQ;
              }
              break;
            case AttrNoQ:
              if (space || c == '>') {
                state = ElemRest;
                if (c == '>') x--;
                eatSpace = true;
                natts();
                atts.put(new String(aname.s, 0, aname.i), new String(aval.s, 0, aval.i));
                ra();
              } else if (c == '&') x = appendEntity(aval, xsc, x);
              else aval.s[aval.i++] = c;
              break;
            case AttrSingle:
              if (c == '\'') {
                state = ElemRest;
                eatSpace = true;
                natts();
                atts.put(new String(aname.s, 0, aname.i), new String(aval.s, 0, aval.i));
                ra();
              } else if (c == '&') x = appendEntity(aval, xsc, x);
              else aval.s[aval.i++] = c;
              break;
            case AttrDouble:
              if (c == '"') {
                state = ElemRest;
                eatSpace = true;
                natts();
                atts.put(new String(aname.s, 0, aname.i), new String(aval.s, 0, aval.i));
                ra();
              } else if (c == '&') x = appendEntity(aval, xsc, x);
              else aval.s[aval.i++] = c;
              break;
            case MaybeChars:
              if (c == '<') {
                if (xsc[x + 1] == '!' && cmatch(xsc, x + 2, "[CDATA[")) {
                  x = x + 8;
                  state = CData;
                  eatSpace = false;
                } else {
                  state = LT;
                  startOfElement = x;
                }
              } else {
                if (c == '&') x = appendEntity(chs, xsc, x);
                else chs.s[chs.i++] = c;
                state = Chars;
                eatSpace = false;
              }
              break;
            case Chars:
              if (c == '<') {
                if (xsc[x + 1] == '!' && cmatch(xsc, x + 2, "[CDATA[")) {
                  x = x + 8;
                  state = CData;
                  eatSpace = false;
                } else {
                  state = LT;
                  startOfElement = x;
                  eatSpace = true;
                  if (chs.i > 0) {
                    ch.characters(chs.s, 0, chs.i);
                    rc();
                  }
                }
              } else if (c == '&') x = appendEntity(chs, xsc, x);
              else chs.s[chs.i++] = c;
              break;
            case CData:
              if (c == ']' && xsc[x + 1] == ']' && xsc[x + 2] == '>') {
                x = x + 2;
                state = Chars;
              } else if (c == '&') x = appendEntity(chs, xsc, x);
              else chs.s[chs.i++] = c;
              break;
            case Comment:
              if (c == '-' && xsc[x + 1] == '-' && xsc[x + 2] == '>') {
                eatSpace = true;
                state = MaybeChars;
              }
              break;
            case End:
              eatSpace = false;
              if (c == '>') {
                npop(ename);
                endElement();
                re();
                state = MaybeChars;
                eatSpace = true;
              } else if (space) {
                eatSpace = true;
                state = EndEnd;
              } else if (c == '&') x = appendEntity(ename, xsc, x);
              else ename.s[ename.i++] = c;
              break;
            case EndEnd:
              if (c == '>') {
                npop(ename);
                endElement();
                state = MaybeChars;
                re();
              } else throw new SAXException("In element end tag, only space before '>'.\n"
                  + toXmlAll());
              break;
            // For now, just count <> nesting and return to normal state on nesting==0;
            case DocType:
              if (c == '<') {
                docTypeNest++; /* capture */
              } else if (c == '>') {
                docTypeNest--;
                if (docTypeNest < 1) state = MaybeChars;
                else {/* capture */}
              } else {/* capture */}
              break;
          }
        }
        ch.endDocument();
        npop("_xml_");
      } catch (Exception e) {
        e.printStackTrace();
        throw new SAXException(e + "\nStopped at:\n" + toXmlAll());
      } // It's parsing all the way down.
    }

    /**
     * Match current characters against a string, not case sensitive.
     * 
     * @param offs offset
     * @param String to match against, should be uppercase.
     */
    boolean cmatch(char[] xsc, int offs, String s) {
      for (int cx = 0; cx < s.length(); offs++, cx++) {
        char c = s.charAt(cx);
        if (xsc[offs] != c && xsc[offs] != Character.toUpperCase(c)) return false;
      }
      return true;
    }

    int appendEntity(Cheapcs cs, char[] xsc, int x) throws SAXException {
      // Already at '&'
      if (x + 5 >= xsc.length)
        throw new SAXException("Truncated input: not enough characters to parse entity.\n"
            + toXmlAll());
      char next = xsc[x + 1];
      if (next == 'l') if (new String(xsc, x + 1, 3).equalsIgnoreCase("lt;")) {
        cs.s[cs.i++] = '<';
        return x + 3;
      }
      if (next == 'g') if (new String(xsc, x + 1, 3).equalsIgnoreCase("gt;")) {
        cs.s[cs.i++] = '>';
        return x + 3;
      }
      if (next == 'a') if (xsc[x + 2] == 'm') {
        if (new String(xsc, x + 1, 4).equalsIgnoreCase("amp;")) {
          cs.s[cs.i++] = '&';
          return x + 4;
        }
      } else if (new String(xsc, x + 1, 5).equalsIgnoreCase("apos;")) {
        cs.s[cs.i++] = '\'';
        return x + 5;
      }
      if (next == 'q') if (new String(xsc, x + 1, 5).equalsIgnoreCase("quot;")) {
        cs.s[cs.i++] = '\"';
        return x + 5;
      }
      if (next == '#') {
        int dx = x + 2, start;
        char[] c;
        if (xsc[x + 2] == 'x') {
          dx = dx + 1;
          start = dx;
          while (xsc[dx] != ';' && dx - x < 8)
            dx++;
          c = Character.toChars(Integer.parseInt(new String(xsc, start, dx - start), 16));
        } else {
          start = dx;
          while (Character.isDigit(xsc[dx]) && dx - x < 8)
            dx++;
          c = Character.toChars(Integer.parseInt(new String(xsc, start, dx - start)));
        }
        if (xsc[dx] == ';') {
          for (int y = 0; y < c.length; y++)
            cs.s[cs.i++] = c[y];
          return dx;
        }
      }
      cs.s[cs.i++] = '&';
      return x; // Fall through to just use '&' as a normal character since we couldn't parse the
// entity.
    }

    private void endElement() throws SAXException {
      String qn = new String(ename.s, 0, ename.i);
      String ns = "", ln = "";
      int col = -1;
      for (int x = 0; x < ename.i; x++)
        if (ename.s[x] == ':') {
          col = x;
          break;
        }
      if (col > -1) {
        ns = new String(ename.s, 0, col);
        ln = new String(ename.s, col + 1, ename.i - col - 1);
      } else {
        ln = new String(ename.s, 0, ename.i);
      }
      ch.endElement(ns, ln, qn);
    }

    private void startElement(SAttributes atts) throws SAXException {
      String qn = new String(ename.s, 0, ename.i);
      if (debug) verbose("sparse.startElement:" + qn);
      String ns = "", ln;
      int col = -1;
      for (int x = 0; x < ename.i; x++)
        if (ename.s[x] == ':') {
          col = x;
          break;
        }
      if (col > -1) {
        ns = new String(ename.s, 0, col);
        ln = new String(ename.s, col + 1, ename.i - col - 1);
      } else {
        ln = new String(ename.s, 0, ename.i);
      }
      ns = p2um.get(ns);
      if (debug) verbose("sparse.startElement after: ns:" + ns + " ln:" + ln + " qn:" + qn);
      ch.startElement(ns, ln, qn, atts);
    }

    void npush(Cheapcs cs) {
      String s = new String(cs.s, 0, cs.i);
      npush(s);
    }

    void npush(String s) {
      u2pm.push(s);
      p2um.push(s);
    }

    // Make ch.endPrefixMapping() calls here!
    String npop(Cheapcs cs) throws SAXException {
      return npop(new String(cs.s, 0, cs.i));
    }

    String npop(String s) throws SAXException {
      u2pm.pop(s);
      p2um.pop(s);
      return s;
    }

    void natts() {
      if (atts == null) atts = new SAttributes();
    }

    void ratts() {
      if (atts != null) atts.reset();
    }

    void ra() {
      aname.i = 0;
      aval.i = 0;
    }

    void re() {
      ename.i = 0;
    }

    void rc() {
      chs.i = 0;
    }

    public void setContentHandler(ContentHandler handler) {
      ch = handler;
    }

    public void setDTDHandler(DTDHandler handler) {}

    public void setEntityResolver(EntityResolver resolver) {}

    public void setErrorHandler(ErrorHandler handler) {
      eh = handler;
    }

    public void setFeature(String name, boolean value) {
      features.put(name, value);
    }

    public void setProperty(String name, Object value) {
      properties.put(name, value);
    }

    public String toXmlAll() {
      return new String(xmlChars, 0, x);
    }

    StackMap<String, String> u2pm = new StackMap<String, String>(); // Static relative to
// SAttributes. // namespace uri to prefix
    StackMap<String, String> p2um = new StackMap<String, String>(); // Static relative to
// SAttributes. // prefix to namespace uri

    class SAttributes implements org.xml.sax.Attributes {
      Map<String, Integer> qnIndex = new TreeMap<String, Integer>(/* 10 */);

      class Attrib {
        String uri;
        String ln;
        String qn;
        String val;

        public Attrib(String u, String l, String q, String v) {
          qn = q;
          uri = u;
          ln = l;
          val = v;
          if (debug) verbose("Sax new Attrib:ulqv:" + /* u+":"+l+":"+ */q + "=" + v);
        }
      }

      ArrayList<Attrib> as = new ArrayList<Attrib>();
      String ns = "";
      String ln = "";
      String qn = "";
      String fqn = "";
      int col;

      public void reset() {
        as.clear();
      }

      public void put(String q, String val) throws SAXException {
        ns = "";
        ln = q;
        qn = q;
        boolean wasXmlns = false;
        if ((col = qn.indexOf(':')) > -1) {
          ns = qn.substring(0, col);
          ln = qn.substring(col + 1);
          if (ns.equalsIgnoreCase("xmlns")) {
            p2um.put(ln, val);
            u2pm.put(val, ln);
            ch.startPrefixMapping(ln, val);
            wasXmlns = true;
          }
        } else if (qn.equalsIgnoreCase("xmlns")) {
          p2um.put("", val);
          u2pm.put(val, "");
          ch.startPrefixMapping("", val);
          wasXmlns = true;
        }
        if (!wasXmlns) {
          ns = ns.length() > 0 ? p2u(ns) : "";
          if (ns.length() != 0) qn = ns + ":" + ln;
          else qn = ln;
          as.add(new Attrib(ns, ln, qn, val));
          qnIndex.put(qn, as.size());
        }
      }

      // Go from namespace URI to namespace id.
      @SuppressWarnings("unused")
      private String u2p(String ns) {
        String q = u2pm.get(ns);
        if (q == null) return ns;
        else return q;
      }

      // go from namespace id to namespace URI.
      private String p2u(String q) {
        String ns = p2um.get(q);
        if (ns == null) return q;
        else return ns;
      }

      public int getIndex(String qn) {
        ns = "";
        ln = qn;
        if ((col = qn.indexOf(':')) > -1) {
          ns = qn.substring(0, col);
          ln = qn.substring(col + 1);
        }
        return getIndex(ns, ln);
      }

      public int getIndex(String uri, String ln) {
        return qnIndex.get(p2u(uri) + ":" + ln);
      }

      public int getLength() {
        return as.size();
      }

      public String getType(String qn) {
        ns = "";
        ln = qn;
        if ((col = qn.indexOf(':')) > -1) {
          ns = qn.substring(0, col);
          ln = qn.substring(col + 1);
        }
        return getType(ns, ln);
      }

      public String getValue(String qn) {
        ns = "";
        ln = qn;
        if ((col = qn.indexOf(':')) > -1) {
          ns = qn.substring(0, col);
          ln = qn.substring(col + 1);
        }
        return getValue(ns, ln);
      }

      public String getValue(String uri, String ln) {
        return as.get(qnIndex.get(p2u(uri) + ":" + ln)).val;
      }

      public String getLocalName(int index) {
        return as.get(index).ln;
      }

      public String getQName(int index) {
        return as.get(index).qn;
      }

      public String getURI(int index) {
        return as.get(index).uri;
      }

      public String getValue(int index) {
        return as.get(index).val;
      }

      // Unimplemented - need DTDs / schema for this.
      public String getType(int index) {
        return "string";
      }

      public String getType(String uri, String ln) {
        return "string";
      }
    } // End SAttributes
  } // End SParser internal SAX parser

  // Now, back to Ssx:
  // private static final Charset UTF8_CHARSET = Charset.forName("UTF-8");
  StringBuilder rawBuf;
  InputStream input = null;
  int lenEst = 0;
  XMLReader parser;
  SParser sparser = null;
  Handler handler;
  String nsSet;
  boolean rawNeeded = false;

  public Ssx() {}

  // Unused so far. Ideas of a namespace pre-defined set...
  public Xml parseUnused(String xmlString, String nsSet) throws IOException, ParseException,
      UnsupportedEncodingException {
    if (debug) verbose("Ssx xml parse of:" + xmlString);
    this.nsSet = nsSet;
    input = new ByteArrayInputStream(xmlString.getBytes(/* UTF8_CHARSET */"UTF-8"));
    lenEst = xmlString.length();
    return parse(null);
  }

  byte[] xmlBytes = null;
  int off;
  int len;

  public Xml parse(byte[] xmlBytes, int off, int len, String nsSet) throws IOException,
      ParseException {
    if (debug) verbose("Ssx xml parse of:" + new String(xmlBytes, off, len));
    this.nsSet = nsSet;
    this.xmlBytes = xmlBytes; // Make input reusable.
    this.off = off;
    this.len = len;
    lenEst = len;
    return parse(null);
  }

  static boolean sparse = true;
  static boolean defaultParser = false;
  static boolean timeAll = false;
  int elements = 0;

  public static void setParseType(boolean sparsep, boolean defaultParserp, boolean timeAllp) {
    sparse = sparsep;
    defaultParser = defaultParserp;
    timeAll = timeAllp;
  }

  public Xml parse(String xml) throws IOException, ParseException {
    return parse("something", xml, false);
  }

  /**
   * Parse an XML string, possibly timed, possibly with both internal and/or external SAX parsers.
   * 
   */
  public Xml parse(String what, String xml, boolean timed) throws IOException, ParseException {
    long now = 0, then = 0;
    if (xml != null) lenEst = xml.length();
    int len = lenEst;
    boolean both = defaultParser && sparse;
    String first = "";
    try {
      if (defaultParser) {
        init(len);
        // if (input==null)
        // input = new ByteArrayInputStream(xml.getBytes(/*UTF8_CHARSET*/ "UTF-8"));
        try {
          parser = XMLReaderFactory.createXMLReader();
        } catch (Exception e) {
          // Try known "default" for Android:
          System.setProperty("org.xml.sax.driver", "org.xmlpull.v1.sax2.Driver");
          parser = XMLReaderFactory.createXMLReader();
        }
        if (timed || timeAll) then = System.currentTimeMillis();
        rawNeeded = true;
        doParse(false, parser, xml);
        if (timed || timeAll) {
          now = System.currentTimeMillis();
          if (debug)
            verbose("Parser:Default Parsed " + what + " " + Integer.toString(len) + " characters, "
                + elements + " elements in " + Long.toString(now - then) + " ms.");
        }
        input = null;
        if (both) first = doc.toXml();
      }
      if (sparse) {
        init(len);
        sparser = new SParser();
        parser = sparser;
        rawNeeded = false;
        if (timed || timeAll) then = System.currentTimeMillis();
        doParse(true, parser, xml);
        if (timed || timeAll) {
          now = System.currentTimeMillis();
          if (debug)
            verbose("Parser:Sparse  Parsed " + what + " " + Integer.toString(len) + " characters, "
                + elements + " elements in " + Long.toString(now - then) + " ms.");
        }
        if (both) {
          String second = doc.toXml();
          if (!first.equals(second)) {
            message("Parsers produced different result!");
            message("Default SAX:\n" + first);
            message("SParse  SAX:\n" + second);
          }
        }
      }
      input = null; // Used up!
      xmlBytes = null; // Forget input
      return doc;
    } catch (SAXException se) {
      se.printStackTrace();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  void doParse(boolean doSparse, XMLReader parser, String xml) throws SAXException, IOException {
    handler = new Handler();
    parser.setContentHandler(handler);
    String propPre = "http://xml.org/sax/properties/";
    String featPre = "http://xml.org/sax/features/";
    try { // Might throw: SAXNotRecognizedException or SAXNotSupportedException
      parser.setProperty(propPre + "lexical-handler", handler);
    } catch (Exception e) {}
    parser.setFeature(featPre + "namespaces", true);
    parser.setFeature(featPre + "namespace-prefixes", true);

    if (xmlBytes != null) // Then this is where our input comes from.
      input = new ByteArrayInputStream(xmlBytes, off, len);

    if (doSparse) {
      if (xml == null) xml = slurp(input);
      // Really only for SParse. This interface intended for a system entity dereference (i.e. a
// file).
      parser.parse(xml);
    } else {
      if (input == null && xml != null) input = new ByteArrayInputStream(xml.getBytes());
      parser.parse(new InputSource(input));
    }
  }

  Xml doc;
  Stack<Xml> stack;
  Map<String, String> nss;
  Map<String, String> ssn;
  // This is a map from Xml+Entry<String,String> to Xml.
  // In other words: from the current node plus ns/ln to child node.
  Map<Integer/* Logically: Map.Entry<Xml,Map.Entry<String,String>> */, Xml> index;
  Map<Xml, Xml> parents;
  Map<Xml, Xml> currentLastChild; // only used during building (i.e. parsing)
  Map<Xml, Xml> siblings;
  Map<Xml, Xml> siblingsSameName;
  Map<Integer, Xml> currentLastSameName; // only used during building (i.e. parsing)
  String xmlPreamble;

  class XmlAttribute {
    public XmlAttribute(String ns, String ln, String qn, String val) {
      this.ns = ns;
      this.ln = ln;
      this.qn = qn;
      value = val;
    }

    public String ns;
    public String ln;
    public String qn;
    public String value;
  }

  Map<Xml, XmlAttribute[]> attributes;
  Map<Xml, StringBuilder> text;
  Map<Xml, String> texts;

  public void init(int len) throws SAXException {
    rawBuf = new StringBuilder(len + 1024);
    doc = null;
    stack = new Stack<Xml>();
    nss = new TreeMap<String, String>(/* 10 */);
    ssn = new TreeMap<String, String>(/* 10 */);
    index = new TreeMap<Integer /* Map.Entry<Xml,Map.Entry<String,String>> */, Xml>(/* 128 */);
    // parents = new TreeMap<Xml,Xml>(/*128*/); // Optional ability to find parent node.
    // What is last Child? For adding to the end of sibling chain.
    parents = new TreeMap<Xml, Xml>();
    currentLastChild = new TreeMap<Xml, Xml>(/* 128 */);
    siblings = new TreeMap<Xml, Xml>(/* 128 */);
    siblingsSameName = new TreeMap<Xml, Xml>(/* 128 */);
    currentLastSameName = new TreeMap<Integer, Xml>(/* 128 */);
    text = new TreeMap<Xml, StringBuilder>(/* 128 */);
    texts = new TreeMap<Xml, String>();
    attributes = new TreeMap<Xml, XmlAttribute[]>(/* 128 */);
    xmlPreamble = null;
    elements = 0;
  }

  class Handler implements ContentHandler, LexicalHandler {
    Locator locator; // Current location

    public Handler() {}

    public void setDocumentLocator(Locator locator) { // This is a waste right now.
      this.locator = locator;
    }

    public void startDocument() throws SAXException {
      if (debug) verbose("startDocument:" + locationString());
      if (rawNeeded) rawSave("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
    }

    public void endDocument() throws SAXException {
      if (debug) verbose("endDocument:" + locationString());
    }

    public void startPrefixMapping(String prefix, String uri) throws SAXException {
      if (prefix == null) prefix = "";
      if (uri == null) uri = "";
      ssn.put(prefix, uri);
      nss.put(uri, prefix);
      if (debug) verbose("startPrefixMapping:" + prefix + " " + uri);
    }

    public void endPrefixMapping(String prefix) throws SAXException {
      if (prefix == null) prefix = "";
      // Don't remove for now, we need to capture a full set for fragments.
      // nss.remove(prefix);
      if (debug) verbose("endPrefixMapping:" + prefix);
    }

    public void startElement(String ns, String ln, String qn, Attributes atts) throws SAXException {
      if (ns == null) ns = "";
      if (ln == null) ln = "";
      if (qn == null) qn = "";
      elements++;

      // from this node: How do we: find attributes, find parent?
      // from parent: How do we: find this node
      // from last sibling: How do we: find this sibling

      Xml parent = stack.empty() ? null : stack.peek();
      int rawPos = sparser == null ? rawPosition() : sparser.startOfElement;
      Xml newXml = new Xml(rawPos, ns, ln);
      Map.Entry<String, String> entry = new MapEntry<String, String>(ns, ln);
      Map.Entry<Xml, Map.Entry<String, String>> childKey =
          new MapEntry<Xml, Map.Entry<String, String>>(parent, entry);
      if (parent == null || doc == null) doc = newXml; // Root element
      int childKeyHash = childKey.hashCode();
      int newXmlHash = newXml.hashCode();
      if (debug)
        verbose("startElement: " + ns + " " + ns2q(ns) + ":" + ln + ", " + qn + " " + childKeyHash);
      Xml existing = index.get(childKeyHash); // Is there a node with same parent plus same ns:ln?
      if (existing == null) { // First child with this ns:ln.
        index.put(childKeyHash, newXml); // Now we can go from node+ns:ln -> node
      } else { // We're a sibling.
        // We only do this when we have found more than one element with the same name;
        Xml clsn = currentLastSameName.get(childKeyHash); // newXmlHash);
        if (clsn == null) clsn = existing; // If there was only 1 so far, use the one linked to from
// parent.
        siblingsSameName.put(clsn, newXml);
        currentLastSameName.put(childKeyHash, newXml); // newXmlHash, newXml);
      }

      if (parent != null) {
        parents.put(newXml, parent); // Optional?
        Xml parentsLastChild = currentLastChild.get(parent);
        if (parentsLastChild != null) {
          currentLastChild.remove(parent);
          if (debug)
            verbose("siblings.put:parentsLastChild:" + parentsLastChild.hashCode() + ", newXml:"
                + newXmlHash);
          siblings.put(parentsLastChild, newXml);
        }
        currentLastChild.put(parent, newXml);
      }

      XmlAttribute[] xas = null;
      if (atts != null && atts.getLength() > 0) {
        int attrLen = atts.getLength();
        xas = new XmlAttribute[attrLen];
        for (int x = 0; x < attrLen; x++) {
          String ans = atts.getURI(x);
          String aln = atts.getLocalName(x);
          String aqn = atts.getQName(x);
          int col;
          // Even after flipping on both namespace features, Apache SAX still doesn't decode these
// for xmlns...
          if (ans.length() == 0 && aln.length() == 0 && aqn.charAt(0) == 'x') {
            if (aqn.startsWith("xmlns:") || aqn.equals("xmlns")) aln = aqn;
            else {
              col = aqn.indexOf(':');
              if (col > -1) {
                ans = aqn.substring(0, col);
                aln = aqn.substring(col + 1);
              }
            }
          }
          if (debug) verbose("Attributes:" + ans + ":" + aln + ":" + aqn + "=" + atts.getValue(x));
          xas[x] = new XmlAttribute(ans, aln, aqn, atts.getValue(x));
        }
        attributes.put(newXml, xas);
      }
      // rawSaveElement(ns, ln, xas);
      if (rawNeeded) rawSaveElement(qn, xas);
      stack.push(newXml);
    }

    public void endElement(String ns, String ln, String qn) throws SAXException {
      if (ns == null) ns = "";
      if (ln == null) ln = "";
      if (qn == null) qn = "";
      if (debug) verbose("endElement: " + ns + " " + ns2q(ns) + ":" + ln + ", " + qn);
      Xml xml = stack.peek();
      // rawSave("</", ns2qn(xml.namespace(), xml.name()), ">");
      if (rawNeeded) {
        rawBuf.append("</");
        ns2qnRawSave(xml.namespace(), xml.name());
        rawBuf.append(">");
      }
      int rawPos = sparser == null ? rawPosition() : sparser.x;
      stack.peek().setEndChar(rawPos); // Character after last character of raw XML bytes for this
// element.
      stack.pop();
    }

    public void characters(char[] chars, int start, int length) throws SAXException {
      // try {
      Xml head = stack.empty() ? null : stack.peek();
      StringBuilder sb = null;
      if (head != null) sb = text.get(head);
      if (sb == null) {
        sb = new StringBuilder(length);
        text.put(head, sb);
        if (debug)
          verbose("characters: text.put:" + head.hashCode() + ", "
              + new String(chars, start, length));
      }
      if (rawNeeded) rawSaveEscaped(chars, start, length);
      sb.append(chars, start, length);
      // } catch (/*UnsupportedEncoding*/ Exception ue) {}
    }

    public void ignorableWhitespace(char[] chars, int start, int length) throws SAXException {}

    public void processingInstruction(String target, String data) throws SAXException {}

    public void skippedEntity(String name) throws SAXException {}

    String locationString() {
      return locator == null ? "" : "Line:" + locator.getLineNumber() + " Col:"
          + locator.getColumnNumber();
    }

    /**
     * Where are we in the bytes for raw XML?
     */
    int rawPosition() {
      return rawBuf.length();
    } // rawBuf.position(); }

    // These are LexicalHandler methods:
    public void comment(char[] ch, int start, int length) {}

    public void endCDATA() {}

    public void endDTD() {}

    public void endEntity(String name) {}

    public void startCDATA() {}

    public void startDTD(String name, String publicId, String systemId) {}

    public void startEntity(String name) {}
  }

  /**
   * Map of simple XML to real XML.
   */
  void map(String map) {}

  private void rawSaveEscaped(String str) {
    char[] chars = str.toCharArray();
    rawSaveEscaped(chars, 0, chars.length);
    /*
     * int len = str.length(), pos=0, x; // Do a first pass to see if creating a new object is
     * necessary. boolean haveTo = false; for (x=0;!haveTo && x < len; x++) switch(str.charAt(x)) {
     * case '\"': case '&': case '\'': case '<': case '>': pos = x; haveTo = true; break; } if
     * (!haveTo) rawBuf.append(str); else { if (pos > 0) rawBuf.append(str.substring(0,pos)); for
     * (x=pos;x < len; x++) { char ch = str.charAt(x); String newstr = null; switch(ch) { case '\"':
     * newstr = "&quot;"; break; case '&': newstr = "&amp;"; break; case '\'': newstr = "&apos;";
     * break; case '<': newstr = "&lt;"; break; case '>': newstr = "&gt;"; break; } if (newstr !=
     * null) rawBuf.append(newstr); else rawBuf.append(ch); } }
     */
  }

  private void rawSaveEscaped(char[] chars, int start, int length) {
    int len = length, pos = 0, x;
    // Do a first pass to see if creating a new object is necessary.
    boolean haveTo = false;
    for (x = start; !haveTo && x < start + len; x++)
      switch (chars[x]) {
        case '\"':
        case '&':
        case '\'':
        case '<':
        case '>':
          pos = x;
          haveTo = true;
          break;
      }
    if (!haveTo) rawBuf.append(chars, start, length);
    else {
      if (pos > start) rawBuf.append(chars, start, pos - start);
      for (x = pos; x < start + len; x++) {
        char ch = chars[x];
        String newstr = null;
        switch (ch) {
          case '\"':
            newstr = "&quot;";
            break;
          case '&':
            newstr = "&amp;";
            break;
          case '\'':
            newstr = "&apos;";
            break;
          case '<':
            newstr = "&lt;";
            break;
          case '>':
            newstr = "&gt;";
            break;
        }
        if (newstr != null) rawBuf.append(newstr);
        else rawBuf.append(ch);
      }
    }
  }

  private void rawSave(String str) {
    rawBuf.append(str);
  }

  private void rawSave(String str, String str2) {
    rawBuf.append(str);
    rawBuf.append(str2);
  }

  @SuppressWarnings("unused")
  private void rawSave(String str, String str2, String str3) {
    rawBuf.append(str);
    rawBuf.append(str2);
    rawBuf.append(str3);
  }

  private void rawSaveElement(String qn, XmlAttribute[] atts) {
    rawSaveElement(null, qn, atts);
  }

  private void rawSaveElement(String ns, String ln, XmlAttribute[] atts) {
    rawBuf.append("<");
    if (ns == null) rawBuf.append(ln);
    else ns2qnRawSave(ns, ln);
    for (int x = 0; atts != null && x < atts.length; x++) {
      rawBuf.append(" ");
      ln = atts[x].ln;
      // Normally we look up ns->prefix, but not for xmlns!
      if (!(ln.equals("xmlns") || ln.startsWith("xmlns:"))) ns2qnRawSave(atts[x].ns, ln);
      rawBuf.append("=\"");
      rawSaveEscaped(atts[x].value);
      rawBuf.append("\"");
    }
    rawBuf.append(">");
  }

  /*
   * public static String escapeXml(String str) { StringBuilder sb; int len = str.length(), pos=0,
   * x; // Do a first pass to see if creating a new object is necessary. boolean haveTo = false; for
   * (x=0;!haveTo && x < len; x++) { switch(str.charAt(x)) { case '\"': case '&': case '\'': case
   * '<': case '>': pos = x; haveTo = true; break; } } if (!haveTo) return str; sb = new
   * StringBuilder(str.length()+32); if (pos > 0) sb.append(str.substring(0,pos)); for (x=pos;x <
   * len; x++) { char ch = str.charAt(x); String newstr = null; switch(ch) { case '\"': newstr =
   * "&quot;"; break; case '&': newstr = "&amp;"; break; case '\'': newstr = "&apos;"; break; case
   * '<': newstr = "&lt;"; break; case '>': newstr = "&gt;"; break; } if (newstr != null)
   * sb.append(newstr); else sb.append(ch); } return sb.toString(); }
   */

  // Can't have a static member of a nested class
  static long XmlserialMaster = 0; // Each Xml node is unique for identity.

  /**
   * Xml node representation.
   */
  public class Xml implements Comparable<Xml> {
    long serial;
    int startChar, endChar;
    String nsi; // namespace
    String lni; // localname
    XmlAttribute selectedAttr;

    public Xml(int startChar, String nsi, String lni) {
      if (debug) verbose("new Xml(" + startChar + ", " + ns2q(nsi) + ", " + lni + ");");
      this.startChar = startChar;
      this.nsi = nsi;
      this.lni = lni;
      serial = incrementSerial();
    }

    // These allow modification of a blank tree or one already parsed.
    // This code section is not yet complete.
    public Xml setName(String ns, String ln) {
      nsi = ns;
      lni = ln;
      return this;
    }

    public Xml setName(String qn) {
      int col;
      String ln = qn, ns = "";
      if ((col = qn.indexOf(':')) > -1) {
        ns = qn.substring(0, col);
        ln = qn.substring(col + 1);
      }
      nsi = ns;
      lni = ln;
      return this;
    }

    public Xml setText(String txt) {
      texts.put(this, txt);
      // text.remove(this); // Or could just ignore and make harmless.
      return this;
    }

    public Xml appendText(String txt) {
      String xs = texts.get(this);
      StringBuilder sb;
      boolean put = false;
      if (xs != null) {
        sb = new StringBuilder(xs);
        put = true;
        texts.remove(this);
      } else sb = text.get(this);
      if (sb == null) {
        sb = new StringBuilder(txt);
        put = true;
      } else sb.append(txt);
      if (put) text.put(this, sb);
      return this;
    }

    // ToDo: Add this as an option.
    public Xml addChild(String qn, String txt) {
      int col;
      String ln = qn, ns = "";
      if ((col = qn.indexOf(':')) > -1) {
        ns = qn.substring(0, col);
        ln = qn.substring(col + 1);
      }
      @SuppressWarnings("unused")
      Xml newXml = new Xml(-1, ns, ln);
      Map.Entry<String, String> entry = new MapEntry<String, String>(ns, ln);
      @SuppressWarnings("unused")
      Map.Entry<Xml, Map.Entry<String, String>> childKey =
          new MapEntry<Xml, Map.Entry<String, String>>(this, entry);
      // ...
      return this;
    }

    public Xml addSibling(String qname, String txt) {
      return this;
    }

    public Xml addAttribute(String qname, String val) {
      return this;
    }

    public Xml delAttribute(String qname) {
      return this;
    }

    public Xml delChild(String qname) {
      return this;
    }

    public Xml delChildren(String qname) {
      return this;
    }

    // Add Xml as: additional child, only child, to replace current node, or to add/replace children
// with children of node
    public Xml addXml(Xml child) {
      return this;
    }

    public Xml setXmlChild(Xml xml) {
      return this;
    }

    public Xml setXml(Xml xml) {
      return this;
    }

    public Xml setXmlChildrenFrom(Xml xml) {
      return this;
    }

    public Xml addXmlChildrenFrom(Xml xml) {
      return this;
    }

    // End modification methods.

    public int compareTo(Xml ol) {
      if (ol.serial == serial) return 0;
      if (serial > ol.serial) return 1;
      else return -1;
    }

    // Every Xml node is unique. And must be.
    private synchronized long incrementSerial() {
      return XmlserialMaster++;
    }

    public int hashCode() {
      return (int) serial;
    } // Should ^ upper / lower...

    // Another Xml is only equal to this one if it is really the same node.
    public boolean equals(Object o) {
      if (o instanceof Xml) {
        Xml ox = (Xml) o;
        if (nsi == null ? ox.nsi == null : nsi.equals(ox.nsi) && lni == null ? ox.lni == null : lni
            .equals(ox.lni) && serial == ox.serial) return true;
        else return false;
      } else return false;
    }

    protected void setEndChar(int end) {
      endChar = end;
      if (debug) verbose("setEndChar:" + end);
    }

    /**
     * Value of this element.
     */
    public String toString() {
      return getText();
    }

    public String getText() {
      // If we just selected an attribute, return the value.
      if (selectedAttr != null) {
        XmlAttribute xa = selectedAttr;
        selectedAttr = null;
        return xa.value;
      }
      // Otherwise get the combined text.
      String xs = texts.get(this);
      if (xs != null) {
        if (debug) verbose("characters: text.get:" + this.hashCode() + ", " + xs);
        return xs;
      }
      StringBuilder sb = text.get(this);
      if (debug)
        verbose("characters: text.get:" + this.hashCode() + ", "
            + (sb == null ? "null" : sb.toString()));
      if (sb == null) return "";
      // Could cache this in another map... Bad for single retrievals, good for repeated cases.
      return sb.toString();
    }

    /**
     * Create an XML preamble and generic top level that includes namespace declarations.
     * 
     */
    public String nss2Xml() throws UnsupportedEncodingException {
      // if (this == doc) return new String(rawBuf.array(), 0, startChar, /*UTF8_CHARSET*/"UTF-8");
// // If we're at root, don't need to wrap.
      if (this == doc) if (sparser == null) return rawBuf.substring(0, startChar).toString();
      else return new String(sparser.xmlChars, 0, startChar);
      if (xmlPreamble == null) { // cache
        StringBuilder nx =
            new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><xml");
        // boolean doneOnce = false;
        for (Map.Entry<String, String> pair : ssn.entrySet()) {
          // if (!doneOnce) { nx.append(" "); doneOnce = true; }
          nx.append(" xmlns" + (pair.getKey().length() > 0 ? (":" + pair.getKey()) : "") + "=\""
              + pair.getValue() + "\"");
        }
        nx.append(">");
        xmlPreamble = nx.toString();
      }
      return xmlPreamble;
    }

    public String nss2XmlEnd() {
      if (this == doc) return ""; // If we're at root, don't need to wrap.
      return "</xml>";
    }

    /**
     * Return XML, with generic '<xml>' plus namespace decls for this element, original if not
     * changed. This is parseable later, including the same namespaces, with an extra '/xml/'
     * element and missing any real parents.
     */
    public String toXml() throws UnsupportedEncodingException {
      String res = toXmlFragment();
      return res == null ? "" : nss2Xml() + res + nss2XmlEnd();
    }

    /**
     * Return XML fragment for this element, original if not changed.
     */
    public String toXmlFragment() throws UnsupportedEncodingException {
      String res = null;
      // res = new String(rawBuf.array(), startChar, endChar-startChar, /*UTF8_CHARSET*/ "UTF-8");
      try {
        if (sparser != null) res = new String(sparser.xmlChars, startChar, endChar - startChar + 1);
        else res = rawBuf.substring(startChar, endChar).toString();
      } catch (Exception e) {
        e.printStackTrace();
      }
      return res;
    }

    /**
     * Next in a series of nodes
     */
    public Xml next() {
      if (debug) verbose("siblings.get:" + this.hashCode());
      return siblings.get(this);
    }

    public Xml nextSameName() {
      if (debug) verbose("siblingsSameName.get:" + this.hashCode());
      return siblingsSameName.get(this);
    }

    public Xml getNode(String localName) {
      return getNode(null, localName);
    }

    public Xml getNode(String ns, String localName) {
      int pos = 0, slash = 0;
      Xml curx = this;
      if (localName.indexOf('/') == 0) {
        curx = doc;
        pos++;
      }
      if (localName.indexOf('@') != 0) {
        // curx should != null, but be sane.
        while (curx != null && (slash = localName.indexOf('/', pos)) > -1) {
          if (slash == pos + 1) continue; // Skip multiple '/'s.
          curx = getChildNode(curx, ns, localName.substring(pos, slash));
          pos = slash + 1;
        }
        Xml tmpx = null;
        // This handles: A) eName@aName, B) eName, C) eName/aName (attribute with element syntax)
        // i.e. allow searching for either element or attribute with '/name' syntax, but try element
// first.
        // This is a programming convenience and one reasonable XML idiom, not anything that breaks
// the XML format.
        // The idea is that a terminal value could reasonbly be an element with text or an
// attribute.
        if ((slash = localName.indexOf('@', pos)) > -1) tmpx =
            getChildNode(curx, ns, localName.substring(pos, slash));
        else tmpx = getChildNode(curx, ns, localName.substring(pos));
        if (tmpx != null) {
          if (slash == -1) { // B) We have a final element, just return
            return tmpx;
          } else pos = slash + 1; // A) eName@aName -> attribute
          curx = tmpx;
        } // else C) we have a name with no separators, but it is not an element, assume it might
// refer to an attribute.
      } else { // Looking for local attribute: "@attr"
        pos = 1;
      }
      // We can only be looking for an attribute in the curx node now.
      String attrName, attrNS;
      attrName = localName.substring(pos);
      int col = attrName.indexOf(':');
      if (col < 0) attrNS = "";
      else {
        attrNS = q2ns(attrName.substring(0, col));
        attrName = attrName.substring(col + 1);
      }
      // Feels like a hack, however this is clean from outside.
      XmlAttribute attrs[] = null;
      attrs = attributes.get(curx);
      if (attrs != null) {
        for (int y = 0; y < attrs.length; y++)
          if (attrs[y].ln.equals(attrName) && attrs[y].ns.equals(attrNS)) {
            curx.selectedAttr = attrs[y];
            return curx;
          }
      }
      return null; // If we had it, we would have found it already.
    }

    // shallow case: find a matching child
    public Xml getChildNode(Xml start, String ns, String localName) {
      if (ns == null) {
        int col;
        if ((col = localName.indexOf(':')) < 0) ns = "";
        else {
          ns = localName.substring(0, col); // prefix
          localName = localName.substring(col + 1);
        }
        ns = q2ns(ns); // prefix -> ns lookup
      }
      // Make a key from (current node + ns + localname).hashCode()
      int hash =
          new MapEntry<Xml, Map.Entry<String, String>>(start, new MapEntry<String, String>(ns,
              localName)).hashCode();
      Xml retXml = index.get(hash);
      if (debug) {
        String retPrint = retXml == null ? "null" : Integer.toString(retXml.hashCode());
        verbose("getNode: " + ns + ":" + localName + " " + hash + "=>" + retPrint);
      }
      return retXml;
    }

    String namespace() {
      return nsi;
    }

    String name() {
      return lni;
    }

    public String get() {
      return getText();
    }

    public String get(String qname) {
      String retVal = null;
      try {
        retVal = get(qname, "");
      } catch (Exception ex) {}

      return retVal;
    }

    /**
     * Get the value of a qname. I.e., single path that might include nsnames. Each level might
     * include a ns!
     */
    public String get(String qname, String def) {
      return get(null, qname, def);
    }

    public String get(String ns, String path, String def) {
      Xml xml = getNode(ns, path);
      if (xml == null) return def;
      else return xml.getText();
    }

    // Int variants
    public int getInt() {
      return Integer.parseInt(get());
    }

    public int getInt(int defaultInt) {
      String val = null;
      int rval = 0;
      try {
        val = get();
        rval = Integer.parseInt(val);
      } catch (Exception e) {
        val = null;
      }
      if (val == null) return defaultInt;
      return rval;
    }

    public int getInt(String path, int defaultInt) {
      String val = null;
      int rval = 0;
      try {
        val = get(path);
        rval = Integer.parseInt(val);
      } catch (Exception e) {
        val = null;
      }
      if (val == null) return defaultInt;
      return rval;
    }

    public int getInt(String path) {
      return Integer.parseInt(get(path));
    }

    // Double
    public double getDouble() {
      return getDouble(0.0);
    }

    public double getDouble(double defaultDouble) {
      String val = null;
      double rval = 0.0;
      try {
        val = get();
        rval = Double.parseDouble(val);
      } catch (Exception e) {
        val = null;
      }
      if (val == null) return defaultDouble;
      return rval;
    }

    public double getDouble(String path, double defaultDouble) {
      String val = null;
      double rval = 0.0;
      try {
        val = get(path);
        rval = Double.parseDouble(val);
      } catch (Exception e) {
        val = null;
      }
      if (val == null) return defaultDouble;
      return rval;
    }

    public double getDouble(String path) {
      return getDouble(path, 0.0);
    }
    // Float
    // Date
  }

  /**
   * Map namespace to prefix used in parsed document. Use this to dynamically map namespace based
   * element or attribute names to whatever prefix the parsed document uses. An empty string implies
   * the default namespace. Null is returned if the namespace is not found. Call this for each
   * desired namespace prefix after the document is parsed.
   * 
   * @param ns The namespace URI to find a mapping for.
   * @return The namespace prefix with no ":". Empty string if this is the default namespace. Null
   *         if not found.
   */
  public String namespaceToPrefix(String ns) {
    String q = nss.get(ns);
    if (q == null) return null;
    else return q;
  }

  /**
   * Go from namespace URI to namespace id.
   */
  private String ns2q(String ns) {
    String q = nss.get(ns);
    if (q == null) return ns;
    else return q;
  }

  /**
   * go from namespace id to namespace URI.
   */
  private String q2ns(String q) {
    String ns = ssn.get(q);
    if (ns == null) return q;
    else return ns;
  }

  /**
   * Convert from ns + localName to qname.
   */
  private void ns2qnRawSave(String ns, String ln) {
    String q = nss.get(ns);
    if (!(q == null || q.length() == 0)) rawSave(q, ":");
    rawBuf.append(ln);
  }

  @SuppressWarnings("unused")
  private String ns2qnHide(String ns, String ln) {
    String q = nss.get(ns);
    if (q == null || q.length() == 0) return ln;
    else return q + ":" + ln;
  }

  public static boolean isEmpty(String s) {
    if (s == null || s.length() == 0) return true;
    return false;
  }

  static boolean debug = false;
  static boolean verbose = false;

  public static void setDebug(boolean deb, boolean verb) {
    debug = deb;
    verbose = verb;
  }

  public static void message(String tag, String msg) {
    if (debug) System.out.println(tag + " " + msg);
  }

  public static void message(String msg) {
    if (debug) System.out.println(msg);
  }

  public static void verbose(String msg) {
    if (verbose) System.out.println(msg);
  }

  public static void error(String msg) {
    System.err.println(msg);
  }

  /*
   * This class: Licensed to the Apache Software Foundation (ASF) under one or more contributor
   * license agreements. See the NOTICE file distributed with this work for additional information
   * regarding copyright ownership. The ASF licenses this file to You under the Apache License,
   * Version 2.0 (the "License"); you may not use this file except in compliance with the License.
   * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
   * required by applicable law or agreed to in writing, software distributed under the License is
   * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
   * or implied. See the License for the specific language governing permissions and limitations
   * under the License.
   */

  // package java.util;

  /**
   * MapEntry is an internal class which provides an implementation of Map.Entry.
   */
  class MapEntry<K, V> implements Map.Entry<K, V>, Cloneable {
    K key;
    V value;

    // interface Type<RT, KT, VT> {
    // RT get(MapEntry<KT, VT> entry);
    // }
    public MapEntry(K theKey) {
      key = theKey;
    }

    public MapEntry(K theKey, V theValue) {
      key = theKey;
      value = theValue;
    }

    @Override
    public Object clone() {
      try {
        return super.clone();
      } catch (CloneNotSupportedException e) {
        return null;
      }
    }

    @Override
    public boolean equals(Object object) {
      if (this == object) { return true; }
      if (object instanceof Map.Entry) {
        Map.Entry<?, ?> entry = (Map.Entry<?, ?>) object;
        return (key == null ? entry.getKey() == null : key.equals(entry.getKey()))
            && (value == null ? entry.getValue() == null : value.equals(entry.getValue()));
      }
      return false;
    }

    public K getKey() {
      return key;
    }

    public V getValue() {
      return value;
    }

    @Override
    public int hashCode() {
      return (key == null ? 0 : key.hashCode()) ^ (value == null ? 0 : value.hashCode());
    }

    public V setValue(V object) {
      V result = value;
      value = object;
      return result;
    }

    @Override
    public String toString() {
      return key + "=" + value;
    }
  }
  // A stack of maps that conflate for lookups.
  public class StackMap<K, V> implements Map<K, V> {
    LinkedList<Map<K, V>> smap = new LinkedList<Map<K, V>>();
    LinkedList<String> sstack = new LinkedList<String>();

    public StackMap() {} // Start with one

    public void push(String ename) {
      smap.addFirst(new /* Tree */TreeMap<K, V>());
      sstack.addFirst(ename);
    }

    public String pop(String ename) throws SAXException {
      String s = pop();
      if (!s.equals(ename)) throw new SAXException("Didn't pop right end tag.  Input truncated?");
      return s;
    }

    public String pop() {
      smap.removeFirst();
      String s = sstack.removeFirst();
      return s;
    }

    public String peek() {
      return sstack.getFirst();
    }

    public int levels() {
      return smap.size();
    }

    public void clear() {
      for (Map<K, V> m : smap)
        m.clear();
    }

    public boolean containsKey(Object key) {
      for (Map<K, V> m : smap)
        if (m.containsKey(key)) return true;
      return false;
    }

    public boolean containsValue(Object val) {
      for (Map<K, V> m : smap)
        if (m.containsValue(val)) return true;
      return false;
    }

    public Set<Map.Entry<K, V>> entrySet() {
      return null;
    } // unused.

    public boolean equals(Object o) {
      if (o == this) return true;
      return false;
    } // unused.

    public V get(Object key) {
      V t;
      for (Map<K, V> m : smap)
        if ((t = m.get(key)) != null) return t;
      return null;
    }

    public int hashCode() {
      int hash = 0;
      for (Map<K, V> m : smap)
        hash = hash ^ m.hashCode();
      return hash;
    }

    public boolean isEmpty() {
      for (Map<K, V> m : smap)
        if (!m.isEmpty()) return false;
      return true;
    }

    public Set<K> keySet() {
      return null;
    }

    public V put(K key, V val) {
      return smap.getFirst().put(key, val);
    }

    public void putAll(Map<? extends K, ? extends V> mn) {
      for (Map.Entry<? extends K, ? extends V> m : mn.entrySet())
        smap.getFirst().put(m.getKey(), m.getValue());
    }

    public V remove(Object key) {
      for (Map<K, V> m : smap)
        if (!m.containsKey(key)) {
          V v = m.remove(key);
          return v;
        }
      return null;
    }

    public int size() {
      int sz = 0;
      for (Map<K, V> m : smap)
        sz = sz + m.size();
      return sz;
    }

    public Collection<V> values() {
      return null;
    } // unused.
  }

  // Some utility methods that are not always available or reliable.
  public static String slurp(InputStream is) throws IOException {
    if (is == null) return null;
    StringBuilder out = new StringBuilder();
    InputStreamReader isr = new InputStreamReader(is, "UTF-8");
    Reader in = new BufferedReader(isr);
    int ch;
    while ((ch = in.read()) > -1)
      out.append((char) ch);
    in.close();
    return out.toString();
  }

  /*
   * Bad because it can cut multi-byte UTF-8 characters byte[] b = new byte[4096]; for (int n; (n =
   * in.read(b)) != -1;) { // info("Slurp got:"+new String(b, 0, n)); out.append(new String(b, 0,
   * n)); }
   */
  public static byte[] slurpBytes(InputStream is) throws IOException {
    int allocLen = 8096;
    byte[] buffer = new byte[allocLen];
    int soFar = 0;
    while (is.available() > 0) {
      if (allocLen - soFar < 1) { // Double buffer, copy, keep reading.
        allocLen = allocLen * 2;
        byte[] newBuf = new byte[allocLen];
        System.arraycopy(buffer, 0, newBuf, 0, soFar);
        buffer = newBuf;
      }
      int justRead = is.read(buffer, soFar, allocLen - soFar);
      soFar = soFar + (justRead > 0 ? justRead : 0);
    }
    // Inneficient, but unless we return more than one variable...
    byte[] retBuf = new byte[soFar];
    System.arraycopy(buffer, 0, retBuf, 0, soFar);
    return retBuf;
  }

  /**
   * urlEncoder that works just like java.net.URLEncoder.encode, except that ' '->'%20'. And [.-~_]
   * not [.-*_]. And we assume UTF-8.
   */
  public static String urlEncode(String s) {
    int slen = s.length();
    int encode = -1;
    for (int x = 0; x < slen; x++)
      if (needsEncode(s.charAt(x))) {
        encode = x;
        break;
      }
    if (encode < 0) return s; // Doesn't need encoding.
    StringBuffer sb = new StringBuffer(s.substring(0, encode));
    for (int x = encode; x < slen; x++) {
      if (!needsEncode(s.charAt(x))) sb.append(s.charAt(x));
      else {
        byte[] bs = null;
        try {
          bs = s.substring(x, x + 1).getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
          e.printStackTrace();
        }
        for (int y = 0; bs != null && y < bs.length; y++)
          sb.append("%" + "0123456789ABCDEF".charAt(bs[y] >> 4)
              + "0123456789ABCDEF".charAt(bs[y] & 0x0f));
      }
    }
    // This tests both encode and decode:
    // info("urlEncode:"+s+"->"+sb.toString()+"\nurlDecode:"+urlDecode(sb.toString()));
    return sb.toString();
  }

  public static boolean needsEncode(char c) {
    // Current server operation:
    if (Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '*' || c == '_') return false;
    // Correct according to spec that refers to RFC 3986:
    // if (Character.isLetterOrDigit(c) || c=='.' || c=='-' || c=='~' || c=='_') return false;
    return true;
  }

  public static String urlDecode(String s) {
    int slen = s.length();
    if (s.indexOf('%') < 0) return s; // Nothing to decode.
    byte[] bytes = new byte[slen];
    int bidx = 0;
    for (int x = 0; x < slen; x++)
      if (s.charAt(x) == '%') {
        bytes[bidx++] = (byte) Integer.parseInt(s.substring(x + 1, x + 3), 16);
        x = x + 2; // Skip 3 bytes on this round.
      } else bytes[bidx++] = (byte) s.charAt(x);
    String rets = null;
    try {
      rets = new String(bytes, 0, bidx, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    return rets;
  }
}
/*
 * Todo notes: Doesn't handle what would be edge cases for most applications: attribute prefixes
 * defined earlier in the same element.
 */
