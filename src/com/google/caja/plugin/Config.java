// Copyright (C) 2007 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.caja.plugin;

import com.google.caja.config.ConfigUtil;
import com.google.caja.config.WhiteList;
import com.google.caja.lang.css.CssSchema;
import com.google.caja.lang.html.HtmlSchema;
import com.google.caja.lexer.InputSource;
import com.google.caja.lexer.ParseException;
import com.google.caja.reporting.BuildInfo;
import com.google.caja.reporting.MessageQueue;
import com.google.caja.reporting.MessageType;
import com.google.caja.util.Join;
import com.google.caja.util.Lists;
import com.google.caja.util.Pair;
import com.google.caja.util.Sets;
import com.google.caja.util.Strings;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

/**
 * Flag processing for main methods.
 *
 * @author mikesamuel@gmail.com
 */
public final class Config {
  private final Options options = new Options();

  private final Option INPUT = defineOption(
      "i", "input", "Input URI containing HTML (with optional "
      + "script and style blocks)", true);

  // This output HTML is not standalone, and requires a container to load the
  // supporting JS and set up a virtual document root.
  private final Option OUTPUT_HTML = defineOption(
      "h", "output_html",
      "Output file path for translated HTML (defaults to input with "
      + "\".out.html\")",
      true);

  private final Option OUTPUT_JS = defineOption(
      "j", "output_js",
      "Output file path for translated JS (defaults to input with \".out.js\")",
      true);

  private final Option OUTPUT_BASE = defineOption(
      "o", "out",
      "Path to which the appropriate extension is added to form output files.",
      true);

  private final Option CSS_PROPERTY_WHITELIST = defineOption(
      "cps", "css_prop_schema",
      "A file: or resource: URI of the CSS Property Whitelist to use.",
      true);

  private final Option HTML_ATTRIBUTE_WHITELIST = defineOption(
      "has", "html_attrib_schema",
      "A file: or resource: URI of the HTML attribute Whitelist to use.",
      true);

  private final Option HTML_ELEMENT_WHITELIST = defineOption(
      "hps", "html_property_schema",
      "A file: or resource: URI of the HTML element Whitelist to use.",
      true);

  private final Option BASE_URI = defineOption(
      "base_uri",
      "The URI relative to which URIs in the inputs are resolved.",
      true);

  private final Option FETCHER_BASE = defineOption(
      "fetcher_base",
      "An ancestor of all files which the URI fetcher is allowed to resolve.",
      true);

  private final Option F_URI = defineOption(
      "fu", "f_uri", "A URI which the URI fetcher is allowed to fetch.", true);

  private final Option F_URI_ALL = defineBooleanOption(
      "fua", "f_uri_all", "Allow all URIs to be fetched.");

  private final Option L_URI = defineOption(
      "lu", "l_uri", "A URI which the URI policy is allowed to link to.", true);

  private final Option L_URI_ALL = defineBooleanOption(
      "lua", "l_uri_all", "Allow all URIs to be linked to.");

  private final Option L_URI_RUNTIME = defineBooleanOption(
      "lur", "l_uri_runtime", "Use a runtime URI policy for linked-to URIs.");

  private final Option SERVICE_PORT = defineOption(
      "port",
      "The port on which cajoling service is run.",
      true);

  private final Option ID_CLASS = defineOption(
      "c",
      "id_class",
      "The module ID if it is statically known",
      true);

  private final Option DEBUG_MODE = defineBooleanOption(
      "g", "debug", "Set to add debugging info to cajoled output.");

  private final Option RENDERER = defineOption(
      "r",
      "renderer",
      "The output renderer "
      + Strings.lower(Arrays.toString(SourceRenderMode.values())) + "",
      true);

  private final Option PIPELINE_PRECONDITIONS = defineOption(
      "ppc", "preconds",
      "Space separated properties as described in help text.", true);

  private final Option PIPELINE_GOALS = defineOption(
      "pg", "goals",
      "Space separated properties as described in help text.", true);

  private final Option NO_PRECAJOLED = defineBooleanOption(
      "np", "no_precajoled", "Don't use the precajoled cache");

  private final Option BENCHMARK = defineOption(
      "bm", "benchmark",
      "Run n times and report stats.", true);

  public enum SourceRenderMode {
    MINIFY,
    PRETTY,
    ;
  }

  private final Class<?> mainClass;
  private final PrintWriter stderr;
  private final String usageText;
  private List<URI> inputUris;
  private File outputBase;
  private File outputJsFile;
  private File outputHtmlFile;
  private URI cssPropertyWhitelistUri;
  private URI htmlAttributeWhitelistUri;
  private URI htmlElementWhitelistUri;
  private URI baseUri;
  private File fetcherBase;
  private SourceRenderMode renderer;
  private Set<String> fUris;
  private boolean fUriAll = false;
  private Set<String> lUris;
  private boolean lUriAll = false;
  private boolean lUriRuntime = false;
  private int servicePort;
  private String idClass;
  private Planner.PlanState negGoals = Planner.EMPTY;
  private Planner.PlanState posGoals = Planner.EMPTY;
  private Planner.PlanState negPreconds = Planner.EMPTY;
  private Planner.PlanState posPreconds = Planner.EMPTY;
  private boolean noPrecajoled = false;
  private int benchmark = 0;

  public Config(Class<?> mainClass, PrintStream stderr, String usageText) {
    this(mainClass, new PrintWriter(stderr), usageText);
  }

  public Config(Class<?> mainClass, PrintWriter stderr, String usageText) {
    this.mainClass = mainClass;
    this.stderr = stderr;
    this.usageText = usageText;
  }

  public Collection<URI> getInputUris() { return inputUris; }
  public File getOutputJsFile() { return outputJsFile; }
  public File getOutputHtmlFile() { return outputHtmlFile; }
  public File getOutputBase() { return outputBase; }
  public int getServicePort() { return servicePort; }
  public URI getCssPropertyWhitelistUri() {
    return cssPropertyWhitelistUri;
  }
  public URI getHtmlAttributeWhitelistUri() {
    return htmlAttributeWhitelistUri;
  }
  public URI getHtmlElementWhitelistUri() {
    return htmlElementWhitelistUri;
  }
  public URI getBaseUri() { return baseUri; }
  public File getFetcherBase() { return fetcherBase; }

  public CssSchema getCssSchema(MessageQueue mq) {
    return new CssSchema(
        whitelist(cssPropertyWhitelistUri, mq),
        whitelist(URI.create(
            "resource:///com/google/caja/lang/css/css-extensions-fns.json"),
            mq));
  }

  public HtmlSchema getHtmlSchema(MessageQueue mq) {
    return new HtmlSchema(
        whitelist(htmlElementWhitelistUri, mq),
        whitelist(htmlAttributeWhitelistUri, mq));
  }

  public String getIdClass() { return idClass; }

  public Set<String> getFetchableUris() { return fUris; }

  public boolean hasFetchableUriAll() { return fUriAll; }

  public Set<String> getLinkableUris() { return lUris; }

  public boolean hasLinkableUriAll() { return lUriAll; }

  public boolean hasLinkableUriRuntime() { return lUriRuntime; }

  public SourceRenderMode renderer() { return renderer; }

  public Planner.PlanState goals(Planner.PlanState ps) {
    return ps.without(negGoals).with(posGoals);
  }

  public Planner.PlanState preconditions(Planner.PlanState ps) {
    return ps.without(negPreconds).with(posPreconds);
  }

  public boolean hasNoPrecajoled() { return noPrecajoled; }

  public int getBenchmark() { return benchmark; }

  public boolean processArguments(String[] argv) {
    try {
      CommandLine cl;
      try {
        cl = new BasicParser().parse(options, argv, false);
      } catch (org.apache.commons.cli.ParseException e) {
        usage(e.getMessage(), stderr);
        return false;
      }

      inputUris = Lists.newArrayList();
      for (String input : getOptionValues(cl, INPUT)) {
        URI inputUri;
        try {
          if (input.indexOf(':') >= 0) {
            inputUri = new URI(input);
          } else {
            File inputFile = new File(input);

            if (!inputFile.exists()) {
              usage("File \"" + input + "\" does not exist", stderr);
              return false;
            }
            if (!inputFile.canRead() || inputFile.isDirectory()) {
              usage("File \"" + input + "\" is not a regular file", stderr);
              return false;
            }

            inputUri = inputFile.getAbsoluteFile().toURI();
          }
        } catch (URISyntaxException ex) {
          usage("Input \"" + input + "\" is not a valid URI", stderr);
          return false;
        }

        inputUris.add(inputUri);
      }
      if (inputUris.isEmpty()) {
        usage("Option \"--" + INPUT.getLongOpt() + "\" missing", stderr);
        return false;
      }

      if (cl.getOptionValue(OUTPUT_BASE.getOpt()) != null) {
        outputBase = new File(cl.getOptionValue(OUTPUT_BASE.getOpt()));

        outputJsFile = substituteExtension(outputBase, ".out.js");
        outputHtmlFile = substituteExtension(outputBase, ".out.html");

        if (cl.getOptionValue(OUTPUT_JS.getOpt()) != null) {
          usage("Can't specify both --out and --output_js", stderr);
          return false;
        }
        if (cl.getOptionValue(OUTPUT_HTML.getOpt()) != null) {
          usage("Can't specify both --out and --output_html", stderr);
          return false;
        }
      } else {
        outputJsFile = cl.getOptionValue(OUTPUT_JS.getOpt()) == null
            ? null : new File(cl.getOptionValue(OUTPUT_JS.getOpt()));

        outputHtmlFile = cl.getOptionValue(OUTPUT_HTML.getOpt()) == null
            ? null : new File(cl.getOptionValue(OUTPUT_HTML.getOpt()));

        if (outputJsFile == null && outputHtmlFile == null) {
          usage("Please specify js output via " + OUTPUT_JS.getLongOpt()
                + " &| html output via " + OUTPUT_HTML.getLongOpt(),
                stderr);
          return false;
        }
      }

      try {
        cssPropertyWhitelistUri = new URI(cl.getOptionValue(
            CSS_PROPERTY_WHITELIST.getOpt(),
            "resource:///com/google/caja/lang/css/css-extensions.json"));
        htmlAttributeWhitelistUri = new URI(cl.getOptionValue(
            HTML_ATTRIBUTE_WHITELIST.getOpt(),
            "resource:///com/google/caja/lang/html"
            + "/html4-attributes-extensions.json"));
        htmlElementWhitelistUri = new URI(cl.getOptionValue(
            HTML_ELEMENT_WHITELIST.getOpt(),
            "resource:///com/google/caja/lang/html"
            + "/html4-elements-extensions.json"));

        if (cl.getOptionValue(BASE_URI.getOpt()) != null) {
          baseUri = new URI(cl.getOptionValue(BASE_URI.getOpt()));
        } else {
          baseUri = inputUris.get(0);
        }
      } catch (URISyntaxException ex) {
        stderr.println("Invalid whitelist URI: " + ex.getInput() + "\n    "
                       + ex.getReason());
        return false;
      }

      if (cl.getOptionValue(FETCHER_BASE.getOpt()) != null) {
        fetcherBase = new File(cl.getOptionValue(FETCHER_BASE.getOpt()));
      } else if (Strings.eqIgnoreCase(baseUri.getScheme(), "file")) {
        fetcherBase = new File(baseUri).getParentFile();
      }

      idClass = cl.getOptionValue(ID_CLASS.getOpt(), null);

      String servicePortString;
      try {
        servicePortString = cl.getOptionValue(SERVICE_PORT.getOpt(), "8887");
        servicePort = Integer.parseInt(servicePortString);
      } catch ( NumberFormatException e ) {
        stderr.println(
            "Invalid service port: " + SERVICE_PORT.getOpt() + "\n    "
            + e.getMessage());
        return false;
      }

      fUris = Sets.immutableSet(getOptionValues(cl, F_URI));
      fUriAll = cl.hasOption(F_URI_ALL.getOpt());

      lUris = Sets.immutableSet(getOptionValues(cl, L_URI));
      lUriAll = cl.hasOption(L_URI_ALL.getOpt());
      lUriRuntime = cl.hasOption(L_URI_RUNTIME.getOpt());

      String renderString = cl.getOptionValue(RENDERER.getOpt());
      if (renderString != null) {
        renderer = SourceRenderMode.valueOf(Strings.upper(renderString));
        if (renderer == null) {
          stderr.println("Invalid renderer: " + renderString);
          return false;
        }
      } else {
        renderer = SourceRenderMode.PRETTY;
      }

      boolean debugMode = cl.hasOption(DEBUG_MODE.getOpt());
      boolean onlyJsEmitted = cl.hasOption(OUTPUT_JS.getOpt())
          && !cl.hasOption(OUTPUT_HTML.getOpt());
      if (debugMode) {
        negGoals = negGoals.with(PipelineMaker.ONE_CAJOLED_MODULE);
        posGoals = posGoals.with(PipelineMaker.ONE_CAJOLED_MODULE_DEBUG);
      }
      if (onlyJsEmitted) {
        negGoals = negGoals.with(PipelineMaker.HTML_SAFE_STATIC);
      }

      String preconds = cl.getOptionValue(PIPELINE_PRECONDITIONS.getOpt());
      if (preconds != null) {
        Pair<Planner.PlanState, Planner.PlanState> deltas
            = planDeltas(preconds);
        negPreconds = negPreconds.with(deltas.a);
        posPreconds = posPreconds.with(deltas.b);
      }

      String goals = cl.getOptionValue(PIPELINE_GOALS.getOpt());
      if (goals != null) {
        Pair<Planner.PlanState, Planner.PlanState> deltas = planDeltas(goals);
        negGoals = negGoals.with(deltas.a);
        posGoals = posGoals.with(deltas.b);
      }

      noPrecajoled = cl.hasOption(NO_PRECAJOLED.getOpt());

      benchmark = Integer.valueOf(cl.getOptionValue(BENCHMARK.getOpt(), "0"));

      return true;
    } finally {
      stderr.flush();
    }
  }

  private Pair<Planner.PlanState, Planner.PlanState> planDeltas(String props) {
    props = props.trim();
    List<String> neg = Lists.newArrayList();
    List<String> pos = Lists.newArrayList();
    for (String part : props.split("\\s+")) {
      if (!"".equals(part)) {
        if (part.startsWith("-")) {
          neg.add(part.substring(1));
        } else {
          pos.add(part.substring(part.startsWith("+") ? 1 : 0));
        }
      }
    }
    try {
      return Pair.pair(
          PipelineMaker.planState(Join.join("", neg)),
          PipelineMaker.planState(Join.join("", pos)));
    } catch (IllegalArgumentException ex) {
      stderr.println("Bad prop in " + props + " : " + ex.getMessage());
      return Pair.pair(Planner.EMPTY, Planner.EMPTY);
    }
  }

  public void usage(String msg, PrintWriter out) {
    out.println(BuildInfo.getInstance().getBuildInfo());
    out.println();
    if (msg != null && !"".equals(msg)) {
      out.println(msg);
      out.println();
    }
    new HelpFormatter().printHelp(
        out, HelpFormatter.DEFAULT_WIDTH,
        (mainClass.getSimpleName()
         + " --input <in.html> [--output_js <out.js> | --out <out>]"),
        "\n", options,
        HelpFormatter.DEFAULT_LEFT_PAD, HelpFormatter.DEFAULT_DESC_PAD,
        "\n" + usageText, false);
    out.println();
    int maxPlanStateWidth = 0;
    for (Pair<String, String> doc
        : PipelineMaker.getPreconditionDocumentation()) {
      maxPlanStateWidth = Math.max(maxPlanStateWidth, doc.a.length());
    }
    for (Pair<String, String> doc : PipelineMaker.getGoalDocumentation()) {
      maxPlanStateWidth = Math.max(maxPlanStateWidth, doc.a.length());
    }
    String fmtStr = "%" + maxPlanStateWidth + "s | %s";
    out.println(
        "Preconditions (default to " + PipelineMaker.DEFAULT_PRECONDS + ")");
    for (Pair<String, String> doc
            : PipelineMaker.getPreconditionDocumentation()) {
      out.println(String.format(fmtStr, doc.a, doc.b));
    }
    out.println();
    out.println("Goals (default to " + PipelineMaker.DEFAULT_GOALS + ")");
    for (Pair<String, String> doc : PipelineMaker.getGoalDocumentation()) {
      out.println(String.format(fmtStr, doc.a, doc.b));
    }
  }

  private static File substituteExtension(File file, String extension) {
    String fileName = file.getName();
    int lastDot = fileName.lastIndexOf('.');
    if (lastDot < 0) { lastDot = fileName.length(); }
    return new File(file.getParentFile(),
                    fileName.substring(0, lastDot) + extension);
  }

  private static WhiteList whitelist(URI uri, MessageQueue mq) {
    InputSource src = new InputSource(uri);
    try {
      return ConfigUtil.loadWhiteListFromJson(
          uri, ConfigUtil.RESOURCE_RESOLVER, mq);
    } catch (IOException ex) {
      mq.addMessage(MessageType.IO_ERROR, src);
    } catch (ParseException ex) {
      ex.toMessageQueue(mq);
    }
    // Return a Null instance if unable to load.
    return new WhiteList() {
        public Set<String> allowedItems() {
          return Collections.<String>emptySet();
        }
        public Map<String, TypeDefinition> typeDefinitions() {
          return Collections.<String, TypeDefinition>emptyMap();
        }
      };
  }

  private Option defineOption(
      String shortFlag, String longFlag, String help, boolean optional) {
    Option opt = new Option(shortFlag, longFlag, /* hasArg: */ true, help);
    opt.setOptionalArg(optional);
    options.addOption(opt);
    return opt;
  }

  private Option defineBooleanOption(
      String shortFlag, String longFlag, String help) {
    Option opt = new Option(shortFlag, longFlag, false, help);
    opt.setOptionalArg(true);
    options.addOption(opt);
    return opt;
  }

  private Option defineOption(String longFlag, String help, boolean optional) {
    return defineOption(longFlag, longFlag, help, optional);
  }

  private static String[] getOptionValues(CommandLine cl, Option opt) {
    String[] values = cl.getOptionValues(opt.getOpt());
    return values != null ? values : new String[0];
  }

  public static void main(String[] argv) {
    Config config = new Config(Config.class, System.err, "Does some stuff.");
    System.err.println(config.processArguments(argv));
  }
}
