package gov.fedramp.automationExample;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathExecutable;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmDestination;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.s9api.XsltExecutable;
import net.sf.saxon.s9api.XsltTransformer;

/**
 * Simple example of how to use Saxon-HE to apply fedramp-automation validation
 * rules to an OSCAL Fedramp System Security Plan.
 */
public class FedrampAutomationValidator {
  private static final String FEDRAMP_AUTOMATION_XSLT = new File("../../../src/validations/target/ssp.xsl")
      .getAbsolutePath();
  private static final String BASELINES_PATH = new File("../../../dist/content/baselines/rev4/xml").getAbsolutePath();
  private static final String RESOURCES_PATH = new File("../../../dist/content/resources/xml").getAbsolutePath();

  private Processor processor;
  private XsltExecutable xsltExecutable;

  public FedrampAutomationValidator() throws SaxonApiException {
    // Create a Saxon processor
    processor = new Processor(false);
    // Compile the source XSLT to an XsltExecutable.
    StreamSource xslDocument = new StreamSource(new File(FEDRAMP_AUTOMATION_XSLT));
    XsltCompiler xsltCompiler = processor.newXsltCompiler();
    xsltExecutable = xsltCompiler.compile(xslDocument);
  }

  /** Validate the SSP with the fedramp-automation rules as compiled to XSLT. */
  public List<Map<String, String>> validateSSP(String sspPath) throws IOException, SaxonApiException {
    XsltTransformer xsltTransformer = getTransformer();

    // Read the source SSP document
    XdmNode inputNode = getInputNode(sspPath);
    xsltTransformer.setInitialContextNode(inputNode);

    // Do the transformation and output the result directly to an XdmValue
    // that we may query with XPath.
    XdmDestination xdmDestination = new XdmDestination();
    xsltTransformer.setDestination(xdmDestination);
    xsltTransformer.transform();

    // Query failed assertions via XPath.
    XdmNode svrlXdmNode = xdmDestination.getXdmNode();
    XdmValue failedAssertions = queryXPath(svrlXdmNode, "//svrl:failed-assert");

    // Accumulate the failed assertion `test` attributes in a list of maps.
    // An assertion looks something like:
    /*
     * <svrl:failed-assert xmlns:svrl="http://purl.oclc.org/dsdl/svrl"
     * test=". =&gt; empty()"><svrl:text>Assertion
     * description</svrl:text></svrl:failed-assert>
     */
    List<Map<String, String>> failedAsserts = new ArrayList<Map<String, String>>();
    for (XdmItem item : failedAssertions) {
      XdmNode node = (XdmNode) item;
      NodeInfo info = node.getUnderlyingNode();
      Map<String, String> attributes = new HashMap<String, String>();
      attributes.put("test", info.getAttributeValue("", "test"));
      failedAsserts.add(attributes);
    }

    return failedAsserts;
  }

  private XsltTransformer getTransformer() {
    XsltTransformer xsltTransformer = xsltExecutable.load();
    xsltTransformer.setParameter(new QName("baselines-base-path"), new XdmAtomicValue(BASELINES_PATH));
    xsltTransformer.setParameter(new QName("registry-base-path"), new XdmAtomicValue(RESOURCES_PATH));
    // Set to `true` to validate external resource references.
    xsltTransformer.setParameter(new QName("param-use-remote-resources"), new XdmAtomicValue(false));
    return xsltTransformer;
  }

  private XdmNode getInputNode(String sspPath) throws SaxonApiException, FileNotFoundException {
    BufferedReader inputReader = new BufferedReader(new FileReader(sspPath));
    StreamSource inputSource = new StreamSource(inputReader);
    DocumentBuilder documentBuilder = processor.newDocumentBuilder();
    return documentBuilder.build(inputSource);
  }

  public XdmValue queryXPath(XdmNode sourceXdmNode, String xPathQuery) throws SaxonApiException {
    XPathCompiler xPathCompiler = processor.newXPathCompiler();
    xPathCompiler.declareNamespace("svrl", "http://purl.oclc.org/dsdl/svrl");
    XPathExecutable executable = xPathCompiler.compile(xPathQuery);
    XPathSelector selector = executable.load();

    selector.setContextItem(sourceXdmNode);
    return selector.evaluate();
  }
}
