# super-simple-xml
Automatically exported from code.google.com/p/super-simple-xml



Simple to use DOM-like XML parsing API with mini-XPath support.

This is a very small, fast XML parsing library with a mini-XPath-like API. It is a single Java file, includes a built-in SAX parser (or the default SAX parser can be used), and has a very concise Java API with a few extra functions. Typical parsing tasks can be done in a few lines of code.

This library is particularly useful for Android projects. It is being actively developed as needed. Incremental DOM is next, then parse tree modification.

Usage:
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
 
