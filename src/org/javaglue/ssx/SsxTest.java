/**
 * SsxTest.java Copyright 2010, 2011, 2012 Stephen D. Williams, OptimaLogic,
 * and Client License: Apache 2.0
 *
 * This needs to be cleaned up and should include a good test corpus.
 * Should provide a good set of examples anyway.
 */

package org.javaglue.ssx;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;

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
      Ssx.setDebug(true, true); // Turns on debug/verbose internal Ssx trace.
      String dtest =
          "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
              + "<cor:errors xmlns:cor=\"http://example.com/api\">"
              + "  <cor:error code=\"-20500\">"
              + "    <cor:message>Login name is invalid</cor:message>" + "  </cor:error>"
              + "</cor:errors>";
      Ssx dssx = new Ssx();
      Ssx.Xml dxml = dssx.parse(dtest);
      int statusCode = 0;
      try {
        statusCode = dxml.getInt("cor:errors/cor:error@code");
      } catch (Exception e) {
        // caught exception
      }

      FileInputStream fis = new FileInputStream("/tmp/test.xml");
      if (fis != null) {
        Ssx.Xml test = null;
        String xmlData = Ssx.slurp(fis);
        try {
          test = ssx.parse(xmlData);
        } catch (Exception e) {
          e.printStackTrace();
        }
        String dc = ssx.namespaceToPrefix("http://purl.org/dc/elements/1.1/");
        if (dc == null) dc = "";
        else dc = dc + ":";
        String cat = ssx.namespaceToPrefix("http://example.com/v1/catalog");
        if (cat == null) cat = "";
        else cat = cat + ":";
        String age = ssx.namespaceToPrefix("http://purl.org/atompub/age/1.0");
        if (age == null) age = "";
        else age = age + ":";
        if (test != null) {
          Ssx.message("Parse for: file:///tmp/test.xml");
          String id = test.get("id");
          String title = test.get("title");
          Ssx.message("id:" + id + " title:" + title);
          Ssx.Xml entry = test.getNode("entry");
          while (entry != null) {
            Ssx.message("Entry:" + entry.get("id"));
            Ssx.Xml dcid = entry.getNode(dc + "identifier");
            while (dcid != null) {
              Ssx.message("  dc:identifier: cat:type:" + dcid.get("@" + cat + "type") + " content:"
                  + dcid.get());
              dcid = dcid.nextSameName();
            }
            entry = entry.nextSameName();
          }
        }
      }
      Ssx.message("testSummary:");
      testSummary();

      fx =
          ssx.parse("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><test><!DOCTYPE greeting [ <!ELEMENT greeting (#PCDATA)> ]>this <![CDATA[ is <nice!> ]]> ok?</test>");
      Ssx.message("DTD / CDATA:" + fx.toXml());
      Ssx.message("Parsed:" + fx/* .getNode("test") */.toXml());
      // ssx.setParseType(true, false, true); // Both SParse and default/native.
      fx = ssx.parse("feed", feed, true);
      fx = ssx.parse("feed pass 2", feed, true); // Just to compare second pass speed
      Ssx.message("Parsed:" + fx.toXml());

      for (Ssx.Xml entry = fx.getNode(/* "http://www.w3.org/2005/Atom: */"entry"); entry != null; entry =
          entry.nextSameName()) {
        System.out.println("Entry: " + entry.toXml());
        for (Ssx.Xml link = entry.getNode("link"); link != null; link = link.nextSameName()) {
          Ssx.message("type " + link.get("@type"));
          Ssx.message("href " + link.get("@href"));
        }
      }

      Ssx.message("Done with feed.\n\n\n");
      Ssx.Xml xml =
          ssx.parse(
              "mini msg",
              "<msg start=\"45\" end='50'><fname>Bob|&amp;|&lt;|&gt;|&apos;|&quot;|&#x20;|</fname><!-- whatever... <ha!> --><age type=\"int\">55</age><data><row>one</row><row>two</row></data></msg>",
              true);
      // ssx.map("fname=entry/fname,"); // Not working yet
      String nodeName = xml.name(); // Returns name of element.
      String fname = xml.get("fname"); // Returns value of "fname" element relative to current
// location.
      Ssx.message("fname:" + fname);
      int start = xml.getInt("@start", -1); // Returns value of "start" attribute as an int, with a
// default of -1 if not found.
      Ssx.message("@start:" + Integer.toString(start));
      int age = xml.getInt("age", -1); // Returns value of "age" element as an int, with a default
// of -1 if not found.
      Ssx.message("age:" + Integer.toString(age));
      String atype = xml.get("age@type"); // Returns the value of attribute "type" on element "age".
      String atype2 = xml.get("age/type"); // Returns element "type" which is a child of "age",
      // OR attribute "type" of age if an element doesn't exist.
      Ssx.Xml data = xml.getNode("data"); // Returns new Xml node for child element "data".
      String dataString = xml.toXml(); // Returns the XML fragment for current node, including
// children.
      String dataString2 = data.toXml();
      Ssx.Xml row = data.getNode("row");
      Ssx.Xml rowb = xml.getNode("data/row");
      String row1 = row.get();
      row = row.nextSameName(); // Returns next element at the current level of the same name.
      // next() returns next element of any name.
      String row2 = row.get();

      Ssx.message("\nParsing feed:");
      Ssx ssx2 = new Ssx();

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  // Example from: http://www.lexcycle.com/developer
  static final String feed =
      "<feed xmlns=\"http://www.w3.org/2005/Atom\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n"
          +
          // "<feed  xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n" +
          "  <title>Online Catalog</title>\n"
          + "  <id>urn:uuid:09aeccc1-c633-aa48-22ab-000052cbd81c</id>\n"
          + "  <updated>2008-09-12T00:44:20+00:00</updated>\n"
          + "  <link rel=\"self\" type=\"application/atom+xml\" href=\"http://www.billybobsbooks.com/catalog/top.atom\"/>\n"
          + "  <link rel=\"search\" title=\"Search Billy Bob's Books\" type=\"application/atom+xml\" href=\"http://www.billybobsbooks.com/catalog/search.php?search={searchTerms}\"/>\n"
          + "  <author>\n"
          + "    <name>Billy Bob</name>\n"
          + "    <uri>http://www.billybobsbooks.com</uri>\n"
          + "    <email>billybob@billybobsbooks.com</email>\n"
          + "  </author>\n"
          + "  <entry>\n"
          + "    <title>1984</title>\n"
          + "    <content type=\"xhtml\">\n"
          + "      <div xmlns:t=\"http://www.w3.org/1999/xhtml\"> Published: 1949 Subject: Novels Language: en</div>\n"
          + "    </content>\n"
          + "    <id>urn:billybobsbooks:1166</id>\n"
          + "    <author>\n"
          + "      <name>Orwell, George</name>\n"
          + "    </author>\n"
          + "    <updated>2008-09-12T00:44:20+00:00</updated>\n"
          + "    <link type=\"application/epub+zip\" href=\"http://www.billybobsbooks.com/book/1984.epub\"/>\n"
          + "    <link rel=\"x-stanza-cover-image-thumbnail\" type=\"image/png\" href=\"http://www.billybobsbooks.com/book/1984.png\"/>\n"
          + "    <link rel=\"x-stanza-cover-image\" type=\"image/png\" href=\"http://www.billybobsbooks.com/book/1984.png\"/>\n"
          + "  </entry>\n"
          + "  <entry>\n"
          + "    <title>The Art of War</title>\n"
          + "    <content type=\"xhtml\">\n"
          + "      <div xmlns:t=\"http://www.w3.org/1999/xhtml\">Published: -500 Subject: Non-Fiction Language: en</div>\n"
          + "    </content>\n"
          + "    <id>urn:billybobsbooks:168</id>\n"
          + "    <author>\n"
          + "      <name>Sun Tzu</name>\n"
          + "    </author>\n"
          + "    <updated>2008-09-12T00:44:20+00:00</updated>\n"
          + "    <link type=\"application/epub+zip\" href=\"http://www.billybobsbooks.com/book/artofwar.epub\"/>\n"
          + "    <link rel=\"x-stanza-cover-image-thumbnail\" type=\"image/png\" href=\"http://www.billybobsbooks.com/book/artofwar.png\"/>\n"
          + "    <link rel=\"x-stanza-cover-image\" type=\"image/png\" href=\"http://www.billybobsbooks.com/book/artofwar.png\"/>\n"
          + "  </entry>\n" + "</feed>\n";

  private static void testSummary() {
    String id;
    String title;
    String updated;
    String age_expires;
    String entry_id;
    String link_rel = "";
    String link_type = "";
    String link_href = "";
    String entry_updated = "";
    String entry_title = "";
    String content_type = "";
    String category_term = "";
    String category_label = "";
    String productID = "";
    String ISBN = "";
    String printISBN = "";
    String productType = "";
    String subTitle = "";
    String creator_profile_url = "";
    String creator_name = "";
    String media_url = "";
    String book_file_size = "";
    String publisher = "";
    String sale_rights = "";
    String book_avg_rating = "";
    String book_type = ""; // fiction, Literature, Mystery
    String sale_price_currency = "";
    String sale_end_date = "";
    String sale_listprice = "";
    String sale_printprice = "";
    String book_short_description = "";
    String book_excerpt_description = "";
    String book_awards = "";
    String print_pages = "";
    String publish_date = "";
    String hash = "";

    ArrayList<String> authors = new ArrayList<String>();

    Ssx ssx = new Ssx();
    Ssx.Xml xml;

    try {
      File file = new File("/tmp/summary.xml");
      FileInputStream fs = new FileInputStream(file);
      byte[] buffer = new byte[fs.available()];
      int length = fs.read(buffer);

      xml = ssx.parse(buffer, 0, length, "");

      updated = (String) xml.get(SyncXmlTags.UPDATED);
      id = (String) xml.get(SyncXmlTags.ID);
      title = (String) xml.get(SyncXmlTags.TITLE);
      age_expires = (String) xml.get(SyncXmlTags.ELEM_AGE_EXPIRES);

      Ssx.Xml item = xml.getNode("entry");
      Ssx.Xml prvitem = null;
      while (item != null) {
        if (prvitem == item) {
          break;
        } else {
          prvitem = item;
        }

        Ssx.message("HandleRecommendation: ", "item::" + item.toXml());

        // <id>
        entry_id = (String) item.get(SyncXmlTags.ID);

        // <link>
        Ssx.Xml link_node = item.getNode(SyncXmlTags.LINK);
        Ssx.message("HandleRecommendation: ", "link_node::" + link_node.toXml());
        while (link_node != null) {
          link_rel = link_node.get("@rel");
          Ssx.message("HandleRecommendation: ", "link_rel::" + link_rel);
          if (link_rel != null) {
            if (link_rel.equals(SyncXmlTags.WEB_DETAIL)) {
              link_type = link_node.get("@type");
              Ssx.message("HandleRecommendation: ", "link_type::" + link_type);
              link_href = link_node.get("@href");
              Ssx.message("HandleRecommendation: ", "link_href::" + link_href);
              break;
            }
          }
          // @@@
          link_node = link_node.nextSameName();
        }

        // <updated>
        entry_updated = (String) item.get(SyncXmlTags.UPDATED);

        // <content>
        // content_type = (String)item.get(SyncXmlTags.CONTENT_TYPE);
        content_type = (String) item.get("content@type");

        // <category>
        Ssx.Xml category = item.getNode(SyncXmlTags.NODE_CATEGORY);
        if (category != null) {
          category_term = category.get("@term");
          category_label = category.get("@label");
        }

        // <dc:identifier>
        Ssx.Xml identifier_node = item.getNode(SyncXmlTags.DC_IDENTIFIER);
        Ssx.message("HandleRecommendation: ", "identifier_node::" + identifier_node.toXml());
        while (identifier_node != null) {
          String catType = (String) identifier_node.get("@cat:type");
          if (catType != null) {
            if (catType.equals("someId")) {
              productID = identifier_node.get();
            } else if (catType.equals(SyncXmlTags.ISBN)) {
              ISBN = identifier_node.get();
            } else if (catType.equals(SyncXmlTags.printISBN)) {
              printISBN = identifier_node.get();
            }
          }
          identifier_node = identifier_node.nextSameName();
        }

        // <title>
        entry_title = (String) item.get(SyncXmlTags.TITLE);

        // <dc:title>
        subTitle = item.get(SyncXmlTags.DC_TITLE);

        // <dc:format>
        Ssx.Xml format_node = item.getNode(SyncXmlTags.DC_FORMAT);
        if (format_node != null) {
          productType = format_node.get();
        }

        // <dc:creator>
        Ssx.Xml creator_node = item.getNode(SyncXmlTags.DC_CREATOR);
        if (creator_node != null) {
          creator_name = creator_node.get();
          authors.add(creator_name);
          creator_profile_url = creator_node.get("@cat:url");
        }

        // <cat:media>
        Ssx.Xml media_node = item.getNode(SyncXmlTags.CAT_MEDIA);
        while (media_node != null) {
          String type = media_node.get("@" + SyncXmlTags.CAT_TYPE);
          if (Ssx.isEmpty(type)) {
            type = media_node.get("@" + SyncXmlTags.TYPE);
          }
          if (type.equals("coverArtBWHiRes")) {
            media_url = media_node.get("@" + SyncXmlTags.AUTHOR_URL);
            if (Ssx.isEmpty(media_url)) {
              media_url = media_node.get("@" + SyncXmlTags.COVER_URL);
            }
            break;
          } else if (type.equals("coverArtLowRes") || type.equals("coverArtMidRes")
              || type.equals("coverArtHiRes") || type.equals("coverArtFullRes")
              || type.equals("coverArtBWLowRes") || type.equals("coverArtBWMidRes")
              || type.equals("coverArtBWFulRes")) {
            media_url = media_node.get("@" + SyncXmlTags.AUTHOR_URL);
            if (Ssx.isEmpty(media_url)) {
              media_url = media_node.get("@" + SyncXmlTags.COVER_URL);
            }
          }
          media_node = media_node.nextSameName();
        }

        // <cat:fileSize>
        book_file_size = item.get(SyncXmlTags.CAT_FILESIZE);

        // <dc:publisher>
        publisher = item.get(SyncXmlTags.DC_PUBLISHER);

        // <cat:salesRights>
        sale_rights = item.get(SyncXmlTags.CAT_SALERIGHTS);

        // <cat:avgRating>
        book_avg_rating = item.get(SyncXmlTags.CAT_AVGRATING);

        // <dc:subject>
        book_type = item.get(SyncXmlTags.DC_SUBJECT);

        // <cat:price>
        Ssx.Xml price_node = item.getNode(SyncXmlTags.CAT_PRICE);
        while (price_node != null) {
          sale_price_currency = price_node.get("@cat:currency");
          String type = price_node.get("@cat:type");

          if (type != null) {
            if (type.equals(SyncXmlTags.CAT_LISTPRICE)) {
              sale_listprice = price_node.get();
            } else if (type.equals(SyncXmlTags.CAT_PRINTPRICE)) {
              sale_printprice = price_node.get();
            }
          }
          price_node = price_node.nextSameName();
        }

        // <cat:description>
        Ssx.Xml desc_node = item.getNode(SyncXmlTags.DC_DESCRIPTION);
        while (desc_node != null) {
          String type = desc_node.get("@cat:type");

          if (type != null) {
            if (type.equals(SyncXmlTags.SHORT_DESCRIPTION)) {
              book_short_description = desc_node.get();
            } else if (type.equals(SyncXmlTags.EXCERPT)) {
              book_excerpt_description = desc_node.get();
            }
          }
          desc_node = desc_node.nextSameName();
        }

        // <cat:award>?
        Ssx.Xml cat_awards = item.getNode(SyncXmlTags.CAT_AWARDS);
        if (cat_awards != null) {
          book_awards = cat_awards.get(SyncXmlTags.CAT_AWARD);
        }

        // <cat:printPage>?
        print_pages = item.get(SyncXmlTags.CAT_PRINT_PAGES);

        // <dc:date>
        Ssx.Xml date_node = item.getNode(SyncXmlTags.DC_DATE);
        if (date_node != null) {
          publish_date = date_node.get();
        }

        Ssx.message("HandleRecommendation:rec ", "entry_id:: " + entry_id + "\n" + "link_rel:: "
            + link_rel + "\n" + "link_type:: " + link_type + "\n" + "link_href:: " + link_href
            + "\n" + "entry_updated:: " + entry_updated + "\n" + "entry_title:: " + entry_title
            + "\n" + "content_type:: " + content_type + "\n" + "category_term:: " + category_term
            + "\n" + "category_label:: " + category_label + "\n" + "productID:: " + productID
            + "\n" + "ISBN:: " + ISBN + "\n" + "printISBN:: " + printISBN + "\n" + "productType:: "
            + productType + "\n" + "subTitle:: " + subTitle + "\n" + "creator_profile_url:: "
            + creator_profile_url + "\n" + "creator_name:: " + creator_name + "\n" + "media_url:: "
            + media_url + "\n");

        Ssx.message("HandleRecommendation:rec ",
        // "front_cover_path:: " + front_cover_path + "\n"+
            "book_file_size:: " + book_file_size + "\n" + "publisher:: " + publisher + "\n"
                + "sale_rights:: " + sale_rights + "\n" + "book_avg_rating:: " + book_avg_rating
                + "\n" + "book_type:: " + book_type + "\n" + "sale_price_currency:: "
                + sale_price_currency + "\n" + "sale_end_date:: " + sale_end_date + "\n"
                + "sale_listprice:: " + sale_listprice + "\n" + "book_short_description:: "
                + book_short_description + "\n" + "book_excerpt_description:: "
                + book_excerpt_description + "\n" + "book_awards:: " + book_awards + "\n"
                + "print_pages:: " + print_pages + "\n" + "publish_date:: " + publish_date + "\n");

        item = item.nextSameName();
      }
    } catch (Exception e) {
      Ssx.message("HandleRecommendation: ", "Caught an exception:" + e.toString());
      e.printStackTrace();
    }
  }

    // sdw: This example breaks some concise programming design rules, but it's not unusual to do it this way.
  public class SyncXmlTags {

    /** The Constant ID_START. */
    public static final String ID_START = "<id>";

    /** The Constant ID_END. */
    public static final String ID_END = "</id>";

    /** The Constant TITLE_START. */
    public static final String TITLE_START = "<title>";

    /** The Constant TITLE_END. */
    public static final String TITLE_END = "</title>";

    /** The Constant CONTENT_END. */
    public static final String CONTENT_END = "</cotent>";

    /** The Constant UPDATED_START. */
    public static final String UPDATED_START = "<updated>";

    /** The Constant UPDATED_END. */
    public static final String UPDATED_END = "</updated>";

    /** The Constant AUTHOR_NAME_START. */
    public static final String AUTHOR_NAME_START = "<author>" + "\n" + "<name>";

    /** The Constant AUTHOR_NAME_END. */
    public static final String AUTHOR_NAME_END = "</name>" + "\n" + "</author>";

    /** The Constant ENTRY_START. */
    public static final String ENTRY_START = "<entry>";

    /** The Constant ENTRY_END. */
    public static final String ENTRY_END = "</entry>";

    /** The Constant FEED_END. */
    public static final String FEED_END = "</feed>";

    /** The Constant SRC_START. */
    public static final String SRC_START = "<source>" + "\n" + "<id>";

    /** The Constant SRC_END. */
    public static final String SRC_END = "</id>" + "\n" + "</source>";

    /** The Constant CONTENT_TYPE. */
    public static final String CONTENT_TYPE = "<content type=\"text\"/>";

    /** The Constant USR_MARKUP. */
    public static final String USR_MARKUP = "<usr:markupType>" + "bookmark" + "</usr:markupType>";

    /** The Constant USR_MARKUP_START. */
    public static final String USR_MARKUP_START = "<usr:markupType>";

    /** The Constant USR_MARKUP_END. */
    public static final String USR_MARKUP_END = "</usr:markupType>";

    /** The Constant USR_POS_OWNER. */
    public static final String USR_POS_OWNER = "<usr:position owner=\"somebody\"";

    /** The Constant USR_POS_START. */
    public static final String USR_POS_START = " start=";

    /** The Constant USR_POS_END. */
    public static final String USR_POS_END = " end=";

    /** The Constant ELEMENT_END. */
    public static final String ELEMENT_END = "/>";

    /** The Constant USR_LAYOUTINFO. */
    public static final String USR_LAYOUTINFO = "<usr:layoutInfo>" + "layout1"
        + "</usr:layoutInfo>";

    /** The Constant USR_COMMENT_START. */
    public static final String USR_COMMENT_START = "<usr:comment type=\"text/plain\">";

    /** The Constant USR_COMMENT_END. */
    public static final String USR_COMMENT_END = "</usr:comment>";

    /** The Constant CAT_ETAG_START. */
    public static final String CAT_ETAG_START = "<cat:etag>";

    /** The Constant CAT_ETAG_END. */
    public static final String CAT_ETAG_END = "</cat:etag>";

    /** The Constant MARKUPTYPE_BOOKMARK. */
    public static final String MARKUPTYPE_BOOKMARK = "bookmark";

    /** The Constant MARKUPTYPE_HIGHLIGHT. */
    public static final String MARKUPTYPE_HIGHLIGHT = "highlight";

    /** The Constant CAT_CONTENT. */
    public static final String CAT_CONTENT = "cat:content";

    /** The Constant CAT_CONTENT_START. */
    public static final String CAT_CONTENT_START = "<cat:content";

    /** The Constant CAT_CONTENT_END. */
    public static final String CAT_CONTENT_END = "</cat:content>";

    /** The Constant READING_POS_START. */
    public static final String READING_POS_START = "<readingPosition ";

    /** The Constant READING_POS_END. */
    public static final String READING_POS_END = "</readingPositions>";

    /** The Constant READING_CREATED_DATE. */
    public static final String READING_CREATED_DATE = "clientCreateDt=\"";

    /** The Constant TYPE_SOMEBODY. */
    public static final String TYPE_SOMEBODY = "type=\"somebody\" ";

    /** The Constant READING_POS. */
    public static final String READING_POS = "usr:readingPosition";

    /** The Constant READING_POS_POSITION. */
    public static final String READING_POS_POSITION = "@position";

    /** The Constant READING_POS_PERCENT. */
    public static final String READING_POS_PERCENT = "@percent";

    /** The Constant READING_POS_DATE. */
    public static final String READING_POS_DATE = "@clientCreateDt";

    /** The Constant PERCENT. */
    public static final String PERCENT = "percent=\"";

    /** The Constant POSITION. */
    public static final String POSITION = "position=\"";

    /** The Constant NEW_LINE. */
    public static final String NEW_LINE = "\n";

    /** The Constant USR_CREATED_DATE. */
    public static final String USR_CREATED_DATE = "usr:svcCreateDate";

    /** The Constant USR_MODIFY_DATE. */
    public static final String USR_MODIFY_DATE = "usr:svcModifyDate";

    /** The Constant UPDATED. */
    public static final String UPDATED = "updated";

    /** The Constant TITLE. */
    public static final String TITLE = "title";

    /** The Constant CAT_ETAG. */
    public static final String CAT_ETAG = "cat:etag";

    /** The Constant USR_POS. */
    public static final String USR_POS = "usr:position";

    /** The Constant USR_POS_OWNER_RSP. */
    public static final String USR_POS_OWNER_RSP = "usr:position@owner";

    /** The Constant USR_POS_START_RSP. */
    public static final String USR_POS_START_RSP = "usr:position@start";

    /** The Constant USR_POS_PERCENT_RSP. */
    public static final String USR_POS_PERCENT_RSP = "usr:position@percent";

    /** The Constant USR_POS_END_RSP. */
    public static final String USR_POS_END_RSP = "usr:position@end";

    /** The Constant USR_COMMENT. */
    public static final String USR_COMMENT = "usr:comment";

    /** The Constant USR_MARKUPTYPE. */
    public static final String USR_MARKUPTYPE = "usr:markupType";

    /** The Constant BM_ID. */
    public static final String BM_ID = "id";

    /** The Constant BM_ITEMSPERPAGE. */
    public static final String BM_ITEMSPERPAGE = "opensearch:itemsPerPage";

    /** The Constant BM_TOTALRESULTS. */
    public static final String BM_TOTALRESULTS = "opensearch:totalResults";

    /** The Constant BM_STARTINDEX. */
    public static final String BM_STARTINDEX = "opensearch:startIndex";

    // public static final String MARKUP_TYPE = "cat:etag"; TODO this need to be
    // supported..

    // //////////////entitlements/////

    /** The Constant ID. */
    public static final String ID = "id";

    /** The Constant LINK. */
    public static final String LINK = "link";

    /** The Constant WEB_DETAIL. */
    public static final String WEB_DETAIL = "web-detail";

    /** The Constant CONTENT_DOWNLOAD_ACSM. */
    public static final String CONTENT_DOWNLOAD_ACSM = "content-download-acsm";

    /** The Constant DC_IDENTIFIER. */
    public static final String DC_IDENTIFIER = "dc:identifier";

    /** The Constant CAT_TYPE. */
    public static final String CAT_TYPE = "cat:type";

    /** The Constant PRODUCT_ID. */
    public static final String PRODUCT_ID = "productId";

    /** The Constant ISBN. */
    public static final String ISBN = "ISBN";

    /** The Constant printISBN. */
    public static final String printISBN = "printISBN";

    /** The Constant DC_FORMAT. */
    public static final String DC_FORMAT = "dc:format";

    /** The Constant CAT_SCHEME. */
    public static final String CAT_SCHEME = "cat:scheme";

    /** The Constant productType. */
    public static final String productType = "productType";

    /** The Constant DC_CREATOR. */
    public static final String DC_CREATOR = "dc:creator";

    /** The Constant AUTHOR. */
    public static final String AUTHOR = "cat:sortable";

    /** The Constant AUTHOR_URL. */
    public static final String AUTHOR_URL = "cat:url";

    /** The Constant CAT_MEDIA. */
    public static final String CAT_MEDIA = "cat:media";

    /** The Constant COVER_URL. */
    public static final String COVER_URL = "url";

    /** The Constant CAT_FILESIZE. */
    public static final String CAT_FILESIZE = "cat:fileSize";

    /** The Constant DC_TITLE. */
    public static final String DC_TITLE = "dc:title";

    /** The Constant DC_PUBLISHER. */
    public static final String DC_PUBLISHER = "dc:publisher";

    /** The Constant CAT_SALERIGHTS. */
    public static final String CAT_SALERIGHTS = "cat:saleRights";

    /** The Constant CAT_AVGRATING. */
    public static final String CAT_AVGRATING = "cat:avgRating";

    /** The Constant DC_SUBJECT. */
    public static final String DC_SUBJECT = "dc:subject";

    /** The Constant CAT_PRICE. */
    public static final String CAT_PRICE = "cat:price";

    /** The Constant CURRENCY. */
    public static final String CURRENCY = "currency";

    /** The Constant PRICE_ENDDATE. */
    public static final String PRICE_ENDDATE = "endDate";

    /** The Constant TYPE. */
    public static final String TYPE = "type";

    /** The Constant CAT_LISTPRICE. */
    public static final String CAT_LISTPRICE = "listPrice";

    /** The Constant CAT_PRINTPRICE. */
    public static final String CAT_PRINTPRICE = "printPrice";

    /** The Constant DC_DESCRIPTION. */
    public static final String DC_DESCRIPTION = "dc:description";

    /** The Constant SHORT_DESCRIPTION. */
    public static final String SHORT_DESCRIPTION = "shortDesc";

    /** The Constant EXCERPT. */
    public static final String EXCERPT = "excerpt";

    /** The Constant CDATA. */
    public static final String CDATA = "![CDATA"; // need to recheck

    /** The Constant CAT_AWARDS. */
    public static final String CAT_AWARDS = "cat:awards";

    /** The Constant CAT_AWARD. */
    public static final String CAT_AWARD = "cat:award";

    /** The Constant CAT_PRINT_PAGES. */
    public static final String CAT_PRINT_PAGES = "cat:printPages";

    /** The Constant DC_DATE. */
    public static final String DC_DATE = "dc:date";

    /** The Constant PUBLISH_DATE. */
    public static final String PUBLISH_DATE = "publishDate";

    /** The Constant USR_PURCHASE_DATE. */
    public static final String USR_PURCHASE_DATE = "usr:purchaseDate";

    // //////////////////////////////////////

    /** The Constant NODE_URI_CONVERSION. */
    public static final String NODE_URI_CONVERSION = "sys:urlConversion";

    /** The Constant ELEM_CONVERTED_URI. */
    public static final String ELEM_CONVERTED_URI = "sys:convertedUri";

    /** The Constant NODE_THREE_G. */
    public static final String NODE_THREE_G = "threeG";

    /** The Constant NODE_URI. */
    public static final String NODE_URI = "uri";

    /** The Constant NODE_WHITE_LIST. */
    public static final String NODE_WHITE_LIST = "whitelist";

    /** The Constant NODE_BLACK_LIST. */
    public static final String NODE_BLACK_LIST = "blacklist";

    /** The Constant NODE_HOOKS. */
    public static final String NODE_HOOKS = "hooks";

    /** The Constant NODE_EMBEDDEDURIS. */
    public static final String NODE_EMBEDDEDURIS = "embeddedUris";

    /** The Constant NODE_PROXIES. */
    public static final String NODE_PROXIES = "proxies";

    /** The Constant NODE_PROXY. */
    public static final String NODE_PROXY = "proxy";

    /** The Constant ELEM_HTTP_HREF. */
    public static final String ELEM_HTTP_HREF = "http@href";

    /** The Constant ELEM_NTP_SERVER. */
    public static final String ELEM_NTP_SERVER = "ntp@server";

    /** The Constant ELEM_AGE_EXPIRES. */
    public static final String ELEM_AGE_EXPIRES = "age:expires";

    /** The Constant NODE_CATEGORY. */
    public static final String NODE_CATEGORY = "category";
    //

    /** The Constant SYNC_PULL. */
    public static final int SYNC_PULL = 0;

    /** The Constant SYNC_CREATE. */
    public static final int SYNC_CREATE = 1;

    /** The Constant SYNC_UPDATE. */
    public static final int SYNC_UPDATE = 2;

    /** The Constant SYNC_DELETE. */
    public static final int SYNC_DELETE = 3;
  }

}
