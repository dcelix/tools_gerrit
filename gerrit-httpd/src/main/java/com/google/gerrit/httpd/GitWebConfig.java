// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.httpd;

import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePath;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class GitWebConfig {
  private static final Logger log = LoggerFactory.getLogger(GitWebConfig.class);

  private final String url;
  private final File gitweb_cgi;
  private final File gitweb_css;
  private final File git_logo_png;

  @Inject
  GitWebConfig(@SitePath final File sitePath,
      @GerritServerConfig final Config cfg) {
    final String cfgUrl = cfg.getString("gitweb", null, "url");
    final String cfgCgi = cfg.getString("gitweb", null, "cgi");

    if ((cfgUrl != null && cfgUrl.isEmpty())
        || (cfgCgi != null && cfgCgi.isEmpty())) {
      // Either setting was explicitly set to the empty string disabling
      // gitweb for this server. Disable the configuration.
      //
      url = null;
      gitweb_cgi = null;
      gitweb_css = null;
      git_logo_png = null;
      return;
    }

    if (cfgUrl != null) {
      // Use an externally managed gitweb instance, and not an internal one.
      //
      url = cfgUrl;
      gitweb_cgi = null;
      gitweb_css = null;
      git_logo_png = null;
      return;
    }

    File cgi, css, logo;
    if (cfgCgi != null) {
      // Use the CGI script configured by the administrator, failing if it
      // cannot be used as specified.
      //
      cgi = new File(cfgCgi);
      if (!cgi.isAbsolute()) {
        cgi = new File(sitePath, cfgCgi).getAbsoluteFile();
      }
      if (!cgi.isFile()) {
        throw new IllegalStateException("Cannot find gitweb.cgi: " + cgi);
      }
      if (!cgi.canExecute()) {
        throw new IllegalStateException("Cannot execute gitweb.cgi: " + cgi);
      }

      // Assume the administrator pointed us the distribution, which
      // also has the corresponding CSS and logo file.
      //
      css = new File(cgi.getParentFile(), "gitweb.css");
      logo = new File(cgi.getParentFile(), "git-logo.png");

    } else {
      // Try to use the OS packaged CGI.
      //
      final File s = new File("/usr/lib/cgi-bin/gitweb.cgi");
      if (s.isFile()) {
        log.debug("Assuming gitweb at " + s);
        cgi = s;
        css = new File("/var/www/gitweb.css");
        logo = new File("/var/www/git-logo.png");

      } else {
        log.warn("gitweb not installed (no " + s + " found)");
        cgi = null;
        css = null;
        logo = null;
      }
    }

    url = cgi != null ? "gitweb" : null;
    gitweb_cgi = cgi;
    gitweb_css = css;
    git_logo_png = logo;
  }

  /**
   * @return URL of the entry point into gitweb. This URL may be relative to our
   *         context if gitweb is hosted by ourselves; or absolute if its hosted
   *         elsewhere; or null if gitweb has not been configured.
   */
  public String getUrl() {
    return url;
  }

  /** @return local path to the CGI executable; null if we shouldn't execute. */
  public File getGitwebCGI() {
    return gitweb_cgi;
  }

  /** @return local path of the {@code gitweb.css} matching the CGI. */
  public File getGitwebCSS() {
    return gitweb_css;
  }

  /** @return local path of the {@code git-logo.png} for the CGI. */
  public File getGitLogoPNG() {
    return git_logo_png;
  }
}