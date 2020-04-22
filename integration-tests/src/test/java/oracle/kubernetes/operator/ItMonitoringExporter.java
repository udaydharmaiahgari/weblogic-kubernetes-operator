// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import javax.xml.bind.DatatypeConverter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlFileInput;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlRadioButtonInput;
import com.gargoylesoftware.htmlunit.html.HtmlSubmitInput;
import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.ExecCommand;
import oracle.kubernetes.operator.utils.ExecResult;
import oracle.kubernetes.operator.utils.LoggerHelper;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.TestUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This test is used for testing Monitoring Exporter with Operator(s) .
 */

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ItMonitoringExporter extends BaseTest {

  private static int number = 5;
  private static Operator operator = null;

  private static Domain domain = null;
  private static String myhost = "";
  private static String monitoringExporterDir = "";
  private static String monitoringExporterEndToEndDir = "";
  private static String resourceExporterDir = "";
  private static String monitoringExporterScriptDir = "";
  private static String exporterUrl = "";
  private static String configPath = "";
  private static String prometheusPort = "30500";
  private static String grafanaPort;
  private static String wlsUser = "";
  private static String wlsPassword = "";
  // "heap_free_current{name="managed-server1"}[15s]" search for results for last 15secs
  private static String prometheusSearchKey1 =
      "heap_free_current%7Bname%3D%22managed-server1%22%7D%5B15s%5D";
  private static String prometheusSearchKey2 =
      "heap_free_current%7Bname%3D%22managed-server2%22%7D%5B15s%5D";
  private static String testappPrometheusSearchKey =
      "weblogic_servlet_invocation_total_count%7Bapp%3D%22httpsessionreptestapp%22%7D%5B15s%5D";
  private static String testClassName;
  private static StringBuffer namespaceList;
  private static String domainNS1;
  private static String domainNS2;
  private static String currentDateTime;
  private static final String monitoringNS = "monitortestns";
  private static String LB_MONITORING_PUBLIC_IP;

  /**
   * This method gets called only once before any of the test methods are executed. It does the
   * initialization of the integration test properties defined in OperatorIT.properties and setting
   * the resultRoot, pvRoot and projectRoot attributes.
   *
   * @throws Exception exception
   */
  @BeforeAll
  public static void staticPrepare() throws Exception {
    if (FULLTEST) {
      namespaceList = new StringBuffer();
      testClassName = new Object() {
      }.getClass().getEnclosingClass().getSimpleName();
      initialize(APP_PROPS_FILE, testClassName);
    }
  }

  /**
   * This method gets called before every test. It creates the result/pv root directories
   * for the test. Creates the operator and domain if its not running.
   *
   * @throws Exception exception if result/pv/operator/domain creation fails
   */
  @BeforeEach
  public void prepare() throws Exception {
    if (FULLTEST) {
      if (operator == null) {
        createResultAndPvDirs(testClassName);
        wlsUser = BaseTest.getUsername();
        wlsPassword = BaseTest.getPassword();

        monitoringExporterDir = getResultDir() + "/monitoring";
        monitoringExporterScriptDir = getResultDir() + "/scripts";
        resourceExporterDir =
            BaseTest.getProjectRoot() + "/integration-tests/src/test/resources/exporter";
        configPath = resourceExporterDir;
        monitoringExporterEndToEndDir = monitoringExporterDir + "/src/samples/kubernetes/end2end/";
        LoggerHelper.getLocal().log(Level.INFO, "Checking if operator and domain are running, if not creating");

        Map<String, Object> operatorMap =
            createOperatorMap(getNewSuffixCount(), true, "monexp");
        domainNS1 = ((ArrayList<String>) operatorMap.get("domainNamespaces")).get(0);
        domainNS2 = "monexp-domainns-" + getNewSuffixCount();
        ((ArrayList<String>) operatorMap.get("domainNamespaces")).add(domainNS2);
        operator = TestUtils.createOperator(operatorMap, Operator.RestCertType.SELF_SIGNED);
        Assertions.assertNotNull(operator);

        namespaceList.append((String)operatorMap.get("namespace"));
        namespaceList.append(" ").append(domainNS1).append(" ").append(domainNS2);
      }
      if (domain == null) {
        Map<String, Object> wlstDomainMap = createDomainMap(getNewSuffixCount(), "monexp");
        wlstDomainMap.put("namespace", domainNS1);
        wlstDomainMap.put("domainUID", domainNS1);
        wlstDomainMap.put("createDomainPyScript",
            "integration-tests/src/test/resources/domain-home-on-pv/create-domain-custom-nap.py");
        domain = TestUtils.createDomain(wlstDomainMap);
        domain.verifyDomainCreated();

        myhost = domain.getHostNameForCurl();
        LoggerHelper.getLocal().log(Level.INFO, "LB_TYPE is set to: " + System.getenv("LB_TYPE"));
        boolean isTraefik = (domain.getLoadBalancerName().equalsIgnoreCase("TRAEFIK"));
        if (isTraefik) {
          LoggerHelper.getLocal().log(Level.INFO, "Upgrading Traefik");
          upgradeTraefikHostName();
        }
        deployRunMonitoringExporter(domain, operator);

        String testAppName = "httpsessionreptestapp";
        String scriptName = "buildDeployAppInPod.sh";
        domain.buildDeployJavaAppInPod(
            testAppName, scriptName, BaseTest.getUsername(), BaseTest.getPassword());
        setupPv();
        if (OKE_CLUSTER) {
          cleanUpPvDirOke("/ci-oke-mysql");
          cleanUpPvDirOke("/ci-oke-prom");
          cleanUpPvDirOke("/ci-oke-alertprom");
          LB_MONITORING_PUBLIC_IP = createMonitorTraefikLB("monitoring");
          upgradeTraefikNamespace();
          //exporterUrl = "http://" + LB_MONITORING_PUBLIC_IP + "/wls-exporter/";
          exporterUrl = "http://" + myhost + "/wls-exporter/";
        } else {
          StringBuffer exportStr = new StringBuffer();
          exportStr.append("http://" + myhost);
          exportStr.append(":" + domain.getLoadBalancerWebPort());
          exportStr.append("/wls-exporter/");
          exporterUrl = exportStr.toString();
        }

        installPrometheusGrafanaWebHookMySqlCoordinator();
      }
      domain.callWebAppAndVerifyLoadBalancing("wls-exporter", false);

    }
  }

  /**
   * Releases k8s cluster lease, archives result, pv directories.
   *
   * @throws Exception if failed to delete resource
   */
  @AfterAll
  public static void staticUnPrepare() throws Exception {
    if (FULLTEST) {
      if (domain != null) {
        domain.destroy();
        TestUtils.deleteWeblogicDomainResources(domainNS1);
        if (BaseTest.SHARED_CLUSTER) {

          String image = System.getenv("REPO_REGISTRY")
              + "/weblogick8s/"
              + domainNS2
              + "-image:" + currentDateTime;
          String cmd = "docker rmi -f " + image;
          TestUtils.exec(cmd, true);
        }
        String cmd = "docker rmi -f " + domainNS2 + "-image:" + currentDateTime;
        TestUtils.exec(cmd, true);
      }
      if (operator != null) {
        operator.destroy();
      }
      try {
        uninstallWebHookPrometheusGrafanaMySql();
        if (BaseTest.OKE_CLUSTER) {
          deleteMonitorTraefikLB();
        }
      } catch (Exception ex) {
        LoggerHelper.getLocal().log(Level.INFO,
            "Exception caught while uninstalling webhook/prometheus/Grafana/Mysql " + ex.getMessage());
      }

      tearDown(new Object() {}.getClass().getEnclosingClass().getSimpleName(), namespaceList.toString());
      LoggerHelper.getLocal().log(Level.INFO,"SUCCESS");
    }
  }

  /**
   * Remove monitoring exporter directory if exists and clone latest from github for monitoring
   * exporter code.
   *
   * @throws Exception if could not run the command successfully to clone from github
   */
  private static void gitCloneBuildMonitoringExporter() throws Exception {

    LoggerHelper.getLocal().log(Level.INFO,
        "installing monitoring exporter version: "
            + MONITORING_EXPORTER_VERSION
            + "running against branch "
            + MONITORING_EXPORTER_BRANCH);

    executeShelScript(
        resourceExporterDir,
        monitoringExporterScriptDir,
        "buildMonitoringExporter.sh",
        monitoringExporterDir + " " + resourceExporterDir + " " + MONITORING_EXPORTER_BRANCH + " "
            + MONITORING_EXPORTER_VERSION, "buildMonitoringExporter.out");
  }

  /**
   * Utility to execute any shell scripts.
   *
   * @param srcLoc   - path to the shell script
   * @param destLoc  - destination path there the shell script will be executed
   * @param fileName - name of the shell script
   * @param args     - args to pass to the shell script
   * @throws Exception if could not run the command successfully to clone from github
   */
  private static void executeShelScript(String srcLoc, String destLoc, String fileName, String args, String outLogFile)
      throws Exception {
    if (!new File(destLoc).exists()) {
      LoggerHelper.getLocal().log(Level.INFO," creating script dir " + destLoc);
      Files.createDirectories(Paths.get(destLoc));
    }
    String crdCmd = " cp " + srcLoc + "/" + fileName + " " + destLoc;
    TestUtils.exec(crdCmd, true);
    crdCmd =
        "cd "
            + destLoc
            + " && chmod 777 ./"
            + fileName
            + " && . ./"
            + fileName
            + " "
            + args
            + " | tee "
            + outLogFile;
    TestUtils.exec(crdCmd, true);
    crdCmd = " cat " + destLoc + "/" + outLogFile;
    ExecResult result = ExecCommand.exec(crdCmd);
    assertFalse(
        result.stdout().contains("BUILD FAILURE"), "Shell script failed: " + result.stdout());
    LoggerHelper.getLocal().log(Level.INFO, "Result output from  the command " + crdCmd + " : " + result.stdout());
  }

  /**
   * Deploy Monitoring Exporter webapp, Prometheus and Grafana.
   *
   * @param exporterAppPath path to exporter webapp
   * @param domain          - domain where monitoring exporter will be deployed
   * @param operator        operator object managing the domain
   * @throws Exception if could not run the command successfully to clone from github
   */
  private static void deployMonitoringExporter(
      String exporterAppPath, Domain domain, Operator operator) throws Exception {

    Map<String, Object> domainMap = domain.getDomainMap();
    // create the app directory in admin pod
    TestUtils.kubectlexec(
        domain.getDomainUid() + ("-") + domainMap.get("adminServerName"),
        "" + domainMap.get("namespace"),
        " -- mkdir -p " + appLocationInPod);
    domain.deployWebAppViaWlst(
        "wls-exporter", exporterAppPath, appLocationInPod, getUsername(), getPassword(), true);
  }

  /**
   * Deploy Monitoring Exporter webapp, Prometheus and Grafana.
   *
   * @param webappName webapp name used to collect metrics for scaling
   * @param domain     - domain where monitoring exporter will be deployed
   * @throws Exception if could not run the command successfully
   */
  private static void verifyScalingViaPrometheus(Domain domain, String webappName)
      throws Exception {

    createWebHookForScale();
    // scale cluster to only one replica
    scaleCluster(1);
    // invoke the app to increase number of the opened sessions
    String testAppName = "httpsessionreptestapp";


    domain.callWebAppAndVerifyLoadBalancing(testAppName + "/CounterServlet?", false);

    String webhookPod = getPodName("name=webhook", "monitoring");
    String command = "kubectl -n " + monitoringNS + " logs " + webhookPod;
    TestUtils.checkAnyCmdInLoop(command, "scaleup hook triggered successfully");

    TestUtils.checkPodCreated(domain.getDomainUid() + "-managed-server2", domain.getDomainNs());
  }

  /**
   * clone, build , deploy monitoring exporter on specified domain, operator.
   *
   * @throws Exception if failed to deploy
   */
  private static void deployRunMonitoringExporter(Domain domain, Operator operator)
      throws Exception {
    gitCloneBuildMonitoringExporter();
    LoggerHelper.getLocal().log(Level.INFO, "Creating Operator & waiting for the script to complete execution");
    boolean testCompletedSuccessfully = false;
    startExporter(domain, operator);
    // check if exporter is up
    domain.callWebAppAndVerifyLoadBalancing("wls-exporter", false);
    testCompletedSuccessfully = true;
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - deployRunMonitoringExporter");
  }

  /**
   * create operator, domain, run some verification tests to check domain runtime.
   *
   * @throws Exception exception
   */
  private Domain createVerifyDomain(int number, Operator operator) throws Exception {
    LoggerHelper.getLocal().log(Level.INFO, "create domain with UID : test" + number);
    Domain domain = TestUtils.createDomain(createDomainMap(number));
    domain.verifyDomainCreated();
    TestUtils.renewK8sClusterLease(getProjectRoot(), getLeaseId());
    LoggerHelper.getLocal().log(Level.INFO, "verify that domain is managed by operator");
    operator.verifyDomainExists(domain.getDomainUid());
    return domain;
  }

  private static void startExporter(Domain domain, Operator operator)
      throws Exception {
    LoggerHelper.getLocal().log(Level.INFO, "deploy exporter ");
    deployMonitoringExporter(
        monitoringExporterDir + "/apps/monitoringexporter/wls-exporter.war", domain, operator);
  }

  private static void setCredentials(WebClient webClient) {
    String base64encodedUsernameAndPassword =
        base64Encode(BaseTest.getUsername() + ":" + BaseTest.getPassword());
    webClient.addRequestHeader("Authorization", "Basic " + base64encodedUsernameAndPassword);
  }

  private static void setCredentials(WebClient webClient, String username, String password) {
    String base64encodedUsernameAndPassword = base64Encode(username + ":" + password);
    webClient.addRequestHeader("Authorization", "Basic " + base64encodedUsernameAndPassword);
  }

  private static String base64Encode(String stringToEncode) {
    return DatatypeConverter.printBase64Binary(stringToEncode.getBytes());
  }

  private void upgradeTraefikHostName() throws Exception {
    String chartDir =
        BaseTest.getProjectRoot()
           + "/kubernetes/samples/charts/ingress-per-domain";


    StringBuffer cmd = new StringBuffer("helm upgrade ");
    cmd.append("--reuse-values ")
        .append("--set ")
        .append("\"")
        .append("traefik.hostname=")
        .append("\"")
        .append(" traefik-ingress-" + domainNS1 + " " + chartDir);
    if (BaseTest.HELM_VERSION.equalsIgnoreCase("V3")) {
      cmd.append(" -n " + domainNS1);
    }


    LoggerHelper.getLocal().log(Level.INFO, " upgradeTraefikNamespace() Running " + cmd.toString());
    TestUtils.exec(cmd.toString());
  }

  /**
   * Checking basic functionality of Monitoring Exporter.
   *
   * @throws Exception if test fails
   */
  @Test
  @Order(2)
  public void testBasicFunctionality() throws Exception {
    if (BaseTest.OKE_CLUSTER) {
      TestUtils.copyFile(resourceExporterDir + "/../oke/monexp-path-route.yaml",
              monitoringExporterScriptDir + "/monexp-path-route.yaml");
      TestUtils.replaceStringInFile(monitoringExporterScriptDir + "/monexp-path-route.yaml",
              "%DOMAINUID%", domain.getDomainUid());
      TestUtils.replaceStringInFile(monitoringExporterScriptDir + "/monexp-path-route.yaml",
              "%DOMAIN_NAMESPACE%", domain.getDomainNs());
      String cmd = "kubectl apply -f " + monitoringExporterScriptDir + "/monexp-path-route.yaml";
      //ExecCommand.exec(cmd, true);
    }
    test01_CheckMetricsViaPrometheus();
    test02_ReplaceConfiguration();
    test03_AppendConfiguration();
    test04_ReplaceOneAttributeValueAsArrayConfiguration();
    test05_AppendArrayWithOneExistedAndOneDifferentAttributeValueAsArrayConfiguration();
    test06_ReplaceWithEmptyConfiguration();
    test07_AppendWithEmptyConfiguration();
    test08_1AppendWithNotYmlConfiguration();
    test08_2ReplaceWithNotYmlConfiguration();
    test09_AppendWithCorruptedYmlConfiguration();
    test10_ReplaceWithCorruptedYmlConfiguration();
    test11_ReplaceWithDublicatedValuesConfiguration();
    test12_AppendWithDuplicatedValuesConfiguration();
    test13_ReplaceMetricsNameSnakeCaseFalseConfiguration();
    test14_ChangeConfigNoCredentials();
    test15_ChangeConfigInvalidUser();
    test16_ChangeConfigInvalidPass();
    test17_ChangeConfigEmptyUser();
    test18_ChangeConfigEmptyPass();
    test19_ReplaceMetricsDomainQualifierTrueConfiguration();
  }


  /**
   * Check that configuration can be reviewed via Prometheus.
   *
   * @throws Exception if test fails
   */
  private void test01_CheckMetricsViaPrometheus() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    boolean testCompletedSuccessfully = false;
    assertTrue(checkMetricsViaPrometheus(testappPrometheusSearchKey, "httpsessionreptestapp"));
    testCompletedSuccessfully = true;
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Replace monitoring exporter configuration and verify it was applied to both managed servers.
   *
   * @throws Exception if test fails
   */
  private void test02_ReplaceConfiguration() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    HtmlPage page = submitConfigureForm(exporterUrl, "replace", configPath + "/rest_jvm.yml");
    assertTrue(checkMetricsViaPrometheus(prometheusSearchKey1, "managed-server1"));
    assertTrue(checkMetricsViaPrometheus(prometheusSearchKey2, "managed-server2"));
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Add additional monitoring exporter configuration and verify it was applied.
   *
   * @throws Exception if test fails
   */
  private void test03_AppendConfiguration() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    // scale cluster to 1 managed server only to test functionality of the exporter without
    // coordinator layer
    scaleCluster(1);

    // make sure some config is there
    HtmlPage page = submitConfigureForm(exporterUrl, "replace", configPath + "/rest_jvm.yml");

    assertTrue(page.asText().contains("JVMRuntime"));
    assertFalse(page.asText().contains("WebAppComponentRuntime"));
    // run append
    page = submitConfigureForm(exporterUrl, "append", configPath + "/rest_webapp.yml");
    assertTrue(page.asText().contains("WebAppComponentRuntime"));
    // check previous config is there
    assertTrue(page.asText().contains("JVMRuntime"));

    assertTrue(checkMetricsViaPrometheus(testappPrometheusSearchKey, "httpsessionreptestapp"));
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Replace monitoring exporter configuration with only one attribute and verify it was applied.
   *
   * @throws Exception if test fails
   */
  private void test04_ReplaceOneAttributeValueAsArrayConfiguration() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    HtmlPage page =
        submitConfigureForm(exporterUrl, "replace", configPath + "/rest_oneattribval.yml");
    assertTrue(page.asText().contains("values: invocationTotalCount"));
    assertFalse(page.asText().contains("reloadTotal"));
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Append monitoring exporter configuration with one more attribute and verify it was applied
   * append to [a] new config [a,b].
   *
   * @throws Exception if test fails
   */
  private void test05_AppendArrayWithOneExistedAndOneDifferentAttributeValueAsArrayConfiguration()
      throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    HtmlPage page =
        submitConfigureForm(exporterUrl, "replace", configPath + "/rest_oneattribval.yml");
    assertTrue(page.asText().contains("values: invocationTotalCount"));
    page = submitConfigureForm(exporterUrl, "append", configPath + "/rest_twoattribs.yml");
    assertTrue(page.asText().contains("values: [invocationTotalCount, executionTimeAverage]"));
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Replace monitoring exporter configuration with empty configuration.
   *
   * @throws Exception if test fails
   */
  private void test06_ReplaceWithEmptyConfiguration() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    HtmlPage page = submitConfigureForm(exporterUrl, "replace", configPath + "/rest_empty.yml");
    assertTrue(page.asText().contains("queries:") && !page.asText().contains("values"));
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Try to append monitoring exporter configuration with empty configuration.
   *
   * @throws Exception if failed to apply configuration or check the expected values.
   */
  private void test07_AppendWithEmptyConfiguration() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    final WebClient webClient = new WebClient();
    HtmlPage originalPage = webClient.getPage(exporterUrl);
    assertNotNull(originalPage);
    HtmlPage page = submitConfigureForm(exporterUrl, "append", configPath + "/rest_empty.yml");
    assertTrue(originalPage.asText().equals(page.asText()));
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Try to append monitoring exporter configuration with configuration file not in the yaml format.
   *
   * @throws Exception if test fails
   */
  private void test08_1AppendWithNotYmlConfiguration() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    changeConfigNegative(
        "append", configPath + "/rest_notymlformat.yml", "Configuration is not in YAML format");
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Try to replace monitoring exporter configuration with configuration file not in the yaml
   * format.
   *
   * @throws Exception if failed to apply configuration or check the expected values.
   */
  private void test08_2ReplaceWithNotYmlConfiguration() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    changeConfigNegative(
        "replace", configPath + "/rest_notymlformat.yml", "Configuration is not in YAML format");
  }

  /**
   * Try to append monitoring exporter configuration with configuration file in the corrupted yaml
   * format.
   *
   * @throws Exception if test fails
   */
  private void test09_AppendWithCorruptedYmlConfiguration() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    changeConfigNegative(
        "append",
        configPath + "/rest_notyml.yml",
        "Configuration YAML format has errors while scanning a simple key");
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Try to replace monitoring exporter configuration with configuration file in the corrupted yaml
   * format.
   *
   * @throws Exception if failed to apply configuration or check the expected values.
   */
  private void test10_ReplaceWithCorruptedYmlConfiguration() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    changeConfigNegative(
        "replace",
        configPath + "/rest_notyml.yml",
        "Configuration YAML format has errors while scanning a simple key");
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Try to replace monitoring exporter configuration with configuration file with dublicated
   * values.
   *
   * @throws Exception if test fails
   */
  private void test11_ReplaceWithDublicatedValuesConfiguration() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    changeConfigNegative(
        "replace",
        configPath + "/rest_dublicatedval.yml",
        "Duplicate values for [deploymentState] at applicationRuntimes.componentRuntimes");
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Try to append monitoring exporter configuration with configuration file with duplicated values.
   *
   * @throws Exception if test fails
   */
  private void test12_AppendWithDuplicatedValuesConfiguration() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    changeConfigNegative(
        "append",
        configPath + "/rest_dublicatedval.yml",
        "Duplicate values for [deploymentState] at applicationRuntimes.componentRuntimes");
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Try to replace monitoring exporter configuration with configuration file with
   * NameSnakeCase=false.
   *
   * @throws Exception if failed to apply configuration or check the expected values.
   */
  private void test13_ReplaceMetricsNameSnakeCaseFalseConfiguration() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    HtmlPage page =
        submitConfigureForm(exporterUrl, "replace", configPath + "/rest_snakecasefalse.yml");
    assertNotNull(page);
    assertFalse(page.asText().contains("metricsNameSnakeCase"));
    String searchKey = "weblogic_servlet_executionTimeAverage%7Bapp%3D%22httpsessionreptestapp%22%7D%5B15s%5D";
    assertTrue(checkMetricsViaPrometheus(searchKey, "httpsessionreptestapp"));
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Test to replace monitoring exporter configuration with configuration file with
   * domainQualifier=true.
   *
   * @throws Exception if failed to apply configuration or check the expected values.
   */
  private void test19_ReplaceMetricsDomainQualifierTrueConfiguration() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    HtmlPage page =
        submitConfigureForm(exporterUrl, "replace", configPath + "/rest_domainqualtrue.yml");
    assertNotNull(page);
    LoggerHelper.getLocal().log(Level.INFO, "page - " + page.asText());
    assertTrue(page.asText().contains("domainQualifier"));

    String searchKey = "weblogic_servlet_executionTimeAverage%7Bapp%3D%22httpsessionreptestapp%22%7D%5B15s%5D";
    assertTrue(checkMetricsViaPrometheus(searchKey, "\"domain\":\"" + domain.getDomainUid() + "\""));

    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Test to change monitoring exporter configuration without authentication.
   *
   * @throws Exception if failed to apply configuration or check the expected values.
   */
  // verify that change configuration fails without authentication
  private void test14_ChangeConfigNoCredentials() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    WebClient webClient = new WebClient();
    String expectedErrorMsg = "401 Unauthorized for " + exporterUrl;
    try {
      HtmlPage page =
          submitConfigureForm(
              exporterUrl, "append", configPath + "/rest_snakecasetrue.yml", webClient);
      throw new RuntimeException("Form was submitted successfully with no credentials");
    } catch (FailingHttpStatusCodeException ex) {
      assertTrue((ex.getMessage()).contains(expectedErrorMsg));
    }
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Try to change monitoring exporter configuration with invalid username.
   *
   * @throws Exception exception
   */
  private void test15_ChangeConfigInvalidUser() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    changeConfigNegativeAuth(
        "replace",
        configPath + "/rest_snakecasetrue.yml",
        "401 Unauthorized for " + exporterUrl,
        "invaliduser",
        wlsPassword);
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Try to change monitoring exporter configuration with invalid password.
   *
   * @throws Exception exception
   */
  private void test16_ChangeConfigInvalidPass() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    changeConfigNegativeAuth(
        "replace",
        configPath + "/rest_snakecasetrue.yml",
        "401 Unauthorized for " + exporterUrl,
        wlsUser,
        "invalidpass");
  }

  /**
   * Try to change monitoring exporter configuration with empty username.
   *
   * @throws Exception if test fails
   */
  private void test17_ChangeConfigEmptyUser() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    changeConfigNegativeAuth(
        "replace",
        configPath + "/rest_snakecasetrue.yml",
        "401 Unauthorized for " + exporterUrl,
        "",
        wlsPassword);
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Try to change monitoring exporter configuration with empty pass.
   *
   * @throws Exception if test fails
   */
  private void test18_ChangeConfigEmptyPass() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    changeConfigNegativeAuth(
        "replace",
        configPath + "/rest_snakecasetrue.yml",
        "401 Unauthorized for " + exporterUrl,
        wlsUser,
        "");
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Install prometheus and grafana with latest version of chart.
   *
   * @throws Exception if test fails
   */
  @Test
  @Order(3)
  public void testCheckPromGrafanaLatestVersion() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    executeShelScript(
        resourceExporterDir,
        monitoringExporterScriptDir,
        "redeployPromGrafanaLatestChart.sh",
        monitoringExporterDir + " " + resourceExporterDir + " " + domainNS1
        + " " + domainNS2, "redeployPromGrafanaLatestChart.out");


    HtmlPage page = submitConfigureForm(exporterUrl, "replace", configPath + "/rest_webapp.yml");
    checkPromGrafana(testappPrometheusSearchKey, "httpsessionreptestapp");
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Test End to End example from MonitoringExporter github project.
   *
   * @throws Exception if test fails
   */
  @Test
  @Order(1)
  public void testEndToEndViaChart() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    try {
      configureDomainInPrometheus(domainNS1, domainNS1, domainNS2, domainNS2);
      createWlsImageAndDeploy();
      checkPromGrafana("wls_servlet_execution_time_average", "test-webapp");
      fireAlert();
      addMonitoringToExistedDomain();
    } catch (Exception ex) {
      //switch Perforce to use domain in pv
      configureDomainInPrometheus(domainNS2, domainNS2, domainNS1, domainNS1);
      throw ex;
    } finally {
      String crdCmd =
          " kubectl delete -f " + monitoringExporterEndToEndDir + "/demo-domains/domainInImage.yaml";
      ExecCommand.exec(crdCmd);
      crdCmd = "kubectl delete secret " + domainNS2 + "-weblogic-credentials";
      ExecCommand.exec(crdCmd);
      TestUtils.deleteWeblogicDomainResources(domainNS2);
    }
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);

  }

  private void fireAlert() throws Exception {
    LoggerHelper.getLocal().log(Level.INFO, "Fire Alert by changing replca count");
    replaceStringInFile(
        monitoringExporterEndToEndDir + "/demo-domains/domainInImage.yaml", "replicas: 2", "replicas: 1");

    // apply new domain yaml and verify pod restart
    String crdCmd =
        " kubectl apply -f " + monitoringExporterEndToEndDir + "/demo-domains/domainInImage.yaml";
    TestUtils.exec(crdCmd);

    TestUtils.checkPodReady(domainNS2 + "-admin-server", domainNS2);
    TestUtils.checkPodReady(domainNS2 + "-managed-server-1", domainNS2);

    String webhookPod = getPodName("app=webhook", "webhook");
    String command = "kubectl -n webhook logs " + webhookPod;
    TestUtils.checkAnyCmdInLoop(
        command, "Some WLS cluster has only one running server for more than 1 minutes");
  }

  private static void addMonitoringToExistedDomain() throws Exception {
    LoggerHelper.getLocal().log(Level.INFO, "Add monitoring to the running domain");
    String exporterAppPath = monitoringExporterDir + "/apps/monitoringexporter/wls-exporter.war";
    configureDomainInPrometheus(domainNS2, domainNS2, domainNS1, domainNS1);
    BaseTest.setWaitTimePod(10);
    assertTrue(
        checkMetricsViaPrometheus("webapp_config_open_sessions_current_count", domainNS1),
        "Can't find expected metrics");
  }

  private static void configureDomainInPrometheus(String oldDomainNS,
                                                  String oldDomainUid,
                                                  String domainNS,
                                                  String domainUid) throws Exception {
    String crdCmd =
        " kubectl -n " + monitoringNS + " get cm prometheus-server -oyaml > "
            + monitoringExporterEndToEndDir
            + "/cm.yaml";
    TestUtils.exec(crdCmd);
    ExecResult result = ExecCommand.exec("cat " + monitoringExporterEndToEndDir + "/cm.yaml");
    LoggerHelper.getLocal().log(Level.INFO, " output for cm " + result.stdout());
    replaceStringInFile(
        monitoringExporterEndToEndDir + "/cm.yaml",
        oldDomainNS + ";" + oldDomainUid + ";cluster-1", domainNS + ";" + domainUid
            + ";cluster-1");
    crdCmd = " kubectl -n " + monitoringNS
        + " apply -f "
        + monitoringExporterEndToEndDir
        + "/cm.yaml";
    TestUtils.exec(crdCmd);
  }

  private static String getPodName(String labelExp, String namespace) throws Exception {
    StringBuffer cmd = new StringBuffer();
    cmd.append(
        "kubectl get pod -l "
            + labelExp
            + " -n "
            + namespace
            + " -o jsonpath=\"{.items[0].metadata.name}\"");
    LoggerHelper.getLocal().log(Level.INFO, " pod name cmd =" + cmd);
    ExecResult result = null;
    String podName = null;
    int i = 0;
    while (i < 4) {
      result = ExecCommand.exec(cmd.toString());
      LoggerHelper.getLocal().log(Level.INFO, " Result output for " + cmd.toString()
              + " : "
              + result.stdout());
      if (result.exitValue() == 0) {
        LoggerHelper.getLocal().log(Level.INFO, result.stdout());
        podName = result.stdout().trim();
        break;
      } else {
        Thread.sleep(10000);
        i++;
      }
    }
    assertNotNull(podName, labelExp + " was not created, can't find running pod ");
    return podName;
  }

  private void changeConfigNegative(String effect, String configFile, String expectedErrorMsg)
      throws Exception {
    final WebClient webClient = new WebClient();
    HtmlPage originalPage = webClient.getPage(exporterUrl);
    assertNotNull(originalPage);
    HtmlPage page = submitConfigureForm(exporterUrl, effect, configFile);
    assertTrue((page.asText()).contains(expectedErrorMsg),
            "Expected Error was not received , actual " +  page.asText());
  }

  private void changeConfigNegativeAuth(
      String effect, String configFile, String expectedErrorMsg, String username, String password)
      throws Exception {
    try {
      final WebClient webClient = new WebClient();
      setCredentials(webClient, username, password);
      HtmlPage page = submitConfigureForm(exporterUrl, effect, configFile, webClient);
      throw new RuntimeException("Expected exception was not thrown ");
    } catch (FailingHttpStatusCodeException ex) {
      assertTrue((ex.getMessage()).contains(expectedErrorMsg));
    }
  }

  private HtmlPage submitConfigureForm(String exporterUrl, String effect, String configFile)
      throws Exception {
    final WebClient webClient = new WebClient();
    webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
    setCredentials(webClient);
    return submitConfigureForm(exporterUrl, effect, configFile, webClient);
  }

  private HtmlPage submitConfigureForm(
          String exporterUrl, String effect, String configFile, WebClient webClient) throws Exception {
    // Get the first page
    HtmlPage page1 = webClient.getPage(exporterUrl);
    if (page1 == null) {
      //try again
      page1 = webClient.getPage(exporterUrl);
    }
    assertNotNull(page1);
    assertTrue((page1.asText()).contains("This is the WebLogic Monitoring Exporter."));

    // Get the form that we are dealing with and within that form,
    // find the submit button and the field that we want to change.Generated form for cluster had
    // extra path for wls-exporter
    HtmlForm form = page1.getFirstByXPath("//form[@action='configure']");
    if (form == null) {
      form = page1.getFirstByXPath("//form[@action='/wls-exporter/configure']");
    }
    assertNotNull(form);
    List<HtmlRadioButtonInput> radioButtons = form.getRadioButtonsByName("effect");
    assertNotNull(radioButtons);
    for (HtmlRadioButtonInput radioButton : radioButtons) {
      if (radioButton.getValueAttribute().equalsIgnoreCase(effect)) {
        radioButton.setChecked(true);
      }
    }

    HtmlSubmitInput button =
            page1.getFirstByXPath("//form//input[@type='submit']");
    assertNotNull(button);
    final HtmlFileInput fileField = form.getInputByName("configuration");
    assertNotNull(fileField);

    // Change the value of the text field
    fileField.setValueAttribute(configFile);
    fileField.setContentType("multipart/form-data");

    // Now submit the form by clicking the button and get back the second page.
    HtmlPage page2 = button.click();
    assertNotNull(page2);
    assertFalse((page2.asText()).contains("Error 500--Internal Server Error"));
    // wait time for coordinator to update both managed configuration
    Thread.sleep(15 * 1000);
    return page2;
  }

  /**
   * Remove monitoring exporter directory if exists and clone latest from github for monitoring
   * exporter code.
   *
   * @throws Exception if could not run the command successfully to install database
   */
  private static void setupPv() throws Exception {
    String pvDir = monitoringExporterEndToEndDir + "pvDir";
    if (new File(pvDir).exists()) {
      LoggerHelper.getLocal().log(Level.INFO, " PV dir already exists , cleaning ");
      if (!pvDir.isEmpty()) {
        deletePvDir();
      }
    } else {
      Files.createDirectories(Paths.get(pvDir));
    }
    if (BaseTest.OKE_CLUSTER) {
      TestUtils.copyFile(resourceExporterDir + "/../oke/okemysql.yaml",
              monitoringExporterEndToEndDir + "/mysql/mysql.yaml");
      TestUtils.copyFile(resourceExporterDir + "/../oke/mysqlpersistence.yaml",
              monitoringExporterEndToEndDir + "/mysql/persistence.yaml");
      TestUtils.copyFile(resourceExporterDir + "/../oke/prompersistence.yaml",
              monitoringExporterEndToEndDir + "/prometheus/persistence.yaml");
      TestUtils.copyFile(resourceExporterDir + "/../oke/alert-prompersistence.yaml",
              monitoringExporterEndToEndDir + "/prometheus/alert-persistence.yaml");
      TestUtils.copyFile(resourceExporterDir + "/../oke/grafanavalues.yaml",
              monitoringExporterEndToEndDir + "/grafana/values.yaml");
    } else {
      replaceStringInFile(
              monitoringExporterEndToEndDir + "/mysql/persistence.yaml", "%PV_ROOT%", pvDir);
      replaceStringInFile(
              monitoringExporterEndToEndDir + "/prometheus/persistence.yaml", "%PV_ROOT%", pvDir);
      replaceStringInFile(
              monitoringExporterEndToEndDir + "/prometheus/alert-persistence.yaml", "%PV_ROOT%", pvDir);
      replaceStringInFile(
              monitoringExporterEndToEndDir + "/grafana/persistence.yaml", "%PV_ROOT%", pvDir);
    }

    replaceStringInFile(
        monitoringExporterEndToEndDir + "/prometheus/persistence.yaml", "monitoring", monitoringNS);
    replaceStringInFile(
        monitoringExporterEndToEndDir + "/prometheus/alert-persistence.yaml", "monitoring", monitoringNS);
    replaceStringInFile(
        monitoringExporterEndToEndDir + "/grafana/persistence.yaml", "monitoring", monitoringNS);

  }

  /**
   * Install wls image tool and update wls pods.
   *
   * @throws Exception if could not run the command successfully to create WLSImage and deploy
   */
  private static void createWlsImageAndDeploy() throws Exception {
    LoggerHelper.getLocal().log(Level.INFO, " Starting to create WLS Image");
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    Date date = new Date();
    currentDateTime = dateFormat.format(date) + "-" + System.currentTimeMillis();
    String image = domainNS2 + "-image:" + currentDateTime;
    executeShelScript(
        resourceExporterDir,
        monitoringExporterScriptDir,
        "createWLSImage.sh",
        monitoringExporterDir + " " + resourceExporterDir
            + " " + domainNS2
        + " " + currentDateTime
        + " " + wlsUser
        + " " +  wlsPassword, "createWLSImage.out");

    // for remote k8s cluster and domain in image case, push the domain image to OCIR
    if (BaseTest.SHARED_CLUSTER) {
      TestUtils.createDockerRegistrySecret(
          "ocirsecret",
          System.getenv("REPO_REGISTRY"),
          System.getenv("REPO_USERNAME"),
          System.getenv("REPO_PASSWORD"),
          System.getenv("REPO_EMAIL"),
          domainNS2);
      String oldImage = image;
      image = System.getenv("REPO_REGISTRY")
            + "/weblogick8s/"
            + oldImage;
      loginAndTagImage(oldImage, image);
      // create ocir registry secret in the same ns as domain which is used while pulling the domain
      // image

      TestUtils.loginAndPushImageToOcir(image);
    }

    replaceStringInFile(monitoringExporterEndToEndDir + "/demo-domains/domainInImage.yaml",
        domainNS2 + "-image:1.0", image);
    replaceStringInFile(monitoringExporterEndToEndDir + "/demo-domains/domainInImage.yaml",
        "30703", String.valueOf(31000 + getNewSuffixCount()));
    replaceStringInFile(monitoringExporterEndToEndDir + "/demo-domains/domainInImage.yaml",
        "30701", String.valueOf(30800 + getNewSuffixCount()));
    LoggerHelper.getLocal().log(Level.INFO, " Starting to create secret");
    String crdCmd =
        "kubectl -n " + domainNS2 + " create secret generic " + domainNS2 + "-weblogic-credentials "
            + "  --from-literal=username="
            + wlsUser
            + "  --from-literal=password="
            + wlsPassword;
    TestUtils.exec(crdCmd);

    // apply new domain yaml and verify pod restart
    crdCmd =
        " kubectl apply -f " + monitoringExporterEndToEndDir + "/demo-domains/domainInImage.yaml";
    TestUtils.exec(crdCmd);

    TestUtils.checkPodReady(domainNS2 + "-admin-server", domainNS2);
    TestUtils.checkPodReady(domainNS2 + "-managed-server-1", domainNS2);
    TestUtils.checkPodReady(domainNS2 + "-managed-server-2", domainNS2);

    replaceStringInFile(monitoringExporterEndToEndDir + "/util/curl.yaml", "default", domainNS2);
    // apply curl to the pod
    crdCmd = " kubectl apply -f " + monitoringExporterEndToEndDir + "/util/curl.yaml";
    TestUtils.exec(crdCmd);

    TestUtils.checkPodReady("curl", domainNS2);
    // access metrics
    crdCmd =
        "kubectl exec -n " + domainNS2 + "  curl -- curl http://"
            + wlsUser
            + ":"
            + wlsPassword
            + "@" + domainNS2 + "-managed-server-1:8001/wls-exporter/metrics";
    ExecResult result = TestUtils.exec(crdCmd);
    assertTrue((result.stdout().contains("wls_servlet_execution_time_average")));
    crdCmd =
        "kubectl exec -n " + domainNS2 + " curl -- curl http://"
            + wlsUser
            + ":"
            + wlsPassword
            + "@" + domainNS2 + "-managed-server-2:8001/wls-exporter/metrics";
    result = TestUtils.exec(crdCmd);
    assertTrue((result.stdout().contains("wls_servlet_execution_time_average")));
  }

  private static ExecResult loginAndTagImage(String oldImageName, String newImageName) throws Exception {
    String dockerLoginAndTagCmd =
        "docker login "
            + System.getenv("REPO_REGISTRY")
            + " -u "
            + System.getenv("REPO_USERNAME")
            + " -p \""
            + System.getenv("REPO_PASSWORD")
            + "\" && docker tag "
            + oldImageName
            + " "
            + newImageName;
    ExecResult result = TestUtils.exec(dockerLoginAndTagCmd);
    LoggerHelper.getLocal().log(Level.INFO,
        "cmd "
            + dockerLoginAndTagCmd
            + "\n result "
            + result.stdout()
            + "\n err "
            + result.stderr());
    return result;
  }

  /**
   * Install Prometheus and Grafana using helm chart, MySql, webhook, coordinator.
   *
   * @throws Exception if could not run the command successfully to install Prometheus and Grafana
   */
  private static void installPrometheusGrafanaWebHookMySqlCoordinator() throws Exception {
    prometheusPort = "30500";
    String crdCmd = " cp " + resourceExporterDir + "/promvalues.yaml"
            + " " + monitoringExporterEndToEndDir
            + "/prometheus/promvalues.yaml";
    if (BaseTest.OKE_CLUSTER) {
      TestUtils.copyFile(resourceExporterDir + "/../oke/okepromvalues.yaml",
              monitoringExporterEndToEndDir
                      + "/prometheus/promvalues.yaml");
      TestUtils.copyFile(resourceExporterDir + "/../oke/grafanavalues.yaml",
              monitoringExporterEndToEndDir
                      + "/grafana/values.yaml");
    } else {
      TestUtils.copyFile(resourceExporterDir + "/promvalues.yaml",
              monitoringExporterEndToEndDir
                      + "/prometheus/promvalues.yaml");

      String promalertmanagerPort = String.valueOf(32500 + getNewSuffixCount());
      replaceStringInFile(
              monitoringExporterEndToEndDir + "/prometheus/promvalues.yaml", "32500", promalertmanagerPort);
      grafanaPort = String.valueOf(31000 + getNewSuffixCount());
      replaceStringInFile(monitoringExporterEndToEndDir + "/grafana/values.yaml",
              "31000", grafanaPort);
    }
    executeShelScript(
        resourceExporterDir,
        monitoringExporterScriptDir,
        "createPromGrafanaMySqlCoordWebhook.sh",
        monitoringExporterDir
            + " " + resourceExporterDir
            + " " + PROMETHEUS_CHART_VERSION
            + " " + GRAFANA_CHART_VERSION
            + " " + domainNS1
            + " " + domainNS2,
            "createPromGrafanaMySqlCoordWebhook.out");


    String webhookPod = getPodName("app=webhook", "webhook");
    TestUtils.checkPodReady(webhookPod, "webhook");

    //update with current WDT version
    replaceStringInFile(monitoringExporterEndToEndDir + "/demo-domains/domainBuilder/build.sh",
        "0.24", WDT_VERSION);
    //update with current Exporter version
    replaceStringInFile(monitoringExporterEndToEndDir + "/demo-domains/domainBuilder/build.sh",
        "1.1.1", MONITORING_EXPORTER_VERSION);

  }

  static void checkPromGrafana(String searchKey, String expectedVal) throws Exception {

    String crdCmd = "kubectl -n " + monitoringNS + " get pods -l app=prometheus";
    ExecResult resultStatus = ExecCommand.exec(crdCmd);
    LoggerHelper.getLocal().log(Level.INFO, "Status of the pods " + resultStatus.stdout());

    assertFalse(
        resultStatus.stdout().contains("CrashLoopBackOff")
            || resultStatus.stdout().contains("Error"),
        "Can't create prometheus pods");
    crdCmd = "kubectl -n " + monitoringNS + " get pods -l app=grafana";
    resultStatus = ExecCommand.exec(crdCmd);
    LoggerHelper.getLocal().log(Level.INFO, "Status of the pods " + resultStatus.stdout());

    String podName = getPodName("app=grafana", monitoringNS);
    TestUtils.checkPodReady(podName, monitoringNS);

    String url = domain.getHostNameForCurl() + ":" + grafanaPort;
    if (BaseTest.OKE_CLUSTER) {
      url = LB_MONITORING_PUBLIC_IP;
    }
    LoggerHelper.getLocal().log(Level.INFO, "installing grafana dashboard");
    crdCmd =
        " cd "
            + monitoringExporterEndToEndDir
            + " && curl -v -H 'Content-Type: application/json' -H \"Content-Type: application/json\""
            + "  -X POST http://admin:12345678@" + url + "/api/datasources/"
            + "  --data-binary @grafana/datasource.json";
    TestUtils.exec(crdCmd);

    crdCmd =
        " cd "
            + monitoringExporterEndToEndDir
            + " && curl -v -H 'Content-Type: application/json' -H \"Content-Type: application/json\""
            + "  -X POST http://admin:12345678@" + url + "/api/dashboards/db/"
            + "  --data-binary @grafana/dashboard.json";
    TestUtils.exec(crdCmd);
    crdCmd = " cd "
        + monitoringExporterEndToEndDir
        + " && "
        + "curl -v  -H 'Content-Type: application/json' "
        + " -X GET http://admin:12345678@" + url + "/api/dashboards/db/weblogic-server-dashboard";
    ExecResult result = ExecCommand.exec(crdCmd);
    assertTrue(result.stdout().contains("wls_jvm_uptime"));
    assertTrue(
        checkMetricsViaPrometheus(searchKey, expectedVal),
        "Can't find expected metrics");
  }


  /**
   * Install WebHook for performing scaling via prometheus.
   *
   * @throws Exception if could not run the command successfully to install webhook and alert
   *                   manager
   */
  private static void createWebHookForScale() throws Exception {

    String webhookResourceDir = resourceExporterDir + "/../webhook";
    String webhookDir = monitoringExporterDir + "/webhook";
    // install webhook
    LoggerHelper.getLocal().log(Level.INFO, "installing webhook ");
    executeShelScript(
        webhookResourceDir,
        monitoringExporterScriptDir,
        "setupWebHook.sh",
        webhookDir + " " + webhookResourceDir + " " + operator.getOperatorNamespace(),
        "setupWebHook.out");
    String webhookPod = getPodName("name=webhook", monitoringNS);
    TestUtils.checkPodReady(webhookPod, monitoringNS);
  }

  /**
   * Uninstall Prometheus and Grafana using helm chart, uninstall Webhook, MySql.
   *
   * @throws Exception if could not run the command successfully to uninstall deployments
   */
  private static void uninstallWebHookPrometheusGrafanaMySql() throws Exception {

    executeShelScript(
        resourceExporterDir,
        monitoringExporterScriptDir,
        "deletePromGrafanaMySqlCoordWebhook.sh",
        monitoringExporterDir + " " + resourceExporterDir + " " + domainNS1,
        "deletePromGrafanaMySqlCoordWebhook.out");

    deletePvDir();
  }

  /**
   * Delete PvDir via docker.
   *
   * @throws Exception if could not run the command successfully to delete PV
   */
  private static void deletePvDir() throws Exception {
    String pvDir = monitoringExporterEndToEndDir + "/pvDir";
    String crdCmd =
        "cd "
            + monitoringExporterEndToEndDir
            + " && docker run --rm -v "
            + monitoringExporterEndToEndDir
            + "pvDir:/tt -v $PWD/util:/util  nginx  /util/clean-pv.sh";
    try {
      if (new File(pvDir).exists()) {
        ExecCommand.exec(crdCmd);
        StringBuffer removeDir = new StringBuffer();
        LoggerHelper.getLocal().log(Level.INFO, "Cleaning PV dir " + pvDir);
        removeDir.append("rm -rf ").append(pvDir);
        ExecCommand.exec(removeDir.toString());
      }
    } finally {
      if (JENKINS) {
        if (new File(pvDir).exists()) {

          LoggerHelper.getLocal().log(Level.INFO, "Deleting pv created dir " + pvDir);
          TestUtils.exec("/usr/local/packages/aime/ias/run_as_root \"rm -rf " + pvDir + "\"");
        }
      }
    }
  }

  /**
   * A utility method to sed files.
   *
   * @throws IOException when copying files from source location to staging area fails
   */
  private static void replaceStringInFile(String filePath, String oldValue, String newValue)
      throws IOException {
    Path src = Paths.get(filePath);
    LoggerHelper.getLocal().log(Level.INFO, "Copying {0}", src.toString());
    Charset charset = StandardCharsets.UTF_8;
    String content = new String(Files.readAllBytes(src), charset);
    content = content.replaceAll(oldValue, newValue);
    LoggerHelper.getLocal().log(Level.INFO, "to {0}", src.toString());
    Files.write(src, content.getBytes(charset));
  }

  /**
   * call operator to scale to specified number of replicas.
   *
   * @param replicas - number of managed servers
   * @throws Exception if scaling fails
   */
  private static void scaleCluster(int replicas) throws Exception {
    LoggerHelper.getLocal().log(Level.INFO, "Scale up/down to " + replicas + " managed servers");
    operator.scale(domain.getDomainUid(), domain.getClusterName(), replicas);
  }

  /**
   * Check metrics using Prometheus.
   *
   * @param searchKey   - metric query expression
   * @param expectedVal - expected metrics to search
   * @throws Exception if command to check metrics fails
   */
  private static boolean checkMetricsViaPrometheus(String searchKey, String expectedVal)
      throws Exception {
    // url
    String url = myhost + ":" + prometheusPort;
    if (BaseTest.OKE_CLUSTER) {
      assertNotNull(LB_MONITORING_PUBLIC_IP, "Prometheus LB External IP is undefined");
      url = LB_MONITORING_PUBLIC_IP;
    }
    StringBuffer testAppUrl = new StringBuffer("http://");
    testAppUrl
        .append(url)
        .append("/api/v1/query?query=")
        .append(searchKey);
    // curl cmd to call webapp
    StringBuffer curlCmd = new StringBuffer("curl --noproxy '*' ");
    curlCmd.append(testAppUrl.toString());
    LoggerHelper.getLocal().log(Level.INFO, "Curl cmd " + curlCmd);
    LoggerHelper.getLocal().log(Level.INFO, "searchKey:" + searchKey);
    LoggerHelper.getLocal().log(Level.INFO, "expected Value " + expectedVal);
    boolean result = false;
    try {
      TestUtils.checkAnyCmdInLoop(curlCmd.toString(), expectedVal);
      LoggerHelper.getLocal().log(Level.INFO,
          "Prometheus application invoked successfully with curlCmd:" + curlCmd);
      result = true;
    } catch (Exception ex) {
      new RuntimeException("FAILURE: can't check metrics" + ex.getMessage());
    }
    return result;
  }

  /**
   * Method to read the yaml file and add extra properties to the root.
   *
   * @param yamlFile - Name of the yaml file to make changes.
   * @throws IOException exception
   */
  private static void addRestOptToYaml(String yamlFile, String prop, int restPort) throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();

    ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
    Object obj = yamlReader.readValue(new File(yamlFile), Object.class);

    ObjectMapper jsonWriter = new ObjectMapper();
    String writeValueAsString = jsonWriter.writeValueAsString(obj);
    JsonNode root = objectMapper.readTree(writeValueAsString);
    ((ObjectNode) root).put(prop, restPort);
    String jsonAsYaml = new YAMLMapper().writerWithDefaultPrettyPrinter().writeValueAsString(root);

    // Write the modified yaml to the file
    Path path = Paths.get(yamlFile);
    Charset charset = StandardCharsets.UTF_8;
    Files.write(path, jsonAsYaml.getBytes(charset));

  }

  /**
   * Method to create Load Balancer to access Prometheus and Grafana in OKE.
   *
   * @param chartName  name for traefik LB (prometheus|grafana)
   * @return external IP for Load Balancer
   * @throws Exception if fails to create or verify LB creation.
   */
  private static String createMonitorTraefikLB(String chartName) throws Exception {
    String crdCmd =
            " kubectl create namespace " + monitoringNS;
    TestUtils.exec(crdCmd);
    String cmdLb = BaseTest.getProjectRoot() + "/kubernetes/samples/charts/util/setup.sh "
            + "create traefik traefik-"
            + chartName
            + " montesttraefikns "
            + resourceExporterDir
            + "/../oke/traefikvalues.yaml";
    LoggerHelper.getLocal().log(Level.INFO, "Executing cmd " + cmdLb);
    ExecResult result = ExecCommand.exec(cmdLb);
    if (result.exitValue() != 0) {
      if (!result.stderr().contains("release named " + chartName + " already exists")) {
        throw new Exception(
                "FAILURE: command to create load balancer "
                        + cmdLb
                        + " failed, returned "
                        + result.stdout()
                        + result.stderr());
      }
    }
    TestUtils.checkLbExternalIpCreated("traefik-" + chartName,"montesttraefikns");
    //String cmdip = "kubectl describe svc traefik-"
    //       + chartName + " --namespace montesttraefikns "
    //       + "| grep Ingress | awk '{print $3}'";

    String cmdip = "kubectl get svc --namespace montesttraefikns "
        + " -o jsonpath='{.items[?(@.metadata.name == \"traefik-"
        + chartName + "\")].status.loadBalancer.ingress[0].ip}'";
    result = TestUtils.exec(cmdip);
    String lbPublicIP = result.stdout().trim();
    assertNotNull(lbPublicIP, "Can't retreive External IP for " + chartName);
    LoggerHelper.getLocal().log(Level.INFO,
            "Load Balancer MONITORING Public IP : " + lbPublicIP);
    // apply new domain yaml and verify pod restart
    crdCmd =
            " kubectl apply -f " + resourceExporterDir + "/../oke/traefik-path-routing-monitoring.yaml";
    result = TestUtils.exec(crdCmd);
    LoggerHelper.getLocal().log(Level.INFO,
            "Created Monitoring LB Path Route " + result.stdout());
    if (result.exitValue() != 0) {
      throw new Exception(
              "FAILURE: command to create load balancer monitoring path routing failed"
                      + cmdLb
                      + " failed, returned "
                      + result.stdout()
                      + result.stderr());

    }
    return lbPublicIP;
  }

  /**
   * Method to delete Load Balancer to access Prometheus and Grafana in OKE.
   *
   * @throws Exception if fails to create or verify LB creation.
   */
  private static void deleteMonitorTraefikLB() throws Exception {
    // delete ingress
    String crdCmd =
            " kubectl delete -f " + resourceExporterDir + "/../oke/traefik-path-routing-monitoring.yaml";
    TestUtils.exec(crdCmd);

    String cmdLb = BaseTest.getProjectRoot() + "/kubernetes/samples/charts/util/setup.sh "
            + "delete traefik monitoringtraefik montesttraefikns";

    LoggerHelper.getLocal().log(Level.INFO, "Executing cmd " + cmdLb);
    ExecResult result = ExecCommand.exec(cmdLb);
    if (result.exitValue() != 0) {
      if (!result.stderr().contains("release named traefik-operator can't be deleted")) {
        throw new RuntimeException(
                "FAILURE: command to delete load balancer "
                        + cmdLb
                        + " failed, returned "
                        + result.stdout()
                        + result.stderr());
      }
    }
  }

  /**
   * Run script to delete pv dirs in oke.
   */
  public static void cleanUpPvDirOke(String fssPath) {

    try {
      String cmd = " kubectl create ns cleanupoketest";
      ExecCommand.exec(cmd);
      String pvYaml = BaseTest.getProjectRoot()
              + "/integration-tests/src/test/resources/oke/cleanupokepv.yaml";
      String okepvYaml = monitoringExporterDir + "/cleanupokepv.yaml";
      String pvcYaml = BaseTest.getProjectRoot()
              + "/integration-tests/src/test/resources/oke/cleanupokepvc.yaml";
      String okepvcYaml = monitoringExporterDir + "/cleanupokepvc.yaml";
      TestUtils.copyFile(pvYaml, okepvYaml);
      TestUtils.copyFile(pvcYaml, okepvcYaml);
      TestUtils.replaceStringInFile(okepvYaml, "NFS_SERVER", "10.0.10.6");
      TestUtils.replaceStringInFile(okepvYaml, "FSS_DIR", fssPath);
      TestUtils.replaceStringInFile(okepvYaml, "TESTUID", "monitoring");;
      TestUtils.replaceStringInFile(okepvcYaml, "TESTUID", "monitoring");


      cmd = " kubectl apply -f " + okepvYaml;
      ExecResult result = ExecCommand.exec(cmd);
      LoggerHelper.getLocal().log(
              Level.INFO, "created  pv to cleanup nfs mounted dirs " + result.stdout());
      cmd = " kubectl apply -f " + okepvcYaml;
      result = ExecCommand.exec(cmd);
      LoggerHelper.getLocal().log(
              Level.INFO, "created  pvc to cleanup nfs mounted dirs " + result.stdout());

      StringBuffer cmdRemove = new StringBuffer();
      cmdRemove.append(BaseTest.getProjectRoot())
              .append("/src/integration-tests/bash/krun.sh -t 460 -l Always -p monitoringpod -m ")
              .append("cleanupoke-monitoring-pvc:/shared/")
              .append(" -n cleanupoketest -c \"rm -rf /shared/* \"");

      LoggerHelper.getLocal().log(Level.INFO, "Delete PVROOT by running " + cmdRemove.toString());
      result = ExecCommand.exec(cmdRemove.toString());
      if (result.exitValue() != 0) {
        //retry
        result = ExecCommand.exec(cmdRemove.toString());
      }
      LoggerHelper.getLocal().log(
              Level.INFO, "rm -rf output " + result.stdout() + " err " + result.stderr());


      StringBuffer cmdLine = new StringBuffer()
              .append(" kubectl delete -f ")
              .append(okepvcYaml);

      ExecCommand.exec(cmdLine.toString());
      cmdLine = new StringBuffer()
              .append(" kubectl delete -f ")
              .append(okepvYaml);
      ExecCommand.exec(cmdLine.toString());
    } catch (Exception ex) {
      LoggerHelper.getLocal().log(Level.INFO, "WARNING: cleaning entire domain home dirs failed ");
    }
  }

  private void upgradeTraefikNamespace() throws Exception {

    String namespace = "{" + domainNS1 + ",montesttraefikns,monitortestns}";
    LoggerHelper.getLocal().log(Level.INFO, "namespace to update" + namespace);
    StringBuffer cmd = new StringBuffer("helm upgrade ");
    cmd.append(" traefik-monitoring")
            .append(" stable/traefik ")
            .append("--namespace montesttraefikns ")
            .append("--reuse-values ")
            .append("--set ")
            .append("\"")
            .append("kubernetes.namespaces=")
            .append(namespace)
            .append("\"");

    if (BaseTest.HELM_VERSION.equals("V2")) {
      if (BaseTest.OKE_CLUSTER) {
        //cmd.append(" --cleanup-on-fail --force --timeout 240");
        cmd.append(" --debug --timeout 240");
      } else {
        cmd.append("  --wait --timeout 240");
      }
    }
    if (BaseTest.HELM_VERSION.equals("V3")) {
      if (BaseTest.OKE_CLUSTER) {
        cmd.append(" --debug  --timeout 4m0s");
      } else {
        cmd.append("  --wait --timeout 4m0s");
      }
    }
    LoggerHelper.getLocal().log(Level.INFO, " upgradeTraefikNamespace() Running " + cmd.toString());
    ExecResult result = ExecCommand.exec(cmd.toString());
    if (result.exitValue() != 0) {
      throw new Exception("Helm Upgrade Failed" + cmd.toString() + result.stderr());
    }
    String outputStr = result.stdout().trim();
    LoggerHelper.getLocal().log(Level.INFO, "Command returned " + outputStr);
  }

}
