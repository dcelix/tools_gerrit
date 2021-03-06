// Copyright (C) 2008 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.httpd.raw;

import com.google.gerrit.common.data.GerritConfig;
import com.google.gerrit.common.data.HostPageData;
import com.google.gerrit.httpd.HtmlDomUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.SitePaths;
import com.google.gwt.user.server.rpc.RPCServletUtils;
import com.google.gwtexpui.linker.server.Permutation;
import com.google.gwtexpui.linker.server.PermutationSelector;
import com.google.gwtjsonrpc.server.JsonServlet;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Sends the Gerrit host page to clients. */
@SuppressWarnings("serial")
@Singleton
public class HostPageServlet extends HttpServlet {
  private static final Logger log =
      LoggerFactory.getLogger(HostPageServlet.class);
  private static final boolean IS_DEV = Boolean.getBoolean("Gerrit.GwtDevMode");
  private static final String HPD_ID = "gerrit_hostpagedata";

  private final Provider<CurrentUser> currentUser;
  private final GerritConfig config;
  private final HostPageData.Theme signedOutTheme;
  private final HostPageData.Theme signedInTheme;
  private final SitePaths site;
  private final Document template;
  private final String noCacheName;
  private final PermutationSelector selector;
  private volatile Page page;

  @Inject
  HostPageServlet(final Provider<CurrentUser> cu, final SitePaths sp,
      final ThemeFactory themeFactory, final GerritConfig gc,
      final ServletContext servletContext) throws IOException, ServletException {
    currentUser = cu;
    config = gc;
    signedOutTheme = themeFactory.getSignedOutTheme();
    signedInTheme = themeFactory.getSignedInTheme();
    site = sp;

    final String pageName = "HostPage.html";
    template = HtmlDomUtil.parseFile(getClass(), pageName);
    if (template == null) {
      throw new FileNotFoundException("No " + pageName + " in webapp");
    }

    if (HtmlDomUtil.find(template, "gerrit_module") == null) {
      throw new ServletException("No gerrit_module in " + pageName);
    }
    if (HtmlDomUtil.find(template, HPD_ID) == null) {
      throw new ServletException("No " + HPD_ID + " in " + pageName);
    }

    final String src = "gerrit/gerrit.nocache.js";
    selector = new PermutationSelector("gerrit");
    if (IS_DEV) {
      noCacheName = src;
    } else {
      final Element devmode = HtmlDomUtil.find(template, "gerrit_gwtdevmode");
      if (devmode != null) {
        devmode.getParentNode().removeChild(devmode);
      }

      InputStream in = servletContext.getResourceAsStream("/" + src);
      if (in == null) {
        throw new IOException("No " + src + " in webapp root");
      }

      final MessageDigest md = Constants.newMessageDigest();
      try {
        try {
          final byte[] buf = new byte[1024];
          int n;
          while ((n = in.read(buf)) > 0) {
            md.update(buf, 0, n);
          }
        } finally {
          in.close();
        }
      } catch (IOException e) {
        throw new IOException("Failed reading " + src, e);
      }
      final String id = ObjectId.fromRaw(md.digest()).name();
      noCacheName = src + "?content=" + id;
      selector.init(servletContext);
    }

    page = new Page();
  }

  private void json(final Object data, final StringWriter w) {
    JsonServlet.defaultGsonBuilder().create().toJson(data, w);
  }

  private Page get() {
    Page p = page;
    if (p.isStale()) {
      final Page newPage;
      try {
        newPage = new Page();
      } catch (IOException e) {
        log.error("Cannot refresh site header/footer", e);
        return p;
      }
      p = newPage;
      page = p;
    }
    return p;
  }

  @Override
  protected void doGet(final HttpServletRequest req,
      final HttpServletResponse rsp) throws IOException {
    final Page.Content page = get().get(select(req));
    final byte[] raw;

    final CurrentUser user = currentUser.get();
    if (user instanceof IdentifiedUser) {
      final StringWriter w = new StringWriter();
      w.write(HPD_ID + ".account=");
      json(((IdentifiedUser) user).getAccount(), w);
      w.write(";");

      w.write(HPD_ID + ".accountDiffPref=");
      json(((IdentifiedUser) user).getAccountDiffPreference(), w);
      w.write(";");

      w.write(HPD_ID + ".theme=");
      json(signedInTheme, w);
      w.write(";");

      final byte[] userData = w.toString().getBytes("UTF-8");
      raw = concat(page.part1, userData, page.part2);
    } else {
      raw = page.full;
    }

    final byte[] tosend;
    if (RPCServletUtils.acceptsGzipEncoding(req)) {
      rsp.setHeader("Content-Encoding", "gzip");
      tosend = raw == page.full ? page.full_gz : HtmlDomUtil.compress(raw);
    } else {
      tosend = raw;
    }

    rsp.setHeader("Expires", "Fri, 01 Jan 1980 00:00:00 GMT");
    rsp.setHeader("Pragma", "no-cache");
    rsp.setHeader("Cache-Control", "no-cache, must-revalidate");
    rsp.setContentType("text/html");
    rsp.setCharacterEncoding(HtmlDomUtil.ENC);
    rsp.setContentLength(tosend.length);
    final OutputStream out = rsp.getOutputStream();
    try {
      out.write(tosend);
    } finally {
      out.close();
    }
  }

  private Permutation select(final HttpServletRequest req) {
    if ("0".equals(req.getParameter("s"))) {
      // If s=0 is used in the URL, the user has explicitly asked us
      // to not perform selection on the server side, perhaps due to
      // it incorrectly guessing their user agent.
      //
      return null;
    }
    return selector.select(req);
  }

  private static byte[] concat(byte[] p1, byte[] p2, byte[] p3) {
    final byte[] r = new byte[p1.length + p2.length + p3.length];
    int p = 0;
    p = append(p1, r, p);
    p = append(p2, r, p);
    p = append(p3, r, p);
    return r;
  }

  private static int append(byte[] src, final byte[] dst, int p) {
    System.arraycopy(src, 0, dst, p, src.length);
    return p + src.length;
  }

  private static class FileInfo {
    private final File path;
    private final long time;

    FileInfo(final File p) {
      path = p;
      time = path.lastModified();
    }

    boolean isStale() {
      return time != path.lastModified();
    }
  }

  private class Page {
    private final FileInfo css;
    private final FileInfo header;
    private final FileInfo footer;
    private final Map<Permutation, Content> permutations;

    Page() throws IOException {
      Document hostDoc = HtmlDomUtil.clone(template);

      css = injectCssFile(hostDoc, "gerrit_sitecss", site.site_css);
      header = injectXmlFile(hostDoc, "gerrit_header", site.site_header);
      footer = injectXmlFile(hostDoc, "gerrit_footer", site.site_footer);

      final HostPageData pageData = new HostPageData();
      pageData.config = config;

      final StringWriter w = new StringWriter();
      w.write("var " + HPD_ID + "=");
      json(pageData, w);
      w.write(";");

      final Element data = HtmlDomUtil.find(hostDoc, HPD_ID);
      asScript(data);
      data.appendChild(hostDoc.createTextNode(w.toString()));
      data.appendChild(hostDoc.createComment(HPD_ID));

      permutations = new HashMap<Permutation, Content>();
      for (Permutation p : selector.getPermutations()) {
        final Document d = HtmlDomUtil.clone(hostDoc);
        Element nocache = HtmlDomUtil.find(d, "gerrit_module");
        nocache.getParentNode().removeChild(nocache);
        p.inject(d);
        permutations.put(p, new Content(d));
      }

      Element nocache = HtmlDomUtil.find(hostDoc, "gerrit_module");
      asScript(nocache);
      nocache.setAttribute("src", noCacheName);
      permutations.put(null, new Content(hostDoc));
    }

    Content get(Permutation p) {
      Content c = permutations.get(p);
      if (c == null) {
        c = permutations.get(null);
      }
      return c;
    }

    boolean isStale() {
      return css.isStale() || header.isStale() || footer.isStale();
    }

    private void asScript(final Element scriptNode) {
      scriptNode.removeAttribute("id");
      scriptNode.setAttribute("type", "text/javascript");
      scriptNode.setAttribute("language", "javascript");
    }

    class Content {
      final byte[] part1;
      final byte[] part2;
      final byte[] full;
      final byte[] full_gz;

      Content(Document hostDoc) throws IOException {
        final String raw = HtmlDomUtil.toString(hostDoc);
        final int p = raw.indexOf("<!--" + HPD_ID);
        if (p < 0) {
          throw new IOException("No tag in transformed host page HTML");
        }
        part1 = raw.substring(0, p).getBytes("UTF-8");
        part2 = raw.substring(raw.indexOf('>', p) + 1).getBytes("UTF-8");

        final StringWriter w = new StringWriter();
        w.write(HPD_ID + ".theme=");
        json(signedOutTheme, w);
        w.write(";");

        final byte[] themeData = w.toString().getBytes("UTF-8");
        full = concat(part1, themeData, part2);
        full_gz = HtmlDomUtil.compress(full);
      }
    }

    private FileInfo injectCssFile(final Document hostDoc, final String id,
        final File src) throws IOException {
      final FileInfo info = new FileInfo(src);
      final Element banner = HtmlDomUtil.find(hostDoc, id);
      if (banner == null) {
        return info;
      }

      while (banner.getFirstChild() != null) {
        banner.removeChild(banner.getFirstChild());
      }

      String css = HtmlDomUtil.readFile(src.getParentFile(), src.getName());
      if (css == null) {
        banner.getParentNode().removeChild(banner);
        return info;
      }

      banner.removeAttribute("id");
      banner.appendChild(hostDoc.createCDATASection("\n" + css + "\n"));
      return info;
    }

    private FileInfo injectXmlFile(final Document hostDoc, final String id,
        final File src) throws IOException {
      final FileInfo info = new FileInfo(src);
      final Element banner = HtmlDomUtil.find(hostDoc, id);
      if (banner == null) {
        return info;
      }

      while (banner.getFirstChild() != null) {
        banner.removeChild(banner.getFirstChild());
      }

      Document html = HtmlDomUtil.parseFile(src);
      if (html == null) {
        banner.getParentNode().removeChild(banner);
        return info;
      }

      final Element content = html.getDocumentElement();
      banner.appendChild(hostDoc.importNode(content, true));
      return info;
    }
  }
}
